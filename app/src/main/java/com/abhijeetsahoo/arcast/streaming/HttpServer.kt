package com.abhijeetsahoo.arcast.streaming

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * HTTP Server for streaming camera and audio data
 */
class HttpServer(
    private val context: Context,
    port: Int
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "HttpServer"
        private const val MIME_MJPEG = "multipart/x-mixed-replace; boundary=--frame"
        private const val MIME_JPEG = "image/jpeg"
        private const val MIME_HTML = "text/html"
    }

    // Track connected clients for statistics
    private val connectedClients = AtomicInteger(0)

    // Latest frame for snapshot
    @Volatile
    private var latestFrame: ByteArray? = null

    // Queues for each client to handle streaming
    private val clientQueues = ConcurrentHashMap<String, LinkedBlockingQueue<ByteArray>>()

    // Track if server is streaming
    private val isStreaming = AtomicBoolean(false)

    // Flag to control streaming
    private val streamingEnabled = AtomicBoolean(false)

    // Current streaming mode
    private var streamingMode = StreamingMode.VIDEO_ONLY

    // Streaming quality
    private var streamQuality = StreamQuality.MEDIUM

    // Protocol settings
    private var streamProtocol = StreamProtocol.HTTP

    /**
     * Initialize server
     */
    init {
        Log.i(TAG, "Starting HTTP server on port $port")
    }

    /**
     * Handle incoming HTTP requests
     */
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.d(TAG, "Received request: $uri from ${session.remoteHostName}")

        // Handle request based on URI
        return when {
            uri.equals("/") -> serveIndexPage(session)
            uri.equals("/image") -> serveImageSnapshot(session)
            uri.equals("/video") -> serveVideoStream(session)
            uri.equals("/audio") -> serveAudioStream(session)
            uri.equals("/stream") -> serveFullStream(session)
            uri.equals("/status") -> serveStatusPage(session)
            uri.equals("/api/status") -> serveStatusJson(session)
            uri.contains("/assets/") -> serveAsset(session)
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_HTML,
                "<html><body><h1>404 Not Found</h1><p>The requested resource was not found.</p></body></html>"
            )
        }
    }

    /**
     * Serve the index page with controls
     */
    private fun serveIndexPage(session: IHTTPSession): Response {
        val indexHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>ARCast - Camera Stream</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f7f7f7; color: #333; }
                    h1 { color: #2c3e50; text-align: center; }
                    .container { max-width: 900px; margin: 0 auto; }
                    .stream-container { width: 100%; background: #000; position: relative; border-radius: 8px; overflow: hidden; }
                    .stream-image { width: 100%; display: block; }
                    .control-panel { background: white; padding: 15px; margin-top: 20px; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
                    .btn { background: #3498db; color: white; border: none; padding: 10px 15px; border-radius: 4px; cursor: pointer; margin: 5px; }
                    .btn:hover { background: #2980b9; }
                    .mode-select { padding: 10px; margin: 10px 0; }
                    .status { background: #e74c3c; color: white; padding: 10px; border-radius: 4px; text-align: center; margin-bottom: 15px; }
                    .status.connected { background: #2ecc71; }
                    .section { margin-bottom: 15px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>ARCast Camera Stream</h1>
                    
                    <div id="status" class="status">Connecting...</div>
                    
                    <div class="stream-container">
                        <img id="streamImage" class="stream-image" src="video" alt="Camera Stream">
                    </div>
                    
                    <div class="control-panel">
                        <div class="section">
                            <h3>Stream Controls</h3>
                            <button id="captureButton" class="btn">Capture Snapshot</button>
                            <button id="audioToggleButton" class="btn">Toggle Audio</button>
                            <select id="modeSelect" class="mode-select">
                                <option value="image">Image Only</option>
                                <option value="video" selected>Video Only</option>
                                <option value="audio">Audio Only</option>
                                <option value="stream">Video + Audio</option>
                            </select>
                        </div>
                        
                        <div class="section">
                            <h3>Camera Controls</h3>
                            <button id="switchCameraButton" class="btn">Switch Camera</button>
                            <button id="flashButton" class="btn">Toggle Flash</button>
                        </div>
                    </div>
                </div>
                
                <script>
                    // Connect to stream
                    document.addEventListener('DOMContentLoaded', function() {
                        // Update UI based on connection
                        const statusElement = document.getElementById('status');
                        statusElement.innerText = 'Connected';
                        statusElement.classList.add('connected');
                        
                        // Set up button actions
                        document.getElementById('captureButton').addEventListener('click', function() {
                            window.open('/image', '_blank');
                        });
                        
                        // Mode selection
                        document.getElementById('modeSelect').addEventListener('change', function(e) {
                            const mode = e.target.value;
                            const streamImage = document.getElementById('streamImage');
                            streamImage.src = mode;
                        });
                        
                        // Fetch status periodically
                        setInterval(function() {
                            fetch('/api/status')
                            .then(response => response.json())
                            .then(data => {
                                console.log(data);
                                // Update UI with status info
                            })
                            .catch(err => console.error('Error fetching status:', err));
                        }, 5000);
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(indexHtml)
    }

    /**
     * Serve a still image snapshot
     */
    private fun serveImageSnapshot(session: IHTTPSession): Response {
        val imageData = latestFrame ?: ByteArray(0)

        return if (imageData.isNotEmpty()) {
            newFixedLengthResponse(Response.Status.OK, MIME_JPEG, ByteArrayInputStream(imageData), imageData.size.toLong())
        } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "No image available")
        }
    }

    /**
     * Serve video stream (MJPEG)
     */
    private fun serveVideoStream(session: IHTTPSession): Response {
        val clientId = session.remoteHostName + ":" + session.remoteIpAddress

        // Increment connected client count
        connectedClients.incrementAndGet()

        Log.i(TAG, "Client connected to video stream: $clientId, total clients: ${connectedClients.get()}")

        // Create a queue for this client
        val queue = LinkedBlockingQueue<ByteArray>(10) // Buffer up to 10 frames
        clientQueues[clientId] = queue

        // Create piped streams for MJPEG streaming
        val pipedOutputStream = PipedOutputStream()
        val pipedInputStream = PipedInputStream(pipedOutputStream)

        val response = newChunkedResponse(Response.Status.OK, MIME_MJPEG, pipedInputStream)

        // Use a thread to send frames to this client
        Thread {
            try {
                // Send MJPEG header
                pipedOutputStream.write("--frame\r\n".toByteArray())

                while (!response.isCloseConnection) {
                    // Wait for next frame
                    val frame = queue.take()

                    try {
                        // Write MJPEG part header
                        pipedOutputStream.write("Content-Type: image/jpeg\r\n".toByteArray())
                        pipedOutputStream.write("Content-Length: ${frame.size}\r\n\r\n".toByteArray())
                        pipedOutputStream.write(frame)
                        pipedOutputStream.write("\r\n--frame\r\n".toByteArray())
                        pipedOutputStream.flush()
                    } catch (e: IOException) {
                        Log.w(TAG, "Error sending frame to client: $clientId", e)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in streaming thread for client: $clientId", e)
            } finally {
                // Clean up when client disconnects
                try {
                    pipedOutputStream.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing stream", e)
                }

                clientQueues.remove(clientId)
                connectedClients.decrementAndGet()
                Log.i(TAG, "Client disconnected from video stream: $clientId, total clients: ${connectedClients.get()}")
            }
        }.start()

        return response
    }

    /**
     * Serve audio stream
     */
    private fun serveAudioStream(session: IHTTPSession): Response {
        // This is a placeholder for audio streaming
        // Would need to implement audio recording and WebSocket or other streaming method
        return newFixedLengthResponse(
            Response.Status.NOT_IMPLEMENTED,
            MIME_HTML,
            "<html><body><h1>Audio Streaming</h1><p>Audio streaming not implemented yet.</p></body></html>"
        )
    }

    /**
     * Serve full stream (video + audio)
     */
    private fun serveFullStream(session: IHTTPSession): Response {
        // For combined streams, we'd use WebRTC or similar
        // For now, just serve the video stream
        return serveVideoStream(session)
    }

    /**
     * Serve status page
     */
    private fun serveStatusPage(session: IHTTPSession): Response {
        val statusHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>ARCast - Status</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f7f7f7; color: #333; }
                    h1 { color: #2c3e50; text-align: center; }
                    .container { max-width: 800px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
                    .info-row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #eee; }
                    .label { font-weight: bold; }
                    .value { color: #3498db; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Server Status</h1>
                    
                    <div class="info-row">
                        <span class="label">Status:</span>
                        <span class="value">Running</span>
                    </div>
                    
                    <div class="info-row">
                        <span class="label">Connected Clients:</span>
                        <span class="value">${connectedClients.get()}</span>
                    </div>
                    
                    <div class="info-row">
                        <span class="label">Streaming Mode:</span>
                        <span class="value">${streamingMode.name}</span>
                    </div>
                    
                    <div class="info-row">
                        <span class="label">Stream Quality:</span>
                        <span class="value">${streamQuality.name}</span>
                    </div>
                    
                    <div class="info-row">
                        <span class="label">Protocol:</span>
                        <span class="value">${streamProtocol.name}</span>
                    </div>
                    
                    <div class="info-row">
                        <span class="label">Available Endpoints:</span>
                        <span class="value">/image, /video, /audio, /stream</span>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(statusHtml)
    }

    /**
     * Serve status as JSON for API clients
     */
    private fun serveStatusJson(session: IHTTPSession): Response {
        val statusJson = """
            {
                "status": "running",
                "connectedClients": ${connectedClients.get()},
                "streamingMode": "${streamingMode.name}",
                "streamQuality": "${streamQuality.name}",
                "protocol": "${streamProtocol.name}",
                "endpoints": ["/image", "/video", "/audio", "/stream"]
            }
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "application/json", statusJson)
    }

    /**
     * Serve static assets (CSS, JS, etc.)
     */
    private fun serveAsset(session: IHTTPSession): Response {
        val uri = session.uri.removePrefix("/assets/")

        try {
            val inputStream = context.assets.open(uri)
            val mimeType = getMimeType(uri)

            return newChunkedResponse(Response.Status.OK, mimeType, inputStream)
        } catch (e: IOException) {
            Log.e(TAG, "Error serving asset: $uri", e)
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "Asset not found")
        }
    }

    /**
     * Get MIME type based on file extension
     */
    private fun getMimeType(filename: String): String {
        return when {
            filename.endsWith(".html") -> "text/html"
            filename.endsWith(".css") -> "text/css"
            filename.endsWith(".js") -> "application/javascript"
            filename.endsWith(".jpg") -> "image/jpeg"
            filename.endsWith(".png") -> "image/png"
            filename.endsWith(".svg") -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }

    /**
     * Process new frame from camera
     */
    fun updateFrame(data: ByteArray?, width: Int, height: Int, format: Int) {
        if (data == null || !streamingEnabled.get()) return

        try {
            // Convert to JPEG for streaming
            val jpegData = convertToJpeg(data, width, height, format, streamQuality.jpegQuality)

            // Update latest frame
            latestFrame = jpegData

            // Send to all connected clients
            for (queue in clientQueues.values) {
                // Non-blocking update to prevent slow clients from affecting others
                // If queue is full, we drop this frame for that client
                if (!queue.offer(jpegData)) {
                    Log.v(TAG, "Dropping frame for slow client")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        }
    }

    /**
     * Convert camera data to JPEG
     */
    private fun convertToJpeg(data: ByteArray, width: Int, height: Int, format: Int, quality: Int): ByteArray {
        if (format == ImageFormat.NV21 || format == ImageFormat.YUV_420_888) {
            val yuvImage = YuvImage(data, format, width, height, null)
            val jpegStream = ByteArrayOutputStream()

            if (yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, jpegStream)) {
                return jpegStream.toByteArray()
            }
        } else {
            // For other formats like JPEG, we might not need conversion
            // This depends on what format is being used by the camera
        }

        // Fallback: just return the original data
        return data
    }

    /**
     * Start streaming
     */
    fun startStreaming(mode: StreamingMode, quality: StreamQuality, protocol: StreamProtocol) {
        streamingMode = mode
        streamQuality = quality
        streamProtocol = protocol
        streamingEnabled.set(true)
        Log.i(TAG, "Streaming started with mode: $mode, quality: $quality, protocol: $protocol")
    }

    /**
     * Stop streaming
     */
    fun stopStreaming() {
        streamingEnabled.set(false)
        Log.i(TAG, "Streaming stopped")
    }

    /**
     * Get connected clients count
     */
    fun getConnectedClientsCount(): Int {
        return connectedClients.get()
    }

    /**
     * Check if streaming is active
     */
    fun isStreaming(): Boolean {
        return streamingEnabled.get()
    }
}
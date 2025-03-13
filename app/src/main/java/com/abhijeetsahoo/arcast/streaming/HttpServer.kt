package com.abhijeetsahoo.arcast.streaming

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.abhijeetsahoo.arcast.R
import com.abhijeetsahoo.arcast.utils.ErrorHandler
import com.abhijeetsahoo.arcast.utils.Logger
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * HTTP Server implementation for streaming camera content
 */
class HttpServer(private val context: Context, port: Int = 8080) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "HttpServer"
        private const val MJPEG_BOUNDARY = "ARCastBoundary"
        private const val MIME_JPEG = "image/jpeg"
        private const val MIME_MJPEG = "multipart/x-mixed-replace;boundary=$MJPEG_BOUNDARY"
        private const val MIME_PLAINTEXT = "text/plain"
    }

    // Current frame being streamed
    @Volatile
    private var currentFrame: ByteArray? = null

    // Counter for connected clients
    private val connectedClients = AtomicInteger(0)

    // Callbacks for client connection events
    private var onClientConnected: ((Int) -> Unit)? = null
    private var onClientDisconnected: ((Int) -> Unit)? = null

    // Start with a default "waiting" image
    init {
        try {
            // Load a default image from resources
            val defaultBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_camera)
            val outputStream = ByteArrayOutputStream()
            defaultBitmap?.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            currentFrame = outputStream.toByteArray()

            Logger.i(TAG, "HTTP Server initialized with default image")
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Failed to initialize default image", e)
        }
    }

    /**
     * Update the current frame being streamed
     */
    fun updateFrame(jpegData: ByteArray) {
        currentFrame = jpegData
    }

    /**
     * Set callback for client connection events
     */
    fun setOnClientConnectedListener(listener: (Int) -> Unit) {
        onClientConnected = listener
    }

    /**
     * Set callback for client disconnection events
     */
    fun setOnClientDisconnectedListener(listener: (Int) -> Unit) {
        onClientDisconnected = listener
    }

    /**
     * Get the current number of connected clients
     */
    fun getConnectedClientCount(): Int {
        return connectedClients.get()
    }

    override fun serve(session: IHTTPSession): Response {
        try {
            val uri = session.uri
            Log.d(TAG, "Received request: $uri")

            return when (uri) {
                "/" -> serveIndexPage(session)
                "/stream" -> serveMjpegStream(session)
                "/snapshot" -> serveSnapshot(session)
                "/info" -> serveDeviceInfo(session)
                else -> serveStaticContent(session)
            }
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error serving HTTP request", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Server Error: ${e.message}"
            )
        }
    }

    /**
     * Serve the main index HTML page
     */
    private fun serveIndexPage(session: IHTTPSession): Response {
        try {
            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>ARCast Stream</title>
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            margin: 0;
                            padding: 0;
                            display: flex;
                            flex-direction: column;
                            height: 100vh;
                            background-color: #121212;
                            color: #FFFFFF;
                        }
                        .header {
                            padding: 10px;
                            background-color: #333;
                            text-align: center;
                        }
                        .content {
                            flex: 1;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            padding: 10px;
                        }
                        .footer {
                            padding: 10px;
                            background-color: #333;
                            text-align: center;
                            font-size: 0.8em;
                        }
                        .streamContainer {
                            text-align: center;
                            max-width: 100%;
                        }
                        #streamImage {
                            max-width: 100%;
                            height: auto;
                            border: 1px solid #444;
                        }
                        .controls {
                            margin-top: 10px;
                            display: flex;
                            justify-content: center;
                            gap: 10px;
                        }
                        button {
                            padding: 8px 16px;
                            background-color: #4CAF50;
                            border: none;
                            color: white;
                            border-radius: 4px;
                            cursor: pointer;
                        }
                        button:hover {
                            background-color: #45a049;
                        }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h1>ARCast Stream</h1>
                    </div>
                    <div class="content">
                        <div class="streamContainer">
                            <img id="streamImage" src="/stream" alt="Camera Stream">
                            <div class="controls">
                                <button onclick="takeSnapshot()">Take Snapshot</button>
                                <button onclick="showInfo()">Show Info</button>
                            </div>
                        </div>
                    </div>
                    <div class="footer">
                        <p>ARCast - Augmented Reality Camera Streaming</p>
                    </div>
                    
                    <script>
                        function takeSnapshot() {
                            window.open('/snapshot', '_blank');
                        }
                        
                        function showInfo() {
                            fetch('/info')
                                .then(response => response.json())
                                .then(data => {
                                    alert('Device Info:\\nModel: ' + data.model + 
                                          '\\nResolution: ' + data.resolution +
                                          '\\nConnected Clients: ' + data.clients);
                                })
                                .catch(error => {
                                    alert('Error fetching device info');
                                });
                        }
                    </script>
                </body>
                </html>
            """.trimIndent()

            return newFixedLengthResponse(Response.Status.OK, "text/html", htmlContent)
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error serving index page", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Server Error: ${e.message}"
            )
        }
    }

    /**
     * Serve a MJPEG stream of the camera
     */
    private fun serveMjpegStream(session: IHTTPSession): Response {
        // Increment connected client count
        val count = connectedClients.incrementAndGet()
        onClientConnected?.invoke(count)

        // Create a chunked response for streaming
        val response = newChunkedResponse(
            Response.Status.OK,
            MIME_MJPEG,
            object : InputStream() {
                private var closed = false
                private var isHeaderSent = false
                private var currentFrameData: ByteArray? = null
                private var currentPosition = 0

                private fun updateCurrentFrame() {
                    currentFrameData = currentFrame
                    currentPosition = 0
                }

                override fun read(): Int {
                    if (closed) return -1

                    try {
                        if (!isHeaderSent) {
                            // Send MJPEG header
                            val header = "--$MJPEG_BOUNDARY\r\nContent-Type: $MIME_JPEG\r\n\r\n"
                            val headerBytes = header.toByteArray()

                            if (currentPosition < headerBytes.size) {
                                return headerBytes[currentPosition++].toInt() and 0xFF
                            } else {
                                isHeaderSent = true
                                updateCurrentFrame()
                                currentPosition = 0
                            }
                        }

                        val frame = currentFrameData
                        if (frame != null && currentPosition < frame.size) {
                            return frame[currentPosition++].toInt() and 0xFF
                        } else {
                            // Send frame boundary
                            val boundary = "\r\n--$MJPEG_BOUNDARY\r\nContent-Type: $MIME_JPEG\r\n\r\n"
                            val boundaryBytes = boundary.toByteArray()

                            if (currentPosition < frame?.size ?: 0 + boundaryBytes.size) {
                                val adjustedPos = currentPosition - (frame?.size ?: 0)
                                if (adjustedPos >= 0 && adjustedPos < boundaryBytes.size) {
                                    return boundaryBytes[adjustedPos].toInt() and 0xFF
                                }
                            }

                            // Reset for next frame
                            isHeaderSent = false
                            currentPosition = 0

                            // Small delay to control frame rate
                            Thread.sleep(33) // ~30fps

                            // Return recursive call to start next frame
                            return read()
                        }
                    } catch (e: Exception) {
                        ErrorHandler.handleException(context, TAG, "Error streaming MJPEG", e)
                        closed = true
                        // Decrement connected client count
                        val newCount = connectedClients.decrementAndGet()
                        onClientDisconnected?.invoke(newCount)
                        return -1
                    }
                }

                override fun close() {
                    if (!closed) {
                        closed = true
                        // Decrement connected client count
                        val newCount = connectedClients.decrementAndGet()
                        onClientDisconnected?.invoke(newCount)
                    }
                    super.close()
                }
            }
        )

        return response
    }

    /**
     * Serve a single JPEG snapshot
     */
    private fun serveSnapshot(session: IHTTPSession): Response {
        try {
            val frame = currentFrame
            return if (frame != null) {
                newFixedLengthResponse(
                    Response.Status.OK,
                    MIME_JPEG,
                    ByteArrayInputStream(frame),
                    frame.size.toLong()
                )
            } else {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "No image available"
                )
            }
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error serving snapshot", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Server Error: ${e.message}"
            )
        }
    }

    /**
     * Serve device information as JSON
     */
    private fun serveDeviceInfo(session: IHTTPSession): Response {
        try {
            val info = """
                {
                    "model": "${android.os.Build.MODEL}",
                    "resolution": "1280x720",
                    "clients": ${connectedClients.get()}
                }
            """.trimIndent()

            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                info
            )
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error serving device info", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Server Error: ${e.message}"
            )
        }
    }

    /**
     * Serve static content
     */
    private fun serveStaticContent(session: IHTTPSession): Response {
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            MIME_PLAINTEXT,
            "Not found: ${session.uri}"
        )
    }

    // No duplicate companion object needed
}
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
import java.util.concurrent.atomic.AtomicReference

/**
 * HTTP Server implementation for streaming camera content
 */
class HttpServer(private val context: Context, port: Int = 8080) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "HttpServer"
        private const val MJPEG_BOUNDARY = "ARCastBoundary"
        private const val AUDIO_BOUNDARY = "ARCastAudioBoundary"

        // MIME types
        private const val MIME_JPEG = "image/jpeg"
        private const val MIME_MJPEG = "multipart/x-mixed-replace;boundary=$MJPEG_BOUNDARY"
        private const val MIME_PLAINTEXT = "text/plain"
        private const val MIME_HTML = "text/html"
        private const val MIME_JSON = "application/json"
        private const val MIME_AUDIO_STREAM = "multipart/x-mixed-replace;boundary=$AUDIO_BOUNDARY"
        private const val MIME_AUDIO_PCM = "audio/pcm"
    }

    // Current frames and data being streamed
    @Volatile
    private var currentFrame = AtomicReference<ByteArray>()

    @Volatile
    private var currentAudioData = AtomicReference<ByteArray>()

    // Counter for connected clients
    private val connectedClients = AtomicInteger(0)

    // Callbacks for client connection events
    private var onClientConnected: ((Int) -> Unit)? = null
    private var onClientDisconnected: ((Int) -> Unit)? = null

    // Current streaming mode
    private var streamingMode = StreamingMode.VIDEO_ONLY

    // Start with a default "waiting" image
    init {
        try {
            // Load a default image from resources
            val defaultBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_camera)
            val outputStream = ByteArrayOutputStream()
            defaultBitmap?.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            currentFrame.set(outputStream.toByteArray())

            // Initialize audio data with empty array
            currentAudioData.set(ByteArray(0))

            Logger.i(TAG, "HTTP Server initialized with default image")
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Failed to initialize default image", e)
        }
    }

    /**
     * Set the current streaming mode
     */
    fun setStreamingMode(mode: StreamingMode) {
        this.streamingMode = mode
        Logger.i(TAG, "Streaming mode set to: $mode")
    }

    /**
     * Update the current frame being streamed
     */
    fun updateFrame(jpegData: ByteArray) {
        currentFrame.set(jpegData)
    }

    /**
     * Update the current audio data being streamed
     */
    fun updateAudioData(audioData: ByteArray) {
        currentAudioData.set(audioData)
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
                "/stream" -> serveFullStream(session)
                "/video" -> serveVideoStream(session)
                "/audio" -> serveAudioStream(session)
                "/image" -> serveImageSnapshot(session)
                "/snapshot" -> serveImageSnapshot(session)
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
            val htmlContent = generateHtmlContent()
            return newFixedLengthResponse(Response.Status.OK, MIME_HTML, htmlContent)
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
     * Generate HTML content based on current streaming mode
     */
    private fun generateHtmlContent(): String {
        val modeSpecificContent = when (streamingMode) {
            StreamingMode.IMAGE_ONLY -> """
                <h3>Image Only Mode</h3>
                <div class="streamContainer">
                    <img id="imageSnapshot" src="/image" alt="Latest Image" />
                    <div class="controls">
                        <button onclick="refreshImage()">Refresh Image</button>
                    </div>
                </div>
                <script>
                    function refreshImage() {
                        const img = document.getElementById('imageSnapshot');
                        img.src = '/image?' + new Date().getTime();
                    }
                    
                    // Auto refresh every 5 seconds
                    setInterval(refreshImage, 5000);
                </script>
            """.trimIndent()

            StreamingMode.AUDIO_ONLY -> """
                <h3>Audio Only Mode</h3>
                <div class="streamContainer">
                    <div class="audioIndicator">
                        <div class="audioWave"></div>
                        <p>Audio Streaming Active</p>
                    </div>
                    <div class="controls">
                        <button id="audioToggle" onclick="toggleAudio()">Pause Audio</button>
                    </div>
                </div>
                <script>
                    // Audio playback would need to be implemented
                    // This is just a placeholder UI
                    let audioPlaying = true;
                    
                    function toggleAudio() {
                        audioPlaying = !audioPlaying;
                        const btn = document.getElementById('audioToggle');
                        btn.textContent = audioPlaying ? 'Pause Audio' : 'Resume Audio';
                        
                        const indicator = document.querySelector('.audioWave');
                        indicator.style.animationPlayState = audioPlaying ? 'running' : 'paused';
                    }
                </script>
                <style>
                    .audioIndicator {
                        text-align: center;
                        padding: 20px;
                        background-color: #222;
                        border-radius: 10px;
                        margin-bottom: 20px;
                    }
                    .audioWave {
                        height: 60px;
                        background: linear-gradient(#4CAF50, #4CAF50) center/2px 60% no-repeat,
                                    linear-gradient(#4CAF50, #4CAF50) 10%/2px 80% no-repeat,
                                    linear-gradient(#4CAF50, #4CAF50) 20%/2px 40% no-repeat,
                                    linear-gradient(#4CAF50, #4CAF50) 30%/2px 70% no-repeat,
                                    linear-gradient(#4CAF50, #4CAF50) 40%/2px 90% no-repeat,
                                    linear-gradient(#4CAF50, #4CAF50) 50%/2px 60% no-repeat,
                                    linear-gradient(#4CAF50, #4CAF50) 60%/2px 100% no-repeat,
                                    linear-gradient(#4CAF50, #4CAF50) 70%/2px 70% no-repeat,
                                    linear-gradient(#4CAF50, #4CAF50) 80%/2px 50% no-repeat,
                                    linear-gradient(#4CAF50, #4CAF50) 90%/2px 60% no-repeat;
                        animation: wave 1s infinite;
                    }
                    @keyframes wave {
                        0% { opacity: 0.5; }
                        50% { opacity: 1; }
                        100% { opacity: 0.5; }
                    }
                </style>
            """.trimIndent()

            StreamingMode.VIDEO_ONLY -> """
                <h3>Video Only Mode</h3>
                <div class="streamContainer">
                    <img id="streamImage" src="/video" alt="Video Stream" />
                    <div class="controls">
                        <button onclick="takeSnapshot()">Take Snapshot</button>
                    </div>
                </div>
            """.trimIndent()

            StreamingMode.VIDEO_AUDIO -> """
                <h3>Video &amp; Audio Mode</h3>
                <div class="streamContainer">
                    <img id="streamImage" src="/stream" alt="Full Stream" />
                    <div class="audioControls">
                        <button id="muteButton" onclick="toggleMute()">Mute Audio</button>
                        <input type="range" id="volumeSlider" min="0" max="100" value="80" oninput="changeVolume(this.value)" />
                    </div>
                    <div class="controls">
                        <button onclick="takeSnapshot()">Take Snapshot</button>
                    </div>
                </div>
                <script>
                    let audioMuted = false;
                    
                    function toggleMute() {
                        audioMuted = !audioMuted;
                        const btn = document.getElementById('muteButton');
                        btn.textContent = audioMuted ? 'Unmute Audio' : 'Mute Audio';
                        
                        // In a real implementation, this would control an audio element
                    }
                    
                    function changeVolume(value) {
                        console.log('Volume set to: ' + value);
                        // In a real implementation, this would set the volume of an audio element
                    }
                </script>
            """.trimIndent()
        }

        return """
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
                        flex-direction: column;
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
                    #streamImage, #imageSnapshot {
                        max-width: 100%;
                        height: auto;
                        border: 1px solid #444;
                    }
                    .controls, .audioControls {
                        margin-top: 10px;
                        display: flex;
                        justify-content: center;
                        gap: 10px;
                        padding: 10px;
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
                    input[type=range] {
                        width: 150px;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>ARCast Stream</h1>
                </div>
                <div class="content">
                    $modeSpecificContent
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
    }

    /**
     * Serve a full stream (video + audio)
     */
    private fun serveFullStream(session: IHTTPSession): Response {
        // For now, just serve video
        // In a real implementation, this would use a more sophisticated
        // method to stream synchronized audio and video
        return serveVideoStream(session)
    }

    /**
     * Serve a MJPEG video stream
     */
    private fun serveVideoStream(session: IHTTPSession): Response {
        // Increment connected client count
        val count = connectedClients.incrementAndGet()
        onClientConnected?.invoke(count)

        // Create a chunked response for streaming
        val response = newChunkedResponse(
            Response.Status.OK,
            MIME_MJPEG,
            createMjpegStream()
        )

        return response
    }

    /**
     * Create an input stream that outputs MJPEG data
     */
    private fun createMjpegStream(): InputStream {
        return object : InputStream() {
            private var closed = false
            private var isHeaderSent = false
            private var currentFrameData: ByteArray? = null
            private var currentPosition = 0

            private fun updateCurrentFrame() {
                currentFrameData = currentFrame.get()
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

                        if (currentPosition < (frame?.size ?: 0) + boundaryBytes.size) {
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
    }

    /**
     * Serve an audio stream
     */
    private fun serveAudioStream(session: IHTTPSession): Response {
        // Increment connected client count
        val count = connectedClients.incrementAndGet()
        onClientConnected?.invoke(count)

        // Create a chunked response for streaming
        val response = newChunkedResponse(
            Response.Status.OK,
            MIME_AUDIO_STREAM,
            createAudioStream()
        )

        return response
    }

    /**
     * Create an input stream that outputs audio data
     */
    private fun createAudioStream(): InputStream {
        return object : InputStream() {
            private var closed = false
            private var isHeaderSent = false
            private var currentAudioChunk: ByteArray? = null
            private var currentPosition = 0

            private fun updateCurrentAudio() {
                currentAudioChunk = currentAudioData.get()
                currentPosition = 0
            }

            override fun read(): Int {
                if (closed) return -1

                try {
                    if (!isHeaderSent) {
                        // Send audio boundary header
                        val header = "--$AUDIO_BOUNDARY\r\nContent-Type: $MIME_AUDIO_PCM\r\n\r\n"
                        val headerBytes = header.toByteArray()

                        if (currentPosition < headerBytes.size) {
                            return headerBytes[currentPosition++].toInt() and 0xFF
                        } else {
                            isHeaderSent = true
                            updateCurrentAudio()
                            currentPosition = 0
                        }
                    }

                    val audio = currentAudioChunk
                    if (audio != null && audio.isNotEmpty() && currentPosition < audio.size) {
                        return audio[currentPosition++].toInt() and 0xFF
                    } else {
                        // Send chunk boundary
                        val boundary = "\r\n--$AUDIO_BOUNDARY\r\nContent-Type: $MIME_AUDIO_PCM\r\n\r\n"
                        val boundaryBytes = boundary.toByteArray()

                        if (currentPosition < (audio?.size ?: 0) + boundaryBytes.size) {
                            val adjustedPos = currentPosition - (audio?.size ?: 0)
                            if (adjustedPos >= 0 && adjustedPos < boundaryBytes.size) {
                                return boundaryBytes[adjustedPos].toInt() and 0xFF
                            }
                        }

                        // Reset for next chunk
                        isHeaderSent = false
                        currentPosition = 0

                        // Small delay
                        Thread.sleep(20)

                        // Return recursive call to start next chunk
                        return read()
                    }
                } catch (e: Exception) {
                    ErrorHandler.handleException(context, TAG, "Error streaming audio", e)
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
    }

    /**
     * Serve a single JPEG snapshot
     */
    private fun serveImageSnapshot(session: IHTTPSession): Response {
        try {
            val frame = currentFrame.get()
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
                    "clients": ${connectedClients.get()},
                    "mode": "${streamingMode.name}"
                }
            """.trimIndent()

            return newFixedLengthResponse(
                Response.Status.OK,
                MIME_JSON,
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
}
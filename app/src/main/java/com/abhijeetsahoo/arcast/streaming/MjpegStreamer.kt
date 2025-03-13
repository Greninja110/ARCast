package com.abhijeetsahoo.arcast.streaming

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.abhijeetsahoo.arcast.utils.ErrorHandler
import com.abhijeetsahoo.arcast.utils.Logger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Processor for camera frames that converts them to MJPEG format for streaming
 */
class MjpegStreamer(private val context: Context) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "MjpegStreamer"
        private const val DEFAULT_JPEG_QUALITY = 80
    }

    // HTTP server for streaming
    private var httpServer: HttpServer? = null

    // Stream processing settings
    private var jpegQuality = DEFAULT_JPEG_QUALITY
    private var isStreaming = false
    private var frameSkip = 0
    private var frameCount = 0

    // Executor for background processing
    private val processingExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Client connection callbacks
    private var onClientCountChanged: ((Int) -> Unit)? = null

    /**
     * Start the MJPEG streamer
     */
    fun start(port: Int = 8080) {
        if (!isStreaming) {
            try {
                httpServer = HttpServer(context, port).apply {
                    // Set up client callbacks
                    setOnClientConnectedListener { count ->
                        Logger.i(TAG, "Client connected. Total: $count")
                        onClientCountChanged?.invoke(count)
                    }

                    setOnClientDisconnectedListener { count ->
                        Logger.i(TAG, "Client disconnected. Total: $count")
                        onClientCountChanged?.invoke(count)
                    }

                    // Start the server
                    start()
                }

                isStreaming = true
                Logger.i(TAG, "MJPEG Streamer started on port $port")
            } catch (e: Exception) {
                ErrorHandler.handleException(context, TAG, "Failed to start streamer", e)
            }
        }
    }

    /**
     * Stop the MJPEG streamer
     */
    fun stop() {
        if (isStreaming) {
            try {
                httpServer?.stop()
                httpServer = null
                isStreaming = false
                Logger.i(TAG, "MJPEG Streamer stopped")
            } catch (e: Exception) {
                ErrorHandler.handleException(context, TAG, "Failed to stop streamer", e)
            }
        }
    }

    /**
     * Set JPEG quality (1-100)
     */
    fun setJpegQuality(quality: Int) {
        jpegQuality = quality.coerceIn(1, 100)
    }

    /**
     * Set frame skip value (0 = process every frame, 1 = skip every other frame, etc.)
     */
    fun setFrameSkip(skip: Int) {
        frameSkip = skip.coerceAtLeast(0)
    }

    /**
     * Set callback for client count changes
     */
    fun setOnClientCountChangedListener(listener: (Int) -> Unit) {
        onClientCountChanged = listener

        // Immediately invoke with current count
        httpServer?.let { server ->
            listener.invoke(server.getConnectedClientCount())
        }
    }

    /**
     * Get the current number of connected clients
     */
    fun getConnectedClientCount(): Int {
        return httpServer?.getConnectedClientCount() ?: 0
    }

    /**
     * Process a frame from the camera
     */
    override fun analyze(image: ImageProxy) {
        try {
            if (isStreaming) {
                // Apply frame skipping if configured
                if (frameSkip > 0) {
                    if (frameCount % (frameSkip + 1) != 0) {
                        frameCount++
                        image.close()
                        return
                    }
                }
                frameCount++

                // Process the image in a background thread to avoid blocking camera
                processingExecutor.execute {
                    try {
                        // Convert image to JPEG bytes
                        val jpegBytes = imageToJpegBytes(image)

                        // Update the current frame in the HTTP server
                        httpServer?.updateFrame(jpegBytes)
                    } catch (e: Exception) {
                        ErrorHandler.handleException(context, TAG, "Failed to process image", e)
                    } finally {
                        // Make sure we close the image
                        image.close()
                    }
                }
            } else {
                // Just close the image if we're not processing
                image.close()
            }
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error in image analyzer", e)
            image.close()
        }
    }

    /**
     * Convert an ImageProxy to JPEG bytes
     */
    private fun imageToJpegBytes(image: ImageProxy): ByteArray {
        // Get the YUV image
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        // Convert to Jpeg
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), jpegQuality, out)

        // Rotate the image if needed
        val jpegBytes = out.toByteArray()
        return if (image.imageInfo.rotationDegrees != 0) {
            rotateJpegBytes(jpegBytes, image.imageInfo.rotationDegrees)
        } else {
            jpegBytes
        }
    }

    /**
     * Rotate a JPEG image by specified degrees
     */
    private fun rotateJpegBytes(jpegBytes: ByteArray, rotationDegrees: Int): ByteArray {
        // Decode the JPEG
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        // Create a matrix for rotation
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())

        // Create a rotated bitmap
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        // Convert back to JPEG
        val out = ByteArrayOutputStream()
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)

        // Clean up
        bitmap.recycle()
        rotatedBitmap.recycle()

        return out.toByteArray()
    }

    /**
     * Release all resources
     */
    fun release() {
        stop()
        processingExecutor.shutdown()
    }
}
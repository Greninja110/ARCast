package com.abhijeetsahoo.arcast.streaming

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Class for handling MJPEG streaming of camera frames
 */
class MjpegStreamer(private val context: Context) {
    companion object {
        private const val TAG = "MjpegStreamer"
    }

    // Reference to the HTTP server
    private var httpServer: HttpServer? = null

    // Background thread for processing
    private val executor = Executors.newSingleThreadExecutor()

    // Stream quality/settings
    private var quality = StreamQuality.MEDIUM

    /**
     * Initialize and start the HTTP server
     *
     * @param port Port to run the server on
     * @return True if started successfully
     */
    fun start(port: Int): Boolean {
        if (httpServer != null) {
            Log.w(TAG, "Server already running")
            return false
        }

        return try {
            // Create and start HTTP server
            httpServer = HttpServer(context, port)
            httpServer?.start()

            Log.i(TAG, "MJPEG streamer started on port $port")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start HTTP server", e)
            false
        }
    }

    /**
     * Stop the HTTP server
     */
    fun stop() {
        httpServer?.let {
            it.stop()
            httpServer = null
            Log.i(TAG, "MJPEG streamer stopped")
        }
    }

    /**
     * Configure streaming quality
     */
    fun configureQuality(streamQuality: StreamQuality) {
        this.quality = streamQuality
        Log.d(TAG, "Stream quality set to: $streamQuality")
    }

    /**
     * Start streaming with specified mode
     */
    fun startStreaming(mode: StreamingMode, protocol: StreamProtocol) {
        httpServer?.startStreaming(mode, quality, protocol)
    }

    /**
     * Stop streaming
     */
    fun stopStreaming() {
        httpServer?.stopStreaming()
    }

    /**
     * Process a new camera frame
     */
    fun processFrame(imageProxy: ImageProxy) {
        executor.execute {
            try {
                val format = imageProxy.format
                val image = imageProxy.image

                if (image != null && (format == ImageFormat.YUV_420_888 || format == ImageFormat.YUV_422_888 || format == ImageFormat.YUV_444_888)) {
                    // Convert to NV21 format which is supported by YuvImage
                    val nv21 = yuv420ToNv21(image, imageProxy.width, imageProxy.height)

                    // Send to HTTP server for streaming
                    httpServer?.updateFrame(nv21, imageProxy.width, imageProxy.height, ImageFormat.NV21)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    /**
     * Convert YUV_420_888 to NV21 format
     */
    private fun yuv420ToNv21(image: Image, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val uvSize = width * height / 4

        val nv21 = ByteArray(ySize + uvSize * 2)

        // Get planes
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride

        val yPixelStride = yPlane.pixelStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        var yPos = 0
        var uvPos = ySize

        // Copy Y plane data
        for (i in 0 until height) {
            val yBufferPos = i * yRowStride

            for (j in 0 until width) {
                nv21[yPos++] = yBuffer[yBufferPos + j * yPixelStride]
            }
        }

        // Copy UV data
        for (i in 0 until height / 2) {
            val uBufferPos = i * uRowStride
            val vBufferPos = i * vRowStride

            for (j in 0 until width / 2) {
                nv21[uvPos++] = vBuffer[vBufferPos + j * vPixelStride]
                nv21[uvPos++] = uBuffer[uBufferPos + j * uPixelStride]
            }
        }

        return nv21
    }

    /**
     * Get number of connected clients
     */
    fun getConnectedClientCount(): Int {
        return httpServer?.getConnectedClientsCount() ?: 0
    }

    /**
     * Check if streaming is active
     */
    fun isStreaming(): Boolean {
        return httpServer?.isStreaming() ?: false
    }
}
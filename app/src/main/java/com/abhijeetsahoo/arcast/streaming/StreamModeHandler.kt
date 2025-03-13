package com.abhijeetsahoo.arcast.streaming

import android.content.Context
import android.content.Intent
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.lifecycle.LifecycleOwner
import com.abhijeetsahoo.arcast.utils.ErrorHandler
import com.abhijeetsahoo.arcast.utils.Logger

/**
 * Handler for different streaming modes.
 * Provides specialized configuration for each mode (IMAGE_ONLY, AUDIO_ONLY, VIDEO_ONLY, VIDEO_AUDIO)
 */
class StreamModeHandler(private val context: Context) {

    companion object {
        private const val TAG = "StreamModeHandler"

        // Quality to resolution mapping
        fun getResolutionForQuality(quality: StreamQuality): Size {
            return when (quality) {
                StreamQuality.LOW -> Size(640, 480)
                StreamQuality.MEDIUM -> Size(1280, 720)
                StreamQuality.HIGH -> Size(1920, 1080)
            }
        }

        // Quality to encoder bitrate mapping (bps)
        fun getBitrateForQuality(quality: StreamQuality): Int {
            return when (quality) {
                StreamQuality.LOW -> 1_000_000    // 1 Mbps
                StreamQuality.MEDIUM -> 2_500_000 // 2.5 Mbps
                StreamQuality.HIGH -> 5_000_000   // 5 Mbps
            }
        }

        // Quality to audio bitrate mapping (bps)
        fun getAudioBitrateForQuality(quality: StreamQuality): Int {
            return when (quality) {
                StreamQuality.LOW -> 64_000      // 64 kbps
                StreamQuality.MEDIUM -> 128_000  // 128 kbps
                StreamQuality.HIGH -> 192_000    // 192 kbps
            }
        }
    }

    // Streaming components
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var mjpegStreamer: MjpegStreamer? = null
    private var audioStreamer: AudioStreamer? = null

    // Current configuration
    private var currentMode: StreamingMode = StreamingMode.VIDEO_ONLY
    private var currentQuality: StreamQuality = StreamQuality.MEDIUM
    private var currentProtocol: StreamProtocol = StreamProtocol.HTTP

    /**
     * Start streaming with the specified mode, quality and protocol
     */
    fun startStreaming(
        mode: StreamingMode,
        quality: StreamQuality,
        protocol: StreamProtocol,
        port: Int = 8080
    ) {
        try {
            Logger.i(TAG, "Starting streaming in mode: $mode, quality: $quality, protocol: $protocol")

            this.currentMode = mode
            this.currentQuality = quality
            this.currentProtocol = protocol

            // Start the appropriate streaming service based on the mode
            val intent = Intent(context, StreamingService::class.java).apply {
                action = StreamingService.ACTION_START_STREAMING
                putExtra(StreamingService.EXTRA_PORT, port)
                putExtra(StreamingService.EXTRA_QUALITY, qualityToInt(quality))
                putExtra(StreamingService.EXTRA_MODE, mode)
                putExtra(StreamingService.EXTRA_PROTOCOL, protocol)
            }

            context.startService(intent)

            Logger.i(TAG, "Streaming service started")
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Failed to start streaming", e)
        }
    }

    /**
     * Stop all streaming components
     */
    fun stopStreaming() {
        try {
            Logger.i(TAG, "Stopping streaming")

            val intent = Intent(context, StreamingService::class.java).apply {
                action = StreamingService.ACTION_STOP_STREAMING
            }

            context.startService(intent)

            Logger.i(TAG, "Streaming service stopped")
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Failed to stop streaming", e)
        }
    }

    /**
     * Update streaming quality
     */
    fun updateQuality(quality: StreamQuality) {
        try {
            Logger.i(TAG, "Updating quality to: $quality")

            this.currentQuality = quality

            val intent = Intent(context, StreamingService::class.java).apply {
                action = StreamingService.ACTION_UPDATE_QUALITY
                putExtra(StreamingService.EXTRA_QUALITY, qualityToInt(quality))
                putExtra(StreamingService.EXTRA_MODE, currentMode)
                putExtra(StreamingService.EXTRA_PROTOCOL, currentProtocol)
            }

            context.startService(intent)
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Failed to update quality", e)
        }
    }

    /**
     * Convert StreamQuality to integer percentage value
     */
    private fun qualityToInt(quality: StreamQuality): Int {
        return when (quality) {
            StreamQuality.LOW -> 35
            StreamQuality.MEDIUM -> 65
            StreamQuality.HIGH -> 90
        }
    }

    /**
     * Create the appropriate ImageAnalysis configuration for the current mode
     */
    fun createImageAnalysisConfig(): ImageAnalysis.Builder {
        val resolution = getResolutionForQuality(currentQuality)

        return ImageAnalysis.Builder()
            .setTargetResolution(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    }

    /**
     * Create the appropriate ImageCapture configuration for the current mode
     */
    fun createImageCaptureConfig(): ImageCapture.Builder {
        val resolution = getResolutionForQuality(currentQuality)

        return ImageCapture.Builder()
            .setTargetResolution(resolution)
            .setCaptureMode(
                when (currentMode) {
                    StreamingMode.IMAGE_ONLY -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    else -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                }
            )
    }
}
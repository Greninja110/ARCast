package com.abhijeetsahoo.arcast.streaming

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.abhijeetsahoo.arcast.utils.ErrorHandler
import com.abhijeetsahoo.arcast.utils.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles audio recording and streaming
 */
class AudioStreamer(private val context: Context) {

    companion object {
        private const val TAG = "AudioStreamer"

        // Audio configuration
        private const val SAMPLE_RATE = 44100
        private const val CHANNELS = AudioFormat.CHANNEL_IN_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }

    private var audioRecord: AudioRecord? = null
    private var bufferSize: Int = 0
    private val isRecording = AtomicBoolean(false)
    private var httpServer: HttpServer? = null

    // Client connection callbacks
    private var onClientCountChanged: ((Int) -> Unit)? = null

    // Executor for background processing
    private val processingExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Start audio recording and streaming
     */
    fun start(port: Int = 8080, quality: StreamQuality = StreamQuality.MEDIUM) {
        if (isRecording.get()) {
            Logger.i(TAG, "Audio streamer already running")
            return
        }

        try {
            // Calculate minimum buffer size for AudioRecord
            bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNELS,
                AUDIO_FORMAT
            )

            // Increase buffer size for better stability
            bufferSize *= BUFFER_SIZE_FACTOR

            // Create AudioRecord instance
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNELS,
                AUDIO_FORMAT,
                bufferSize
            )

            // Start the HTTP server if it's not already running
            if (httpServer == null) {
                httpServer = HttpServer(context, port).apply {
                    setOnClientConnectedListener { count ->
                        Logger.i(TAG, "Audio client connected. Total: $count")
                        onClientCountChanged?.invoke(count)
                    }

                    setOnClientDisconnectedListener { count ->
                        Logger.i(TAG, "Audio client disconnected. Total: $count")
                        onClientCountChanged?.invoke(count)
                    }

                    // Start the server
                    start()
                }
            }

            // Start recording
            audioRecord?.startRecording()
            isRecording.set(true)

            // Start processing in background
            processingExecutor.execute {
                processAudioStream()
            }

            Logger.i(TAG, "Audio streamer started on port $port")
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Failed to start audio streamer", e)
            stop()
        }
    }

    /**
     * Stop audio recording and streaming
     */
    fun stop() {
        if (!isRecording.get()) {
            return
        }

        try {
            // Stop recording flag
            isRecording.set(false)

            // Stop and release AudioRecord
            audioRecord?.apply {
                try {
                    stop()
                    release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping AudioRecord", e)
                }
            }
            audioRecord = null

            // Stop HTTP server
            httpServer?.stop()
            httpServer = null

            Logger.i(TAG, "Audio streamer stopped")
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Failed to stop audio streamer", e)
        }
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
     * Main processing loop for audio data
     */
    private fun processAudioStream() {
        val buffer = ByteArray(bufferSize)

        try {
            while (isRecording.get()) {
                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (readResult > 0) {
                    // For HTTP streaming, we'd normally need to format this as a proper audio stream
                    // For simplicity, we're just passing the raw PCM data
                    // In a real implementation, we would encode this to AAC, MP3, or Opus
                    httpServer?.updateAudioData(buffer.copyOf(readResult))
                }
            }
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error processing audio", e)
            stop()
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        stop()
        processingExecutor.shutdown()
    }
}
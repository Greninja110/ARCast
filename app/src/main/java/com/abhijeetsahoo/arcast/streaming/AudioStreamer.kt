package com.abhijeetsahoo.arcast.streaming

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * Class for streaming audio from the device microphone
 */
class AudioStreamer(private val context: Context) {

    companion object {
        private const val TAG = "AudioStreamer"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    // Audio buffer size
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )

    // Audio recorder
    private var audioRecord: AudioRecord? = null

    // Flag to control recording
    private val isRecording = AtomicBoolean(false)

    // Thread for audio recording
    private val recordingExecutor = Executors.newSingleThreadExecutor()

    // Queue of audio clients
    private val audioClients = ConcurrentLinkedQueue<AudioClient>()

    /**
     * Start audio recording and streaming
     */
    fun start() {
        if (isRecording.get()) {
            Log.w(TAG, "Audio streamer already running")
            return
        }

        // Create and start audio recorder
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord")
                return
            }

            // Start recording
            audioRecord?.startRecording()
            isRecording.set(true)

            // Start processing thread
            recordingExecutor.execute {
                processAudio()
            }

            Log.i(TAG, "Audio streamer started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recorder", e)
        }
    }

    /**
     * Stop audio recording and streaming
     */
    fun stop() {
        if (!isRecording.get()) return

        isRecording.set(false)

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            Log.i(TAG, "Audio streamer stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recorder", e)
        }
    }

    /**
     * Process audio data in a loop
     */
    private fun processAudio() {
        val buffer = ByteArray(bufferSize)

        try {
            while (isRecording.get()) {
                // Read audio data
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (readSize > 0) {
                    // Create a copy of the data for sending
                    val audioData = buffer.copyOf(readSize)

                    // Send to all connected clients
                    sendAudioToClients(audioData)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
            isRecording.set(false)
        }
    }

    /**
     * Send audio data to all connected clients
     */
    private fun sendAudioToClients(audioData: ByteArray) {
        audioClients.forEach { client ->
            try {
                client.onAudioData(audioData)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending audio to client", e)
            }
        }
    }

    /**
     * Add an audio client
     */
    fun addClient(client: AudioClient) {
        audioClients.add(client)
        Log.d(TAG, "Audio client added, total clients: ${audioClients.size}")
    }

    /**
     * Remove an audio client
     */
    fun removeClient(client: AudioClient) {
        audioClients.remove(client)
        Log.d(TAG, "Audio client removed, total clients: ${audioClients.size}")
    }

    /**
     * Get the number of connected audio clients
     */
    fun getClientCount(): Int {
        return audioClients.size
    }
}

/**
 * Interface for audio streaming clients
 */
interface AudioClient {
    fun onAudioData(data: ByteArray)
}
package com.abhijeetsahoo.arcast.streaming

/**
 * Streaming mode options for the service
 */
enum class StreamingMode {
    IMAGE_ONLY,    // Only streams static images
    AUDIO_ONLY,    // Only streams audio
    VIDEO_ONLY,    // Only streams video (no audio)
    VIDEO_AUDIO    // Streams both video and audio
}

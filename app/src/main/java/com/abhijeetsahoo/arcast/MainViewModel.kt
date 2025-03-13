package com.abhijeetsahoo.arcast

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abhijeetsahoo.arcast.streaming.StreamProtocol
import com.abhijeetsahoo.arcast.streaming.StreamQuality
import com.abhijeetsahoo.arcast.streaming.StreamingMode
import com.abhijeetsahoo.arcast.streaming.StreamingServiceState
import kotlinx.coroutines.launch

/**
 * ViewModel for the main activity, maintaining app state and handling logic
 */
class MainViewModel : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
    }

    // App state
    private val _appState = MutableLiveData<AppState>()
    val appState: LiveData<AppState> = _appState

    init {
        // Initialize app state with defaults
        _appState.value = AppState()
    }

    /**
     * Update the app state
     */
    private fun updateState(update: (AppState) -> AppState) {
        _appState.value = _appState.value?.let(update)
    }

    /**
     * Set the streaming mode
     */
    fun setStreamingMode(mode: StreamingMode) {
        updateState { it.copy(streamingMode = mode) }
        Log.d(TAG, "Streaming mode set to: $mode")
    }

    /**
     * Set the streaming quality
     */
    fun setStreamQuality(quality: StreamQuality) {
        updateState { it.copy(streamQuality = quality) }
        Log.d(TAG, "Stream quality set to: $quality")
    }

    /**
     * Set the streaming protocol
     */
    fun setStreamProtocol(protocol: StreamProtocol) {
        updateState { it.copy(streamProtocol = protocol) }
        Log.d(TAG, "Stream protocol set to: $protocol")
    }

    /**
     * Start streaming
     */
    fun startStreaming() {
        viewModelScope.launch {
            updateState { it.copy(isStreaming = true) }
            Log.d(TAG, "Streaming started")
        }
    }

    /**
     * Stop streaming
     */
    fun stopStreaming() {
        viewModelScope.launch {
            updateState { it.copy(isStreaming = false) }
            Log.d(TAG, "Streaming stopped")
        }
    }

    /**
     * Update state from streaming service
     */
    fun updateStreamingState(serviceState: StreamingServiceState) {
        updateState {
            it.copy(
                isStreaming = serviceState.isStreaming,
                streamingMode = serviceState.streamingMode,
                streamQuality = serviceState.streamQuality,
                streamProtocol = serviceState.streamProtocol,
                connectedClients = serviceState.connectedClients,
                ipAddress = serviceState.localIpAddress,
                streamUrls = serviceState.streamUrls
            )
        }
    }

    /**
     * Set camera settings
     */
    fun setCameraSettings(useFlash: Boolean, useFrontCamera: Boolean, autoFocus: Boolean) {
        updateState {
            it.copy(
                useFlash = useFlash,
                useFrontCamera = useFrontCamera,
                autoFocus = autoFocus
            )
        }
    }

    /**
     * Switch between light and dark theme
     */
    fun setDarkTheme(useDarkTheme: Boolean) {
        updateState { it.copy(useDarkTheme = useDarkTheme) }
    }

    /**
     * Set server port
     */
    fun setServerPort(port: Int) {
        updateState { it.copy(serverPort = port) }
    }
}

/**
 * App state data class
 */
data class AppState(
    // Camera state
    val isCameraReady: Boolean = false,
    val useFrontCamera: Boolean = false,
    val useFlash: Boolean = false,
    val autoFocus: Boolean = true,

    // Streaming state
    val isStreaming: Boolean = false,
    val streamingMode: StreamingMode = StreamingMode.VIDEO_ONLY,
    val streamQuality: StreamQuality = StreamQuality.MEDIUM,
    val streamProtocol: StreamProtocol = StreamProtocol.HTTP,
    val connectedClients: Int = 0,
    val ipAddress: String = "Not connected",
    val streamUrls: Map<String, String> = emptyMap(),
    val serverPort: Int = 8080,

    // AR state
    val arSessionActive: Boolean = false,

    // App settings
    val useDarkTheme: Boolean = false
)
package com.abhijeetsahoo.arcast

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abhijeetsahoo.arcast.streaming.StreamingMode
import com.abhijeetsahoo.arcast.streaming.StreamQuality
import com.abhijeetsahoo.arcast.streaming.StreamProtocol
import com.abhijeetsahoo.arcast.utils.NetworkUtils
import kotlinx.coroutines.launch

/**
 * ViewModel for the main activity to handle application state
 */
class MainViewModel : ViewModel() {

    private val _appState = MutableLiveData<AppState>()
    val appState: LiveData<AppState> = _appState

    // Streaming settings
    private val _streamingMode = MutableLiveData(StreamingMode.VIDEO_ONLY)
    val streamingMode: LiveData<StreamingMode> = _streamingMode

    private val _streamQuality = MutableLiveData(StreamQuality.MEDIUM)
    val streamQuality: LiveData<StreamQuality> = _streamQuality

    private val _streamProtocol = MutableLiveData(StreamProtocol.HTTP)
    val streamProtocol: LiveData<StreamProtocol> = _streamProtocol

    // Streaming state
    private val _isStreaming = MutableLiveData(false)
    val isStreaming: LiveData<Boolean> = _isStreaming

    private val _connectedClients = MutableLiveData(0)
    val connectedClients: LiveData<Int> = _connectedClients

    private val _streamUrl = MutableLiveData<String?>(null)
    val streamUrl: LiveData<String?> = _streamUrl

    init {
        // Initialize app state
        _appState.value = AppState()
    }

    /**
     * Check required permissions for app functionality
     */
    fun checkRequiredPermissions() {
        viewModelScope.launch {
            // Check permissions logic would go here
        }
    }

    /**
     * Update streaming mode
     */
    fun setStreamingMode(mode: StreamingMode) {
        _streamingMode.value = mode
    }

    /**
     * Update streaming quality
     */
    fun setStreamQuality(quality: StreamQuality) {
        _streamQuality.value = quality
    }

    /**
     * Update streaming protocol
     */
    fun setStreamProtocol(protocol: StreamProtocol) {
        _streamProtocol.value = protocol
    }

    /**
     * Update streaming state
     */
    fun setStreamingState(isStreaming: Boolean) {
        _isStreaming.value = isStreaming
    }

    /**
     * Update connected clients count
     */
    fun setConnectedClients(count: Int) {
        _connectedClients.value = count
    }

    /**
     * Update stream URL
     */
    fun setStreamUrl(url: String?) {
        _streamUrl.value = url
    }

    /**
     * Get IP address from network utilities
     */
    fun getDeviceIpAddress(context: Context): String? {
        return NetworkUtils.getWifiIPAddress(context)
    }
}

/**
 * Data class representing the app state
 */
data class AppState(
    val isCameraReady: Boolean = false,
    val isStreaming: Boolean = false,
    val currentIpAddress: String = "",
    val arSessionActive: Boolean = false
)
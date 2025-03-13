package com.abhijeetsahoo.arcast.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.core.app.NotificationCompat
import com.abhijeetsahoo.arcast.MainActivity
import com.abhijeetsahoo.arcast.R
import com.abhijeetsahoo.arcast.utils.ErrorHandler
import com.abhijeetsahoo.arcast.utils.Logger
import com.abhijeetsahoo.arcast.utils.NetworkUtils
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service that manages HTTP streaming
 */
class StreamingService : Service() {

    companion object {
        private const val TAG = "StreamingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "streaming_service_channel"

        // Actions
        const val ACTION_START_STREAMING = "com.abhijeetsahoo.arcast.START_STREAMING"
        const val ACTION_STOP_STREAMING = "com.abhijeetsahoo.arcast.STOP_STREAMING"
        const val ACTION_UPDATE_QUALITY = "com.abhijeetsahoo.arcast.UPDATE_QUALITY"
        const val ACTION_CHECK_STATUS = "com.abhijeetsahoo.arcast.CHECK_STATUS"

        // Broadcast actions
        const val BROADCAST_STREAMING_STARTED = "com.abhijeetsahoo.arcast.STREAMING_STARTED"
        const val BROADCAST_STREAMING_STOPPED = "com.abhijeetsahoo.arcast.STREAMING_STOPPED"
        const val BROADCAST_STREAMING_ERROR = "com.abhijeetsahoo.arcast.STREAMING_ERROR"
        const val BROADCAST_URL_UPDATED = "com.abhijeetsahoo.arcast.URL_UPDATED"
        const val BROADCAST_CLIENTS_CHANGED = "com.abhijeetsahoo.arcast.CLIENTS_CHANGED"

        // Extras
        const val EXTRA_PORT = "port"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_MODE = "mode"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_URL = "url"
        const val EXTRA_IP_ADDRESS = "ip_address"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_CLIENT_COUNT = "client_count"

        // Default values
        private const val DEFAULT_PORT = 8080
        private const val DEFAULT_QUALITY = 75

        // Events
        private val clientCount = AtomicInteger(0)

        // Status tracking
        private var isServiceRunning = false

        fun isRunning(): Boolean {
            return isServiceRunning
        }

        fun getClientCount(): Int {
            return clientCount.get()
        }
    }

    // Binder for local clients
    private val binder = LocalBinder()

    // Streaming server
    private var httpServer: HttpServer? = null

    // Configuration
    private var port = DEFAULT_PORT
    private var quality = DEFAULT_QUALITY
    private var streamingMode = StreamingMode.VIDEO_ONLY
    private var streamQuality = StreamQuality.MEDIUM
    private var streamProtocol = StreamProtocol.HTTP
    private var streamUrl: String? = null
    private var ipAddress: String? = null

    // Notification Manager
    private lateinit var notificationManager: NotificationManager

    // Camera and image analysis
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var imageAnalysis: ImageAnalysis? = null
    private var mjpegStreamer: MjpegStreamer? = null
    private var audioStreamer: AudioStreamer? = null

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "StreamingService created")
        createNotificationChannel()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_START_STREAMING -> {
                    val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
                    val quality = intent.getIntExtra(EXTRA_QUALITY, DEFAULT_QUALITY)
                    val mode = intent.getSerializableExtra(EXTRA_MODE) as? StreamingMode ?: StreamingMode.VIDEO_ONLY
                    val protocol = intent.getSerializableExtra(EXTRA_PROTOCOL) as? StreamProtocol ?: StreamProtocol.HTTP

                    startStreaming(port, quality, mode, protocol)
                }
                ACTION_STOP_STREAMING -> {
                    stopStreaming()
                    stopSelf()
                }
                ACTION_UPDATE_QUALITY -> {
                    val quality = intent.getIntExtra(EXTRA_QUALITY, DEFAULT_QUALITY)
                    val mode = intent.getSerializableExtra(EXTRA_MODE) as? StreamingMode
                    val protocol = intent.getSerializableExtra(EXTRA_PROTOCOL) as? StreamProtocol

                    if (mode != null) {
                        updateStreamingMode(mode)
                    }
                    if (protocol != null) {
                        updateProtocol(protocol)
                    }
                    updateQuality(quality)
                }
                ACTION_CHECK_STATUS -> {
                    // Broadcast current status
                    broadcastStatus()
                }
                else -> {
                    // Default case for when expression to be exhaustive
                    Log.d(TAG, "Unknown action: ${intent?.action}")
                }
            }
        } catch (e: Exception) {
            ErrorHandler.handleException(applicationContext, TAG, "Error in onStartCommand", e)
            broadcastError(e.message ?: "Unknown error")
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        cameraExecutor.shutdown()
        isServiceRunning = false
        Logger.i(TAG, "StreamingService destroyed")
    }

    /**
     * Start streaming server
     */
    private fun startStreaming(port: Int, quality: Int, mode: StreamingMode, protocol: StreamProtocol) {
        this.port = port
        this.quality = quality
        this.streamingMode = mode
        this.streamProtocol = protocol

        // Get the IP address first
        ipAddress = NetworkUtils.getWifiIPAddress(applicationContext)
        if (ipAddress == null || ipAddress == "127.0.0.1") {
            broadcastError("Couldn't determine device IP address. Make sure WiFi is connected.")
            stopSelf()
            return
        }

        // Calculate the stream URL based on mode
        streamUrl = when (mode) {
            StreamingMode.IMAGE_ONLY -> "http://$ipAddress:$port/image"
            StreamingMode.AUDIO_ONLY -> "http://$ipAddress:$port/audio"
            StreamingMode.VIDEO_ONLY -> "http://$ipAddress:$port/video"
            StreamingMode.VIDEO_AUDIO -> "http://$ipAddress:$port/stream"
        }

        // Start as a foreground service with notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        try {
            // Initialize appropriate streamers based on mode
            when (mode) {
                StreamingMode.IMAGE_ONLY -> {
                    initializeHttpServer(port)
                }
                StreamingMode.AUDIO_ONLY -> {
                    initializeHttpServer(port)
                    initializeAudioStreamer(port)
                }
                StreamingMode.VIDEO_ONLY -> {
                    initializeHttpServer(port)
                    initializeMjpegStreamer(port)
                }
                StreamingMode.VIDEO_AUDIO -> {
                    initializeHttpServer(port)
                    initializeMjpegStreamer(port)
                    initializeAudioStreamer(port)
                }
            }

            Logger.i(TAG, "Streaming server started on port $port with mode $streamingMode and protocol $streamProtocol")
            isServiceRunning = true

            // Broadcast that streaming has started
            broadcastStreamingStarted()

        } catch (e: Exception) {
            ErrorHandler.handleException(applicationContext, TAG, "Failed to start streaming server", e)
            broadcastError("Failed to start streaming: ${e.message}")
            stopSelf()
        }
    }

    /**
     * Initialize HTTP server for streaming
     */
    private fun initializeHttpServer(port: Int) {
        httpServer = HttpServer(applicationContext, port).apply {
            setStreamingMode(streamingMode)

            setOnClientConnectedListener { count ->
                clientCount.set(count)
                updateNotification()
                broadcastClientCountChanged(count)
            }

            setOnClientDisconnectedListener { count ->
                clientCount.set(count)
                updateNotification()
                broadcastClientCountChanged(count)
            }

            // Start server
            start()
        }
    }

    /**
     * Initialize MJPEG streamer for video
     */
    private fun initializeMjpegStreamer(port: Int) {
        mjpegStreamer = MjpegStreamer(applicationContext).apply {
            // Set quality based on selected quality
            val jpegQuality = when (getQualityFromInt(quality)) {
                StreamQuality.LOW -> 50
                StreamQuality.MEDIUM -> 70
                StreamQuality.HIGH -> 90
                null -> 70 // Default to medium if null
            }
            setJpegQuality(jpegQuality)

            // Set client connection listener
            setOnClientCountChangedListener { count ->
                updateNotification()
                broadcastClientCountChanged(count)
            }

            // Start the streamer
            start(port)
        }
    }

    /**
     * Initialize Audio streamer
     */
    private fun initializeAudioStreamer(port: Int) {
        audioStreamer = AudioStreamer(applicationContext).apply {
            // Start the streamer with non-null quality
            val quality = getQualityFromInt(quality) ?: StreamQuality.MEDIUM

            // Set client connection listener
            setOnClientCountChangedListener { count ->
                updateNotification()
                broadcastClientCountChanged(count)
            }

            start(port, quality)
        }
    }

    /**
     * Stop streaming server
     */
    private fun stopStreaming() {
        try {
            // Stop all streamers
            mjpegStreamer?.stop()
            mjpegStreamer = null

            audioStreamer?.stop()
            audioStreamer = null

            httpServer?.stop()
            httpServer = null

            clientCount.set(0)
            isServiceRunning = false

            // Broadcast that streaming has stopped
            broadcastStreamingStopped()

            Logger.i(TAG, "Streaming server stopped")
        } catch (e: Exception) {
            ErrorHandler.handleException(applicationContext, TAG, "Failed to stop streaming server", e)
            broadcastError("Error stopping streaming: ${e.message}")
        }
    }

    /**
     * Update streaming quality
     */
    private fun updateQuality(quality: Int) {
        this.quality = quality
        Logger.i(TAG, "Streaming quality updated to $quality")

        // Update quality in active streamers
        mjpegStreamer?.setJpegQuality(quality)

        // Update notification with new quality
        updateNotification()

        // Broadcast quality change
        broadcastStatus()
    }

    /**
     * Update streaming mode
     */
    private fun updateStreamingMode(mode: StreamingMode) {
        this.streamingMode = mode
        Logger.i(TAG, "Streaming mode updated to $mode")

        // Update mode in HTTP server
        httpServer?.setStreamingMode(mode)

        // Here you'd reconfigure the streaming server based on the new mode
        // For now, we just update the notification
        updateNotification()

        // Broadcast mode change
        broadcastStatus()
    }

    /**
     * Update streaming protocol
     */
    private fun updateProtocol(protocol: StreamProtocol) {
        // Can't change protocol while streaming is active
        // Would need to stop and restart the server
        this.streamProtocol = protocol
        Logger.i(TAG, "Streaming protocol updated to $protocol")

        // Broadcast protocol change
        broadcastStatus()
    }

    /**
     * Broadcast that streaming has started
     */
    private fun broadcastStreamingStarted() {
        val intent = Intent(BROADCAST_STREAMING_STARTED).apply {
            putExtra(EXTRA_PORT, port)
            putExtra(EXTRA_URL, streamUrl)
            putExtra(EXTRA_IP_ADDRESS, ipAddress)
            putExtra(EXTRA_MODE, streamingMode.toString())
            putExtra(EXTRA_QUALITY, quality)
            putExtra(EXTRA_PROTOCOL, streamProtocol.toString())
        }
        sendBroadcast(intent)
    }

    /**
     * Broadcast that streaming has stopped
     */
    private fun broadcastStreamingStopped() {
        val intent = Intent(BROADCAST_STREAMING_STOPPED)
        sendBroadcast(intent)
    }

    /**
     * Broadcast error message
     */
    private fun broadcastError(errorMessage: String) {
        val intent = Intent(BROADCAST_STREAMING_ERROR).apply {
            putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        }
        sendBroadcast(intent)
    }

    /**
     * Broadcast current status
     */
    private fun broadcastStatus() {
        val intent = Intent(BROADCAST_URL_UPDATED).apply {
            putExtra(EXTRA_URL, streamUrl)
            putExtra(EXTRA_IP_ADDRESS, ipAddress)
            putExtra(EXTRA_MODE, streamingMode.toString())
            putExtra(EXTRA_QUALITY, quality)
            putExtra(EXTRA_PROTOCOL, streamProtocol.toString())
            putExtra(EXTRA_CLIENT_COUNT, clientCount.get())
        }
        sendBroadcast(intent)
    }

    /**
     * Broadcast client count changed
     */
    private fun broadcastClientCountChanged(count: Int) {
        val intent = Intent(BROADCAST_CLIENTS_CHANGED).apply {
            putExtra(EXTRA_CLIENT_COUNT, count)
        }
        sendBroadcast(intent)
    }

    /**
     * Create notification channel (required for Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AR Camera Streaming"
            val descriptionText = "Notification for active camera streaming"
            val importance = NotificationManager.IMPORTANCE_LOW

            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create the foreground service notification
     */
    private fun createNotification(): Notification {
        // Intent to open app when notification is clicked
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                0
            )
        }

        // Intent to stop streaming when "Stop" action is clicked
        val stopIntent = Intent(this, StreamingService::class.java).apply {
            action = ACTION_STOP_STREAMING
        }
        val stopPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                0,
                stopIntent,
                0
            )
        }

        // Client count text
        val clientCountText = if (clientCount.get() > 0) {
            "${clientCount.get()} client${if (clientCount.get() > 1) "s" else ""} connected"
        } else {
            "No clients connected"
        }

        // Quality text based on stream quality
        val qualityText = when (getQualityFromInt(quality)) {
            StreamQuality.LOW -> "Low (480p)"
            StreamQuality.MEDIUM -> "Medium (720p)"
            StreamQuality.HIGH -> "High (1080p)"
            null -> "Custom ($quality%)"
        }

        // Stream URL text
        val streamUrlText = streamUrl ?: "URL not available"

        // Mode text
        val modeText = when (streamingMode) {
            StreamingMode.IMAGE_ONLY -> "Image Only"
            StreamingMode.AUDIO_ONLY -> "Audio Only"
            StreamingMode.VIDEO_ONLY -> "Video Only"
            StreamingMode.VIDEO_AUDIO -> "Video & Audio"
        }

        // Protocol text
        val protocolText = when (streamProtocol) {
            StreamProtocol.HTTP -> "HTTP"
            StreamProtocol.WEBRTC -> "WebRTC"
            StreamProtocol.RTSP -> "RTSP"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ARCast Streaming")
            .setContentText("$modeText ($qualityText) - $clientCountText")
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle("ARCast Streaming")
                .bigText("Mode: $modeText\nQuality: $qualityText\nProtocol: $protocolText\n$clientCountText\nURL: $streamUrlText"))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Update the notification with current information
     */
    private fun updateNotification() {
        try {
            // Create a new notification with updated information
            val notification = createNotification()

            // Update the existing notification
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            ErrorHandler.handleException(applicationContext, TAG, "Failed to update notification", e)
        }
    }

    /**
     * Convert integer quality value to StreamQuality enum
     */
    private fun getQualityFromInt(quality: Int): StreamQuality? {
        return when {
            quality <= 40 -> StreamQuality.LOW
            quality <= 70 -> StreamQuality.MEDIUM
            quality <= 100 -> StreamQuality.HIGH
            else -> null
        }
    }

    /**
     * Convert StreamQuality enum to resolution Size
     */
    private fun getResolutionForQuality(quality: StreamQuality): Size {
        return when (quality) {
            StreamQuality.LOW -> Size(640, 480)
            StreamQuality.MEDIUM -> Size(1280, 720)
            StreamQuality.HIGH -> Size(1920, 1080)
        }
    }

    /**
     * Local binder class for binding to the service
     */
    inner class LocalBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }
}
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
import androidx.core.app.NotificationCompat
import com.abhijeetsahoo.arcast.MainActivity
import com.abhijeetsahoo.arcast.utils.ErrorHandler
import com.abhijeetsahoo.arcast.utils.Logger
import com.abhijeetsahoo.arcast.utils.NetworkUtils
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

        // Extras
        const val EXTRA_PORT = "port"
        const val EXTRA_QUALITY = "quality"

        // Default values
        private const val DEFAULT_PORT = 8080
        private const val DEFAULT_QUALITY = 75

        // Events
        private val clientCount = AtomicInteger(0)

        // Callbacks
        private var onClientConnectedListener: ((Int) -> Unit)? = null
        private var onClientDisconnectedListener: ((Int) -> Unit)? = null

        fun setOnClientConnectedListener(listener: (Int) -> Unit) {
            onClientConnectedListener = listener
        }

        fun setOnClientDisconnectedListener(listener: (Int) -> Unit) {
            onClientDisconnectedListener = listener
        }

        fun getConnectedClients(): Int {
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

    // Notification Manager
    private lateinit var notificationManager: NotificationManager

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
                    startStreaming(port, quality)
                }
                ACTION_STOP_STREAMING -> {
                    stopStreaming()
                    stopSelf()
                }
                ACTION_UPDATE_QUALITY -> {
                    val quality = intent.getIntExtra(EXTRA_QUALITY, DEFAULT_QUALITY)
                    updateQuality(quality)
                }
            }
        } catch (e: Exception) {
            ErrorHandler.handleException(applicationContext, TAG, "Error in onStartCommand", e)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        Logger.i(TAG, "StreamingService destroyed")
    }

    /**
     * Start streaming server
     */
    private fun startStreaming(port: Int, quality: Int) {
        this.port = port
        this.quality = quality

        // Start as a foreground service with notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        try {
            // Create and start HTTP server
            httpServer = HttpServer(applicationContext, port).apply {
                setOnClientConnectedListener { count ->
                    clientCount.set(count)
                    updateNotification()
                    onClientConnectedListener?.invoke(count)
                }

                setOnClientDisconnectedListener { count ->
                    clientCount.set(count)
                    updateNotification()
                    onClientDisconnectedListener?.invoke(count)
                }

                // Start server
                start()
            }

            Logger.i(TAG, "Streaming server started on port $port")

            // Broadcast that streaming has started
            val broadcastIntent = Intent("com.abhijeetsahoo.arcast.STREAMING_STARTED").apply {
                putExtra("port", port)
                putExtra("url", getStreamUrl())
            }
            sendBroadcast(broadcastIntent)

        } catch (e: Exception) {
            ErrorHandler.handleException(applicationContext, TAG, "Failed to start streaming server", e)
            stopSelf()
        }
    }

    /**
     * Stop streaming server
     */
    private fun stopStreaming() {
        try {
            httpServer?.stop()
            httpServer = null

            clientCount.set(0)

            // Broadcast that streaming has stopped
            val broadcastIntent = Intent("com.abhijeetsahoo.arcast.STREAMING_STOPPED")
            sendBroadcast(broadcastIntent)

            Logger.i(TAG, "Streaming server stopped")
        } catch (e: Exception) {
            ErrorHandler.handleException(applicationContext, TAG, "Failed to stop streaming server", e)
        }
    }

    /**
     * Update streaming quality
     */
    private fun updateQuality(quality: Int) {
        this.quality = quality
        Logger.i(TAG, "Streaming quality updated to $quality")
    }

    /**
     * Get the stream URL
     */
    private fun getStreamUrl(): String? {
        val ipAddress = NetworkUtils.getWifiIPAddress(applicationContext)
        return if (ipAddress != null) {
            "http://$ipAddress:$port"
        } else {
            null
        }
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

        // Stream URL text
        val streamUrl = getStreamUrl() ?: "URL not available"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AR Camera Streaming")
            .setContentText(clientCountText)
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle("AR Camera Streaming")
                .bigText("$clientCountText\nURL: $streamUrl"))
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
     * Local binder class for binding to the service
     */
    inner class LocalBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }
}
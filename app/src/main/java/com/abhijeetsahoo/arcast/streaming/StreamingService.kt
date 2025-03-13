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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.abhijeetsahoo.arcast.MainActivity
import com.abhijeetsahoo.arcast.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Service for handling camera streaming in the background
 */
class StreamingService : Service(), ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "StreamingService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "streaming_service_channel"

        // Default values
        private const val DEFAULT_HTTP_PORT = 8080
        private const val DEFAULT_RTSP_PORT = 8554
    }

    // Binder for service connection
    private val binder = LocalBinder()

    // Camera executor
    private lateinit var cameraExecutor: ExecutorService

    // Camera selector (front/back)
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    // MJPEG streamer
    private lateinit var mjpegStreamer: MjpegStreamer

    // Audio streamer
    private lateinit var audioStreamer: AudioStreamer

    // Session manager
    private lateinit var sessionManager: SessionManager

    // Current streaming settings
    private var streamingMode = StreamingMode.VIDEO_ONLY
    private var streamQuality = StreamQuality.MEDIUM
    private var streamProtocol = StreamProtocol.HTTP

    // Service state
    private val _serviceState = MutableLiveData<StreamingServiceState>()
    val serviceState: LiveData<StreamingServiceState> = _serviceState

    // Port numbers
    private var httpPort = DEFAULT_HTTP_PORT
    private var rtspPort = DEFAULT_RTSP_PORT

    // Current IP address
    private var localIpAddress: String = "Unknown"
    private var streamUrls: Map<String, String> = emptyMap()

    /**
     * Initialize service
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "StreamingService: onCreate")

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize streamers
        mjpegStreamer = MjpegStreamer(this)
        audioStreamer = AudioStreamer(this)

        // Initialize session manager
        sessionManager = SessionManager()

        // Initialize state
        _serviceState.value = StreamingServiceState(
            isStreaming = false,
            streamingMode = streamingMode,
            streamQuality = streamQuality,
            streamProtocol = streamProtocol,
            connectedClients = 0,
            localIpAddress = "Not connected",
            streamUrls = emptyMap()
        )

        // Create notification channel
        createNotificationChannel()
    }

    /**
     * Handle start command
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "StreamingService: onStartCommand")

        // Start foreground service with notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Parse intent extras to configure service
        intent?.let { parseIntent(it) }

        // Get local IP address
        localIpAddress = NetworkUtils.getLocalIpAddress(this)
        Log.i(TAG, "Local IP address: $localIpAddress")

        // Generate stream URLs
        streamUrls = NetworkUtils.generateStreamUrls(localIpAddress, httpPort)

        // Update service state
        updateServiceState()

        return START_STICKY
    }

    /**
     * Parse intent extras
     */
    private fun parseIntent(intent: Intent) {
        // Get streaming mode
        val modeStr = intent.getStringExtra("streaming_mode")
        if (modeStr != null) {
            try {
                streamingMode = StreamingMode.valueOf(modeStr)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid streaming mode: $modeStr")
            }
        }

        // Get stream quality
        val qualityStr = intent.getStringExtra("stream_quality")
        if (qualityStr != null) {
            try {
                streamQuality = StreamQuality.valueOf(qualityStr)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid stream quality: $qualityStr")
            }
        }

        // Get stream protocol
        val protocolStr = intent.getStringExtra("stream_protocol")
        if (protocolStr != null) {
            try {
                streamProtocol = StreamProtocol.valueOf(protocolStr)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid stream protocol: $protocolStr")
            }
        }

        // Get ports
        httpPort = intent.getIntExtra("http_port", DEFAULT_HTTP_PORT)
        rtspPort = intent.getIntExtra("rtsp_port", DEFAULT_RTSP_PORT)
    }

    /**
     * Binding to allow activity to communicate with service
     */
    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    /**
     * Create notification channel for foreground service
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Streaming Service"
            val descriptionText = "AR Camera streaming service"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification for foreground service
     */
    private fun createNotification(): Notification {
        // Create a pending intent for the notification
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getActivity(
                    this, 0, notificationIntent, 0
                )
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ARCast Camera Streamer")
            .setContentText("Streaming is active - ${localIpAddress}:$httpPort")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Start streaming
     */
    fun startStreaming() {
        try {
            Log.i(TAG, "Starting streaming with mode: $streamingMode, quality: $streamQuality, protocol: $streamProtocol")

            // Start HTTP server
            if (streamProtocol == StreamProtocol.HTTP) {
                val started = mjpegStreamer.start(httpPort)

                if (started) {
                    // Configure quality
                    mjpegStreamer.configureQuality(streamQuality)

                    // Start streaming with the selected mode
                    mjpegStreamer.startStreaming(streamingMode, streamProtocol)

                    // Start audio if needed
                    if (streamingMode == StreamingMode.AUDIO_ONLY || streamingMode == StreamingMode.VIDEO_AUDIO) {
                        audioStreamer.start()
                    }

                    // Set up camera preview if video streaming
                    if (streamingMode == StreamingMode.VIDEO_ONLY || streamingMode == StreamingMode.VIDEO_AUDIO) {
                        bindCameraUseCases()
                    }

                    // Mark service as streaming
                    _serviceState.postValue(_serviceState.value?.copy(
                        isStreaming = true,
                        streamingMode = streamingMode,
                        connectedClients = 0
                    ))

                    // Update notification
                    updateNotification()
                } else {
                    Log.e(TAG, "Failed to start MJPEG streamer")
                }
            } else {
                Log.w(TAG, "Protocol not implemented yet: $streamProtocol")
                // Implement RTSP protocols here
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting streaming", e)
        }
    }

    /**
     * Stop streaming
     */
    fun stopStreaming() {
        try {
            Log.i(TAG, "Stopping streaming")

            // Stop audio streaming
            audioStreamer.stop()

            // Stop video streaming
            mjpegStreamer.stopStreaming()
            mjpegStreamer.stop()

            // Update service state
            _serviceState.postValue(_serviceState.value?.copy(
                isStreaming = false,
                connectedClients = 0
            ))

            // Update notification
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping streaming", e)
        }
    }

    /**
     * Bind camera use cases
     */
    private fun bindCameraUseCases() {
        // Get the camera provider
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                // Get camera provider
                val cameraProvider = cameraProviderFuture.get()

                // Set up image analysis use case
                val imageAnalysis = createImageAnalysisUseCase()

                // Unbind all use cases
                cameraProvider.unbindAll()

                // Bind camera to lifecycle - in a service, we need to manage this manually
                // Here we don't have a lifecycle owner but we can still bind the camera
                // and handle cleanup manually
                val camera = cameraProvider.bindToCamera(
                    cameraSelector,
                    imageAnalysis
                )

                Log.d(TAG, "Camera use cases bound successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error binding camera use cases", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Bind camera to use cases without lifecycle owner
     */
    private fun ProcessCameraProvider.bindToCamera(
        cameraSelector: CameraSelector,
        imageAnalysis: ImageAnalysis
    ) = this.bindToLifecycle(
        cameraSelector,
        imageAnalysis
    )

    /**
     * Create image analysis use case
     */
    private fun createImageAnalysisUseCase(): ImageAnalysis {
        // Determine resolution based on quality setting
        val resolution = when (streamQuality) {
            StreamQuality.LOW -> Size(640, 480)
            StreamQuality.MEDIUM -> Size(1280, 720)
            StreamQuality.HIGH -> Size(1920, 1080)
        }

        return ImageAnalysis.Builder()
            .setTargetResolution(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, this)
            }
    }

    /**
     * Image analysis - process each frame
     */
    override fun analyze(imageProxy: ImageProxy) {
        if (_serviceState.value?.isStreaming == true) {
            // Process frame for streaming
            mjpegStreamer.processFrame(imageProxy)
        } else {
            // If not streaming, just close the image
            imageProxy.close()
        }

        // Update connected clients count periodically
        updateClientsCount()
    }

    /**
     * Update connected clients count
     */
    private fun updateClientsCount() {
        val videoClients = mjpegStreamer.getConnectedClientCount()
        val audioClients = audioStreamer.getClientCount()
        val totalClients = videoClients + audioClients

        // Only update if the count has changed
        if (_serviceState.value?.connectedClients != totalClients) {
            _serviceState.postValue(_serviceState.value?.copy(
                connectedClients = totalClients
            ))

            // Update notification with client count
            updateNotification()
        }
    }

    /**
     * Update notification with current status
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    /**
     * Update service state
     */
    private fun updateServiceState() {
        _serviceState.postValue(
            StreamingServiceState(
                isStreaming = _serviceState.value?.isStreaming ?: false,
                streamingMode = streamingMode,
                streamQuality = streamQuality,
                streamProtocol = streamProtocol,
                connectedClients = _serviceState.value?.connectedClients ?: 0,
                localIpAddress = localIpAddress,
                streamUrls = streamUrls
            )
        )
    }

    /**
     * Configure streaming settings
     */
    fun configure(
        mode: StreamingMode,
        quality: StreamQuality,
        protocol: StreamProtocol
    ) {
        streamingMode = mode
        streamQuality = quality
        streamProtocol = protocol

        // Update state
        updateServiceState()

        Log.i(TAG, "Configured streaming - Mode: $mode, Quality: $quality, Protocol: $protocol")
    }

    /**
     * Configure camera
     */
    fun switchCamera(frontCamera: Boolean) {
        cameraSelector = if (frontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Rebind camera if currently streaming
        if (_serviceState.value?.isStreaming == true) {
            bindCameraUseCases()
        }

        Log.i(TAG, "Camera switched to: ${if (frontCamera) "FRONT" else "BACK"}")
    }

    /**
     * Clean up on service destroy
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "StreamingService: onDestroy")

        // Stop streaming
        stopStreaming()

        // Shutdown camera executor
        cameraExecutor.shutdown()
    }

    /**
     * Local binder class
     */
    inner class LocalBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }
}

/**
 * Data class to represent streaming service state
 */
data class StreamingServiceState(
    val isStreaming: Boolean,
    val streamingMode: StreamingMode,
    val streamQuality: StreamQuality,
    val streamProtocol: StreamProtocol,
    val connectedClients: Int,
    val localIpAddress: String,
    val streamUrls: Map<String, String>
)
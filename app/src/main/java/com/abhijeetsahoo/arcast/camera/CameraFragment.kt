package com.abhijeetsahoo.arcast.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.abhijeetsahoo.arcast.R
import com.abhijeetsahoo.arcast.streaming.MjpegStreamer
import com.abhijeetsahoo.arcast.utils.ErrorHandler
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment() {
    companion object {
        private const val TAG = "CameraFragment"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

    // Camera variables
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null

    // UI components
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var exposureSeekBar: SeekBar
    private lateinit var captureButton: ImageButton
    private lateinit var switchCameraButton: ImageButton
    private lateinit var flashButton: ImageButton
    private lateinit var focusButton: ImageButton
    private lateinit var streamButton: Button
    private lateinit var streamStatusText: TextView

    // Streaming
    private var isStreaming = false
    private var mjpegStreamer: MjpegStreamer? = null

    // Gesture detectors
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    // Current camera settings
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var isAutoFocusEnabled = true

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var permissionsGranted = true
        permissions.entries.forEach {
            if (!it.value) {
                permissionsGranted = false
                Log.d(TAG, "Permission not granted: ${it.key}")
            }
        }

        if (permissionsGranted) {
            startCamera()
        } else {
            Toast.makeText(
                requireContext(),
                "Permissions not granted by the user.",
                Toast.LENGTH_SHORT
            ).show()

            // Show a message in place of camera preview
            showPermissionRequiredMessage()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return createCameraUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            // Initialize camera executor
            cameraExecutor = Executors.newSingleThreadExecutor()

            // Initialize MJPEG streamer
            mjpegStreamer = MjpegStreamer(requireContext())

            // Initialize scale gesture detector
            scaleGestureDetector = ScaleGestureDetector(requireContext(),
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val currentZoomRatio = cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                        val delta = detector.scaleFactor
                        cameraControl?.setZoomRatio(currentZoomRatio * delta)
                        return true
                    }
                })

            // Check permissions
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
            }
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error in onViewCreated", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            // Stop streaming if active
            if (isStreaming) {
                stopStreaming()
            }

            // Release streamer resources
            mjpegStreamer?.release()

            // Shutdown camera executor
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error in onDestroyView", e)
        }
    }

    private fun createCameraUI(): View {
        try {
            // Root layout using a LinearLayout instead of ConstraintLayout
            val rootLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
            }

            // Top Controls Container
            val topControlsContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                setPadding(16, 16, 16, 16)
            }

            // Stream status text
            streamStatusText = TextView(requireContext()).apply {
                text = "Not Streaming"
                setTextColor(Color.RED)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            // Focus button
            focusButton = ImageButton(requireContext()).apply {
                setBackgroundResource(android.R.drawable.ic_menu_compass)
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.white)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    toggleAutoFocus()
                }
            }

            // Add views to top controls
            topControlsContainer.addView(streamStatusText)
            topControlsContainer.addView(focusButton)

            // Camera preview container
            val previewContainer = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f // Take most of the space
                )
            }

            // Camera preview
            previewView = PreviewView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER

                // Set up touch listener for manual focus
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        return@setOnTouchListener true
                    } else if (event.action == MotionEvent.ACTION_UP) {
                        // Focus on tap if auto-focus is disabled
                        if (!isAutoFocusEnabled) {
                            focusOnPoint(event.x, event.y)
                        }
                        return@setOnTouchListener true
                    } else if (event.pointerCount >= 2) {
                        // Handle pinch zoom
                        scaleGestureDetector.onTouchEvent(event)
                        return@setOnTouchListener true
                    }
                    return@setOnTouchListener false
                }
            }

            previewContainer.addView(previewView)

            // Seekbar Container
            val seekBarContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 8, 16, 8)
            }

            // Exposure seek bar
            exposureSeekBar = SeekBar(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 8) // Bottom margin
                }
                max = 100
                progress = 50 // Middle position for neutral exposure
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val exposureValue = (progress - 50) / 25f // Range from -2EV to +2EV
                            cameraControl?.setExposureCompensationIndex(exposureValue.toInt())
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }

            // Zoom seek bar
            zoomSeekBar = SeekBar(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                max = 100
                progress = 0
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val zoomProgress = progress / 100f
                            val zoomRatio = 1f + (cameraInfo?.zoomState?.value?.maxZoomRatio ?: 5f - 1f) * zoomProgress
                            cameraControl?.setZoomRatio(zoomRatio)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }

            seekBarContainer.addView(exposureSeekBar)
            seekBarContainer.addView(zoomSeekBar)

            // Bottom Controls Container
            val bottomControlsContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 32) // Extra bottom padding
            }

            // Switch camera button
            switchCameraButton = ImageButton(requireContext()).apply {
                setBackgroundResource(android.R.drawable.ic_popup_sync)
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.white)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 32
                }
                setOnClickListener {
                    switchCamera()
                }
            }

            // Capture button
            captureButton = ImageButton(requireContext()).apply {
                setBackgroundResource(android.R.drawable.ic_menu_camera)
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.white)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    takePhoto()
                }
            }

            // Flash button
            flashButton = ImageButton(requireContext()).apply {
                setBackgroundResource(android.R.color.transparent) // Transparent background
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 32
                }
                // Set initial flash icon
                setImageResource(android.R.drawable.ic_menu_gallery)
                setOnClickListener {
                    cycleFlashMode()
                }
            }

            // Add views to bottom controls
            bottomControlsContainer.addView(switchCameraButton)
            bottomControlsContainer.addView(captureButton)
            bottomControlsContainer.addView(flashButton)

            // Stream Button (added to bottom)
            streamButton = Button(requireContext()).apply {
                text = "Start Stream"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16, 0, 16, 16) // Margins around button
                }
                setOnClickListener {
                    toggleStreaming()
                }
            }

            // Add all components to root layout
            rootLayout.addView(topControlsContainer)
            rootLayout.addView(previewContainer)
            rootLayout.addView(seekBarContainer)
            rootLayout.addView(bottomControlsContainer)
            rootLayout.addView(streamButton)

            return rootLayout
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error creating camera UI", e)

            // Return a simple error view if UI creation fails
            return TextView(context).apply {
                text = "Error initializing camera. Please check permissions."
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
            }
        }
    }

    private fun updateFlashButtonIcon() {
        try {
            // Set the appropriate icon based on current flash mode
            val iconResource = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> android.R.drawable.ic_menu_gallery
                ImageCapture.FLASH_MODE_ON -> android.R.drawable.ic_menu_compass
                else -> android.R.drawable.ic_menu_camera
            }

            flashButton.setImageResource(iconResource)
        } catch (e: Exception) {
            // Fallback to a generic icon if there's an issue
            flashButton.setImageResource(android.R.drawable.ic_menu_manage)
            ErrorHandler.handleException(context, TAG, "Error updating flash icon", e)
        }
    }

    private fun showPermissionRequiredMessage() {
        try {
            // Remove all children from preview container
            if (previewView.parent is ViewGroup) {
                val container = previewView.parent as ViewGroup
                container.removeView(previewView)

                // Add a message about required permissions
                val messageText = TextView(requireContext()).apply {
                    text = "Camera permission is required for this feature\nTap here to request permission"
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )

                    // Add button to request permission again
                    setOnClickListener {
                        requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
                    }
                }

                container.addView(messageText)
            }
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error showing permission message", e)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

            cameraProviderFuture.addListener({
                try {
                    // Used to bind the lifecycle of cameras to the lifecycle owner
                    val cameraProvider = cameraProviderFuture.get()

                    // Preview
                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    // Image capture use case
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(flashMode)
                        .build()

                    // Image analysis for streaming
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    // Set the analyzer if streaming is active
                    if (isStreaming) {
                        imageAnalysis.setAnalyzer(cameraExecutor, mjpegStreamer!!)
                    }

                    try {
                        // Unbind use cases before rebinding
                        cameraProvider.unbindAll()

                        // Bind use cases to camera
                        camera = cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview, imageCapture, imageAnalysis
                        )

                        // Get camera control and info for additional features
                        cameraControl = camera?.cameraControl
                        cameraInfo = camera?.cameraInfo

                        // Set up zoom state listener
                        cameraInfo?.zoomState?.observe(viewLifecycleOwner) { zoomState ->
                            updateZoomUI(zoomState)
                        }

                        // Set up exposure state listener
                        cameraInfo?.exposureState?.let { exposureState ->
                            val range = exposureState.exposureCompensationRange
                            val index = exposureState.exposureCompensationIndex
                            if (range.lower != range.upper) {
                                val progress = ((index - range.lower) * 100) / (range.upper - range.lower)
                                exposureSeekBar.progress = progress
                            }
                        }

                    } catch (e: Exception) {
                        ErrorHandler.handleException(context, TAG, "Use case binding failed", e)
                    }

                } catch (e: Exception) {
                    ErrorHandler.handleException(context, TAG, "Camera provider failed", e)
                }
            }, ContextCompat.getMainExecutor(requireContext()))
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Camera initialization failed", e)
        }
    }

    private fun updateZoomUI(zoomState: ZoomState) {
        val zoomRatio = zoomState.zoomRatio
        val maxZoomRatio = zoomState.maxZoomRatio
        val minZoomRatio = zoomState.minZoomRatio

        // Calculate progress as percentage of available zoom range
        val progress = ((zoomRatio - minZoomRatio) * 100 / (maxZoomRatio - minZoomRatio)).toInt()
        zoomSeekBar.progress = progress
    }

    private fun takePhoto() {
        try {
            // Get a stable reference of the modifiable image capture use case
            val imageCapture = imageCapture ?: return

            // Create time stamped name and MediaStore entry
            val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis())
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ARCast")
                }
            }

            // Create output options object which contains file + metadata
            val outputOptions = ImageCapture.OutputFileOptions
                .Builder(
                    requireContext().contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                .build()

            // Set up image capture listener, which is triggered after photo has been taken
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        ErrorHandler.handleException(context, TAG, "Photo capture failed", exc)
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val msg = "Photo captured successfully: ${output.savedUri}"
                        Log.d(TAG, msg)
                        Toast.makeText(context, "Image saved!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error taking photo", e)
        }
    }

    private fun switchCamera() {
        try {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            // Restart camera with new selector
            startCamera()
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error switching camera", e)
        }
    }

    private fun cycleFlashMode() {
        try {
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }

            imageCapture?.flashMode = flashMode

            // Update flash button icon
            updateFlashButtonIcon()

            // Update UI based on flash mode
            val flashMessage = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> "Flash: Off"
                ImageCapture.FLASH_MODE_ON -> "Flash: On"
                else -> "Flash: Auto"
            }

            Toast.makeText(context, flashMessage, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error changing flash mode", e)
        }
    }

    private fun toggleAutoFocus() {
        try {
            isAutoFocusEnabled = !isAutoFocusEnabled

            if (isAutoFocusEnabled) {
                cameraControl?.cancelFocusAndMetering()
                Toast.makeText(context, "Auto-focus enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Tap to focus enabled", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error toggling auto-focus", e)
        }
    }

    private fun focusOnPoint(x: Float, y: Float) {
        try {
            // Convert tap coordinates to camera sensor coordinates
            val meteringPointFactory = previewView.meteringPointFactory
            val point = meteringPointFactory.createPoint(x, y)

            // Create focus action
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()

            // Execute focus action
            val future = cameraControl?.startFocusAndMetering(action)
            future?.addListener({
                Toast.makeText(context, "Focus set", Toast.LENGTH_SHORT).show()
            }, ContextCompat.getMainExecutor(requireContext()))

        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error focusing on point", e)
        }
    }

    private fun toggleStreaming() {
        try {
            isStreaming = !isStreaming

            if (isStreaming) {
                startStreaming()
            } else {
                stopStreaming()
            }

            updateStreamingUI()
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error toggling streaming", e)
            isStreaming = false
            updateStreamingUI()
        }
    }

    private fun startStreaming() {
        try {
            // Start the MJPEG streamer
            mjpegStreamer?.start()

            // Update the image analysis with the streamer
            val cameraProvider = ProcessCameraProvider.getInstance(requireContext()).get()

            // Restart camera to bind the image analyzer
            startCamera()

            // Update client count when clients connect/disconnect
            mjpegStreamer?.setOnClientCountChangedListener { count ->
                activity?.runOnUiThread {
                    val status = if (count > 0) {
                        "Streaming ($count clients)"
                    } else {
                        "Streaming (no clients)"
                    }
                    streamStatusText.text = status
                }
            }

            Toast.makeText(context, "Streaming started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error starting streaming", e)
            isStreaming = false
        }
    }

    private fun stopStreaming() {
        try {
            // Stop the MJPEG streamer
            mjpegStreamer?.stop()

            // Restart camera to unbind the image analyzer
            startCamera()

            Toast.makeText(context, "Streaming stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error stopping streaming", e)
        }
    }

    private fun updateStreamingUI() {
        if (isStreaming) {
            streamStatusText.text = "Streaming"
            streamStatusText.setTextColor(Color.GREEN)
            streamButton.text = "Stop Stream"
        } else {
            streamStatusText.text = "Not Streaming"
            streamStatusText.setTextColor(Color.RED)
            streamButton.text = "Start Stream"
        }
    }
}
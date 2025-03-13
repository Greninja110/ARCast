package com.abhijeetsahoo.arcast.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.abhijeetsahoo.arcast.MainActivity
import com.abhijeetsahoo.arcast.MainViewModel
import com.abhijeetsahoo.arcast.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Fragment for camera preview and basic controls
 */
class CameraFragment : Fragment() {

    private lateinit var viewModel: MainViewModel

    // Camera views
    private lateinit var previewView: PreviewView
    private lateinit var btnSwitchCamera: FloatingActionButton
    private lateinit var btnSwitchToMode: Button
    private lateinit var textCameraInfo: TextView

    // Camera state
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startCamera()
        } else {
            Toast.makeText(
                requireContext(),
                "Camera permissions are required for this app",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        // Find views
        previewView = view.findViewById(R.id.preview_view)
        btnSwitchCamera = view.findViewById(R.id.btn_switch_camera)
        btnSwitchToMode = view.findViewById(R.id.btn_switch_to_mode)
        textCameraInfo = view.findViewById(R.id.text_camera_info)

        // Set up button click listeners
        btnSwitchCamera.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        btnSwitchToMode.setOnClickListener {
            // Navigate to Mode fragment
            (activity as? MainActivity)?.binding?.bottomNav?.selectedItemId = R.id.nav_mode
        }

        // Check permissions and start camera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }

        // Observe ViewModel state
        observeViewModel()
    }

    /**
     * Observe ViewModel state changes
     */
    private fun observeViewModel() {
        viewModel.appState.observe(viewLifecycleOwner) { state ->
            // Update UI based on app state
            val cameraInfo = "Camera: ${if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) "Back" else "Front"}\n" +
                    "Flash: ${if (state.useFlash) "On" else "Off"}\n" +
                    "Auto Focus: ${if (state.autoFocus) "Enabled" else "Disabled"}"

            textCameraInfo.text = cameraInfo
        }
    }

    /**
     * Start the camera
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                // Get camera provider
                val cameraProvider = cameraProviderFuture.get()

                // Set up preview use case
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // Unbind existing use cases
                cameraProvider.unbindAll()

                // Bind camera to lifecycle
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)

                // Update ViewModel
                viewModel.setCameraSettings(
                    useFlash = viewModel.appState.value?.useFlash ?: false,
                    useFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA,
                    autoFocus = viewModel.appState.value?.autoFocus ?: true
                )

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Failed to start camera: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /**
     * Check if all required permissions are granted
     */
    private fun allPermissionsGranted(): Boolean {
        return arrayOf(Manifest.permission.CAMERA).all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
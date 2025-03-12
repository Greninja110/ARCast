package com.abhijeetsahoo.arcast.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.abhijeetsahoo.arcast.R

class CameraFragment : Fragment() {
    companion object {
        private const val TAG = "CameraFragment"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var permissionsGranted = true
        permissions.entries.forEach {
            if (!it.value) {
                permissionsGranted = false
                Log.e(TAG, "Permission not granted: ${it.key}")
            }
        }

        if (permissionsGranted) {
            // All permissions granted, now we can initialize camera
            Toast.makeText(requireContext(), "Camera permission granted!", Toast.LENGTH_SHORT).show()
            initializeCamera()
        } else {
            Toast.makeText(requireContext(),
                "Permissions not granted by the user.",
                Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // For now, just inflate a simple layout
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            // Check for required permissions
            if (allPermissionsGranted()) {
                // Permissions are already granted, initialize camera
                initializeCamera()
            } else {
                // Request permissions
                requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated", e)
            Toast.makeText(context, "Error initializing camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun initializeCamera() {
        // Just a placeholder for now - we'll implement actual camera initialization later
        Log.d(TAG, "Camera initialization would happen here")
        Toast.makeText(context, "Camera ready", Toast.LENGTH_SHORT).show()
    }
}
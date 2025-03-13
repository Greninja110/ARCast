package com.abhijeetsahoo.arcast.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.abhijeetsahoo.arcast.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dialog to display app information and credits
 */
class AboutDialog : DialogFragment() {

    private lateinit var textAppInfo: TextView
    private lateinit var textDeveloperInfo: TextView
    private lateinit var btnClose: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find views
        textAppInfo = view.findViewById(R.id.text_app_info)
        textDeveloperInfo = view.findViewById(R.id.text_developer_info)
        btnClose = view.findViewById(R.id.btn_close)

        // Set up app info
        val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        val versionInfo = "Version ${packageInfo.versionName} (${packageInfo.versionCode})"

        // Format creation date
        val creationDate = SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(Date(packageInfo.firstInstallTime))

        // Set app info text
        textAppInfo.text = """
            ARCast - Advanced AR Camera Streamer
            $versionInfo
            
            First installed: $creationDate
            
            ARCast is an advanced camera streaming application that allows you to stream your device's camera feed, audio, and AR content over WiFi to any browser.
        """.trimIndent()

        // Set developer info text
        textDeveloperInfo.text = """
            Developed by: ARCast Team
            
            This application uses the following technologies:
            • CameraX for camera access
            • ARCore for augmented reality
            • NanoHTTPD for streaming server
            • WebRTC for real-time communication
            
            © 2023 ARCast. All rights reserved.
        """.trimIndent()

        // Set up close button
        btnClose.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()

        // Make dialog width match parent
        val dialog = dialog
        if (dialog != null) {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.WRAP_CONTENT
            dialog.window?.setLayout(width, height)
        }
    }
}
package com.abhijeetsahoo.arcast.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.abhijeetsahoo.arcast.R
import com.abhijeetsahoo.arcast.streaming.StreamProtocol
import com.abhijeetsahoo.arcast.utils.ErrorHandler

class SettingsFragment : Fragment() {
    companion object {
        private const val TAG = "SettingsFragment"

        // Default port options
        private val PORT_OPTIONS = arrayOf(8080, 8081, 8082, 8083, 8888)
    }

    // UI Components
    private lateinit var protocolSpinner: Spinner
    private lateinit var portSpinner: Spinner
    private lateinit var qualityRadioGroup: RadioGroup
    private lateinit var qualityLowRadio: RadioButton
    private lateinit var qualityMediumRadio: RadioButton
    private lateinit var qualityHighRadio: RadioButton

    private lateinit var flashSwitch: SwitchCompat
    private lateinit var autoFocusSwitch: SwitchCompat
    private lateinit var cameraRadioGroup: RadioGroup
    private lateinit var cameraFrontRadio: RadioButton
    private lateinit var cameraRearRadio: RadioButton

    private lateinit var themeSwitch: SwitchCompat
    private lateinit var aboutButton: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        try {
            // Inflate the layout
            val view = inflater.inflate(R.layout.fragment_settings, container, false)

            // Initialize UI components
            initUI(view)

            // Load saved settings
            loadSettings()

            // Set up listeners
            setupListeners()

            return view
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error creating settings view", e)

            // Return a simple text view as fallback
            return TextView(context).apply {
                text = "Settings (Error loading UI)"
                setPadding(16, 16, 16, 16)
            }
        }
    }

    private fun initUI(view: View) {
        // Streaming settings
        protocolSpinner = view.findViewById(R.id.spinner_protocol)
        portSpinner = view.findViewById(R.id.spinner_port)
        qualityRadioGroup = view.findViewById(R.id.radio_group_quality)
        qualityLowRadio = view.findViewById(R.id.radio_quality_low)
        qualityMediumRadio = view.findViewById(R.id.radio_quality_medium)
        qualityHighRadio = view.findViewById(R.id.radio_quality_high)

        // Camera settings
        flashSwitch = view.findViewById(R.id.switch_flash)
        autoFocusSwitch = view.findViewById(R.id.switch_autofocus)
        cameraRadioGroup = view.findViewById(R.id.radio_group_camera)
        cameraFrontRadio = view.findViewById(R.id.radio_camera_front)
        cameraRearRadio = view.findViewById(R.id.radio_camera_rear)

        // App settings
        themeSwitch = view.findViewById(R.id.switch_dark_theme)
        aboutButton = view.findViewById(R.id.button_about)

        // Set up spinners
        setupProtocolSpinner()
        setupPortSpinner()
    }

    private fun setupProtocolSpinner() {
        val protocols = StreamProtocol.values().map { it.name }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            protocols
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        protocolSpinner.adapter = adapter
    }

    private fun setupPortSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            PORT_OPTIONS
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        portSpinner.adapter = adapter
    }

    private fun setupListeners() {
        // Protocol selection
        protocolSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedProtocol = StreamProtocol.values()[position]
                saveProtocolSetting(selectedProtocol)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }

        // Port selection
        portSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedPort = PORT_OPTIONS[position]
                savePortSetting(selectedPort)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }

        // Quality radio group
        qualityRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_quality_low -> saveQualitySetting("low")
                R.id.radio_quality_medium -> saveQualitySetting("medium")
                R.id.radio_quality_high -> saveQualitySetting("high")
            }
        }

        // Flash switch
        flashSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveCameraSetting("flash", isChecked)
        }

        // Auto-focus switch
        autoFocusSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveCameraSetting("auto_focus", isChecked)
        }

        // Camera radio group
        cameraRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val isFront = checkedId == R.id.radio_camera_front
            saveCameraSetting("front_camera", isFront)
        }

        // Theme switch
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveAppSetting("dark_theme", isChecked)
            showThemeChangeMessage()
        }

        // About button
        aboutButton.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun loadSettings() {
        // Load streaming settings
        val streamPrefs = requireContext().getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
        val protocolOrdinal = streamPrefs.getInt("protocol", StreamProtocol.HTTP.ordinal)
        protocolSpinner.setSelection(protocolOrdinal)

        val port = streamPrefs.getInt("port", 8080)
        val portIndex = PORT_OPTIONS.indexOf(port).coerceAtLeast(0)
        portSpinner.setSelection(portIndex)

        val quality = streamPrefs.getString("quality", "medium")
        when (quality) {
            "low" -> qualityLowRadio.isChecked = true
            "high" -> qualityHighRadio.isChecked = true
            else -> qualityMediumRadio.isChecked = true
        }

        // Load camera settings
        val cameraPrefs = requireContext().getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
        flashSwitch.isChecked = cameraPrefs.getBoolean("flash", false)
        autoFocusSwitch.isChecked = cameraPrefs.getBoolean("auto_focus", true)
        val isFrontCamera = cameraPrefs.getBoolean("front_camera", false)
        if (isFrontCamera) {
            cameraFrontRadio.isChecked = true
        } else {
            cameraRearRadio.isChecked = true
        }

        // Load app settings
        val appPrefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        themeSwitch.isChecked = appPrefs.getBoolean("dark_theme", false)
    }

    private fun saveProtocolSetting(protocol: StreamProtocol) {
        requireContext().getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("protocol", protocol.ordinal)
            .apply()
    }

    private fun savePortSetting(port: Int) {
        requireContext().getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("port", port)
            .apply()
    }

    private fun saveQualitySetting(quality: String) {
        requireContext().getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("quality", quality)
            .apply()
    }

    private fun saveCameraSetting(key: String, value: Boolean) {
        requireContext().getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    private fun saveAppSetting(key: String, value: Boolean) {
        requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    private fun showThemeChangeMessage() {
        Toast.makeText(
            requireContext(),
            "Theme will be applied when you restart the app",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showAboutDialog() {
        val aboutBuilder = AlertDialog.Builder(requireContext())
            .setTitle("About ARCast")
            .setMessage("""
                ARCast - Augmented Reality Camera Streamer
                Version 1.0
                
                A powerful app that allows you to stream your camera feed, audio and AR content over WiFi.
                
                Developed by: ARCast Team
                First Created: March 2025
                
                This application uses:
                • CameraX for camera access
                • ARCore for augmented reality
                • WebRTC & RTSP for streaming
            """.trimIndent())
            .setPositiveButton("OK", null)

        aboutBuilder.show()
    }
}
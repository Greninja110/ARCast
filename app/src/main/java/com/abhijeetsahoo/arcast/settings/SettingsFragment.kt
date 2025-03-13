package com.abhijeetsahoo.arcast.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.abhijeetsahoo.arcast.MainViewModel
import com.abhijeetsahoo.arcast.R
import com.abhijeetsahoo.arcast.streaming.StreamProtocol
import com.google.android.material.card.MaterialCardView

/**
 * Fragment for app settings
 */
class SettingsFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var sharedPreferences: SharedPreferences

    // UI elements
    private lateinit var protocolSpinner: Spinner
    private lateinit var portSpinner: Spinner
    private lateinit var defaultQualitySpinner: Spinner

    private lateinit var switchFlash: Switch
    private lateinit var switchAutoFocus: Switch
    private lateinit var switchFrontCamera: Switch

    private lateinit var switchDarkTheme: Switch

    private lateinit var cardAbout: MaterialCardView
    private lateinit var textVersion: TextView

    companion object {
        private const val PREFS_NAME = "ARCastPreferences"
        private const val KEY_PROTOCOL = "protocol"
        private const val KEY_PORT = "port"
        private const val KEY_QUALITY = "quality"
        private const val KEY_FLASH = "flash"
        private const val KEY_AUTOFOCUS = "autofocus"
        private const val KEY_FRONT_CAMERA = "front_camera"
        private const val KEY_DARK_THEME = "dark_theme"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        // Initialize SharedPreferences
        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Find UI elements
        initViews(view)

        // Set up UI with saved preferences
        setupUI()

        // Set up listeners
        setupListeners()
    }

    /**
     * Initialize views
     */
    private fun initViews(view: View) {
        // Streaming settings
        protocolSpinner = view.findViewById(R.id.spinner_protocol)
        portSpinner = view.findViewById(R.id.spinner_port)
        defaultQualitySpinner = view.findViewById(R.id.spinner_default_quality)

        // Camera settings
        switchFlash = view.findViewById(R.id.switch_flash)
        switchAutoFocus = view.findViewById(R.id.switch_autofocus)
        switchFrontCamera = view.findViewById(R.id.switch_front_camera)

        // App settings
        switchDarkTheme = view.findViewById(R.id.switch_dark_theme)

        // About section
        cardAbout = view.findViewById(R.id.card_about)
        textVersion = view.findViewById(R.id.text_version)
    }

    /**
     * Set up UI elements with saved preferences
     */
    private fun setupUI() {
        // Set up protocol spinner
        val protocols = arrayOf("HTTP", "WebRTC", "RTSP")
        val protocolAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, protocols)
        protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        protocolSpinner.adapter = protocolAdapter

        // Set selected protocol
        val savedProtocol = sharedPreferences.getString(KEY_PROTOCOL, "HTTP")
        val protocolIndex = protocols.indexOf(savedProtocol)
        if (protocolIndex >= 0) {
            protocolSpinner.setSelection(protocolIndex)
        }

        // Set up port spinner
        val ports = arrayOf("8080", "8081", "8082", "8083", "8084")
        val portAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, ports)
        portAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        portSpinner.adapter = portAdapter

        // Set selected port
        val savedPort = sharedPreferences.getString(KEY_PORT, "8080")
        val portIndex = ports.indexOf(savedPort)
        if (portIndex >= 0) {
            portSpinner.setSelection(portIndex)
        }

        // Set up quality spinner
        val qualities = arrayOf("Low (480p)", "Medium (720p)", "High (1080p)")
        val qualityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, qualities)
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        defaultQualitySpinner.adapter = qualityAdapter

        // Set selected quality
        val savedQuality = sharedPreferences.getString(KEY_QUALITY, "Medium (720p)")
        val qualityIndex = qualities.indexOf(savedQuality)
        if (qualityIndex >= 0) {
            defaultQualitySpinner.setSelection(qualityIndex)
        }

        // Set camera settings from preferences
        switchFlash.isChecked = sharedPreferences.getBoolean(KEY_FLASH, false)
        switchAutoFocus.isChecked = sharedPreferences.getBoolean(KEY_AUTOFOCUS, true)
        switchFrontCamera.isChecked = sharedPreferences.getBoolean(KEY_FRONT_CAMERA, false)

        // Set app settings from preferences
        switchDarkTheme.isChecked = sharedPreferences.getBoolean(KEY_DARK_THEME, false)

        // Set version info
        val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        textVersion.text = "Version ${packageInfo.versionName} (${packageInfo.versionCode})"
    }

    /**
     * Set up listeners for UI elements
     */
    private fun setupListeners() {
        // Protocol spinner
        protocolSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val protocol = parent?.getItemAtPosition(position).toString()
                savePreference(KEY_PROTOCOL, protocol)

                // Update ViewModel
                when (protocol) {
                    "HTTP" -> viewModel.setStreamProtocol(StreamProtocol.HTTP)
                    "WebRTC" -> viewModel.setStreamProtocol(StreamProtocol.WEBRTC)
                    "RTSP" -> viewModel.setStreamProtocol(StreamProtocol.RTSP)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Port spinner
        portSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val port = parent?.getItemAtPosition(position).toString()
                savePreference(KEY_PORT, port)

                // Update ViewModel
                viewModel.setServerPort(port.toInt())
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Quality spinner
        defaultQualitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val quality = parent?.getItemAtPosition(position).toString()
                savePreference(KEY_QUALITY, quality)

                // Update ViewModel with quality
                when (position) {
                    0 -> viewModel.setStreamQuality(com.abhijeetsahoo.arcast.streaming.StreamQuality.LOW)
                    1 -> viewModel.setStreamQuality(com.abhijeetsahoo.arcast.streaming.StreamQuality.MEDIUM)
                    2 -> viewModel.setStreamQuality(com.abhijeetsahoo.arcast.streaming.StreamQuality.HIGH)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Camera settings
        val cameraChangeListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            when (buttonView.id) {
                R.id.switch_flash -> {
                    savePreference(KEY_FLASH, isChecked)
                    updateCameraSettings()
                }
                R.id.switch_autofocus -> {
                    savePreference(KEY_AUTOFOCUS, isChecked)
                    updateCameraSettings()
                }
                R.id.switch_front_camera -> {
                    savePreference(KEY_FRONT_CAMERA, isChecked)
                    updateCameraSettings()
                }
            }
        }

        switchFlash.setOnCheckedChangeListener(cameraChangeListener)
        switchAutoFocus.setOnCheckedChangeListener(cameraChangeListener)
        switchFrontCamera.setOnCheckedChangeListener(cameraChangeListener)

        // Dark theme switch
        switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            savePreference(KEY_DARK_THEME, isChecked)
            updateTheme(isChecked)
        }

        // About card
        cardAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    /**
     * Update camera settings in ViewModel
     */
    private fun updateCameraSettings() {
        viewModel.setCameraSettings(
            useFlash = switchFlash.isChecked,
            useFrontCamera = switchFrontCamera.isChecked,
            autoFocus = switchAutoFocus.isChecked
        )
    }

    /**
     * Update app theme
     */
    private fun updateTheme(isDarkTheme: Boolean) {
        viewModel.setDarkTheme(isDarkTheme)

        // Apply theme
        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    /**
     * Show about dialog
     */
    private fun showAboutDialog() {
        val dialog = AboutDialog()
        dialog.show(childFragmentManager, "AboutDialog")
    }

    /**
     * Save preference to SharedPreferences
     */
    private fun savePreference(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    /**
     * Save preference to SharedPreferences
     */
    private fun savePreference(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }
}
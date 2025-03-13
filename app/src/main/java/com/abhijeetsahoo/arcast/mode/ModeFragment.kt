package com.abhijeetsahoo.arcast.streaming

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.abhijeetsahoo.arcast.R
import com.abhijeetsahoo.arcast.utils.ErrorHandler
import com.google.android.material.card.MaterialCardView

class ModeFragment : Fragment() {
    companion object {
        private const val TAG = "ModeFragment"
        private const val DEFAULT_PORT = 8080
    }

    // UI components
    private lateinit var radioGroupMode: RadioGroup
    private lateinit var radioGroupQuality: RadioGroup
    private lateinit var radioImageOnly: RadioButton
    private lateinit var radioAudioOnly: RadioButton
    private lateinit var radioVideoOnly: RadioButton
    private lateinit var radioVideoAudio: RadioButton
    private lateinit var radioLowQuality: RadioButton
    private lateinit var radioMediumQuality: RadioButton
    private lateinit var radioHighQuality: RadioButton
    private lateinit var descriptionText: TextView
    private lateinit var startStreamButton: Button

    // Selected options
    private var selectedMode = StreamingMode.VIDEO_ONLY
    private var selectedQuality = StreamQuality.MEDIUM
    private var selectedProtocol = StreamProtocol.HTTP

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        try {
            // Inflate a simple layout
            val view = inflater.inflate(R.layout.fragment_mode, container, false)

            // Initialize UI components
            radioGroupMode = view.findViewById(R.id.radio_group_mode)
            radioGroupQuality = view.findViewById(R.id.radio_group_quality)
            radioImageOnly = view.findViewById(R.id.radio_image_only)
            radioAudioOnly = view.findViewById(R.id.radio_audio_only)
            radioVideoOnly = view.findViewById(R.id.radio_video_only)
            radioVideoAudio = view.findViewById(R.id.radio_video_audio)
            radioLowQuality = view.findViewById(R.id.radio_quality_low)
            radioMediumQuality = view.findViewById(R.id.radio_quality_medium)
            radioHighQuality = view.findViewById(R.id.radio_quality_high)
            descriptionText = view.findViewById(R.id.text_mode_description)
            startStreamButton = view.findViewById(R.id.button_start_stream)

            // Set up listeners
            setupListeners()

            // Restore any saved selection
            loadSavedSettings()

            return view
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error creating view", e)

            // Fallback to a simple view in case of error
            return TextView(context).apply {
                text = "Streaming Mode Selection"
                textSize = 18f
                setPadding(16, 16, 16, 16)
            }
        }
    }

    private fun setupListeners() {
        // Mode selection listener
        radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_image_only -> {
                    selectedMode = StreamingMode.IMAGE_ONLY
                    updateDescription("Image Only: Streams static images. Low bandwidth, suitable for low connectivity.")
                }
                R.id.radio_audio_only -> {
                    selectedMode = StreamingMode.AUDIO_ONLY
                    updateDescription("Audio Only: Streams only audio without video. Very low bandwidth usage.")
                }
                R.id.radio_video_only -> {
                    selectedMode = StreamingMode.VIDEO_ONLY
                    updateDescription("Video Only: Streams video without audio. Good for surveillance or monitoring.")
                }
                R.id.radio_video_audio -> {
                    selectedMode = StreamingMode.VIDEO_AUDIO
                    updateDescription("Video & Audio: Complete streaming experience with both video and audio.")
                }
            }

            // Save selection to preferences
            saveSettings()
        }

        // Quality selection listener
        radioGroupQuality.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_quality_low -> {
                    selectedQuality = StreamQuality.LOW
                    updateQualityDescription("Low Quality (480p): Uses less bandwidth, suitable for slow connections.")
                }
                R.id.radio_quality_medium -> {
                    selectedQuality = StreamQuality.MEDIUM
                    updateQualityDescription("Medium Quality (720p): Balanced quality and bandwidth usage.")
                }
                R.id.radio_quality_high -> {
                    selectedQuality = StreamQuality.HIGH
                    updateQualityDescription("High Quality (1080p): Best quality, requires good connection.")
                }
            }

            // Save selection to preferences
            saveSettings()
        }

        // Start stream button
        startStreamButton.setOnClickListener {
            startStreaming()
        }
    }

    private fun updateDescription(description: String) {
        descriptionText.text = description
    }

    private fun updateQualityDescription(description: String) {
        // Could be added to the UI if needed
    }

    private fun startStreaming() {
        try {
            // Get quality value based on selected quality
            val qualityValue = when (selectedQuality) {
                StreamQuality.LOW -> 35
                StreamQuality.MEDIUM -> 65
                StreamQuality.HIGH -> 90
            }

            // Start streaming service
            val intent = Intent(requireContext(), StreamingService::class.java).apply {
                action = StreamingService.ACTION_START_STREAMING
                putExtra(StreamingService.EXTRA_PORT, DEFAULT_PORT)
                putExtra(StreamingService.EXTRA_QUALITY, qualityValue)
                putExtra(StreamingService.EXTRA_MODE, selectedMode)
                putExtra(StreamingService.EXTRA_PROTOCOL, selectedProtocol)
            }

            // Start the service
            requireContext().startService(intent)

            // Save streaming state to preferences
            val sharedPrefs = requireContext().getSharedPreferences("stream_state", Context.MODE_PRIVATE)
            sharedPrefs.edit().apply {
                putBoolean("is_streaming", true)
                putInt("stream_mode", selectedMode.ordinal)
                putInt("stream_quality", selectedQuality.ordinal)
                putInt("stream_protocol", selectedProtocol.ordinal)
                apply()
            }

            // Show toast
            Toast.makeText(
                requireContext(),
                "Starting stream with ${selectedMode.name} mode",
                Toast.LENGTH_SHORT
            ).show()

            // Navigate to stream fragment
            findNavController().navigate(R.id.action_modeFragment_to_streamFragment)

        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error starting stream", e)
        }
    }

    private fun saveSettings() {
        val sharedPrefs = requireContext().getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putInt("selected_mode", selectedMode.ordinal)
            putInt("selected_quality", selectedQuality.ordinal)
            putInt("selected_protocol", selectedProtocol.ordinal)
            apply()
        }
    }

    private fun loadSavedSettings() {
        val sharedPrefs = requireContext().getSharedPreferences("stream_settings", Context.MODE_PRIVATE)

        // Load mode
        val modeOrdinal = sharedPrefs.getInt("selected_mode", StreamingMode.VIDEO_ONLY.ordinal)
        selectedMode = StreamingMode.values()[modeOrdinal]

        // Load quality
        val qualityOrdinal = sharedPrefs.getInt("selected_quality", StreamQuality.MEDIUM.ordinal)
        selectedQuality = StreamQuality.values()[qualityOrdinal]

        // Load protocol
        val protocolOrdinal = sharedPrefs.getInt("selected_protocol", StreamProtocol.HTTP.ordinal)
        selectedProtocol = StreamProtocol.values()[protocolOrdinal]

        // Update radio buttons
        when (selectedMode) {
            StreamingMode.IMAGE_ONLY -> radioImageOnly.isChecked = true
            StreamingMode.AUDIO_ONLY -> radioAudioOnly.isChecked = true
            StreamingMode.VIDEO_ONLY -> radioVideoOnly.isChecked = true
            StreamingMode.VIDEO_AUDIO -> radioVideoAudio.isChecked = true
        }

        when (selectedQuality) {
            StreamQuality.LOW -> radioLowQuality.isChecked = true
            StreamQuality.MEDIUM -> radioMediumQuality.isChecked = true
            StreamQuality.HIGH -> radioHighQuality.isChecked = true
        }
    }
}
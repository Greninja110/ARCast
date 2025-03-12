package com.abhijeetsahoo.arcast

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {
    companion object {
        private const val TAG = "SettingsFragment"

        // Shared preferences keys
        private const val PREFS_NAME = "ARCastPrefs"
        private const val KEY_AUTO_FOCUS = "auto_focus"
        private const val KEY_HDR_MODE = "hdr_mode"
        private const val KEY_ENABLE_AUDIO = "enable_audio"
        private const val KEY_HIGH_QUALITY = "high_quality"
        private const val KEY_PLANE_DETECTION = "plane_detection"
        private const val KEY_DEPTH_SENSING = "depth_sensing"
    }

    // Keep track of settings
    private var autoFocus = true
    private var hdrMode = false
    private var enableAudio = true
    private var highQuality = false
    private var planeDetection = true
    private var depthSensing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Load settings
        loadSettings()

        return createSettingsUI()
    }

    private fun createSettingsUI(): View {
        // Root ScrollView
        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = true
        }

        // Root layout
        val rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 32, 32, 32)
        }

        // Title
        val titleText = TextView(requireContext()).apply {
            text = "Settings"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 32
            }
        }

        // Add sections
        val cameraCard = createSettingsSection("Camera Settings")
        cameraCard.addView(createSettingItem("Auto Focus", "Automatically adjust focus", autoFocus) { isChecked ->
            autoFocus = isChecked
            saveSettings()
        })

        cameraCard.addView(createSettingItem("HDR Mode", "Enable High Dynamic Range", hdrMode) { isChecked ->
            hdrMode = isChecked
            saveSettings()
        })

        val streamingCard = createSettingsSection("Streaming Settings")
        streamingCard.addView(createSettingItem("Enable Audio", "Stream audio with video", enableAudio) { isChecked ->
            enableAudio = isChecked
            saveSettings()
        })

        streamingCard.addView(createSettingItem("High Quality", "Stream in high quality (uses more bandwidth)", highQuality) { isChecked ->
            highQuality = isChecked
            saveSettings()
        })

        val arCard = createSettingsSection("AR Settings")
        arCard.addView(createSettingItem("Plane Detection", "Detect and visualize planes", planeDetection) { isChecked ->
            planeDetection = isChecked
            saveSettings()
        })

        arCard.addView(createSettingItem("Depth Sensing", "Enable depth data streaming (if available)", depthSensing) { isChecked ->
            depthSensing = isChecked
            saveSettings()
        })

        // Add views to root layout
        rootLayout.addView(titleText)
        rootLayout.addView(cameraCard)
        rootLayout.addView(streamingCard)
        rootLayout.addView(arCard)

        // Add root layout to scroll view
        scrollView.addView(rootLayout)

        return scrollView
    }

    private fun createSettingsSection(title: String): LinearLayout {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
        }

        // Card for the section
        val card = CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            radius = 16f
            cardElevation = 8f
            setCardBackgroundColor(Color.parseColor("#333333"))
        }

        // Card content
        val cardContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(24, 24, 24, 24)
        }

        // Section title
        val titleText = TextView(requireContext()).apply {
            text = title
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        cardContent.addView(titleText)
        card.addView(cardContent)
        container.addView(card)

        return cardContent
    }

    private fun createSettingItem(
        title: String,
        description: String,
        initialValue: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ): View {
        val itemLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        // Text container
        val textContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // weight
            )
        }

        // Title text
        val titleText = TextView(requireContext()).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Description text
        val descText = TextView(requireContext()).apply {
            text = description
            textSize = 14f
            setTextColor(Color.LTGRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Switch for toggling the setting
        val switch = Switch(requireContext()).apply {
            isChecked = initialValue
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(isChecked)
            }
        }

        // Add views to containers
        textContainer.addView(titleText)
        textContainer.addView(descText)

        itemLayout.addView(textContainer)
        itemLayout.addView(switch)

        return itemLayout
    }

    private fun loadSettings() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        autoFocus = prefs.getBoolean(KEY_AUTO_FOCUS, true)
        hdrMode = prefs.getBoolean(KEY_HDR_MODE, false)
        enableAudio = prefs.getBoolean(KEY_ENABLE_AUDIO, true)
        highQuality = prefs.getBoolean(KEY_HIGH_QUALITY, false)
        planeDetection = prefs.getBoolean(KEY_PLANE_DETECTION, true)
        depthSensing = prefs.getBoolean(KEY_DEPTH_SENSING, false)
    }

    private fun saveSettings() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_AUTO_FOCUS, autoFocus)
            putBoolean(KEY_HDR_MODE, hdrMode)
            putBoolean(KEY_ENABLE_AUDIO, enableAudio)
            putBoolean(KEY_HIGH_QUALITY, highQuality)
            putBoolean(KEY_PLANE_DETECTION, planeDetection)
            putBoolean(KEY_DEPTH_SENSING, depthSensing)
            apply()
        }
    }
}
package com.abhijeetsahoo.arcast.settings

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

/**
 * A very simple settings fragment that won't crash
 */
class SettingsFragment : Fragment() {
    companion object {
        private const val TAG = "SettingsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        try {
            // Create a simple layout that definitely won't crash
            val rootView = ScrollView(requireContext())
            rootView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            val linearLayout = LinearLayout(requireContext())
            linearLayout.orientation = LinearLayout.VERTICAL
            linearLayout.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            linearLayout.setPadding(16, 16, 16, 16)

            // Title
            val titleView = TextView(requireContext())
            titleView.text = "Settings"
            titleView.textSize = 24f
            val titleParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            titleParams.bottomMargin = 24
            titleView.layoutParams = titleParams
            linearLayout.addView(titleView)

            // Simple settings labels
            addSettingLabel(linearLayout, "Camera Settings",
                "Use default camera settings for now.\nMore options will be available soon.")

            addSettingLabel(linearLayout, "Streaming Settings",
                "Default port: 8080\nMJPEG format is used for maximum compatibility.")

            addSettingLabel(linearLayout, "AR Settings",
                "AR features will be available in a future update.")

            rootView.addView(linearLayout)
            return rootView
        } catch (e: Exception) {
            Log.e(TAG, "Error creating settings view", e)

            // Fallback to extremely simple view if anything goes wrong
            val textView = TextView(context)
            textView.text = "Settings (loading error)"
            textView.setPadding(16, 16, 16, 16)
            return textView
        }
    }

    private fun addSettingLabel(parent: LinearLayout, title: String, description: String) {
        val titleView = TextView(requireContext())
        titleView.text = title
        titleView.textSize = 18f
        val titleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        titleParams.topMargin = 24
        titleParams.bottomMargin = 8
        titleView.layoutParams = titleParams
        parent.addView(titleView)

        val descriptionView = TextView(requireContext())
        descriptionView.text = description
        descriptionView.textSize = 14f
        val descParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        descParams.bottomMargin = 16
        descriptionView.layoutParams = descParams
        parent.addView(descriptionView)
    }
}
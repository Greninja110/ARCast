package com.abhijeetsahoo.arcast.settings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {
    companion object {
        private const val TAG = "SettingsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            // Use a very simple TextView for now to test navigation
            return TextView(context).apply {
                text = "Settings Fragment (Placeholder)"
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Remove ID assignment that's causing the error
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating view", e)
            return null
        }
    }
}
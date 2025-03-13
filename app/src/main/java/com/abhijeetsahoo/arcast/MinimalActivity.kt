package com.abhijeetsahoo.arcast

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Absolute minimal Activity with no dependencies on XML resources
 */
class MinimalActivity : Activity() {

    companion object {
        private const val TAG = "MinimalActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Log.d(TAG, "Starting MinimalActivity onCreate")

            // Most basic possible layout
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 16)
            }

            layout.addView(TextView(this).apply {
                text = "Minimal Activity"
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 24)
            })

            layout.addView(TextView(this).apply {
                text = "This activity uses no XML layouts or resources\n\n" +
                        "Package: ${this@MinimalActivity.packageName}\n" +
                        "Class: ${this@MinimalActivity::class.java.name}"
                textSize = 16f
                gravity = Gravity.CENTER
            })

            setContentView(layout)

            Log.d(TAG, "MinimalActivity successfully created")
            Toast.makeText(this, "MinimalActivity loaded successfully!", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Log.e(TAG, "Error in MinimalActivity", e)

            // Try to show error even if normal initialization fails
            try {
                val errorText = TextView(this).apply {
                    text = "ERROR: ${e.message}"
                    textSize = 18f
                    setTextColor(0xFFFF0000.toInt())
                    gravity = Gravity.CENTER
                    setPadding(16, 16, 16, 16)
                }
                setContentView(errorText)
            } catch (e2: Exception) {
                // At this point, we can't even show an error message
                Log.e(TAG, "Critical error: Failed to show error message", e2)
            }
        }
    }
}
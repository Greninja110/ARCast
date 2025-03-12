package com.abhijeetsahoo.arcast

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Check if we should use the super simple layout
            val useSimpleLayout = intent.getBooleanExtra("USE_SIMPLE_LAYOUT", false)

            if (useSimpleLayout) {
                // Create a super simple layout programmatically to avoid XML parsing issues
                createSimpleLayout()
            } else {
                // Try to use the XML layout
                setContentView(R.layout.activity_main)

                // We won't do any view binding or complex navigation here
                // Just show a message that it worked
                Toast.makeText(this, "XML layout loaded successfully!", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            // If there's still an error, fall back to absolute simplest layout
            Log.e(TAG, "Error in onCreate, falling back to emergency layout", e)
            createEmergencyLayout(e.message ?: "Unknown error")
        }
    }

    private fun createSimpleLayout() {
        // Create a very simple layout programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Add a title
        layout.addView(TextView(this).apply {
            text = "ARCast Simple Mode"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        })

        // Add an explanation
        layout.addView(TextView(this).apply {
            text = "This is a simplified version of the app to troubleshoot XML layout issues."
            textSize = 16f
            setPadding(0, 0, 0, 24)
        })

        // Add a camera button
        layout.addView(Button(this).apply {
            text = "Camera Tab"
            setOnClickListener {
                Toast.makeText(applicationContext, "Camera functionality will be implemented here", Toast.LENGTH_SHORT).show()
            }
            setPadding(0, 16, 0, 16)
        })

        // Add a streaming button
        layout.addView(Button(this).apply {
            text = "Streaming Tab"
            setOnClickListener {
                Toast.makeText(applicationContext, "Streaming functionality will be implemented here", Toast.LENGTH_SHORT).show()
            }
            setPadding(0, 16, 0, 16)
        })

        // Add a settings button
        layout.addView(Button(this).apply {
            text = "Settings Tab"
            setOnClickListener {
                Toast.makeText(applicationContext, "Settings functionality will be implemented here", Toast.LENGTH_SHORT).show()
            }
            setPadding(0, 16, 0, 16)
        })

        // Set the content view
        setContentView(layout)
    }

    private fun createEmergencyLayout(errorMessage: String) {
        // Create absolute minimal layout for emergency situations
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Add error info
        layout.addView(TextView(this).apply {
            text = "EMERGENCY MODE"
            textSize = 24f
            setTextColor(getColor(android.R.color.holo_red_dark))
            setPadding(0, 0, 0, 16)
        })

        layout.addView(TextView(this).apply {
            text = "Error loading app: $errorMessage"
            textSize = 16f
            setPadding(0, 0, 0, 24)
        })

        // Add a return button
        layout.addView(Button(this).apply {
            text = "Return to Debug Mode"
            setOnClickListener {
                finish()
            }
        })

        // Set the content view
        setContentView(layout)
    }
}
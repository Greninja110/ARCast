package com.abhijeetsahoo.arcast

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class EmergencyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Log.d("Emergency", "Creating emergency activity")

            // Create the simplest possible layout
            val textView = TextView(this).apply {
                text = "Emergency Activity Loaded"
                textSize = 24f
                gravity = Gravity.CENTER
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(textView)
            }

            // Set the content view to this simple layout
            setContentView(layout)

            Log.d("Emergency", "Emergency activity created successfully")
        } catch (e: Exception) {
            Log.e("Emergency", "Error in emergency activity: ${e.message}", e)
        }
    }
}
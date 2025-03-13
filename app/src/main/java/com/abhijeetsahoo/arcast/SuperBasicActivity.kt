package com.abhijeetsahoo.arcast

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SuperBasicActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create a simple TextView programmatically
        val textView = TextView(this).apply {
            text = "ARCast App Loaded Successfully!"
            textSize = 24f
            setPadding(50, 50, 50, 50)
        }

        // Set the content view to this TextView
        setContentView(textView)
    }
}
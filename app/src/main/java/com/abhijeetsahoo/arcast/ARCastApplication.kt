package com.abhijeetsahoo.arcast

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class UltraMinimalActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this)
        textView.text = "Ultra Minimal Activity Working"
        setContentView(textView)
    }
}
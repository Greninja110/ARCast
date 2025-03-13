package com.abhijeetsahoo.arcast

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Enhanced debug activity that can inspect XML files and launch MainActivity with different layouts
 */
class DebugActivity : Activity() {

    companion object {
        private const val TAG = "DebugActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Create a simple vertical layout
            val mainLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                setPadding(16, 16, 16, 16)
            }

            // Add a title text
            val titleText = TextView(this).apply {
                text = "ARCast Debug Mode"
                textSize = 24f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 32)
                }
            }
            mainLayout.addView(titleText)

            // Add error information
            val errorInfo = TextView(this).apply {
                text = "Current Error: Binary XML file line #27 in com.abhijeetsahoo"
                textSize = 16f
                setTextColor(resources.getColor(android.R.color.holo_red_dark))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
            }
            mainLayout.addView(errorInfo)

            // Button to check activity_main.xml line 27
            val checkMainLayout = Button(this).apply {
                text = "Check activity_main.xml"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
                setOnClickListener {
                    showXmlContents("activity_main.xml")
                }
            }
            mainLayout.addView(checkMainLayout)

            // Button to try super simple layout
            val trySuperSimpleLayout = Button(this).apply {
                text = "Use Super Simple Layout"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
                setOnClickListener {
                    setSimpleLayout()
                }
            }
            mainLayout.addView(trySuperSimpleLayout)

            // Button to try standard layout
            val tryStandardLayout = Button(this).apply {
                text = "Try Launch MainActivity"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
                setOnClickListener {
                    launchMainActivity()
                }
            }
            mainLayout.addView(tryStandardLayout)

            // Text area to show XML contents
            val xmlTextArea = TextView(this).apply {
                id = View.generateViewId()
                text = "Select an option above to inspect XML files"
                textSize = 14f
                background = getDrawable(android.R.drawable.edit_text)
                setPadding(16, 16, 16, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    weight = 1f
                    setMargins(0, 16, 0, 0)
                }
            }
            mainLayout.addView(xmlTextArea)

            // Set the content view
            val scrollView = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                addView(mainLayout)
            }
            setContentView(scrollView)

        } catch (e: Exception) {
            Log.e(TAG, "Error creating debug activity", e)
            Toast.makeText(this, "Error starting debug activity: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showXmlContents(filename: String) {
        try {
            val textView = findViewById<TextView>(android.R.id.content)
                .findViewById<ScrollView>(0)
                .findViewById<LinearLayout>(0)
                .getChildAt(4) as TextView

            try {
                val inputStream = assets.open("res/layout/$filename")
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = StringBuilder()
                var lineNumber = 1
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    content.append("$lineNumber: $line\n")
                    lineNumber++
                }

                textView.text = content.toString()

            } catch (e: Exception) {
                textView.text = "Could not read file: $filename\nError: ${e.message}"
                Log.e(TAG, "Error reading XML file", e)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error displaying XML content: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setSimpleLayout() {
        // This would normally modify a preference to use a simple layout
        // For now, just show a Toast
        Toast.makeText(this, "Simple layout will be used on next launch", Toast.LENGTH_SHORT).show()

        // Launch MainActivity
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("USE_SIMPLE_LAYOUT", true)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching MainActivity", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchMainActivity() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching MainActivity", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
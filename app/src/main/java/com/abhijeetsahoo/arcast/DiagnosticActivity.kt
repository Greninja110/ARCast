package com.abhijeetsahoo.arcast

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Ultra-simplified diagnostic activity
 */
class DiagnosticActivity : Activity() {

    companion object {
        private const val TAG = "DiagnosticActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                gravity = Gravity.CENTER_HORIZONTAL
            }

            // Add title
            val titleButton = Button(this).apply {
                text = "ARCast Diagnostic"
                textSize = 24f
                isEnabled = false
                background = null
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 32)
                }
            }
            layout.addView(titleButton)

            // Add diagnostic buttons with separate creation for each
            val minimalButton = Button(this).apply {
                text = "Try Minimal Activity Launch"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 16, 0, 16)
                }
                setOnClickListener { launchMinimalActivity() }
            }
            layout.addView(minimalButton)

            val packageButton = Button(this).apply {
                text = "Simple Package Check"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 16, 0, 16)
                }
                setOnClickListener { simplePackageCheck() }
            }
            layout.addView(packageButton)

            val resourceButton = Button(this).apply {
                text = "Basic Resource Check"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 16, 0, 16)
                }
                setOnClickListener { basicResourceCheck() }
            }
            layout.addView(resourceButton)

            val menuButton = Button(this).apply {
                text = "Check Bottom Nav Menu"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 16, 0, 16)
                }
                setOnClickListener { checkBottomNavMenu() }
            }
            layout.addView(menuButton)

            val containerButton = Button(this).apply {
                text = "Check Fragment Container"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 16, 0, 16)
                }
                setOnClickListener { checkFragmentContainer() }
            }
            layout.addView(containerButton)

            setContentView(layout)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error creating diagnostic: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchMinimalActivity() {
        try {
            val intent = Intent(this, MinimalActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching MinimalActivity", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun simplePackageCheck() {
        try {
            val appPackage = packageName
            Toast.makeText(this, "App package: $appPackage", Toast.LENGTH_LONG).show()
            Log.d(TAG, "App package: $appPackage")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking package", e)
            Toast.makeText(this, "Package check error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun basicResourceCheck() {
        try {
            val stringResourceId = R.string.app_name
            val appName = getString(stringResourceId)
            Toast.makeText(this, "Found app_name: $appName", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Found app_name resource: $appName (ID: $stringResourceId)")
        } catch (e: Exception) {
            Log.e(TAG, "Error testing basic resource", e)
            Toast.makeText(this, "Resource error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkBottomNavMenu() {
        try {
            val menuId = R.menu.bottom_nav_menu
            Toast.makeText(this, "Found bottom_nav_menu (ID: $menuId)", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Found bottom_nav_menu (ID: $menuId)")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking bottom_nav_menu", e)
            Toast.makeText(this, "Menu error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkFragmentContainer() {
        try {
            val containerId = R.id.fragment_container
            Toast.makeText(this, "Found fragment_container (ID: $containerId)", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Found fragment_container (ID: $containerId)")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking fragment_container", e)
            Toast.makeText(this, "Container error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
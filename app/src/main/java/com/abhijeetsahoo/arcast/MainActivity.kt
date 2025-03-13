package com.abhijeetsahoo.arcast

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.abhijeetsahoo.arcast.streaming.StreamingService
import com.abhijeetsahoo.arcast.utils.ErrorHandler
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var navController: NavController
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if dark theme is enabled
        applyTheme()

        try {
            setContentView(R.layout.activity_main_temp)

            // Set up Navigation
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navController = navHostFragment.navController

            // Set up bottom navigation with the nav controller
            bottomNav = findViewById(R.id.bottom_nav)
            bottomNav.setupWithNavController(navController)

            // Handle item selection manually to ensure proper navigation
            bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_stream -> {
                        navController.navigate(R.id.streamFragment)
                        true
                    }
                    R.id.nav_mode -> {
                        navController.navigate(R.id.modeFragment)
                        true
                    }
                    R.id.nav_settings -> {
                        navController.navigate(R.id.settingsFragment)
                        true
                    }
                    else -> false
                }
            }

            // Add a listener to update the UI when navigation changes
            navController.addOnDestinationChangedListener { _, destination, _ ->
                // Update selected item in bottom navigation
                when (destination.id) {
                    R.id.streamFragment -> bottomNav.selectedItemId = R.id.nav_stream
                    R.id.modeFragment -> bottomNav.selectedItemId = R.id.nav_mode
                    R.id.settingsFragment -> bottomNav.selectedItemId = R.id.nav_settings
                }
            }

        } catch (e: Exception) {
            ErrorHandler.handleException(this, TAG, "Error in onCreate", e)
        }
    }

    private fun applyTheme() {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isDarkTheme = sharedPrefs.getBoolean("dark_theme", false)

        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()
        // Check for any active streaming services
        updateStreamingStatus()
    }

    private fun updateStreamingStatus() {
        // This method would check if the streaming service is running
        // and update the UI accordingly
        try {
            val serviceIntent = Intent(this, StreamingService::class.java)
            // You could add a flag here to just check status without starting the service
            // serviceIntent.action = StreamingService.ACTION_CHECK_STATUS
            // startService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking streaming service status", e)
        }
    }
}
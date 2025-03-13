package com.abhijeetsahoo.arcast

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
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
            setContentView(R.layout.activity_main)

            // Set up Navigation
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navController = navHostFragment.navController

            // Set up bottom navigation with the nav controller
            bottomNav = findViewById(R.id.bottom_nav)
            bottomNav.setupWithNavController(navController)

            // Add a listener to update the UI when navigation changes
            navController.addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.streamFragment, R.id.modeFragment, R.id.settingsFragment -> {
                        // Show bottom navigation
                        bottomNav.visibility = View.VISIBLE
                    }
                    else -> {
                        // Hide bottom navigation for other destinations
                        bottomNav.visibility = View.GONE
                    }
                }
            }

            // Handle navigation item reselection (to avoid recreating the fragment)
            bottomNav.setOnItemReselectedListener { item ->
                // Do nothing to prevent recreation
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
}
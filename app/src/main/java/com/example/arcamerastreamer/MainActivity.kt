package com.abhijeetsahoo.arcast

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            // Initialize bottom navigation
            bottomNav = findViewById(R.id.bottom_nav)

            // Set listener for bottom navigation
            bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_camera -> {
                        loadFragment(CameraFragment())
                        true
                    }
                    R.id.nav_stream -> {
                        loadFragment(StreamFragment())
                        true
                    }
                    R.id.nav_settings -> {
                        loadFragment(SettingsFragment())
                        true
                    }
                    else -> false
                }
            }

            // Load default fragment
            if (savedInstanceState == null) {
                loadFragment(CameraFragment())
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
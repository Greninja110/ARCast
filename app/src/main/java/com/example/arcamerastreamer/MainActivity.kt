package com.abhijeetsahoo.arcast

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.abhijeetsahoo.arcast.camera.CameraFragment
import com.abhijeetsahoo.arcast.databinding.ActivityMainBinding
import com.abhijeetsahoo.arcast.mode.ModeFragment
import com.abhijeetsahoo.arcast.settings.SettingsFragment
import com.abhijeetsahoo.arcast.streaming.StreamFragment
import com.abhijeetsahoo.arcast.streaming.StreamingService
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity(), BottomNavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    // Make binding public so fragments can access it
    lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    // Service connection
    private var streamingService: StreamingService? = null
    private var isBound = false

    // Service connection
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as StreamingService.LocalBinder
            streamingService = binder.getService()
            isBound = true

            // Observe streaming service state
            streamingService?.serviceState?.observe(this@MainActivity) { state ->
                viewModel.updateStreamingState(state)
            }

            Log.d(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            streamingService = null
            isBound = false
            Log.d(TAG, "Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate layout
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Set up bottom navigation
        binding.bottomNav.setOnNavigationItemSelectedListener(this)

        // Load default fragment
        if (savedInstanceState == null) {
            val initialFragment = CameraFragment()
            loadFragment(initialFragment)
            binding.bottomNav.selectedItemId = R.id.nav_stream
        }

        // Observe ViewModel state
        observeViewModel()

        // Bind to streaming service
        bindStreamingService()
    }

    /**
     * Observe ViewModel state changes
     */
    private fun observeViewModel() {
        viewModel.appState.observe(this) { state ->
            // Handle state changes here (e.g., update UI)
            Log.d(TAG, "App state updated: $state")
        }
    }

    /**
     * Navigation item selected handler
     */
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_stream -> {
                loadFragment(StreamFragment())
                return true
            }
            R.id.nav_mode -> {
                loadFragment(ModeFragment())
                return true
            }
            R.id.nav_settings -> {
                loadFragment(SettingsFragment())
                return true
            }
            else -> return false
        }
    }

    /**
     * Load a fragment
     */
    private fun loadFragment(fragment: Fragment) {
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading fragment", e)
        }
    }

    /**
     * Bind to the streaming service
     */
    private fun bindStreamingService() {
        Intent(this, StreamingService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Start streaming service
     */
    fun startStreamingService() {
        val serviceIntent = Intent(this, StreamingService::class.java)
        startService(serviceIntent)

        if (!isBound) {
            bindStreamingService()
        }
    }

    /**
     * Stop streaming service
     */
    fun stopStreamingService() {
        if (isBound) {
            streamingService?.stopStreaming()
            unbindService(connection)
            isBound = false
        }

        val serviceIntent = Intent(this, StreamingService::class.java)
        stopService(serviceIntent)
    }

    /**
     * Handle back button
     */
    override fun onBackPressed() {
        // Get current fragment
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        // If we're not on stream fragment, go back to it
        if (currentFragment !is StreamFragment) {
            binding.bottomNav.selectedItemId = R.id.nav_stream
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Clean up on activity destroy
     */
    override fun onDestroy() {
        super.onDestroy()

        // Unbind from service
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
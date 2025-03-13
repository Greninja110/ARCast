package com.abhijeetsahoo.arcast.streaming

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.abhijeetsahoo.arcast.R
import com.abhijeetsahoo.arcast.utils.ErrorHandler
import com.abhijeetsahoo.arcast.utils.NetworkUtils

class StreamFragment : Fragment() {
    companion object {
        private const val TAG = "StreamFragment"
    }

    // UI components
    private lateinit var statusChip: View
    private lateinit var textCurrentMode: TextView
    private lateinit var textCurrentQuality: TextView
    private lateinit var textIpAddress: TextView
    private lateinit var textUrl: TextView
    private lateinit var textConnectedClients: TextView
    private lateinit var buttonCopyUrl: Button
    private lateinit var buttonStartStream: Button
    private var videoLayout: ConstraintLayout? = null
    private var imageOnlyLayout: ConstraintLayout? = null
    private var audioOnlyLayout: ConstraintLayout? = null
    private var fullStreamLayout: ConstraintLayout? = null
    private var qrCodeCard: CardView? = null

    // Streaming variables
    private var isStreaming = false
    private var streamUrl: String? = null
    private var ipAddress: String? = null
    private var streamingMode = StreamingMode.VIDEO_ONLY
    private var streamQuality = StreamQuality.MEDIUM
    private var clientCount = 0

    // Broadcast Receivers
    private val streamingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                StreamingService.BROADCAST_STREAMING_STARTED -> {
                    isStreaming = true
                    streamUrl = intent.getStringExtra(StreamingService.EXTRA_URL)
                    ipAddress = intent.getStringExtra(StreamingService.EXTRA_IP_ADDRESS)
                    val modeString = intent.getStringExtra(StreamingService.EXTRA_MODE)
                    val quality = intent.getIntExtra(StreamingService.EXTRA_QUALITY, 75)

                    // Parse mode
                    try {
                        streamingMode = StreamingMode.valueOf(modeString ?: "VIDEO_ONLY")
                    } catch (e: Exception) {
                        streamingMode = StreamingMode.VIDEO_ONLY
                    }

                    // Update quality
                    streamQuality = when {
                        quality <= 40 -> StreamQuality.LOW
                        quality <= 70 -> StreamQuality.MEDIUM
                        else -> StreamQuality.HIGH
                    }

                    updateUI()
                }
                StreamingService.BROADCAST_STREAMING_STOPPED -> {
                    isStreaming = false
                    updateUI()
                }
                StreamingService.BROADCAST_STREAMING_ERROR -> {
                    val errorMessage = intent.getStringExtra(StreamingService.EXTRA_ERROR_MESSAGE) ?: "Unknown error"
                    isStreaming = false
                    showError(errorMessage)
                    updateUI()
                }
                StreamingService.BROADCAST_URL_UPDATED -> {
                    streamUrl = intent.getStringExtra(StreamingService.EXTRA_URL)
                    ipAddress = intent.getStringExtra(StreamingService.EXTRA_IP_ADDRESS)
                    val modeString = intent.getStringExtra(StreamingService.EXTRA_MODE)
                    val quality = intent.getIntExtra(StreamingService.EXTRA_QUALITY, 75)
                    clientCount = intent.getIntExtra(StreamingService.EXTRA_CLIENT_COUNT, 0)

                    // Parse mode
                    try {
                        streamingMode = StreamingMode.valueOf(modeString ?: "VIDEO_ONLY")
                    } catch (e: Exception) {
                        streamingMode = StreamingMode.VIDEO_ONLY
                    }

                    // Update quality
                    streamQuality = when {
                        quality <= 40 -> StreamQuality.LOW
                        quality <= 70 -> StreamQuality.MEDIUM
                        else -> StreamQuality.HIGH
                    }

                    updateUI()
                }
                StreamingService.BROADCAST_CLIENTS_CHANGED -> {
                    clientCount = intent.getIntExtra(StreamingService.EXTRA_CLIENT_COUNT, 0)
                    updateClientCount()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            // First try to inflate the original layout
            return inflater.inflate(R.layout.fragment_stream_info, container, false)
        } catch (e: Exception) {
            // Log the error
            Log.e(TAG, "Error inflating original layout", e)

            // Create and return a simple fallback layout
            return createFallbackLayout()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            // Initialize UI components
            initUI(view)

            // Set up click listeners
            setupListeners()

            // Register broadcast receivers
            registerReceivers()

            // Check current IP address
            checkNetworkStatus()

            // Check if streaming service is running
            checkStreamingStatus()

            // Update the UI based on current state
            updateUI()
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error in onViewCreated", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            // Unregister broadcast receivers
            requireActivity().unregisterReceiver(streamingReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    /**
     * Initialize UI components
     */
    private fun initUI(view: View) {
        try {
            // Find all views - handle potential missing views

            // Main status views
            statusChip = view.findViewById(R.id.status_chip)
            textCurrentMode = view.findViewById(R.id.text_current_mode)
            textCurrentQuality = view.findViewById(R.id.text_current_quality)
            textIpAddress = view.findViewById(R.id.text_ip_address)
            textUrl = view.findViewById(R.id.text_url)
            textConnectedClients = view.findViewById(R.id.text_connected_clients)
            buttonCopyUrl = view.findViewById(R.id.button_copy_url)
            buttonStartStream = view.findViewById(R.id.button_start_stream)

            // Mode-specific layouts - handle them safely as nullable
            try {
                videoLayout = view.findViewById(R.id.video_layout)
                imageOnlyLayout = view.findViewById(R.id.image_only_layout)
                audioOnlyLayout = view.findViewById(R.id.audio_only_layout)
                fullStreamLayout = view.findViewById(R.id.full_stream_layout)
                qrCodeCard = view.findViewById(R.id.qr_code_card)
            } catch (e: Exception) {
                Log.e(TAG, "Some optional UI components not found", e)
                // Not a critical error, we can continue without these
            }

            // Set initial values
            textIpAddress.text = "IP Address: Checking..."
            textUrl.text = "Stream URL: Not available"
            textConnectedClients.text = "0"

            // Disable copy button initially
            buttonCopyUrl.isEnabled = false
        } catch (e: Exception) {
            throw Exception("Error initializing UI components", e)
        }
    }

    /**
     * Set up click listeners
     */
    private fun setupListeners() {
        // Copy URL button
        buttonCopyUrl.setOnClickListener {
            copyUrlToClipboard()
        }

        // Start/Stop Streaming button
        buttonStartStream.setOnClickListener {
            toggleStreaming()
        }

        // Video mode buttons if available
        // Video mode buttons if available
        try {
            // Safe lookup with null check
            val switchCameraButton = view?.findViewById<Button>(R.id.button_switch_camera)
            switchCameraButton?.setOnClickListener {
                Toast.makeText(context, "Switch Camera clicked", Toast.LENGTH_SHORT).show()
            }

            // Safe lookup with null check
            val toggleFlashButton = view?.findViewById<Button>(R.id.button_toggle_flash)
            toggleFlashButton?.setOnClickListener {
                Toast.makeText(context, "Toggle Flash clicked", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // Not critical, some buttons might not be available in the layout
            Log.e(TAG, "Error setting up optional button listeners", e)
        }
    }

    /**
     * Register broadcast receivers
     */
    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(StreamingService.BROADCAST_STREAMING_STARTED)
            addAction(StreamingService.BROADCAST_STREAMING_STOPPED)
            addAction(StreamingService.BROADCAST_STREAMING_ERROR)
            addAction(StreamingService.BROADCAST_URL_UPDATED)
            addAction(StreamingService.BROADCAST_CLIENTS_CHANGED)
        }
        requireActivity().registerReceiver(streamingReceiver, filter)
    }

    /**
     * Check network status and update IP address
     */
    private fun checkNetworkStatus() {
        // Run in background to not block UI
        Thread {
            val ip = NetworkUtils.getWifiIPAddress(requireContext())
            activity?.runOnUiThread {
                ipAddress = ip
                textIpAddress.text = "IP Address: ${ip ?: "Not connected"}"
            }
        }.start()
    }

    /**
     * Check if streaming service is running
     */
    private fun checkStreamingStatus() {
        val intent = Intent(requireContext(), StreamingService::class.java).apply {
            action = StreamingService.ACTION_CHECK_STATUS
        }
        requireContext().startService(intent)
    }

    /**
     * Update UI based on current state
     */
    private fun updateUI() {
        activity?.runOnUiThread {
            try {
                // Update stream status
                if (isStreaming) {
                    statusChip.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_status_tracking)
                    statusChip.visibility = View.VISIBLE
                    (statusChip as? TextView)?.text = "Streaming"
                    buttonStartStream.text = "Stop Streaming"
                    // Show or hide appropriate layouts
                    updateLayoutVisibility()
                } else {
                    statusChip.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_status_paused)
                    statusChip.visibility = View.VISIBLE
                    (statusChip as? TextView)?.text = "Not Streaming"
                    buttonStartStream.text = "Start Streaming"
                    // Hide all mode layouts
                    hideAllModeLayouts()
                }

                // Update mode and quality text
                val modeText = when (streamingMode) {
                    StreamingMode.IMAGE_ONLY -> "Image Only"
                    StreamingMode.AUDIO_ONLY -> "Audio Only"
                    StreamingMode.VIDEO_ONLY -> "Video Only"
                    StreamingMode.VIDEO_AUDIO -> "Video & Audio"
                }
                textCurrentMode.text = modeText

                val qualityText = when (streamQuality) {
                    StreamQuality.LOW -> "Low (480p)"
                    StreamQuality.MEDIUM -> "Medium (720p)"
                    StreamQuality.HIGH -> "High (1080p)"
                }
                textCurrentQuality.text = qualityText

                // Update URL
                textUrl.text = "Stream URL: ${streamUrl ?: "Not available"}"
                buttonCopyUrl.isEnabled = !streamUrl.isNullOrEmpty()

                // Update client count
                updateClientCount()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating UI", e)
            }
        }
    }

    /**
     * Update client count display
     */
    private fun updateClientCount() {
        activity?.runOnUiThread {
            textConnectedClients.text = clientCount.toString()
        }
    }

    /**
     * Update layout visibility based on streaming mode
     */
    private fun updateLayoutVisibility() {
        try {
            // Hide all layouts first
            hideAllModeLayouts()

            // Show layout based on mode
            when (streamingMode) {
                StreamingMode.IMAGE_ONLY -> {
                    imageOnlyLayout?.visibility = View.VISIBLE
                }
                StreamingMode.AUDIO_ONLY -> {
                    audioOnlyLayout?.visibility = View.VISIBLE
                }
                StreamingMode.VIDEO_ONLY -> {
                    videoLayout?.visibility = View.VISIBLE
                }
                StreamingMode.VIDEO_AUDIO -> {
                    fullStreamLayout?.visibility = View.VISIBLE ?: run {
                        // Fallback to video layout if full stream layout is not available
                        videoLayout?.visibility = View.VISIBLE
                    }
                }
            }

            // Show QR code if streaming
            qrCodeCard?.visibility = if (isStreaming) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "Error updating layout visibility", e)
        }
    }

    /**
     * Hide all mode-specific layouts
     */
    private fun hideAllModeLayouts() {
        try {
            videoLayout?.visibility = View.GONE
            imageOnlyLayout?.visibility = View.GONE
            audioOnlyLayout?.visibility = View.GONE
            fullStreamLayout?.visibility = View.GONE
            qrCodeCard?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding layouts", e)
        }
    }

    /**
     * Copy stream URL to clipboard
     */
    private fun copyUrlToClipboard() {
        try {
            val url = streamUrl ?: return
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Stream URL", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error copying URL to clipboard", e)
        }
    }

    /**
     * Toggle streaming on/off
     */
    private fun toggleStreaming() {
        try {
            val context = requireContext()

            if (isStreaming) {
                // Stop streaming
                val intent = Intent(context, StreamingService::class.java).apply {
                    action = StreamingService.ACTION_STOP_STREAMING
                }
                context.startService(intent)
            } else {
                // Start streaming
                val intent = Intent(context, StreamingService::class.java).apply {
                    action = StreamingService.ACTION_START_STREAMING
                    putExtra(StreamingService.EXTRA_PORT, 8080) // Default port
                    putExtra(StreamingService.EXTRA_QUALITY, 70) // Medium quality
                    putExtra(StreamingService.EXTRA_MODE, streamingMode)
                    putExtra(StreamingService.EXTRA_PROTOCOL, StreamProtocol.HTTP)
                }
                context.startService(intent)
            }
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error toggling streaming", e)
        }
    }

    /**
     * Show error message
     */
    private fun showError(message: String) {
        activity?.runOnUiThread {
            try {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()

                // Update status chip to show error
                statusChip.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_status_error)
                (statusChip as? TextView)?.text = "Error"
            } catch (e: Exception) {
                Log.e(TAG, "Error showing error message", e)
            }
        }
    }

    /**
     * Create a simple fallback layout for when the main layout fails to inflate
     */
    private fun createFallbackLayout(): View {
        val scrollView = NestedScrollView(requireContext())
        scrollView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 32, 32, 32)
        }

        // Add title
        val titleView = TextView(requireContext()).apply {
            text = "Stream Status"
            textSize = 22f
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        container.addView(titleView)

        // Add IP address
        textIpAddress = TextView(requireContext()).apply {
            text = "IP Address: Checking..."
            textSize = 16f
            setPadding(0, 8, 0, 8)
        }
        container.addView(textIpAddress)

        // Add URL
        textUrl = TextView(requireContext()).apply {
            text = "Stream URL: Not available"
            textSize = 16f
            setPadding(0, 8, 0, 8)
        }
        container.addView(textUrl)

        // Add Mode
        textCurrentMode = TextView(requireContext()).apply {
            text = "Mode: Video Only"
            textSize = 16f
            setPadding(0, 8, 0, 8)
        }
        container.addView(textCurrentMode)

        // Add Quality
        textCurrentQuality = TextView(requireContext()).apply {
            text = "Quality: Medium (720p)"
            textSize = 16f
            setPadding(0, 8, 0, 8)
        }
        container.addView(textCurrentQuality)

        // Add Client Count
        val clientLabel = TextView(requireContext()).apply {
            text = "Connected Clients: "
            textSize = 16f
            setPadding(0, 8, 0, 8)
        }

        val clientLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        clientLayout.addView(clientLabel)

        textConnectedClients = TextView(requireContext()).apply {
            text = "0"
            textSize = 16f
        }
        clientLayout.addView(textConnectedClients)

        container.addView(clientLayout)

        // Add copy URL button
        buttonCopyUrl = Button(requireContext()).apply {
            text = "Copy URL"
            isEnabled = false
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 32
            params.bottomMargin = 16
            layoutParams = params
        }
        container.addView(buttonCopyUrl)

        // Add start streaming button
        buttonStartStream = Button(requireContext()).apply {
            text = "Start Streaming"
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 16
            params.bottomMargin = 32
            layoutParams = params
        }
        container.addView(buttonStartStream)

        // Add status chip (simple TextView in fallback mode)
        statusChip = TextView(requireContext()).apply {
            text = "Not Streaming"
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_status_paused)
            setPadding(24, 12, 24, 12)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = params
        }
        container.addView(statusChip)

        scrollView.addView(container)
        return scrollView
    }
}
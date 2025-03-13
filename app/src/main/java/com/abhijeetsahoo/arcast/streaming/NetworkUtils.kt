package com.abhijeetsahoo.arcast.streaming

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Enumeration

/**
 * Utility class for network-related operations
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * Get the IP address of the device on the current WiFi network
     */
    fun getLocalIpAddress(context: Context): String {
        // First try to get IP from WiFi Manager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipAddress = wifiInfo.ipAddress

        if (ipAddress != 0) {
            return formatIpAddress(ipAddress)
        }

        // If WiFi Manager method failed, try network interfaces approach
        try {
            val networkInterfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface: NetworkInterface = networkInterfaces.nextElement()
                val addresses = networkInterface.inetAddresses

                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    // Filter for IPv4 and non-loopback addresses
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: SocketException) {
            Log.e(TAG, "Error getting IP address: ${e.message}", e)
        }

        return "127.0.0.1" // Return loopback as fallback
    }

    /**
     * Format IP address from int to string
     */
    private fun formatIpAddress(ipAddress: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }

    /**
     * Checks if the device is connected to WiFi
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Generates URLs for different streaming modes
     */
    fun generateStreamUrls(ipAddress: String, port: Int): Map<String, String> {
        return mapOf(
            "image" to "http://$ipAddress:$port/image",
            "video" to "http://$ipAddress:$port/video",
            "audio" to "http://$ipAddress:$port/audio",
            "stream" to "http://$ipAddress:$port/stream",
            "webrtc" to "webrtc://$ipAddress:$port",
            "rtsp" to "rtsp://$ipAddress:$port"
        )
    }

    /**
     * Generate QR code content for a URL (used for easy connection)
     */
    fun generateQrCodeContent(url: String): String {
        return url
    }
}
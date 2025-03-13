package com.abhijeetsahoo.arcast.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.math.BigInteger
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.UnknownHostException
import java.nio.ByteOrder
import java.util.Collections

/**
 * Utility class for network operations
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * Check if the device is connected to the internet
     */
    fun isNetworkAvailable(context: Context): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                return activeNetworkInfo != null && activeNetworkInfo.isConnected
            }
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error checking network availability", e)
            return false
        }
    }

    /**
     * Get the device's WiFi IP address
     */
    fun getWifiIPAddress(context: Context): String? {
        try {
            // First try to get IP from WiFi Manager
            val wifiIP = getWifiManagerIP(context)
            if (wifiIP != null) {
                return wifiIP
            }

            // Fallback to checking network interfaces
            return getNetworkInterfaceIP()
        } catch (e: Exception) {
            ErrorHandler.handleException(context, TAG, "Error getting WiFi IP address", e)
            return null
        }
    }

    /**
     * Get WiFi IP address using WifiManager
     */
    private fun getWifiManagerIP(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            var ipAddress = wifiManager.connectionInfo.ipAddress

            // Convert little-endian to big-endian if needed
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                ipAddress = Integer.reverseBytes(ipAddress)
            }

            // Convert to IPv4 string format
            val ipByteArray = BigInteger.valueOf(ipAddress.toLong()).toByteArray()

            try {
                val ipAddressString = InetAddress.getByAddress(ipByteArray).hostAddress
                if (ipAddressString != null && ipAddressString != "0.0.0.0") {
                    return ipAddressString
                }
            } catch (ex: UnknownHostException) {
                Log.e(TAG, "Error getting host address", ex)
            }

            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WiFi Manager IP", e)
            return null
        }
    }

    /**
     * Get IP address by enumeration network interfaces
     */
    private fun getNetworkInterfaceIP(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            for (intf in interfaces) {
                // Skip loopback interfaces and interfaces that are down
                if (intf.isLoopback || !intf.isUp) {
                    continue
                }

                val addresses = Collections.list(intf.inetAddresses)

                for (addr in addresses) {
                    // Skip loopback addresses, IPv6 addresses, and null addresses
                    if (addr.isLoopbackAddress || addr.hostAddress.contains(':') || addr.hostAddress == null) {
                        continue
                    }

                    return addr.hostAddress
                }
            }

            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP from network interfaces", e)
            return null
        }
    }

    /**
     * Check if port is available
     */
    fun isPortAvailable(port: Int): Boolean {
        var socket: java.net.ServerSocket? = null

        try {
            socket = java.net.ServerSocket(port)
            socket.reuseAddress = true
            return true
        } catch (e: Exception) {
            return false
        } finally {
            socket?.close()
        }
    }

    /**
     * Find an available port starting from the specified port
     */
    fun findAvailablePort(startPort: Int, maxPort: Int = 65535): Int {
        for (port in startPort..maxPort) {
            if (isPortAvailable(port)) {
                return port
            }
        }

        return -1
    }

    /**
     * Generate a QR code for the given URL
     * Returns a Bitmap containing the QR code
     */
    fun generateQRCode(url: String, size: Int = 512): android.graphics.Bitmap? {
        try {
            // This is just a placeholder - in a real implementation you would use
            // a library like ZXing to generate an actual QR code
            // Leaving this as a stub for now
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error generating QR code", e)
            return null
        }
    }
}
package com.abhijeetsahoo.arcast.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.math.BigInteger
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.UnknownHostException
import java.nio.ByteOrder
import java.util.Collections
import java.util.Locale

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
            Log.e(TAG, "Error checking network availability", e)
            return false
        }
    }

    /**
     * Get the device's WiFi IP address
     */
    fun getWifiIPAddress(context: Context): String? {
        try {
            // First try using WifiManager (more reliable on most devices)
            val wifiIP = getWifiManagerIP(context)
            if (!wifiIP.isNullOrEmpty() && wifiIP != "0.0.0.0") {
                return wifiIP
            }

            // Fallback to checking network interfaces
            val interfaceIP = getNetworkInterfaceIP()
            if (!interfaceIP.isNullOrEmpty() && interfaceIP != "0.0.0.0") {
                return interfaceIP
            }

            // Last resort - localhost
            return "127.0.0.1"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WiFi IP address", e)
            return "127.0.0.1"
        }
    }

    /**
     * Get WiFi IP address using WifiManager
     */
    private fun getWifiManagerIP(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipAddress = wifiInfo.ipAddress

            // Handle little-endian to big-endian conversion if needed
            val formattedIpAddress = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                // Format as standard IP address string
                String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    (ipAddress shr 8) and 0xff,
                    (ipAddress shr 16) and 0xff,
                    (ipAddress shr 24) and 0xff
                )
            } else {
                // Convert to IPV4 string format
                InetAddress.getByAddress(BigInteger.valueOf(ipAddress.toLong()).toByteArray()).hostAddress
            }

            return if (formattedIpAddress == "0.0.0.0") null else formattedIpAddress
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

            // First check wlan interfaces which are typically WiFi
            for (intf in interfaces) {
                if (intf.name.startsWith("wlan")) {
                    val addresses = Collections.list(intf.inetAddresses)
                    for (addr in addresses) {
                        if (!addr.isLoopbackAddress && addr.hostAddress.indexOf(':') == -1) {
                            return addr.hostAddress
                        }
                    }
                }
            }

            // Then check all other interfaces
            for (intf in interfaces) {
                // Skip loopback interfaces and interfaces that are down
                if (intf.isLoopback || !intf.isUp) {
                    continue
                }

                val addresses = Collections.list(intf.inetAddresses)
                for (addr in addresses) {
                    // Skip loopback addresses, IPv6 addresses
                    if (addr.isLoopbackAddress || addr.hostAddress.contains(':')) {
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
        var socket: ServerSocket? = null
        return try {
            socket = ServerSocket(port)
            socket.reuseAddress = true
            true
        } catch (e: Exception) {
            false
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
}
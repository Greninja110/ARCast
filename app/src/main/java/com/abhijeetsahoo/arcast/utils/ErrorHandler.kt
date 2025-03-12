package com.abhijeetsahoo.arcast.utils

import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.abhijeetsahoo.arcast.utils.Logger

/**
 * Utility class to handle errors consistently throughout the app
 */
object ErrorHandler {
    private const val TAG = "ErrorHandler"

    /**
     * Handle an exception with appropriate logging and user feedback
     */
    fun handleException(context: Context?, tag: String, message: String, exception: Throwable) {
        // Log the error
        Logger.e(tag, "$message: ${exception.message}", exception)

        // Show a toast if context is available
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Extension function for Fragment to handle exceptions
     */
    fun Fragment.handleError(tag: String, message: String, exception: Throwable) {
        handleException(context, tag, message, exception)
    }

    /**
     * Execute a block of code with error handling
     */
    inline fun <T> tryOrNull(tag: String, message: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            Logger.e(tag, "$message: ${e.message}", e)
            null
        }
    }

    /**
     * Execute a block of code with error handling and context for UI feedback
     */
    inline fun <T> tryWithToast(context: Context, tag: String, message: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            handleException(context, tag, message, e)
            null
        }
    }

    /**
     * Execute a block of code with error handling and a fallback value
     */
    inline fun <T> tryOrDefault(tag: String, message: String, defaultValue: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            Logger.e(tag, "$message: ${e.message}", e)
            defaultValue
        }
    }
}
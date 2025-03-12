package com.abhijeetsahoo.arcast.utils

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * A simple logging utility that logs to both Android's LogCat and a file.
 */
object Logger {
    private const val TAG = "ARCastLogger"

    // Log levels
    enum class Level { DEBUG, INFO, WARNING, ERROR }

    // Timestamp formatter
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // File for logging
    private var logFile: File? = null

    // Use a single thread executor for file operations to avoid concurrent writes
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Initialize the logger with a log file
     */
    fun initialize(file: File) {
        logFile = file
        log(Level.INFO, TAG, "Logger initialized")
    }

    /**
     * Log a debug message
     */
    fun d(tag: String, message: String) {
        log(Level.DEBUG, tag, message)
    }

    /**
     * Log an info message
     */
    fun i(tag: String, message: String) {
        log(Level.INFO, tag, message)
    }

    /**
     * Log a warning message
     */
    fun w(tag: String, message: String) {
        log(Level.WARNING, tag, message)
    }

    /**
     * Log an error message
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }

    /**
     * Log a message at the specified level
     */
    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        // Log to LogCat
        when (level) {
            Level.DEBUG -> Log.d(tag, message)
            Level.INFO -> Log.i(tag, message)
            Level.WARNING -> Log.w(tag, message)
            Level.ERROR -> {
                if (throwable != null) {
                    Log.e(tag, message, throwable)
                } else {
                    Log.e(tag, message)
                }
            }
        }

        // Log to file asynchronously
        executor.submit {
            try {
                logToFile(level, tag, message, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to log file", e)
            }
        }
    }

    /**
     * Write the log entry to the log file
     */
    private fun logToFile(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        val file = logFile ?: return

        try {
            PrintWriter(FileOutputStream(file, true)).use { writer ->
                val timestamp = dateFormat.format(Date())
                val levelStr = level.name

                // Write log entry
                writer.println("$timestamp | $levelStr | $tag | $message")

                // Write stack trace if there's an exception
                if (throwable != null) {
                    throwable.printStackTrace(writer)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to log file", e)
        }
    }
}
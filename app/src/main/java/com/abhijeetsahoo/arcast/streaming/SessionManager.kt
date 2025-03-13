package com.abhijeetsahoo.arcast.streaming

import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Manages streaming sessions and client connections
 */
class SessionManager {
    companion object {
        private const val TAG = "SessionManager"

        // Session timeout in milliseconds
        private const val SESSION_TIMEOUT = 60_000L // 1 minute
    }

    // Map of session ID to session data
    private val sessions = ConcurrentHashMap<String, SessionData>()

    // Read-write lock for managing access to sessions
    private val sessionsLock = ReentrantReadWriteLock()

    // Session cleanup thread
    private val cleanupThread = Thread {
        while (!Thread.currentThread().isInterrupted) {
            try {
                cleanupExpiredSessions()
                Thread.sleep(30_000) // Check every 30 seconds
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in cleanup thread", e)
            }
        }
    }

    init {
        // Start cleanup thread
        cleanupThread.isDaemon = true
        cleanupThread.start()
    }

    /**
     * Create a new session
     */
    fun createSession(clientIp: String, mode: StreamingMode): String {
        val sessionId = UUID.randomUUID().toString()

        sessionsLock.write {
            sessions[sessionId] = SessionData(
                id = sessionId,
                clientIp = clientIp,
                createdAt = System.currentTimeMillis(),
                lastUpdatedAt = System.currentTimeMillis(),
                streamingMode = mode
            )
        }

        Log.d(TAG, "Created session $sessionId for client $clientIp with mode $mode")
        return sessionId
    }

    /**
     * Get a session by ID
     */
    fun getSession(sessionId: String): SessionData? {
        return sessionsLock.read {
            sessions[sessionId]?.also {
                // Update last access time
                it.lastUpdatedAt = System.currentTimeMillis()
            }
        }
    }

    /**
     * Update a session
     */
    fun updateSession(sessionId: String, update: (SessionData) -> SessionData): Boolean {
        return sessionsLock.write {
            val session = sessions[sessionId] ?: return@write false
            sessions[sessionId] = update(session)
            true
        }
    }

    /**
     * Close a session
     */
    fun closeSession(sessionId: String) {
        sessionsLock.write {
            sessions.remove(sessionId)?.let {
                Log.d(TAG, "Closed session $sessionId for client ${it.clientIp}")
            }
        }
    }

    /**
     * Get all active sessions
     */
    fun getAllSessions(): List<SessionData> {
        return sessionsLock.read {
            sessions.values.toList()
        }
    }

    /**
     * Get total count of active sessions
     */
    fun getSessionCount(): Int {
        return sessionsLock.read {
            sessions.size
        }
    }

    /**
     * Cleanup expired sessions
     */
    private fun cleanupExpiredSessions() {
        val currentTime = System.currentTimeMillis()
        val expiredSessions = mutableListOf<String>()

        sessionsLock.read {
            sessions.forEach { (id, data) ->
                if (currentTime - data.lastUpdatedAt > SESSION_TIMEOUT) {
                    expiredSessions.add(id)
                }
            }
        }

        if (expiredSessions.isNotEmpty()) {
            sessionsLock.write {
                expiredSessions.forEach { id ->
                    sessions.remove(id)?.let {
                        Log.d(TAG, "Expired session $id for client ${it.clientIp}")
                    }
                }
            }
        }
    }

    /**
     * Shutdown the session manager
     */
    fun shutdown() {
        cleanupThread.interrupt()
        sessionsLock.write {
            sessions.clear()
        }
    }
}

/**
 * Data class to represent a streaming session
 */
data class SessionData(
    val id: String,
    val clientIp: String,
    val createdAt: Long,
    var lastUpdatedAt: Long,
    val streamingMode: StreamingMode,
    var customData: Map<String, Any> = emptyMap()
)
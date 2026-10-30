package com.jarvis.assistant.nexus.security

import android.util.Log
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

data class NexusSession(
    val sessionId: String,
    val peerId: String,
    val peerName: String = "Unknown",
    val issuedAt: Long = System.currentTimeMillis(),
    var lastAccessedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (30 * 60 * 1000L) // 30 minutes TTL
) {
    val isValid: Boolean
        get() = System.currentTimeMillis() <= expiresAt
}

object NexusSessionManager {
    private const val TAG = "NexusSessionManager"
    private const val SESSION_TTL_MS = 30 * 60 * 1000L // 30 minutes
    private val secureRandom = SecureRandom()
    private val activeSessions = ConcurrentHashMap<String, NexusSession>()

    fun createSession(peerId: String, peerName: String = "Unknown"): String {
        cleanupExpiredSessions()
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val sessionId = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val now = System.currentTimeMillis()
        val session = NexusSession(
            sessionId = sessionId,
            peerId = peerId,
            peerName = peerName,
            issuedAt = now,
            lastAccessedAt = now,
            expiresAt = now + SESSION_TTL_MS
        )
        activeSessions[sessionId] = session
        Log.i(TAG, "Created secure session for peer: $peerId ($peerName)")
        return sessionId
    }

    fun getSession(sessionId: String): NexusSession? {
        cleanupExpiredSessions()
        val session = activeSessions[sessionId] ?: return nilOrNull()
        if (System.currentTimeMillis() > session.expiresAt) {
            activeSessions.remove(sessionId)
            return null
        }
        return session
    }

    private fun nilOrNull(): NexusSession? = null

    fun isSessionValid(sessionId: String, peerId: String): Boolean {
        cleanupExpiredSessions()
        val session = activeSessions[sessionId] ?: return false
        if (session.peerId != peerId) return false
        if (System.currentTimeMillis() > session.expiresAt) {
            activeSessions.remove(sessionId)
            return false
        }
        session.lastAccessedAt = System.currentTimeMillis()
        return true
    }

    fun revokeSession(sessionId: String) {
        activeSessions.remove(sessionId)
        Log.i(TAG, "Revoked session: $sessionId")
    }

    fun revokeSessionsForPeer(peerId: String) {
        activeSessions.entries.removeIf { it.value.peerId == peerId }
        Log.i(TAG, "Revoked all sessions for peer: $peerId")
    }

    private fun cleanupExpiredSessions() {
        val now = System.currentTimeMillis()
        activeSessions.entries.removeIf { now > it.value.expiresAt }
    }
}

package com.jarvis.assistant.nexus.security

import android.content.Context
import android.util.Log
import com.jarvis.assistant.nexus.data.NexusDatabase
import com.jarvis.assistant.nexus.data.NexusPairedDeviceEntity
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class SecurePairingChallenge(
    val challengeId: String,
    val oneTimeSecret: String,
    val hostFingerprint: String,
    val expiresAt: Long
)

object PairingManager {
    private const val TAG = "PairingManager"
    private const val CHALLENGE_TTL_MS = 2 * 60 * 1000L // 2 minutes
    private val secureRandom = SecureRandom()

    private val pendingChallenges = ConcurrentHashMap<String, SecurePairingChallenge>()
    private val failureTracker = ConcurrentHashMap<String, Pair<Int, Long>>()
    private const val MAX_FAILURES = 5
    private const val LOCKOUT_DURATION_MS = 60 * 1000L // 60 seconds

    fun generateChallenge(hostFingerprint: String): SecurePairingChallenge {
        cleanupExpired()
        val cBytes = ByteArray(24)
        secureRandom.nextBytes(cBytes)
        val challengeId = Base64.getUrlEncoder().withoutPadding().encodeToString(cBytes)

        val sBytes = ByteArray(32)
        secureRandom.nextBytes(sBytes)
        val oneTimeSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(sBytes)

        val now = System.currentTimeMillis()
        val challenge = SecurePairingChallenge(
            challengeId = challengeId,
            oneTimeSecret = oneTimeSecret,
            hostFingerprint = hostFingerprint,
            expiresAt = now + CHALLENGE_TTL_MS
        )
        pendingChallenges[challengeId] = challenge
        Log.i(TAG, "Generated secure pairing challenge: $challengeId")
        return challenge
    }

    fun verifyAndConsumeChallenge(challengeId: String, clientIp: String): SecurePairingChallenge? {
        cleanupExpired()
        val lockout = failureTracker[clientIp]
        val now = System.currentTimeMillis()
        if (lockout != null && now < lockout.second) {
            Log.w(TAG, "Pairing rate-limit lockout active for IP: $clientIp")
            return null
        }

        val challenge = pendingChallenges.remove(challengeId)
        if (challenge == null || now > challenge.expiresAt) {
            recordFailure(clientIp)
            return null
        }
        failureTracker.remove(clientIp)
        return challenge
    }

    /**
     * Completes pairing after explicit Host-side approval callback.
     * Only when approved, creates session token and persists device identity in database.
     */
    suspend fun approveAndStorePeer(
        context: Context,
        deviceId: String,
        displayName: String,
        modelName: String,
        ipAddress: String,
        port: Int,
        mode: String,
        publicKey: String,
        initialCapabilities: List<String>
    ): String {
        val dao = NexusDatabase.getInstance(context).nexusDao()
        val now = System.currentTimeMillis()
        val capsJson = initialCapabilities.joinToString(",")

        val entity = NexusPairedDeviceEntity(
            id = deviceId,
            displayName = displayName,
            modelName = modelName,
            ipAddress = ipAddress,
            port = port,
            mode = mode,
            publicKey = publicKey,
            pairedAt = now,
            lastSeenAt = now,
            grantedCapabilitiesJson = capsJson
        )
        dao.insertOrUpdateDevice(entity)
        Log.i(TAG, "Host approved peer device: $displayName ($deviceId). Stored in database.")

        // Create and return secure session token
        return NexusSessionManager.createSession(deviceId)
    }

    private fun recordFailure(clientIp: String) {
        val now = System.currentTimeMillis()
        val current = failureTracker[clientIp] ?: Pair(0, 0L)
        val newCount = current.first + 1
        if (newCount >= MAX_FAILURES) {
            failureTracker[clientIp] = Pair(newCount, now + LOCKOUT_DURATION_MS)
        } else {
            failureTracker[clientIp] = Pair(newCount, now)
        }
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        pendingChallenges.entries.removeIf { now > it.value.expiresAt }
    }
}

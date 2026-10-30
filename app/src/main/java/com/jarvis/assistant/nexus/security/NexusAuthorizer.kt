package com.jarvis.assistant.nexus.security

import android.content.Context
import com.jarvis.assistant.nexus.data.NexusDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Capability(val value: String) {
    DEVICE_STATUS("device.status.read"),
    CHAT_PROMPT("jarvis.chat.send_prompt"),
    FILE_BROWSE("fs.browse"),
    FILE_RECEIVE("file.receive.accept"),
    GALLERY_BROWSE("gallery.browse"),
    MEDIA_CONTROL("media.control"),
    SCREEN_SHARE("screen.share.request"),
    CAMERA_PREVIEW("camera.preview.request"),
    DIALER_CALL("dialer.call"),
    ACCESSIBILITY_NAV("accessibility.nav"),
    CLIPBOARD_SHARE("clipboard.share"),
    ALL("all")
}

sealed class AuthorizationResult {
    object Allow : AuthorizationResult()
    data class Deny(val reason: String) : AuthorizationResult()
    object NeedLocalApproval : AuthorizationResult()
}

object NexusAuthorizer {

    private val activeNonces = mutableSetOf<String>()
    private val sessionSequenceMap = mutableMapOf<String, Long>()

    suspend fun authorize(
        context: Context,
        session: NexusSession,
        action: String,
        capability: Capability,
        nonce: String = "",
        sequence: Long = 0L
    ): AuthorizationResult = withContext(Dispatchers.IO) {
        // 1. Check Session Validity & TTL
        if (!session.isValid || !NexusSessionManager.isSessionValid(session.sessionId, session.peerId)) {
            return@withContext AuthorizationResult.Deny("Session token is invalid, expired, or revoked.")
        }

        // 2. Check Nonce & Replay protection if provided
        if (nonce.isNotBlank()) {
            if (activeNonces.contains(nonce)) {
                return@withContext AuthorizationResult.Deny("Replay attack detected: nonce already used.")
            }
            activeNonces.add(nonce)
            if (activeNonces.size > 5000) {
                val iter = activeNonces.iterator()
                repeat(1000) { if (iter.hasNext()) { iter.next(); iter.remove() } }
            }
        }

        // 3. Check Sequence number if provided
        if (sequence > 0L) {
            val lastSeq = sessionSequenceMap[session.sessionId] ?: 0L
            if (sequence <= lastSeq) {
                return@withContext AuthorizationResult.Deny("Sequence error: out-of-order or replayed packet.")
            }
            sessionSequenceMap[session.sessionId] = sequence
        }

        // 4. Verify Peer Identity in Database & Revocation status
        val dao = NexusDatabase.getInstance(context).nexusDao()
        val peer = dao.getDeviceById(session.peerId)
        if (peer == null) {
            return@withContext AuthorizationResult.Deny("Peer device identity not found or unpaired.")
        }

        // 5. Verify Granted Capabilities
        val grantedCaps = peer.grantedCapabilitiesJson
        val requiredCapStr = capability.value
        if (!grantedCaps.contains(requiredCapStr) && !grantedCaps.contains("all")) {
            return@withContext AuthorizationResult.Deny("Capability '${capability.name}' ($requiredCapStr) not granted to peer.")
        }

        // 6. Check if action requires explicit Host-side approval
        val sensitiveActions = setOf(
            "camera.preview.request",
            "screen.share.request",
            "file.send.request",
            "gallery.browse",
            "dialer.call"
        )
        if (sensitiveActions.contains(action)) {
            return@withContext AuthorizationResult.NeedLocalApproval
        }

        return@withContext AuthorizationResult.Allow
    }
}

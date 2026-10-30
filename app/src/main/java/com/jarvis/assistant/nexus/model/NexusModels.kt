package com.jarvis.assistant.nexus.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class NexusMode {
    LAN,
    INTERNET
}

enum class NexusConnectionState {
    DISCONNECTED,
    CONNECTING_IP,
    VERIFYING_CODE,
    ESTABLISHING_TLS,
    CONNECTED,
    ERROR
}

enum class TransferDirection {
    UPLOAD,
    DOWNLOAD
}

enum class TransferStatus {
    PENDING,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class NexusDevice(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val ipAddress: String,
    val port: Int = 8765,
    val mode: NexusMode = NexusMode.LAN,
    val publicKey: String = "",
    val pairedAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true,
    val batteryLevel: Int = 0,
    val isWifiConnected: Boolean = true,
    val androidVersion: String = "Unknown",
    val modelName: String = "Android Device",
    var sessionId: String = ""
) {
    val formattedLastSeen: String
        get() {
            val diffMinutes = ((System.currentTimeMillis() - lastSeenAt) / (1000 * 60)).coerceAtLeast(0)
            return when {
                diffMinutes < 1 -> "Online now"
                diffMinutes < 60 -> "${diffMinutes}m ago"
                else -> "${diffMinutes / 60}h ago"
            }
        }
}

data class NexusDiscoveredDevice(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val deviceModel: String,
    val jarvisVersion: String = "1.8.7",
    val discoveryTime: Long = System.currentTimeMillis()
)

data class PairingRequest(
    val requestId: String = UUID.randomUUID().toString().take(8),
    val controllerName: String,
    val controllerIp: String,
    val pairingCode: String,
    val publicKey: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DeviceCapability(
    val key: String,
    val title: String,
    val description: String,
    var isEnabled: Boolean = true,
    val requiresApproval: Boolean = false
)

data class NexusCommand(
    val version: String = "1.0",
    val messageId: String = UUID.randomUUID().toString().take(8).uppercase(),
    val senderDeviceId: String = "",
    val targetDeviceId: String = "",
    val sessionId: String = "",
    val capability: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val expiration: Long = System.currentTimeMillis() + 60000,
    val nonce: String = UUID.randomUUID().toString(),
    val payload: String = "",
    val signature: String = ""
)

data class NexusSession(
    val sessionId: String = UUID.randomUUID().toString().take(12),
    val peerDevice: NexusDevice,
    val mode: NexusMode,
    val startedAt: Long = System.currentTimeMillis(),
    var isConnected: Boolean = true,
    val grantedCapabilities: MutableSet<String> = mutableSetOf(
        "device.status.read",
        "jarvis.chat.send_prompt",
        "fs.browse",
        "fs.download",
        "gallery.browse",
        "media.control"
    )
)

data class AuditEvent(
    val id: String = UUID.randomUUID().toString().take(8),
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val targetDevice: String,
    val result: String,
    val details: String = ""
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

data class RemoteChatMessage(
    val id: String = UUID.randomUUID().toString().take(8),
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Delivered"
) {
    val formattedTime: String
        get() = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
}

data class RemoteFileItem(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long = System.currentTimeMillis()
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "Folder"
            val kb = size / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
                else -> "$size B"
            }
        }
}

data class TransferRecord(
    val transferId: String = UUID.randomUUID().toString().take(8),
    val fileName: String,
    val totalBytes: Long,
    var bytesTransferred: Long = 0,
    val direction: TransferDirection,
    var status: TransferStatus = TransferStatus.PENDING,
    var speedBytesPerSec: Long = 0,
    val sourceDevice: String,
    val destinationDevice: String,
    val localFilePath: String,
    val sha256Checksum: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    var completedAt: Long = 0
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((bytesTransferred * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    val formattedProgress: String
        get() {
            val transMb = bytesTransferred / (1024.0 * 1024.0)
            val totalMb = totalBytes / (1024.0 * 1024.0)
            return String.format(Locale.US, "%.1f MB / %.1f MB (%d%%)", transMb, totalMb, progressPercent)
        }
}

data class RemoteMediaItem(
    val id: Long,
    val title: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAdded: Long,
    val uriString: String,
    val isVideo: Boolean = false,
    val durationMs: Long = 0
)

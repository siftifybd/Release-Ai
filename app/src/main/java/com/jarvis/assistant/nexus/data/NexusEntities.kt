package com.jarvis.assistant.nexus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nexus_paired_devices")
data class NexusPairedDeviceEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val modelName: String,
    val ipAddress: String,
    val port: Int,
    val mode: String,
    val publicKey: String,
    val pairedAt: Long,
    val lastSeenAt: Long,
    val grantedCapabilitiesJson: String
)

@Entity(tableName = "nexus_transfers")
data class NexusTransferEntity(
    @PrimaryKey val transferId: String,
    val fileName: String,
    val totalBytes: Long,
    val bytesTransferred: Long,
    val direction: String,
    val status: String,
    val sourceDevice: String,
    val destinationDevice: String,
    val localFilePath: String,
    val sha256Checksum: String,
    val timestamp: Long
)

@Entity(tableName = "nexus_audit_logs")
data class NexusAuditEntity(
    @PrimaryKey val id: String,
    val action: String,
    val targetDevice: String,
    val result: String,
    val details: String,
    val timestamp: Long
)

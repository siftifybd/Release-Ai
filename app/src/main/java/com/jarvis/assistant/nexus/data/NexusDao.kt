package com.jarvis.assistant.nexus.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NexusDao {

    @Query("SELECT * FROM nexus_paired_devices ORDER BY lastSeenAt DESC")
    fun getAllPairedDevices(): Flow<List<NexusPairedDeviceEntity>>

    @Query("SELECT * FROM nexus_paired_devices WHERE id = :id")
    suspend fun getDeviceById(id: String): NexusPairedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevice(device: NexusPairedDeviceEntity)

    @Delete
    suspend fun deleteDevice(device: NexusPairedDeviceEntity)

    @Query("DELETE FROM nexus_paired_devices WHERE id = :deviceId")
    suspend fun deleteDeviceById(deviceId: String)

    @Query("SELECT * FROM nexus_transfers ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<NexusTransferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTransfer(transfer: NexusTransferEntity)

    @Query("SELECT * FROM nexus_audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentAuditLogs(): Flow<List<NexusAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: NexusAuditEntity)
}

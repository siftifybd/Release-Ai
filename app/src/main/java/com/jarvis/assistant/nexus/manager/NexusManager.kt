package com.jarvis.assistant.nexus.manager

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.jarvis.assistant.nexus.data.NexusAuditEntity
import com.jarvis.assistant.nexus.data.NexusDatabase
import com.jarvis.assistant.nexus.data.NexusPairedDeviceEntity
import com.jarvis.assistant.nexus.data.NexusTransferEntity
import com.jarvis.assistant.nexus.discovery.NexusDiscoveryManager
import com.jarvis.assistant.nexus.model.AuditEvent
import com.jarvis.assistant.nexus.model.DeviceCapability
import com.jarvis.assistant.nexus.model.NexusConnectionState
import com.jarvis.assistant.nexus.model.NexusDevice
import com.jarvis.assistant.nexus.model.NexusDiscoveredDevice
import com.jarvis.assistant.nexus.model.NexusMode
import com.jarvis.assistant.nexus.model.NexusSession
import com.jarvis.assistant.nexus.model.RemoteChatMessage
import com.jarvis.assistant.nexus.model.RemoteFileItem
import com.jarvis.assistant.nexus.model.RemoteMediaItem
import com.jarvis.assistant.nexus.model.TransferDirection
import com.jarvis.assistant.nexus.model.TransferRecord
import com.jarvis.assistant.nexus.model.TransferStatus
import com.jarvis.assistant.nexus.security.NexusSecurityManager
import com.jarvis.assistant.nexus.service.NexusHostService
import com.jarvis.assistant.nexus.transport.NexusClient
import com.jarvis.assistant.nexus.transport.NexusLocalServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

object NexusManager {

    private lateinit var appContext: Context
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Default)

    lateinit var database: NexusDatabase
        private set

    lateinit var localServer: NexusLocalServer
        private set

    lateinit var discoveryManager: NexusDiscoveryManager
        private set

    val client = NexusClient()

    private val _connectionState = MutableStateFlow(NexusConnectionState.DISCONNECTED)
    val connectionState: StateFlow<NexusConnectionState> = _connectionState.asStateFlow()

    private val _activeSession = MutableStateFlow<NexusSession?>(null)
    val activeSession: StateFlow<NexusSession?> = _activeSession.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<NexusDevice>>(emptyList())
    val pairedDevices: StateFlow<List<NexusDevice>> = _pairedDevices.asStateFlow()

    private val _connectionHistory = MutableStateFlow<List<NexusDevice>>(emptyList())
    val connectionHistory: StateFlow<List<NexusDevice>> = _connectionHistory.asStateFlow()

    private val _capabilities = MutableStateFlow<List<DeviceCapability>>(emptyList())
    val capabilities: StateFlow<List<DeviceCapability>> = _capabilities.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<RemoteChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<RemoteChatMessage>> = _chatMessages.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditEvent>>(emptyList())
    val auditLogs: StateFlow<List<AuditEvent>> = _auditLogs.asStateFlow()

    private val _activeTransfers = MutableStateFlow<List<TransferRecord>>(emptyList())
    val activeTransfers: StateFlow<List<TransferRecord>> = _activeTransfers.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        database = NexusDatabase.getInstance(appContext)
        localServer = NexusLocalServer(appContext)
        discoveryManager = NexusDiscoveryManager(appContext)

        initCapabilities()
        observeDatabase()
    }

    private fun initCapabilities() {
        _capabilities.value = listOf(
            DeviceCapability("device.status.read", "Device Status", "Read real battery, Wi-Fi and device health", true),
            DeviceCapability("jarvis.chat.send_prompt", "Remote Chat", "Send prompts and receive AI responses", true),
            DeviceCapability("fs.browse", "File Browsing", "Browse permitted folders on Host", true),
            DeviceCapability("fs.download", "File Download", "Download files with SHA-256 chunk verification", true),
            DeviceCapability("gallery.browse", "Gallery Access", "View authorized media items on Host", true, true),
            DeviceCapability("screen.share.request", "Screen Share", "Live view of device screen with system consent", true, true),
            DeviceCapability("dialer.call", "Phone Call / Dialer", "Open dialer on Host with explicit owner confirmation", true, true),
            DeviceCapability("accessibility.action", "Remote Interaction", "Global navigation gestures via Accessibility Service", true, true),
            DeviceCapability("media.control", "Media Control", "Play, pause and skip tracks remotely", true)
        )
    }

    private fun observeDatabase() {
        scope.launch {
            database.nexusDao().getAllPairedDevices().collect { entities ->
                _pairedDevices.value = entities.map { e ->
                    NexusDevice(
                        id = e.id,
                        displayName = e.displayName,
                        ipAddress = e.ipAddress,
                        port = e.port,
                        mode = if (e.mode == "INTERNET") NexusMode.INTERNET else NexusMode.LAN,
                        publicKey = e.publicKey,
                        pairedAt = e.pairedAt,
                        lastSeenAt = e.lastSeenAt,
                        modelName = e.modelName
                    )
                }
            }
        }

        scope.launch {
            database.nexusDao().getRecentAuditLogs().collect { entities ->
                _auditLogs.value = entities.map { e ->
                    AuditEvent(
                        id = e.id,
                        timestamp = e.timestamp,
                        action = e.action,
                        targetDevice = e.targetDevice,
                        result = e.result,
                        details = e.details
                    )
                }
            }
        }
    }

    fun startHostService(context: Context) {
        NexusHostService.start(context)
    }

    fun stopHostService(context: Context) {
        NexusHostService.stop(context)
    }

    fun startLocalServer(port: Int = NexusLocalServer.DEFAULT_PORT) {
        localServer.start(scope, port)
    }

    fun stopLocalServer() {
        localServer.stop()
    }

    fun startDiscovery() {
        discoveryManager.startDiscovery()
    }

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
    }

    fun connectDevice(
        ipAddress: String,
        port: Int,
        pairingCode: String,
        mode: NexusMode,
        onProgress: (NexusConnectionState, String) -> Unit,
        onComplete: (Boolean, NexusDevice?, String?) -> Unit
    ) {
        scope.launch {
            _connectionState.value = NexusConnectionState.CONNECTING_IP
            val result = client.connectAndAuthenticate(ipAddress, port, pairingCode, mode) { state, msg ->
                _connectionState.value = state
                onProgress(state, msg)
            }

            if (result.isSuccess) {
                val device = result.getOrNull()!!
                val session = NexusSession(
                    peerDevice = device,
                    mode = mode
                )
                _activeSession.value = session
                _connectionState.value = NexusConnectionState.CONNECTED

                // Save to Room persistent paired devices
                val entity = NexusPairedDeviceEntity(
                    id = device.id,
                    displayName = device.displayName,
                    modelName = device.modelName,
                    ipAddress = device.ipAddress,
                    port = device.port,
                    mode = mode.name,
                    publicKey = device.publicKey,
                    pairedAt = System.currentTimeMillis(),
                    lastSeenAt = System.currentTimeMillis(),
                    grantedCapabilitiesJson = gson.toJson(session.grantedCapabilities)
                )
                database.nexusDao().insertOrUpdateDevice(entity)

                // Add to history & audit
                addToHistory(device)
                recordAudit("Connect", device.displayName, "SUCCESS", "Connected to ${device.ipAddress}:${device.port} via ${mode.name}")

                _chatMessages.value = listOf(
                    RemoteChatMessage(
                        text = "Device Info:\nModel: ${device.displayName}\nAndroid: ${device.androidVersion}\nBattery: ${if (device.batteryLevel >= 0) "${device.batteryLevel}%" else "Unavailable"}\nWi-Fi: ${if (device.isWifiConnected) "Connected" else "Disconnected"}",
                        isFromMe = false
                    )
                )

                launch(Dispatchers.Main) {
                    onComplete(true, device, null)
                }
            } else {
                _connectionState.value = NexusConnectionState.ERROR
                val err = result.exceptionOrNull()?.message ?: "Connection error"
                recordAudit("Connect", ipAddress, "FAILED", err)
                launch(Dispatchers.Main) {
                    onComplete(false, null, err)
                }
            }
        }
    }

    fun disconnectActiveSession() {
        val session = _activeSession.value ?: return
        recordAudit("Disconnect", session.peerDevice.displayName, "SUCCESS", "Session ended cleanly")
        _activeSession.value = null
        _connectionState.value = NexusConnectionState.DISCONNECTED
    }

    fun disconnectAll() {
        disconnectActiveSession()
        stopLocalServer()
        recordAudit("Disconnect All", "All Devices", "SUCCESS", "All remote sessions terminated")
    }

    fun sendRemotePrompt(prompt: String, onReply: (String) -> Unit) {
        val device = _activeSession.value?.peerDevice ?: return
        val userMsg = RemoteChatMessage(text = prompt, isFromMe = true)
        _chatMessages.value = _chatMessages.value + userMsg

        scope.launch {
            val res = client.executeCommand(device, "jarvis.chat.send_prompt", mapOf("prompt" to prompt))
            val replyText = if (res.isSuccess) {
                res.getOrNull()!!
            } else {
                "Error: ${res.exceptionOrNull()?.message}"
            }
            val replyMsg = RemoteChatMessage(text = replyText, isFromMe = false)
            _chatMessages.value = _chatMessages.value + replyMsg
            recordAudit("Remote Chat", device.displayName, if (res.isSuccess) "SUCCESS" else "FAILED", prompt.take(30))
            launch(Dispatchers.Main) {
                onReply(replyText)
            }
        }
    }

    fun executeRemoteAction(action: String, params: Map<String, String> = emptyMap(), onResult: (Boolean, String) -> Unit) {
        val device = _activeSession.value?.peerDevice ?: run {
            onResult(false, "No active remote session")
            return
        }
        scope.launch {
            val res = client.executeCommand(device, action, params)
            val msg = if (res.isSuccess) res.getOrNull()!! else res.exceptionOrNull()?.message ?: "Action failed"
            recordAudit(action, device.displayName, if (res.isSuccess) "SUCCESS" else "FAILED", msg)
            launch(Dispatchers.Main) {
                onResult(res.isSuccess, msg)
            }
        }
    }

    fun downloadFile(remoteFile: RemoteFileItem, destinationDir: File, onProgress: (TransferRecord) -> Unit, onComplete: (Boolean, String) -> Unit) {
        val device = _activeSession.value?.peerDevice ?: run {
            onComplete(false, "No active session")
            return
        }

        val destFile = File(destinationDir, remoteFile.name)
        val transfer = TransferRecord(
            fileName = remoteFile.name,
            totalBytes = remoteFile.size,
            direction = TransferDirection.DOWNLOAD,
            sourceDevice = device.displayName,
            destinationDevice = Build.MODEL ?: "Local Device",
            localFilePath = destFile.absolutePath,
            status = TransferStatus.IN_PROGRESS
        )

        _activeTransfers.value = listOf(transfer) + _activeTransfers.value

        scope.launch {
            val result = client.downloadRemoteFile(device, remoteFile.path, destFile) { bytes, total ->
                transfer.bytesTransferred = bytes
                onProgress(transfer)
            }

            if (result.isSuccess) {
                transfer.status = TransferStatus.COMPLETED
                transfer.completedAt = System.currentTimeMillis()
                saveTransferRecord(transfer)
                recordAudit("File Download", device.displayName, "SUCCESS", "${remoteFile.name} verified with SHA-256")
                launch(Dispatchers.Main) {
                    onComplete(true, "File downloaded and verified: ${destFile.name}")
                }
            } else {
                transfer.status = TransferStatus.FAILED
                val err = result.exceptionOrNull()?.message ?: "Download failed"
                recordAudit("File Download", device.displayName, "FAILED", err)
                launch(Dispatchers.Main) {
                    onComplete(false, err)
                }
            }
        }
    }

    private fun saveTransferRecord(record: TransferRecord) {
        scope.launch {
            val entity = NexusTransferEntity(
                transferId = record.transferId,
                fileName = record.fileName,
                totalBytes = record.totalBytes,
                bytesTransferred = record.bytesTransferred,
                direction = record.direction.name,
                status = record.status.name,
                sourceDevice = record.sourceDevice,
                destinationDevice = record.destinationDevice,
                localFilePath = record.localFilePath,
                sha256Checksum = record.sha256Checksum,
                timestamp = record.startedAt
            )
            database.nexusDao().insertOrUpdateTransfer(entity)
        }
    }

    private fun addToHistory(device: NexusDevice) {
        val updated = listOf(device) + _connectionHistory.value.filter { it.ipAddress != device.ipAddress }.take(19)
        _connectionHistory.value = updated
    }

    fun recordAudit(action: String, targetDevice: String, result: String, details: String) {
        val event = AuditEvent(
            action = action,
            targetDevice = targetDevice,
            result = result,
            details = details
        )
        _auditLogs.value = listOf(event) + _auditLogs.value.take(49)
        scope.launch {
            val entity = NexusAuditEntity(
                id = event.id,
                action = action,
                targetDevice = targetDevice,
                result = result,
                details = details,
                timestamp = event.timestamp
            )
            database.nexusDao().insertAuditLog(entity)
        }
    }
}

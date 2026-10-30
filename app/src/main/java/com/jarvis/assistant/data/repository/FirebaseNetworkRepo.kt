package com.jarvis.assistant.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Data model representing a snapshot of the device's network connectivity status.
 */
data class DeviceNetworkStatus(
    val deviceId: String = "",
    val deviceModel: String = "",
    val isConnected: Boolean = false,
    val connectionType: String = "NONE", // WIFI, CELLULAR, ETHERNET, VPN, OTHER, NONE
    val isMetered: Boolean = false,
    val isInternetValidated: Boolean = false,
    val ipAddress: String? = null,
    val wifiSsid: String? = null,
    val linkDownstreamBandwidthKbps: Int = 0,
    val linkUpstreamBandwidthKbps: Int = 0,
    val networkState: String = "OFFLINE", // ONLINE, CONNECTING, OFFLINE
    val timestamp: Long = System.currentTimeMillis(),
    val lastSeenIso: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "deviceId" to deviceId,
        "deviceModel" to deviceModel,
        "isConnected" to isConnected,
        "connectionType" to connectionType,
        "isMetered" to isMetered,
        "isInternetValidated" to isInternetValidated,
        "ipAddress" to ipAddress,
        "wifiSsid" to wifiSsid,
        "linkDownstreamBandwidthKbps" to linkDownstreamBandwidthKbps,
        "linkUpstreamBandwidthKbps" to linkUpstreamBandwidthKbps,
        "networkState" to networkState,
        "timestamp" to timestamp,
        "lastSeenIso" to lastSeenIso
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): DeviceNetworkStatus {
            return DeviceNetworkStatus(
                deviceId = map["deviceId"] as? String ?: "",
                deviceModel = map["deviceModel"] as? String ?: "",
                isConnected = map["isConnected"] as? Boolean ?: false,
                connectionType = map["connectionType"] as? String ?: "NONE",
                isMetered = map["isMetered"] as? Boolean ?: false,
                isInternetValidated = map["isInternetValidated"] as? Boolean ?: false,
                ipAddress = map["ipAddress"] as? String,
                wifiSsid = map["wifiSsid"] as? String,
                linkDownstreamBandwidthKbps = (map["linkDownstreamBandwidthKbps"] as? Number)?.toInt() ?: 0,
                linkUpstreamBandwidthKbps = (map["linkUpstreamBandwidthKbps"] as? Number)?.toInt() ?: 0,
                networkState = map["networkState"] as? String ?: "OFFLINE",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                lastSeenIso = map["lastSeenIso"] as? String ?: ""
            )
        }
    }
}

/**
 * Data model for a remote network command received or sent via Firestore 'network_commands' collection.
 */
data class NetworkRemoteCommand(
    val commandId: String = "",
    val targetDeviceId: String = "",
    val sourceDeviceId: String = "",
    val action: String = "", // e.g. "TOGGLE_WIFI", "ENABLE_WIFI", "DISABLE_WIFI", "SYNC_STATUS", "PING"
    val parameters: Map<String, Any?> = emptyMap(),
    val executed: Boolean = false,
    val resultMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "commandId" to commandId,
        "targetDeviceId" to targetDeviceId,
        "sourceDeviceId" to sourceDeviceId,
        "action" to action,
        "parameters" to parameters,
        "executed" to executed,
        "resultMessage" to resultMessage,
        "timestamp" to timestamp
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): NetworkRemoteCommand {
            val rawParams = map["parameters"] as? Map<*, *>
            val cleanParams = rawParams?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
            return NetworkRemoteCommand(
                commandId = id,
                targetDeviceId = map["targetDeviceId"] as? String ?: "",
                sourceDeviceId = map["sourceDeviceId"] as? String ?: "",
                action = map["action"] as? String ?: "",
                parameters = cleanParams,
                executed = map["executed"] as? Boolean ?: false,
                resultMessage = map["resultMessage"] as? String,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/**
 * Repository responsible for synchronizing local device network status with Cloud Firestore,
 * and enabling real-time remote monitoring of internet connectivity across devices.
 */
class FirebaseNetworkRepo(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "FirebaseNetworkRepo"
        const val COLLECTION_NETWORK_STATUS = "device_network_status"
        const val COLLECTION_NETWORK_COMMANDS = "network_commands"
        private const val PREFS_NAME = "nexus_network_repo_prefs"
        private const val KEY_PERSISTENT_DEVICE_ID = "persistent_device_id"

        @Volatile
        private var INSTANCE: FirebaseNetworkRepo? = null

        fun getInstance(context: Context): FirebaseNetworkRepo {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseNetworkRepo(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    val deviceId: String by lazy { resolveDeviceId() }
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private val _localNetworkStatus = MutableStateFlow(getCurrentNetworkStatus())
    val localNetworkStatus: StateFlow<DeviceNetworkStatus> = _localNetworkStatus.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var syncJob: Job? = null
    private var isMonitoring = false

    private fun resolveDeviceId(): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                androidId
            } else {
                getOrCreateFallbackDeviceId()
            }
        } catch (_: Throwable) {
            getOrCreateFallbackDeviceId()
        }
    }

    private fun getOrCreateFallbackDeviceId(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PERSISTENT_DEVICE_ID, null)
        if (!saved.isNullOrBlank()) return saved

        val generated = "jarvis_${UUID.randomUUID().toString().take(12)}"
        prefs.edit().putString(KEY_PERSISTENT_DEVICE_ID, generated).apply()
        return generated
    }

    /**
     * Inspects current hardware & network capabilities to produce a fresh snapshot.
     */
    @SuppressLint("MissingPermission")
    fun getCurrentNetworkStatus(): DeviceNetworkStatus {
        val cm = connectivityManager
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val now = System.currentTimeMillis()
        val isoDate = isoFormat.format(Date(now))

        if (cm == null) {
            return DeviceNetworkStatus(
                deviceId = deviceId,
                deviceModel = deviceModel,
                isConnected = false,
                connectionType = "NONE",
                networkState = "OFFLINE",
                timestamp = now,
                lastSeenIso = isoDate
            )
        }

        val activeNetwork: Network? = cm.activeNetwork
        val capabilities: NetworkCapabilities? = activeNetwork?.let { cm.getNetworkCapabilities(it) }

        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isMetered = cm.isActiveNetworkMetered

        val connType = when {
            capabilities == null -> "NONE"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
            else -> "OTHER"
        }

        val downstream = capabilities?.linkDownstreamBandwidthKbps ?: 0
        val upstream = capabilities?.linkUpstreamBandwidthKbps ?: 0

        val state = when {
            hasInternet && isValidated -> "ONLINE"
            hasInternet -> "CONNECTING"
            else -> "OFFLINE"
        }

        val ip = getActiveIpAddress()
        val wifiSsid = if (connType == "WIFI") getWifiSsid() else null

        return DeviceNetworkStatus(
            deviceId = deviceId,
            deviceModel = deviceModel,
            isConnected = hasInternet && isValidated,
            connectionType = connType,
            isMetered = isMetered,
            isInternetValidated = isValidated,
            ipAddress = ip,
            wifiSsid = wifiSsid,
            linkDownstreamBandwidthKbps = downstream,
            linkUpstreamBandwidthKbps = upstream,
            networkState = state,
            timestamp = now,
            lastSeenIso = isoDate
        )
    }

    /**
     * Synchronizes current local network status to the device's Firestore document.
     */
    suspend fun syncCurrentStatus(): Result<DeviceNetworkStatus> {
        val status = getCurrentNetworkStatus()
        _localNetworkStatus.value = status

        return try {
            val docRef = firestore.collection(COLLECTION_NETWORK_STATUS).document(deviceId)
            docRef.set(status.toMap(), SetOptions.merge()).awaitResult()
            Log.d(TAG, "Successfully synced network status for device $deviceId: ${status.networkState} (${status.connectionType})")
            Result.success(status)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to sync network status to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Starts automatic real-time synchronization. Listens to Android connectivity changes
     * and writes updates to Firestore immediately.
     */
    @Synchronized
    fun startAutoSync(scope: CoroutineScope) {
        if (isMonitoring) return
        val cm = connectivityManager ?: return

        isMonitoring = true

        // Push initial status
        syncJob = scope.launch(Dispatchers.IO) {
            syncCurrentStatus()
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch(Dispatchers.IO) {
                    Log.d(TAG, "Network became available")
                    syncCurrentStatus()
                }
            }

            override fun onLost(network: Network) {
                scope.launch(Dispatchers.IO) {
                    Log.d(TAG, "Network lost")
                    val offlineStatus = getCurrentNetworkStatus().copy(
                        isConnected = false,
                        networkState = "OFFLINE"
                    )
                    _localNetworkStatus.value = offlineStatus
                    try {
                        firestore.collection(COLLECTION_NETWORK_STATUS)
                            .document(deviceId)
                            .set(offlineStatus.toMap(), SetOptions.merge())
                            .awaitResult()
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to write offline state: ${e.message}")
                    }
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                scope.launch(Dispatchers.IO) {
                    syncCurrentStatus()
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(networkCallback!!)
            } else {
                cm.registerNetworkCallback(request, networkCallback!!)
            }
            Log.i(TAG, "Registered NetworkCallback for Firestore auto-sync.")
        } catch (e: Throwable) {
            Log.e(TAG, "Error registering NetworkCallback: ${e.message}")
        }
    }

    /**
     * Stops the connectivity listener and cleans up resources.
     */
    @Synchronized
    fun stopAutoSync() {
        if (!isMonitoring) return
        isMonitoring = false

        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
                Log.i(TAG, "Unregistered NetworkCallback.")
            } catch (e: Throwable) {
                Log.w(TAG, "Error unregistering NetworkCallback: ${e.message}")
            }
        }
        networkCallback = null
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * Marks the device as explicitly OFFLINE in Firestore (e.g. during shutdown or logout).
     */
    suspend fun markOffline(): Result<Unit> {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val now = System.currentTimeMillis()
        val offline = getCurrentNetworkStatus().copy(
            isConnected = false,
            networkState = "OFFLINE",
            timestamp = now,
            lastSeenIso = isoFormat.format(Date(now))
        )
        _localNetworkStatus.value = offline
        return try {
            firestore.collection(COLLECTION_NETWORK_STATUS)
                .document(deviceId)
                .set(offline.toMap(), SetOptions.merge())
                .awaitResult()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Real-time remote monitoring: Observes a specific remote device's network status
     * via Firestore snapshot listener.
     */
    fun observeRemoteDevice(targetDeviceId: String): Flow<DeviceNetworkStatus?> = callbackFlow {
        val listener = firestore.collection(COLLECTION_NETWORK_STATUS)
            .document(targetDeviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error monitoring remote device $targetDeviceId: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data
                    if (data != null) {
                        trySend(DeviceNetworkStatus.fromMap(data))
                    } else {
                        trySend(null)
                    }
                } else {
                    trySend(null)
                }
            }

        awaitClose {
            listener.remove()
        }
    }

    /**
     * Real-time remote monitoring: Observes all registered devices in the network telemetry collection.
     */
    fun observeAllRemoteDevices(): Flow<List<DeviceNetworkStatus>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_NETWORK_STATUS)
            .addSnapshotListener { querySnapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error monitoring all devices: ${error.message}")
                    return@addSnapshotListener
                }

                val devices = querySnapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { DeviceNetworkStatus.fromMap(it) }
                } ?: emptyList()

                trySend(devices)
            }

        awaitClose {
            listener.remove()
        }
    }

    /**
     * Real-time remote monitoring: Observes incoming network commands targeted at this device
     * via Firestore 'network_commands' collection.
     */
    fun observeIncomingCommands(targetId: String = deviceId): Flow<List<NetworkRemoteCommand>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_NETWORK_COMMANDS)
            .whereEqualTo("targetDeviceId", targetId)
            .whereEqualTo("executed", false)
            .addSnapshotListener { querySnapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening for incoming network commands: ${error.message}")
                    return@addSnapshotListener
                }

                val commands = querySnapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { NetworkRemoteCommand.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(commands)
            }

        awaitClose {
            listener.remove()
        }
    }

    /**
     * Sends a network command to a target remote device via Firestore 'network_commands'.
     */
    suspend fun sendRemoteCommand(
        targetDeviceId: String,
        action: String,
        parameters: Map<String, Any?> = emptyMap()
    ): Result<String> {
        val cmdId = "cmd_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val command = NetworkRemoteCommand(
            commandId = cmdId,
            targetDeviceId = targetDeviceId,
            sourceDeviceId = deviceId,
            action = action,
            parameters = parameters,
            executed = false,
            resultMessage = null,
            timestamp = System.currentTimeMillis()
        )

        return try {
            firestore.collection(COLLECTION_NETWORK_COMMANDS)
                .document(cmdId)
                .set(command.toMap(), SetOptions.merge())
                .awaitResult()
            Log.i(TAG, "Sent remote network command '$action' to device $targetDeviceId (ID: $cmdId)")
            Result.success(cmdId)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to send remote network command: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Marks a network command as executed and stores the execution result message.
     */
    suspend fun markCommandExecuted(commandId: String, resultMessage: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "executed" to true,
                "resultMessage" to resultMessage,
                "executedAt" to System.currentTimeMillis()
            )
            firestore.collection(COLLECTION_NETWORK_COMMANDS)
                .document(commandId)
                .update(updates)
                .awaitResult()
            Log.d(TAG, "Command $commandId marked executed: $resultMessage")
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to mark command $commandId as executed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun getActiveIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun getWifiSsid(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifiManager?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "")
            if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") ssid else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Extension helper to await Google Play Tasks in coroutines cleanly without extra dependencies.
     */
    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
}

package com.jarvis.assistant.network

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.data.repository.DeviceNetworkStatus
import com.jarvis.assistant.data.repository.FirebaseNetworkRepo
import com.jarvis.assistant.data.repository.NetworkRemoteCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manager that interfaces with Android ConnectivityManager and WifiManager to inspect,
 * listen for network changes, and provide methods to toggle WiFi state both locally and remotely,
 * synchronizing all state changes in real-time with Firestore via FirebaseNetworkRepo.
 */
class NetworkControlManager(
    private val context: Context,
    val networkRepo: FirebaseNetworkRepo = FirebaseNetworkRepo.getInstance(context)
) {

    companion object {
        private const val TAG = "NetworkControlManager"

        @Volatile
        private var INSTANCE: NetworkControlManager? = null

        fun getInstance(context: Context): NetworkControlManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkControlManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _isWifiActive = MutableStateFlow(isWifiEnabled())
    val isWifiActive: StateFlow<Boolean> = _isWifiActive.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var monitoringScope: CoroutineScope? = null
    private var isMonitoring = false

    /**
     * Returns the array of runtime and normal permissions needed for complete Wi-Fi state control and network scanning.
     */
    fun getRequiredWifiPermissions(): Array<String> {
        val perms = mutableListOf(
            android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.CHANGE_WIFI_STATE,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add("android.permission.NEARBY_WIFI_DEVICES")
        }
        return perms.toTypedArray()
    }

    /**
     * Checks if all required Wi-Fi and network location permissions are currently granted.
     */
    fun hasRequiredWifiPermissions(): Boolean {
        return getRequiredWifiPermissions().all { perm ->
            androidx.core.content.ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if Wi-Fi hardware is currently enabled.
     */
    fun isWifiEnabled(): Boolean {
        return try {
            wifiManager?.isWifiEnabled == true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Toggles Wi-Fi state locally and triggers an immediate Firestore synchronization.
     */
    fun toggleWifi(): Boolean {
        val currentState = isWifiEnabled()
        return setWifiEnabled(!currentState)
    }

    /**
     * Sets Wi-Fi state locally. On Android 9 and lower, uses direct WifiManager API.
     * On Android 10+ (Q+), attempts direct toggle and falls back gracefully to opening
     * the Wi-Fi panel so the user can toggle with one touch.
     */
    @SuppressLint("MissingPermission")
    fun setWifiEnabled(enable: Boolean): Boolean {
        Log.i(TAG, "Requesting Wi-Fi state change to: $enable")
        var success = false

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                success = wifiManager?.setWifiEnabled(enable) == true
            } else {
                // On Android Q+, try direct method via reflection or standard API
                try {
                    @Suppress("DEPRECATION")
                    success = wifiManager?.setWifiEnabled(enable) == true
                } catch (_: Throwable) {
                    success = false
                }

                // If direct toggle is restricted by OS, launch the interactive quick Settings Panel
                if (!success) {
                    openWifiSettingsPanel()
                    success = true // Handled via system intent
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error toggling Wi-Fi: ${e.message}")
            openWifiSettingsPanel()
            success = false
        }

        _isWifiActive.value = isWifiEnabled()

        // Sync fresh status to Firestore
        monitoringScope?.launch(Dispatchers.IO) {
            networkRepo.syncCurrentStatus()
        }

        return success
    }

    /**
     * Opens the Android Wi-Fi Settings Panel overlay for 1-tap toggling on Android 10+.
     */
    fun openWifiSettingsPanel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(panelIntent)
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not launch Wi-Fi settings panel: ${e.message}")
            try {
                val fallbackIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Starts listening for network changes via ConnectivityManager and syncs changes to Firestore.
     */
    @Synchronized
    fun startNetworkMonitoring(scope: CoroutineScope) {
        if (isMonitoring) return
        val cm = connectivityManager ?: return

        isMonitoring = true
        monitoringScope = scope

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isWifiActive.value = isWifiEnabled()
                scope.launch(Dispatchers.IO) {
                    networkRepo.syncCurrentStatus()
                }
            }

            override fun onLost(network: Network) {
                _isWifiActive.value = isWifiEnabled()
                scope.launch(Dispatchers.IO) {
                    networkRepo.syncCurrentStatus()
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                _isWifiActive.value = isWifiEnabled()
                scope.launch(Dispatchers.IO) {
                    networkRepo.syncCurrentStatus()
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(networkCallback!!)
            } else {
                cm.registerNetworkCallback(request, networkCallback!!)
            }
            Log.i(TAG, "ConnectivityManager callback registered.")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    /**
     * Stops listening for network changes.
     */
    @Synchronized
    fun stopNetworkMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false

        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
                Log.i(TAG, "ConnectivityManager callback unregistered.")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to unregister network callback: ${e.message}")
            }
        }
        networkCallback = null
        monitoringScope = null
    }

    private var ringtonePlayer: MediaPlayer? = null
    private var ringJob: Job? = null
    private var isTorchOn: Boolean = false

    /**
     * Locks the device remotely using JarvisAccessibilityService (API 28+)
     * or DevicePolicyManager fallback.
     */
    fun lockDevice(): Boolean {
        Log.i(TAG, "Executing device lock request")
        if (JarvisAccessibilityService.lockScreen()) {
            Log.i(TAG, "Device screen locked via JarvisAccessibilityService")
            return true
        }

        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            if (dpm != null) {
                dpm.lockNow()
                Log.i(TAG, "Device locked via DevicePolicyManager")
                return true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "DevicePolicyManager lock failed: ${e.message}")
        }
        return false
    }

    /**
     * Rings the device at maximum alarm volume to locate it.
     */
    @Synchronized
    fun ringDevice(start: Boolean, durationMs: Long = 30000L): Boolean {
        return try {
            if (!start) {
                ringJob?.cancel()
                ringJob = null
                ringtonePlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
                ringtonePlayer = null
                Log.i(TAG, "Ringing stopped")
                return true
            }

            ringtonePlayer?.release()
            ringJob?.cancel()

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 7
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)

            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            val player = MediaPlayer().apply {
                setDataSource(context, alertUri)
                setAudioStreamType(AudioManager.STREAM_ALARM)
                isLooping = true
                prepare()
                start()
            }
            ringtonePlayer = player

            monitoringScope?.let { scope ->
                ringJob = scope.launch(Dispatchers.Default) {
                    delay(durationMs)
                    ringDevice(false)
                }
            }
            Log.i(TAG, "Remote device ringing started at max volume")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error ringing device: ${e.message}")
            false
        }
    }

    /**
     * Toggles or sets camera flashlight remotely.
     */
    fun toggleFlashlight(enable: Boolean? = null): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return false
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false

            val newState = enable ?: !isTorchOn
            cameraManager.setTorchMode(cameraId, newState)
            isTorchOn = newState
            Log.i(TAG, "Flashlight state changed to: $newState")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to toggle flashlight: ${e.message}")
            false
        }
    }

    /**
     * Vibrates the device remotely.
     */
    fun vibrateDevice(durationMs: Long = 1000L): Boolean {
        return try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Vibration failed: ${e.message}")
            false
        }
    }

    /**
     * Executes an incoming remote command dispatched via Firestore 'network_commands'.
     */
    suspend fun executeRemoteCommand(command: NetworkRemoteCommand): Boolean {
        Log.i(TAG, "Executing incoming remote network command: ${command.action} (ID: ${command.commandId})")
        var resultMessage = "Action completed"
        var success = false

        when (command.action.uppercase()) {
            "TOGGLE_WIFI" -> {
                val before = isWifiEnabled()
                setWifiEnabled(!before)
                val after = isWifiEnabled()
                success = true
                resultMessage = "Wi-Fi toggled from $before to $after"
            }

            "ENABLE_WIFI" -> {
                success = setWifiEnabled(true)
                resultMessage = if (success) "Wi-Fi enabled successfully" else "Failed to enable Wi-Fi"
            }

            "DISABLE_WIFI" -> {
                success = setWifiEnabled(false)
                resultMessage = if (success) "Wi-Fi disabled successfully" else "Failed to disable Wi-Fi"
            }

            "OPEN_WIFI_SETTINGS" -> {
                openWifiSettingsPanel()
                success = true
                resultMessage = "Wi-Fi Settings Panel launched"
            }

            "LOCK_DEVICE", "LOCK_SCREEN" -> {
                val locked = lockDevice()
                success = locked
                resultMessage = if (locked) "Host screen locked successfully" else "Failed to lock screen (Accessibility required)"
            }

            "RING_PHONE", "LOCATE_DEVICE", "ALARM" -> {
                val duration = (command.parameters["durationMs"] as? Number)?.toLong() ?: 30000L
                success = ringDevice(true, duration)
                resultMessage = if (success) "Host ringing at maximum volume for ${duration / 1000}s" else "Failed to trigger ringtone"
            }

            "STOP_RING" -> {
                success = ringDevice(false)
                resultMessage = "Host ringtone stopped"
            }

            "TOGGLE_FLASHLIGHT" -> {
                success = toggleFlashlight()
                resultMessage = if (success) "Flashlight state toggled (Torch: $isTorchOn)" else "Flashlight unavailable"
            }

            "ENABLE_FLASHLIGHT" -> {
                success = toggleFlashlight(true)
                resultMessage = if (success) "Flashlight turned ON" else "Failed to turn ON flashlight"
            }

            "DISABLE_FLASHLIGHT" -> {
                success = toggleFlashlight(false)
                resultMessage = if (success) "Flashlight turned OFF" else "Failed to turn OFF flashlight"
            }

            "VIBRATE" -> {
                val duration = (command.parameters["durationMs"] as? Number)?.toLong() ?: 1200L
                success = vibrateDevice(duration)
                resultMessage = "Host vibrated for ${duration}ms"
            }

            "SYNC_STATUS" -> {
                val res = networkRepo.syncCurrentStatus()
                success = res.isSuccess
                resultMessage = if (success) "Network status synced with Firestore" else "Status sync failed: ${res.exceptionOrNull()?.message}"
            }

            "PING" -> {
                success = true
                val status = networkRepo.getCurrentNetworkStatus()
                resultMessage = "Host PONG: ${status.networkState} (${status.connectionType}) IP: ${status.ipAddress ?: "N/A"}"
            }

            else -> {
                success = false
                resultMessage = "Unknown remote action '${command.action}'"
            }
        }

        // Mark the command document in Firestore as executed
        networkRepo.markCommandExecuted(command.commandId, resultMessage)
        return success
    }

    /**
     * Sends a remote Wi-Fi command to another JARVIS NEXUS device via Firestore.
     */
    suspend fun sendRemoteWifiCommand(targetDeviceId: String, action: String): Result<String> {
        return networkRepo.sendRemoteCommand(
            targetDeviceId = targetDeviceId,
            action = action,
            parameters = mapOf("sourceModel" to "${Build.MANUFACTURER} ${Build.MODEL}")
        )
    }
}

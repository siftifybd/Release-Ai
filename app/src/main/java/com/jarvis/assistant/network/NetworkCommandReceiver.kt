package com.jarvis.assistant.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jarvis.assistant.data.repository.FirebaseNetworkRepo
import com.jarvis.assistant.data.repository.NetworkRemoteCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Receiver and dispatcher component for JARVIS NEXUS remote network commands.
 * Listens to the 'network_commands' Firestore collection in real time and maps
 * incoming commands (e.g. TOGGLE_WIFI, LOCK_DEVICE, RING_PHONE) directly to
 * NetworkControlManager methods, recording execution feedback in Cloud Firestore.
 */
class NetworkCommandReceiver(
    private val context: Context,
    val networkControlManager: NetworkControlManager = NetworkControlManager.getInstance(context)
) {

    companion object {
        private const val TAG = "NetworkCommandReceiver"
        const val ACTION_EXECUTE_COMMAND = "com.jarvis.assistant.network.ACTION_EXECUTE_COMMAND"
        const val EXTRA_ACTION = "extra_action"
        const val EXTRA_PARAMETERS = "extra_parameters"

        @Volatile
        private var INSTANCE: NetworkCommandReceiver? = null

        fun getInstance(context: Context): NetworkCommandReceiver {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkCommandReceiver(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var listenerJob: Job? = null
    private var listeningScope: CoroutineScope? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastReceivedCommand = MutableStateFlow<NetworkRemoteCommand?>(null)
    val lastReceivedCommand: StateFlow<NetworkRemoteCommand?> = _lastReceivedCommand.asStateFlow()

    private val _executedCount = MutableStateFlow(0)
    val executedCount: StateFlow<Int> = _executedCount.asStateFlow()

    /**
     * Starts listening to Firestore 'network_commands' targeted at this device.
     */
    @Synchronized
    fun startListening(scope: CoroutineScope) {
        if (_isListening.value && listenerJob?.isActive == true) {
            Log.d(TAG, "Already actively listening for network_commands.")
            return
        }

        listeningScope = scope
        _isListening.value = true

        val repo = networkControlManager.networkRepo
        val targetDeviceId = repo.deviceId

        Log.i(TAG, "Starting Firestore 'network_commands' listener for deviceId: $targetDeviceId")

        listenerJob?.cancel()
        listenerJob = scope.launch(Dispatchers.IO) {
            try {
                repo.observeIncomingCommands(targetDeviceId).collect { commandList ->
                    for (cmd in commandList) {
                        if (!cmd.executed) {
                            Log.i(TAG, "Received unexecuted command: ${cmd.action} (ID: ${cmd.commandId})")
                            _lastReceivedCommand.value = cmd
                            processCommand(cmd)
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "network_commands listener coroutine cancelled normally: ${e.message}")
            } catch (e: Throwable) {
                Log.e(TAG, "Exception in network_commands observer: ${e.message}", e)
            } finally {
                _isListening.value = false
            }
        }
    }

    /**
     * Stops listening to the Firestore 'network_commands' collection.
     */
    @Synchronized
    fun stopListening() {
        Log.i(TAG, "Stopping Firestore 'network_commands' listener.")
        listenerJob?.cancel()
        listenerJob = null
        listeningScope = null
        _isListening.value = false
    }

    /**
     * Maps an incoming NetworkRemoteCommand to NetworkControlManager methods
     * and records the execution state in Firestore.
     */
    suspend fun processCommand(command: NetworkRemoteCommand): Boolean {
        Log.i(TAG, "Mapping command '${command.action}' to NetworkControlManager...")
        val actionName = command.action.trim().uppercase()
        var resultMessage = "Action completed"
        var isSuccess = false

        try {
            when (actionName) {
                "TOGGLE_WIFI" -> {
                    val wasEnabled = networkControlManager.isWifiEnabled()
                    networkControlManager.toggleWifi()
                    val nowEnabled = networkControlManager.isWifiEnabled()
                    isSuccess = true
                    resultMessage = "Wi-Fi toggled from $wasEnabled to $nowEnabled"
                }

                "ENABLE_WIFI" -> {
                    isSuccess = networkControlManager.setWifiEnabled(true)
                    resultMessage = if (isSuccess) "Wi-Fi successfully enabled" else "Failed to enable Wi-Fi"
                }

                "DISABLE_WIFI" -> {
                    isSuccess = networkControlManager.setWifiEnabled(false)
                    resultMessage = if (isSuccess) "Wi-Fi successfully disabled" else "Failed to disable Wi-Fi"
                }

                "OPEN_WIFI_SETTINGS" -> {
                    networkControlManager.openWifiSettingsPanel()
                    isSuccess = true
                    resultMessage = "Opened system Wi-Fi settings panel"
                }

                "LOCK_DEVICE", "LOCK_SCREEN" -> {
                    isSuccess = networkControlManager.lockDevice()
                    resultMessage = if (isSuccess) {
                        "Device screen locked successfully via Accessibility"
                    } else {
                        "Lock screen failed: please enable Jarvis Accessibility Service"
                    }
                }

                "RING_PHONE", "LOCATE_DEVICE", "ALARM" -> {
                    val durationMs = (command.parameters["durationMs"] as? Number)?.toLong() ?: 30000L
                    isSuccess = networkControlManager.ringDevice(true, durationMs)
                    resultMessage = if (isSuccess) {
                        "Ringing device at max volume for ${durationMs / 1000}s"
                    } else {
                        "Failed to ring device"
                    }
                }

                "STOP_RING" -> {
                    isSuccess = networkControlManager.ringDevice(false)
                    resultMessage = "Device ringing stopped"
                }

                "TOGGLE_FLASHLIGHT" -> {
                    isSuccess = networkControlManager.toggleFlashlight()
                    resultMessage = if (isSuccess) "Flashlight state toggled" else "Flashlight unavailable"
                }

                "ENABLE_FLASHLIGHT" -> {
                    isSuccess = networkControlManager.toggleFlashlight(true)
                    resultMessage = if (isSuccess) "Flashlight turned ON" else "Failed to turn ON flashlight"
                }

                "DISABLE_FLASHLIGHT" -> {
                    isSuccess = networkControlManager.toggleFlashlight(false)
                    resultMessage = if (isSuccess) "Flashlight turned OFF" else "Failed to turn OFF flashlight"
                }

                "VIBRATE" -> {
                    val durationMs = (command.parameters["durationMs"] as? Number)?.toLong() ?: 1200L
                    isSuccess = networkControlManager.vibrateDevice(durationMs)
                    resultMessage = "Vibrated device for ${durationMs}ms"
                }

                "SYNC_STATUS" -> {
                    val syncRes = networkControlManager.networkRepo.syncCurrentStatus()
                    isSuccess = syncRes.isSuccess
                    resultMessage = if (isSuccess) {
                        "Network telemetry synced to Firestore"
                    } else {
                        "Status sync failed: ${syncRes.exceptionOrNull()?.message}"
                    }
                }

                "PING" -> {
                    val currentStatus = networkControlManager.networkRepo.getCurrentNetworkStatus()
                    isSuccess = true
                    resultMessage = "Host PONG [${Build.MANUFACTURER} ${Build.MODEL}] • State: ${currentStatus.networkState} • IP: ${currentStatus.ipAddress ?: "N/A"}"
                }

                else -> {
                    // Fall back to direct execution in NetworkControlManager
                    isSuccess = networkControlManager.executeRemoteCommand(command)
                    return isSuccess
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing mapped command '${command.action}': ${e.message}")
            isSuccess = false
            resultMessage = "Execution error: ${e.message}"
        }

        // Persist executed status and feedback message back to Firestore
        networkControlManager.networkRepo.markCommandExecuted(command.commandId, resultMessage)
        _executedCount.value += 1
        Log.i(TAG, "Command '${command.action}' completed with result: $resultMessage")
        return isSuccess
    }

    /**
     * Optional Android BroadcastReceiver allowing system or internal intents
     * to trigger mapped commands via local broadcasts.
     */
    class LocalCommandBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == ACTION_EXECUTE_COMMAND) {
                val action = intent.getStringExtra(EXTRA_ACTION) ?: return
                val receiver = getInstance(context)
                val cmd = NetworkRemoteCommand(
                    commandId = "local-${System.currentTimeMillis()}",
                    targetDeviceId = receiver.networkControlManager.networkRepo.deviceId,
                    action = action
                )
                receiver.listeningScope?.launch(Dispatchers.IO) {
                    receiver.processCommand(cmd)
                } ?: run {
                    CoroutineScope(Dispatchers.IO).launch {
                        receiver.processCommand(cmd)
                    }
                }
            }
        }
    }
}

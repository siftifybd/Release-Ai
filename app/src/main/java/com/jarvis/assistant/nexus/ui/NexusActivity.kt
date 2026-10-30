package com.jarvis.assistant.nexus.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.ActivityNexusBinding
import com.jarvis.assistant.nexus.manager.NexusManager
import com.jarvis.assistant.nexus.model.NexusConnectionState
import com.jarvis.assistant.nexus.model.NexusDevice
import com.jarvis.assistant.nexus.model.NexusDiscoveredDevice
import com.jarvis.assistant.nexus.model.NexusMode
import com.jarvis.assistant.nexus.model.RemoteChatMessage
import com.jarvis.assistant.nexus.model.RemoteFileItem
import com.jarvis.assistant.nexus.security.NexusSecurityManager
import com.jarvis.assistant.nexus.service.NexusHostService
import com.jarvis.assistant.nexus.service.NexusScreenCaptureService
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.launch
import java.io.File

class NexusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNexusBinding
    private var selectedMode = NexusMode.LAN
    private var currentPane = PANE_MODE_SELECT

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            NexusScreenCaptureService.start(this, result.resultCode, result.data!!)
            Toast.makeText(this, "Screen sharing started on this Host device", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission declined", Toast.LENGTH_SHORT).show()
        }
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment ?: "Selected File"
            Toast.makeText(this, "Sending file: $fileName", Toast.LENGTH_SHORT).show()
            NexusManager.executeRemoteAction("file.send.request", mapOf("fileName" to fileName)) { success, reply ->
                Toast.makeText(this, reply, Toast.LENGTH_LONG).show()
            }
        }
    }

    private val wifiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Wi-Fi & Network permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some Wi-Fi permissions denied. Using system panel fallback.", Toast.LENGTH_LONG).show()
        }
        updateServerUI()
    }

    private fun checkAndPromptWifiPermissions(onGranted: () -> Unit) {
        val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(this)
        val missing = netMgr.getRequiredWifiPermissions().filter { perm ->
            ContextCompat.checkSelfPermission(this, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onGranted()
        } else {
            wifiPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    companion object {
        const val PANE_MODE_SELECT = 0
        const val PANE_ADD_DEVICE = 1
        const val PANE_CONNECTING = 2
        const val PANE_CONNECTED = 3
        const val PANE_SCREEN_SHARE = 4
        const val PANE_FILE_TRANSFER = 5
        const val PANE_REMOTE_CHAT = 6
        const val PANE_HISTORY = 7
        const val PANE_SECURITY = 8

        fun start(context: Context) {
            context.startActivity(Intent(context, NexusActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityNexusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NexusManager.init(this)

        setupTopBar()
        setupModeSelectPane()
        setupAddDevicePane()
        setupConnectedPane()
        setupScreenSharePane()
        setupFileTransferPane()
        setupRemoteChatPane()
        setupHistoryPane()
        setupSecurityPane()
        setupDisconnectedModal()
        observeManagerState()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })

        showPane(PANE_MODE_SELECT)
    }

    private fun setupTopBar() {
        binding.btnNexusBack.setOnClickListener { handleBack() }
        binding.btnNexusSettings.setOnClickListener { showPane(PANE_SECURITY) }
    }

    private fun handleBack() {
        if (binding.layoutDisconnectedModal.visibility == View.VISIBLE) {
            binding.layoutDisconnectedModal.visibility = View.GONE
            showPane(PANE_MODE_SELECT)
            return
        }

        when (currentPane) {
            PANE_ADD_DEVICE, PANE_HISTORY, PANE_SECURITY -> showPane(PANE_MODE_SELECT)
            PANE_SCREEN_SHARE, PANE_FILE_TRANSFER, PANE_REMOTE_CHAT -> showPane(PANE_CONNECTED)
            PANE_CONNECTED -> showPane(PANE_MODE_SELECT)
            else -> finish()
        }
    }

    private fun showPane(pane: Int) {
        currentPane = pane
        binding.paneModeSelect.visibility = if (pane == PANE_MODE_SELECT) View.VISIBLE else View.GONE
        binding.paneAddDevice.visibility = if (pane == PANE_ADD_DEVICE) View.VISIBLE else View.GONE
        binding.paneConnecting.visibility = if (pane == PANE_CONNECTING) View.VISIBLE else View.GONE
        binding.paneConnected.visibility = if (pane == PANE_CONNECTED) View.VISIBLE else View.GONE
        binding.paneScreenShare.visibility = if (pane == PANE_SCREEN_SHARE) View.VISIBLE else View.GONE
        binding.paneFileTransfer.visibility = if (pane == PANE_FILE_TRANSFER) View.VISIBLE else View.GONE
        binding.paneRemoteChat.visibility = if (pane == PANE_REMOTE_CHAT) View.VISIBLE else View.GONE
        binding.paneHistory.visibility = if (pane == PANE_HISTORY) View.VISIBLE else View.GONE
        binding.paneSecurity.visibility = if (pane == PANE_SECURITY) View.VISIBLE else View.GONE
    }

    private fun setupModeSelectPane() {
        updateServerUI()
        binding.btnToggleServer.setOnClickListener {
            if (NexusHostService.isRunning || NexusManager.localServer.isRunning) {
                NexusManager.stopHostService(this)
                NexusManager.stopLocalServer()
                lifecycleScope.launch {
                    try {
                        com.jarvis.assistant.JarvisApp.instance.firebaseNetworkRepo.markOffline()
                    } catch (_: Throwable) {}
                }
            } else {
                checkAndPromptWifiPermissions {
                    NexusManager.startHostService(this)
                }
            }
            binding.root.postDelayed({ updateServerUI() }, 400)
        }

        binding.btnSelectLanMode.setOnClickListener {
            selectedMode = NexusMode.LAN
            binding.tvNexusSubtitle.text = "LAN Mode (Same Wi-Fi network)"
            showPane(PANE_ADD_DEVICE)
        }

        binding.btnSelectInternetMode.setOnClickListener {
            selectedMode = NexusMode.INTERNET
            binding.tvNexusSubtitle.text = "Internet Mode (Relay Server)"
            showPane(PANE_ADD_DEVICE)
        }

        binding.btnQuickPair.setOnClickListener {
            showPane(PANE_ADD_DEVICE)
        }

        binding.btnQuickHistory.setOnClickListener {
            renderHistoryItems()
            showPane(PANE_HISTORY)
        }
    }

    private fun updateServerUI() {
        val running = NexusHostService.isRunning || NexusManager.localServer.isRunning
        val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(this)
        val hasPerms = netMgr.hasRequiredWifiPermissions()
        val ip = NexusManager.localServer.getLocalIpAddress()
        val port = NexusManager.localServer.activePort

        if (running) {
            binding.tvServerTitle.text = "LOCAL SERVER ACTIVE • LAN"
            binding.tvServerTitle.setTextColor(Color.parseColor("#00E5FF"))
            binding.tvServerIp.text = "Ready for connection on $ip:$port • Firestore Live"
            binding.btnToggleServer.text = "STOP"
            binding.btnToggleServer.setBackgroundResource(R.drawable.bg_nexus_purple_btn)
            binding.dotServerGlow.setBackgroundResource(R.drawable.circle_cyan)
        } else {
            binding.tvServerTitle.text = if (hasPerms) "LOCAL SERVER STOPPED" else "PERMISSIONS NEEDED • LOCAL SERVER STOPPED"
            binding.tvServerTitle.setTextColor(if (hasPerms) Color.parseColor("#94A3B8") else Color.parseColor("#F59E0B"))
            binding.tvServerIp.text = if (hasPerms) "Tap START to accept incoming connections" else "Tap START to grant Wi-Fi & Network permissions"
            binding.btnToggleServer.text = "START"
            binding.btnToggleServer.setBackgroundResource(R.drawable.bg_nexus_tile)
            binding.dotServerGlow.setBackgroundResource(R.drawable.bg_jarvis_mic_muted)
        }
    }

    private fun setupAddDevicePane() {
        refreshPairingCode()

        binding.tabAddIp.setOnClickListener {
            binding.tabAddIp.setBackgroundResource(R.drawable.bg_nexus_purple_btn)
            binding.tabAddIp.setTextColor(Color.WHITE)
            binding.tabAddQr.background = null
            binding.tabAddQr.setTextColor(Color.parseColor("#94A3B8"))
            binding.layoutIpFields.visibility = View.VISIBLE
            binding.layoutQrFields.visibility = View.GONE
        }

        binding.tabAddQr.setOnClickListener {
            refreshPairingCode()
            binding.tabAddQr.setBackgroundResource(R.drawable.bg_nexus_purple_btn)
            binding.tabAddQr.setTextColor(Color.WHITE)
            binding.tabAddIp.background = null
            binding.tabAddIp.setTextColor(Color.parseColor("#94A3B8"))
            binding.layoutIpFields.visibility = View.GONE
            binding.layoutQrFields.visibility = View.VISIBLE
        }

        binding.btnRegenerateCode.setOnClickListener {
            val newCode = NexusSecurityManager.generatePairingCode()
            binding.tvGeneratedPairingCode.text = "PAIRING CODE: $newCode"
            Toast.makeText(this, "New 6-digit code generated (5m TTL)", Toast.LENGTH_SHORT).show()
        }

        binding.btnSearchNearby.setOnClickListener {
            checkAndPromptWifiPermissions {
                if (NexusManager.discoveryManager.isSearching.value) {
                    NexusManager.stopDiscovery()
                    binding.btnSearchNearby.text = "SEARCH"
                    binding.tvDiscoveryStatus.text = "Discovery stopped."
                } else {
                    NexusManager.startDiscovery()
                    binding.btnSearchNearby.text = "STOP"
                    binding.tvDiscoveryStatus.text = "Searching Wi-Fi LAN & Cloud for JARVIS Hosts..."
                }
            }
        }

        binding.btnConnectIp.setOnClickListener {
            val ip = binding.etDeviceIp.text.toString().trim()
            val port = binding.etDevicePort.text.toString().trim().toIntOrNull() ?: 8765
            val code = binding.etPairingCode.text.toString().trim()

            if (ip.isBlank()) {
                Toast.makeText(this, "Please enter target IP address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (code.isBlank() || code.length < 4) {
                Toast.makeText(this, "Please enter target phone's 6-digit pairing code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedMode == NexusMode.INTERNET && !ip.startsWith("wss://") && !ip.startsWith("ws://") && !ip.contains(".")) {
                Toast.makeText(this, "Internet Relay Server URL required for Internet Mode.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            initiateConnection(ip, port, code, selectedMode)
        }

        binding.btnSimulateQrScan.setOnClickListener {
            val code = NexusSecurityManager.getCurrentPairingCode()
            binding.etPairingCode.setText(code)
            binding.tabAddIp.performClick()
        }
    }

    private fun refreshPairingCode() {
        val code = NexusSecurityManager.getCurrentPairingCode()
        binding.tvGeneratedPairingCode.text = "PAIRING CODE: $code"
    }

    private fun initiateConnection(ip: String, port: Int, code: String, mode: NexusMode) {
        showPane(PANE_CONNECTING)
        resetConnectingChecks()

        NexusManager.connectDevice(ip, port, code, mode,
            onProgress = { state, msg ->
                runOnUiThread {
                    when (state) {
                        NexusConnectionState.CONNECTING_IP -> {
                            binding.ivCheckIp.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_cyan)
                            binding.tvCheckIp.setTextColor(Color.WHITE)
                            binding.tvCheckIp.text = msg
                        }
                        NexusConnectionState.VERIFYING_CODE -> {
                            binding.ivCheckCode.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_cyan)
                            binding.tvCheckCode.setTextColor(Color.WHITE)
                            binding.tvCheckCode.text = msg
                        }
                        NexusConnectionState.ESTABLISHING_TLS -> {
                            binding.ivCheckTls.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_cyan)
                            binding.tvCheckTls.setTextColor(Color.WHITE)
                            binding.tvCheckTls.text = msg
                        }
                        NexusConnectionState.CONNECTED -> {
                            binding.ivCheckDone.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_green)
                            binding.tvCheckDone.setTextColor(Color.parseColor("#10B981"))
                            binding.tvCheckDone.text = msg
                        }
                        NexusConnectionState.ERROR -> {
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        }
                        else -> {}
                    }
                }
            },
            onComplete = { success, device, errorMsg ->
                runOnUiThread {
                    if (success && device != null) {
                        displayConnectedDevice(device)
                        showPane(PANE_CONNECTED)
                    } else {
                        Toast.makeText(this, errorMsg ?: "Connection failed", Toast.LENGTH_LONG).show()
                        showPane(PANE_ADD_DEVICE)
                    }
                }
            }
        )
    }

    private fun resetConnectingChecks() {
        val muted = Color.parseColor("#64748B")
        binding.tvCheckIp.setTextColor(muted)
        binding.tvCheckCode.setTextColor(muted)
        binding.tvCheckTls.setTextColor(muted)
        binding.tvCheckDone.setTextColor(muted)
        val mutedTint = ContextCompat.getColorStateList(this, R.color.text_subtle)
        binding.ivCheckIp.imageTintList = mutedTint
        binding.ivCheckCode.imageTintList = mutedTint
        binding.ivCheckTls.imageTintList = mutedTint
        binding.ivCheckDone.imageTintList = mutedTint
    }

    private fun setupConnectedPane() {
        binding.btnDisconnectActive.setOnClickListener {
            NexusManager.disconnectActiveSession()
            binding.layoutDisconnectedModal.visibility = View.VISIBLE
        }

        binding.optRemoteChat.setOnClickListener {
            showPane(PANE_REMOTE_CHAT)
        }

        binding.optScreenShare.setOnClickListener {
            showPane(PANE_SCREEN_SHARE)
        }

        binding.optCameraAccess.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Remote Camera Request")
                .setMessage("Request live camera feed from paired Host phone?")
                .setPositiveButton("Request") { _, _ ->
                    NexusManager.executeRemoteAction("camera.preview.request") { success, res ->
                        Toast.makeText(this, res, Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.optFileTransfer.setOnClickListener {
            showPane(PANE_FILE_TRANSFER)
            refreshRemoteFiles("/")
        }

        binding.optDeviceInfo.setOnClickListener {
            val device = NexusManager.activeSession.value?.peerDevice
            if (device != null) {
                AlertDialog.Builder(this)
                    .setTitle("Remote Device Information")
                    .setMessage("Name: ${device.displayName}\nModel: ${device.modelName}\nIP Address: ${device.ipAddress}:${device.port}\nMode: ${device.mode}\nAndroid Version: ${device.androidVersion}\nBattery: ${if (device.batteryLevel >= 0) "${device.batteryLevel}%" else "Unavailable"}\nSecurity: TLS 1.3 / Keystore Verified")
                    .setPositiveButton("Close", null)
                    .show()
            }
        }

        binding.optMediaControl.setOnClickListener {
            NexusManager.executeRemoteAction("media.play_pause") { success, res ->
                Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnQuickWifiToggle.setOnClickListener {
            toggleRemoteWifi()
        }

        binding.optRemoteWifiControl.setOnClickListener {
            showRemoteNetworkManagementDialog()
        }

        binding.btnQuickRemoteLock.setOnClickListener {
            lockRemoteDevice()
        }

        binding.optRemoteDeviceLock.setOnClickListener {
            showRemoteSafetyDialog()
        }

        binding.optMoreOptions.setOnClickListener {
            showMoreActionsDialog()
        }
    }

    private fun toggleRemoteWifi() {
        val target = NexusManager.activeSession.value?.peerDevice
        val repo = com.jarvis.assistant.JarvisApp.instance.firebaseNetworkRepo
        val targetId = target?.id ?: repo.deviceId

        lifecycleScope.launch {
            Toast.makeText(this@NexusActivity, "Dispatching Wi-Fi toggle...", Toast.LENGTH_SHORT).show()
            val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(this@NexusActivity)
            val res = netMgr.sendRemoteWifiCommand(targetId, "TOGGLE_WIFI")
            if (res.isSuccess) {
                Toast.makeText(this@NexusActivity, "Command sent to Cloud Relay (ID: ${res.getOrNull()})", Toast.LENGTH_SHORT).show()
                binding.tvRemoteWifiStatusSub.text = "Wi-Fi Toggle Sent • Awaiting host confirmation"
            } else {
                NexusManager.executeRemoteAction("wifi.toggle") { success, msg ->
                    Toast.makeText(this@NexusActivity, "LAN Wi-Fi toggle: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun lockRemoteDevice() {
        val target = NexusManager.activeSession.value?.peerDevice
        val repo = com.jarvis.assistant.JarvisApp.instance.firebaseNetworkRepo
        val targetId = target?.id ?: repo.deviceId

        lifecycleScope.launch {
            Toast.makeText(this@NexusActivity, "Dispatching Remote Screen Lock...", Toast.LENGTH_SHORT).show()
            val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(this@NexusActivity)
            val res = netMgr.sendRemoteWifiCommand(targetId, "LOCK_DEVICE")
            if (res.isSuccess) {
                Toast.makeText(this@NexusActivity, "Lock command sent via Cloud Relay", Toast.LENGTH_SHORT).show()
            }
            NexusManager.executeRemoteAction("device.lock") { success, msg ->
                if (success) {
                    Toast.makeText(this@NexusActivity, "Direct LAN: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showRemoteSafetyDialog() {
        val target = NexusManager.activeSession.value?.peerDevice
        val repo = com.jarvis.assistant.JarvisApp.instance.firebaseNetworkRepo
        val targetId = target?.id ?: repo.deviceId
        val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(this)

        val options = arrayOf(
            "Lock Host Screen Immediately 🔒",
            "Ring Host Phone (Find Device at Max Alarm) 🔔",
            "Stop Host Phone Ringing 🔕",
            "Toggle Host Flashlight 🔦",
            "Vibrate Host Device 📳"
        )

        AlertDialog.Builder(this)
            .setTitle("Remote Lock & Safety Hub")
            .setItems(options) { _, which ->
                lifecycleScope.launch {
                    when (which) {
                        0 -> lockRemoteDevice()
                        1 -> {
                            netMgr.sendRemoteWifiCommand(targetId, "RING_PHONE")
                            NexusManager.executeRemoteAction("device.ring") { _, _ -> }
                            Toast.makeText(this@NexusActivity, "Ringing Host Phone...", Toast.LENGTH_SHORT).show()
                        }
                        2 -> {
                            netMgr.sendRemoteWifiCommand(targetId, "STOP_RING")
                            NexusManager.executeRemoteAction("device.ring.stop") { _, _ -> }
                            Toast.makeText(this@NexusActivity, "Stopping Ringtone...", Toast.LENGTH_SHORT).show()
                        }
                        3 -> {
                            netMgr.sendRemoteWifiCommand(targetId, "TOGGLE_FLASHLIGHT")
                            NexusManager.executeRemoteAction("device.flashlight") { _, _ -> }
                            Toast.makeText(this@NexusActivity, "Toggling Flashlight...", Toast.LENGTH_SHORT).show()
                        }
                        4 -> {
                            netMgr.sendRemoteWifiCommand(targetId, "VIBRATE")
                            NexusManager.executeRemoteAction("device.vibrate") { _, _ -> }
                            Toast.makeText(this@NexusActivity, "Vibrating Host Device...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoteNetworkManagementDialog() {
        val target = NexusManager.activeSession.value?.peerDevice
        val repo = com.jarvis.assistant.JarvisApp.instance.firebaseNetworkRepo
        val targetId = target?.id ?: repo.deviceId
        val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(this)

        val options = arrayOf(
            "Toggle Remote Wi-Fi (Invert State)",
            "Turn Remote Wi-Fi ON",
            "Turn Remote Wi-Fi OFF",
            "Force Sync Host Network Status (Firestore)",
            "Ping Host Network Health",
            "Open Local Wi-Fi Quick Panel"
        )

        AlertDialog.Builder(this)
            .setTitle("Remote Wi-Fi & Network Control")
            .setItems(options) { _, which ->
                lifecycleScope.launch {
                    when (which) {
                        0 -> {
                            netMgr.sendRemoteWifiCommand(targetId, "TOGGLE_WIFI")
                            Toast.makeText(this@NexusActivity, "Dispatched TOGGLE_WIFI", Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            netMgr.sendRemoteWifiCommand(targetId, "ENABLE_WIFI")
                            Toast.makeText(this@NexusActivity, "Dispatched ENABLE_WIFI", Toast.LENGTH_SHORT).show()
                        }
                        2 -> {
                            netMgr.sendRemoteWifiCommand(targetId, "DISABLE_WIFI")
                            Toast.makeText(this@NexusActivity, "Dispatched DISABLE_WIFI", Toast.LENGTH_SHORT).show()
                        }
                        3 -> {
                            netMgr.sendRemoteWifiCommand(targetId, "SYNC_STATUS")
                            Toast.makeText(this@NexusActivity, "Requested SYNC_STATUS", Toast.LENGTH_SHORT).show()
                        }
                        4 -> {
                            netMgr.sendRemoteWifiCommand(targetId, "PING")
                            Toast.makeText(this@NexusActivity, "Host PING dispatched", Toast.LENGTH_SHORT).show()
                        }
                        5 -> {
                            netMgr.openWifiSettingsPanel()
                        }
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showMoreActionsDialog() {
        val options = arrayOf(
            "Remote Lock & Safety Hub 🔒",
            "Remote Wi-Fi & Network Hub 📶",
            "Phone Call / Open Dialer on Host",
            "Launch YouTube on Host",
            "Launch Settings on Host",
            "Sync Host Notifications",
            "Accessibility: Navigate Back",
            "Accessibility: Go to Home Screen",
            "Accessibility: Show Recents",
            "Permissions & Security Settings"
        )
        AlertDialog.Builder(this)
            .setTitle("Advanced Remote Actions")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRemoteSafetyDialog()
                    1 -> showRemoteNetworkManagementDialog()
                    2 -> promptDialerCall()
                    3 -> NexusManager.executeRemoteAction("app.launch", mapOf("packageName" to "com.google.android.youtube")) { _, msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                    4 -> NexusManager.executeRemoteAction("app.launch", mapOf("packageName" to "com.android.settings")) { _, msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                    5 -> NexusManager.executeRemoteAction("notification.digest") { _, msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
                    6 -> NexusManager.executeRemoteAction("accessibility.action", mapOf("subAction" to "back")) { _, msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                    7 -> NexusManager.executeRemoteAction("accessibility.action", mapOf("subAction" to "home")) { _, msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                    8 -> NexusManager.executeRemoteAction("accessibility.action", mapOf("subAction" to "recents")) { _, msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                    9 -> showPane(PANE_SECURITY)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptDialerCall() {
        val input = EditText(this).apply {
            hint = "e.g. +1234567890"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        AlertDialog.Builder(this)
            .setTitle("Open Dialer on Host Phone")
            .setMessage("Host owner will see system phone dialer with this number and must press Call:")
            .setView(input)
            .setPositiveButton("Open Dialer") { _, _ ->
                val phone = input.text.toString().trim()
                if (phone.isNotBlank()) {
                    NexusManager.executeRemoteAction("dialer.call", mapOf("phoneNumber" to phone)) { success, res ->
                        Toast.makeText(this, res, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun displayConnectedDevice(device: NexusDevice) {
        binding.tvConnectedDeviceName.text = device.displayName
        binding.tvConnectedDeviceIp.text = "${device.ipAddress} | Port: ${device.port}"
        binding.tvTileBattery.text = if (device.batteryLevel >= 0) "${device.batteryLevel}%" else "N/A"
        binding.tvTileWifi.text = if (device.isWifiConnected) "Connected" else "Offline"
        binding.tvTileAndroid.text = device.androidVersion
        binding.tvTileOnlineTime.text = device.formattedLastSeen
    }

    private fun setupScreenSharePane() {
        binding.btnScreenShareStop.setOnClickListener {
            NexusScreenCaptureService.stop(this)
            Toast.makeText(this, "Screen share stopped", Toast.LENGTH_SHORT).show()
            showPane(PANE_CONNECTED)
        }

        binding.btnScreenShareFullscreen.setOnClickListener {
            // Prompt host screen sharing via MediaProjection
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (mpManager != null) {
                mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
            }
        }

        binding.btnScreenShareSettings.setOnClickListener {
            Toast.makeText(this, "Streaming: 720p Adaptive JPEG • TLS Encrypted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFileTransferPane() {
        binding.tabFileSend.setOnClickListener {
            binding.tabFileSend.setBackgroundResource(R.drawable.bg_nexus_purple_btn)
            binding.tabFileSend.setTextColor(Color.WHITE)
            binding.tabFileReceive.background = null
            binding.tabFileReceive.setTextColor(Color.parseColor("#94A3B8"))
        }

        binding.tabFileReceive.setOnClickListener {
            binding.tabFileReceive.setBackgroundResource(R.drawable.bg_nexus_purple_btn)
            binding.tabFileReceive.setTextColor(Color.WHITE)
            binding.tabFileSend.background = null
            binding.tabFileSend.setTextColor(Color.parseColor("#94A3B8"))
            refreshRemoteFiles("/")
        }

        binding.btnCategoryImages.setOnClickListener { filePickerLauncher.launch("image/*") }
        binding.btnCategoryDocuments.setOnClickListener { filePickerLauncher.launch("*/*") }
        binding.btnCategoryVideos.setOnClickListener { filePickerLauncher.launch("video/*") }
        binding.btnCategoryApps.setOnClickListener { filePickerLauncher.launch("application/vnd.android.package-archive") }
        binding.btnSelectFileToTransfer.setOnClickListener { filePickerLauncher.launch("*/*") }
    }

    private fun refreshRemoteFiles(path: String) {
        val device = NexusManager.activeSession.value?.peerDevice ?: return
        lifecycleScope.launch {
            val result = NexusManager.client.browseRemoteFiles(device, path)
            if (result.isSuccess) {
                val files = result.getOrNull() ?: emptyList()
                showRemoteFilesDialog(files)
            } else {
                Toast.makeText(this@NexusActivity, "Failed to browse: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRemoteFilesDialog(files: List<RemoteFileItem>) {
        if (files.isEmpty()) {
            Toast.makeText(this, "No files found in remote directory", Toast.LENGTH_SHORT).show()
            return
        }
        val names = files.map { "${if (it.isDirectory) "📁" else "📄"} ${it.name} (${it.formattedSize})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Host Files")
            .setItems(names) { _, which ->
                val selected = files[which]
                if (selected.isDirectory) {
                    refreshRemoteFiles(selected.path)
                } else {
                    promptDownloadFile(selected)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun promptDownloadFile(file: RemoteFileItem) {
        AlertDialog.Builder(this)
            .setTitle("Download File")
            .setMessage("Download '${file.name}' (${file.formattedSize}) from Host with SHA-256 verification?")
            .setPositiveButton("Download") { _, _ ->
                val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: cacheDir
                Toast.makeText(this, "Starting download...", Toast.LENGTH_SHORT).show()
                NexusManager.downloadFile(file, downloadsDir,
                    onProgress = { tr ->
                        runOnUiThread {
                            // Progress feedback
                        }
                    },
                    onComplete = { success, msg ->
                        runOnUiThread {
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupRemoteChatPane() {
        binding.btnRemoteChatSend.setOnClickListener {
            val text = binding.etRemoteChatInput.text.toString().trim()
            if (text.isNotBlank()) {
                binding.etRemoteChatInput.text.clear()
                NexusManager.sendRemotePrompt(text) {}
            }
        }

        binding.btnRemoteChatMic.setOnClickListener {
            val voiceSample = "Jarvis, report battery and system diagnostics on remote device."
            binding.etRemoteChatInput.setText(voiceSample)
            binding.etRemoteChatInput.setSelection(voiceSample.length)
        }
    }

    private fun renderChatMessages(messages: List<RemoteChatMessage>) {
        val container = binding.layoutRemoteChatMessages
        container.removeAllViews()

        messages.forEach { msg ->
            val bubble = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                    gravity = if (msg.isFromMe) android.view.Gravity.END else android.view.Gravity.START
                }
                layoutParams = lp
                background = ContextCompat.getDrawable(
                    this@NexusActivity,
                    if (msg.isFromMe) R.drawable.bg_nexus_purple_btn else R.drawable.bg_nexus_card
                )
            }

            val tv = TextView(this).apply {
                text = msg.text
                setTextColor(Color.WHITE)
                textSize = 14f
            }
            val time = TextView(this).apply {
                text = msg.formattedTime
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 10f
                setPadding(0, dp(4), 0, 0)
                gravity = if (msg.isFromMe) android.view.Gravity.END else android.view.Gravity.START
            }

            bubble.addView(tv)
            bubble.addView(time)
            container.addView(bubble)
        }
    }

    private fun setupHistoryPane() {
        renderHistoryItems()
    }

    private fun renderHistoryItems() {
        val container = binding.layoutHistoryItems
        container.removeAllViews()

        val items = NexusManager.connectionHistory.value
        if (items.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No connection history recorded yet."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 14f
                setPadding(dp(16), dp(24), dp(16), dp(24))
                gravity = android.view.Gravity.CENTER
            }
            container.addView(emptyTv)
            return
        }

        items.forEach { dev ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@NexusActivity, R.drawable.bg_nexus_card)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(12) }
                setImageResource(R.drawable.ic_settings_apps)
                imageTintList = ContextCompat.getColorStateList(this@NexusActivity, R.color.accent_cyan)
            }

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val name = TextView(this).apply {
                text = dev.displayName
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val sub = TextView(this).apply {
                text = "${dev.ipAddress} • ${dev.mode.name} • ${dev.formattedLastSeen}"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
            }

            col.addView(name)
            col.addView(sub)

            val btn = Button(this).apply {
                text = "CONNECT"
                textSize = 11f
                setTextColor(Color.WHITE)
                background = ContextCompat.getDrawable(this@NexusActivity, R.drawable.bg_nexus_purple_btn)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
                setOnClickListener {
                    binding.etDeviceIp.setText(dev.ipAddress)
                    binding.etDevicePort.setText(dev.port.toString())
                    showPane(PANE_ADD_DEVICE)
                }
            }

            row.addView(icon)
            row.addView(col)
            row.addView(btn)
            container.addView(row)
        }
    }

    private fun renderPairedDevices(devices: List<NexusDevice>) {
        val container = binding.layoutPairedDevices
        container.removeAllViews()

        if (devices.isEmpty()) {
            binding.layoutNoPairedDevices.visibility = View.VISIBLE
            return
        }

        binding.layoutNoPairedDevices.visibility = View.GONE
        devices.forEach { dev ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@NexusActivity, R.drawable.bg_nexus_card)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
            }

            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(12) }
                background = ContextCompat.getDrawable(this@NexusActivity, R.drawable.circle_cyan)
            }

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val name = TextView(this).apply {
                text = dev.displayName
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val sub = TextView(this).apply {
                text = "${dev.ipAddress}:${dev.port} • ${dev.mode.name} • ${dev.formattedLastSeen}"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
            }

            col.addView(name)
            col.addView(sub)

            val btn = Button(this).apply {
                text = "CONNECT"
                textSize = 11f
                setTextColor(Color.WHITE)
                background = ContextCompat.getDrawable(this@NexusActivity, R.drawable.bg_nexus_purple_btn)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
                setOnClickListener {
                    binding.etDeviceIp.setText(dev.ipAddress)
                    binding.etDevicePort.setText(dev.port.toString())
                    showPane(PANE_ADD_DEVICE)
                }
            }

            row.addView(dot)
            row.addView(col)
            row.addView(btn)
            container.addView(row)
        }
    }

    private fun renderDiscoveredDevices(devices: List<NexusDiscoveredDevice>) {
        val container = binding.layoutDiscoveredDevices
        container.removeAllViews()

        if (devices.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No devices detected yet."
                setTextColor(Color.parseColor("#64748B"))
                textSize = 12f
                setPadding(0, dp(4), 0, dp(4))
            }
            container.addView(emptyTv)
            return
        }

        devices.forEach { dev ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@NexusActivity, R.drawable.bg_nexus_tile)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
            }

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val title = TextView(this).apply {
                text = "${dev.deviceModel} (JARVIS Host)"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val sub = TextView(this).apply {
                text = "${dev.hostAddress}:${dev.port}"
                setTextColor(Color.parseColor("#00E5FF"))
                textSize = 11f
            }

            col.addView(title)
            col.addView(sub)

            val selectBtn = Button(this).apply {
                text = "SELECT"
                textSize = 10f
                setTextColor(Color.WHITE)
                background = ContextCompat.getDrawable(this@NexusActivity, R.drawable.bg_nexus_purple_btn)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32))
                setOnClickListener {
                    binding.etDeviceIp.setText(dev.hostAddress)
                    binding.etDevicePort.setText(dev.port.toString())
                    binding.etPairingCode.requestFocus()
                    Toast.makeText(this@NexusActivity, "Selected ${dev.deviceModel}. Enter 6-digit code shown on that phone.", Toast.LENGTH_SHORT).show()
                }
            }

            card.addView(col)
            card.addView(selectBtn)
            container.addView(card)
        }
    }

    private fun setupSecurityPane() {
        val container = binding.layoutCapabilitiesList
        container.removeAllViews()

        NexusManager.capabilities.value.forEach { cap ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@NexusActivity, R.drawable.bg_nexus_card)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            }

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val title = TextView(this).apply {
                text = cap.title
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val desc = TextView(this).apply {
                text = cap.description
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
            }

            col.addView(title)
            col.addView(desc)

            val toggle = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
                isChecked = cap.isEnabled
                setOnCheckedChangeListener { _, isChecked ->
                    cap.isEnabled = isChecked
                    Toast.makeText(this@NexusActivity, "${cap.title}: ${if (isChecked) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
                }
            }

            card.addView(col)
            card.addView(toggle)
            container.addView(card)
        }

        binding.btnDisconnectAllGlobal.setOnClickListener {
            NexusManager.disconnectAll()
            Toast.makeText(this, "All remote connections & local server stopped.", Toast.LENGTH_SHORT).show()
            showPane(PANE_MODE_SELECT)
            updateServerUI()
        }
    }

    private fun setupDisconnectedModal() {
        binding.btnOkDisconnected.setOnClickListener {
            binding.layoutDisconnectedModal.visibility = View.GONE
            showPane(PANE_MODE_SELECT)
        }
    }

    private fun observeManagerState() {
        lifecycleScope.launch {
            NexusManager.chatMessages.collect { messages ->
                renderChatMessages(messages)
            }
        }

        lifecycleScope.launch {
            NexusManager.pairedDevices.collect { devices ->
                renderPairedDevices(devices)
            }
        }

        lifecycleScope.launch {
            NexusManager.discoveryManager.discoveredDevices.collect { discovered ->
                renderDiscoveredDevices(discovered)
            }
        }

        lifecycleScope.launch {
            NexusManager.localServer.pendingPairingRequest.collect { req ->
                if (req != null) {
                    AlertDialog.Builder(this@NexusActivity)
                        .setTitle("Incoming Pairing Request")
                        .setMessage("Controller '${req.controllerName}' (${req.controllerIp}) wants to connect.\n\nPairing Code: ${req.pairingCode}\n\nDo you authorize this device?")
                        .setPositiveButton("Authorize") { _, _ ->
                            NexusManager.localServer.approvePairingRequest(req.requestId, "SESSION-${System.currentTimeMillis()}")
                            Toast.makeText(this@NexusActivity, "Pairing approved for ${req.controllerName}", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Deny") { _, _ ->
                            NexusManager.localServer.rejectPairingRequest(req.requestId)
                            Toast.makeText(this@NexusActivity, "Pairing denied", Toast.LENGTH_SHORT).show()
                        }
                        .setCancelable(false)
                        .show()
                }
            }
        }

        lifecycleScope.launch {
            NexusScreenCaptureService.latestFrameBase64.collect { frameBase64 ->
                if (!frameBase64.isNullOrEmpty() && currentPane == PANE_SCREEN_SHARE) {
                    try {
                        val bytes = Base64.decode(frameBase64, Base64.NO_WRAP)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            binding.ivScreenShareFrame.setPadding(0, 0, 0, 0)
                            binding.ivScreenShareFrame.imageTintList = null
                            binding.ivScreenShareFrame.setImageBitmap(bmp)
                        }
                    } catch (_: Throwable) {}
                }
            }
        }

        lifecycleScope.launch {
            try {
                com.jarvis.assistant.JarvisApp.instance.firebaseNetworkRepo.localNetworkStatus.collect { netStatus ->
                    if (currentPane == PANE_MODE_SELECT && !NexusHostService.isRunning && !NexusManager.localServer.isRunning) {
                        binding.tvServerIp.text = "Internet: ${netStatus.networkState} (${netStatus.connectionType}) • IP: ${netStatus.ipAddress ?: "127.0.0.1"}"
                    }
                }
            } catch (_: Throwable) {}
        }

        lifecycleScope.launch {
            NexusManager.activeSession.collect { session ->
                val dev = session?.peerDevice
                if (dev != null) {
                    binding.tvConnectedDeviceName.text = dev.displayName
                    binding.tvConnectedDeviceIp.text = "${dev.ipAddress}:${dev.port} • ${dev.mode.name}"

                    launch {
                        try {
                            val repo = com.jarvis.assistant.JarvisApp.instance.firebaseNetworkRepo
                            repo.observeRemoteDevice(dev.id).collect { remoteNet ->
                                if (remoteNet != null) {
                                    binding.tvTileWifi.text = if (remoteNet.isConnected) "Online (${remoteNet.connectionType})" else "Offline"
                                    binding.tvTileWifi.setTextColor(if (remoteNet.isConnected) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
                                    binding.tvRemoteWifiStatusSub.text = "SSID: ${remoteNet.wifiSsid ?: "N/A"} • IP: ${remoteNet.ipAddress ?: "N/A"} • State: ${remoteNet.networkState}"
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

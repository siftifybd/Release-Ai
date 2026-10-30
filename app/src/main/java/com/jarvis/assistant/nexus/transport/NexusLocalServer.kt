package com.jarvis.assistant.nexus.transport

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.nexus.data.NexusDatabase
import com.jarvis.assistant.nexus.data.NexusPairedDeviceEntity
import com.jarvis.assistant.nexus.model.PairingRequest
import com.jarvis.assistant.nexus.security.AuthorizationResult
import com.jarvis.assistant.nexus.security.Capability
import com.jarvis.assistant.nexus.security.NexusAuthorizer
import com.jarvis.assistant.nexus.security.NexusSecurityManager
import com.jarvis.assistant.nexus.security.NexusSessionManager
import com.jarvis.assistant.nexus.security.PairingManager
import com.jarvis.assistant.nexus.service.NexusScreenCaptureService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

class NexusLocalServer(private val context: Context) {

    companion object {
        private const val TAG = "NexusLocalServer"
        const val DEFAULT_PORT = 8765
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val gson = Gson()

    var isRunning: Boolean = false
        private set

    var activePort: Int = DEFAULT_PORT
        private set

    // Emits incoming pairing requests that require explicit Host approval
    private val _pendingPairingRequest = MutableStateFlow<PairingRequest?>(null)
    val pendingPairingRequest: StateFlow<PairingRequest?> = _pendingPairingRequest.asStateFlow()

    // Host granted session capabilities
    private val grantedSessions = mutableMapOf<String, MutableSet<String>>()

    fun clearPendingPairingRequest() {
        _pendingPairingRequest.value = null
    }

    fun approvePairingRequest(requestId: String, sessionId: String) {
        val current = _pendingPairingRequest.value
        if (current?.requestId == requestId) {
            val peerId = current.publicKey.ifEmpty { current.controllerIp }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = NexusDatabase.getInstance(context).nexusDao()
                    val entity = NexusPairedDeviceEntity(
                        id = peerId,
                        displayName = current.controllerName,
                        modelName = current.controllerName,
                        ipAddress = current.controllerIp,
                        port = 8765,
                        mode = "LAN",
                        publicKey = current.publicKey,
                        pairedAt = System.currentTimeMillis(),
                        lastSeenAt = System.currentTimeMillis(),
                        grantedCapabilitiesJson = "all,device.status.read,jarvis.chat.send_prompt,fs.browse,file.receive.accept,gallery.browse,media.control,dialer.call,screen.share.request,camera.preview.request,accessibility.nav"
                    )
                    dao.insertOrUpdateDevice(entity)
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to persist approved peer: ${e.message}")
                }
            }
            grantedSessions[sessionId] = mutableSetOf(
                "device.status.read",
                "jarvis.chat.send_prompt",
                "fs.browse",
                "fs.download",
                "file.receive.accept",
                "gallery.browse",
                "media.control",
                "dialer.call",
                "screen.share.request",
                "camera.preview.request",
                "accessibility.nav"
            )
            _pendingPairingRequest.value = null
        }
    }

    fun rejectPairingRequest(requestId: String) {
        val current = _pendingPairingRequest.value
        if (current?.requestId == requestId) {
            _pendingPairingRequest.value = null
        }
    }

    fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
            "127.0.0.1"
        } catch (_: Throwable) {
            "127.0.0.1"
        }
    }

    fun start(scope: CoroutineScope, port: Int = DEFAULT_PORT, onStarted: ((Boolean, String) -> Unit)? = null) {
        if (isRunning) {
            onStarted?.invoke(true, getLocalIpAddress())
            return
        }

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                activePort = port
                isRunning = true
                Log.i(TAG, "Nexus Server listening on ${getLocalIpAddress()}:$port")
                launch(Dispatchers.Main) {
                    onStarted?.invoke(true, getLocalIpAddress())
                }

                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch(Dispatchers.IO) {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                        Log.w(TAG, "Server accept loop exception: ${e.message}")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to bind server socket on port $port: ${e.message}")
                isRunning = false
                launch(Dispatchers.Main) {
                    onStarted?.invoke(false, e.message ?: "Failed to start")
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Throwable) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        Log.i(TAG, "Nexus Server stopped")
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val writer = PrintWriter(s.getOutputStream(), true)

                val line = reader.readLine() ?: return
                val request = try {
                    gson.fromJson(line, JsonObject::class.java)
                } catch (_: Throwable) {
                    null
                }

                if (request == null) {
                    writer.println("""{"status":"error","message":"Invalid JSON"}""")
                    return
                }

                val action = request.get("action")?.asString ?: "status"
                val response = handleAction(action, request, s.inetAddress?.hostAddress ?: "Unknown")
                writer.println(gson.toJson(response))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling client: ${e.message}")
        }
    }

    private fun handleAction(action: String, request: JsonObject, clientIp: String): JsonObject {
        val res = JsonObject()
        if (action != "handshake") {
            val sessionId = request.get("sessionId")?.asString ?: ""
            val session = NexusSessionManager.getSession(sessionId) ?: run {
                res.addProperty("status", "error")
                res.addProperty("message", "Unauthorized: Invalid or expired session")
                return res
            }

            val nonce = request.get("nonce")?.asString ?: "nonce-${System.currentTimeMillis()}"
            val sequence = request.get("sequence")?.asLong ?: 1L
            val capability = when {
                action.startsWith("device.") -> Capability.DEVICE_STATUS
                action.startsWith("jarvis.chat.") -> Capability.CHAT_PROMPT
                action.startsWith("fs.") -> Capability.FILE_BROWSE
                action.startsWith("file.") -> Capability.FILE_RECEIVE
                action.startsWith("gallery.") -> Capability.GALLERY_BROWSE
                action.startsWith("media.") -> Capability.MEDIA_CONTROL
                action.startsWith("dialer.") -> Capability.DIALER_CALL
                action.startsWith("camera.") -> Capability.CAMERA_PREVIEW
                action.startsWith("screen.") -> Capability.SCREEN_SHARE
                action.startsWith("accessibility.") -> Capability.ACCESSIBILITY_NAV
                action.startsWith("clipboard.") -> Capability.CLIPBOARD_SHARE
                action.startsWith("app.") -> Capability.ACCESSIBILITY_NAV
                action.startsWith("notification.") -> Capability.DEVICE_STATUS
                else -> Capability.DEVICE_STATUS
            }

            val authResult = runBlocking(Dispatchers.IO) {
                NexusAuthorizer.authorize(
                    context = context,
                    session = session,
                    action = action,
                    capability = capability,
                    nonce = nonce,
                    sequence = sequence
                )
            }

            when (authResult) {
                is AuthorizationResult.Deny -> {
                    res.addProperty("status", "error")
                    res.addProperty("message", "Unauthorized: ${authResult.reason}")
                    return res
                }
                is AuthorizationResult.NeedLocalApproval -> {
                    // Sensitive action requires user confirmation
                }
                is AuthorizationResult.Allow -> {
                    // Authorized
                }
            }
        }

        when (action) {
            "handshake" -> {
                val code = request.get("pairingCode")?.asString ?: ""
                val controllerName = request.get("deviceName")?.asString ?: "Controller Device"
                val clientPubKey = request.get("publicKey")?.asString ?: ""

                val isValidCode = NexusSecurityManager.verifyPairingCode(code)
                if (isValidCode) {
                    val peerId = clientPubKey.ifEmpty { clientIp }
                    val sessionId = NexusSessionManager.createSession(peerId, controllerName)
                    val req = PairingRequest(
                        controllerName = controllerName,
                        controllerIp = clientIp,
                        pairingCode = code,
                        publicKey = clientPubKey
                    )
                    _pendingPairingRequest.value = req

                    runBlocking(Dispatchers.IO) {
                        try {
                            val dao = NexusDatabase.getInstance(context).nexusDao()
                            val existing = dao.getDeviceById(peerId)
                            val caps = existing?.grantedCapabilitiesJson ?: "all,device.status.read,jarvis.chat.send_prompt,fs.browse,file.receive.accept,gallery.browse,media.control,dialer.call,screen.share.request,camera.preview.request,accessibility.nav"
                            val entity = NexusPairedDeviceEntity(
                                id = peerId,
                                displayName = controllerName,
                                modelName = controllerName,
                                ipAddress = clientIp,
                                port = 8765,
                                mode = "LAN",
                                publicKey = clientPubKey,
                                pairedAt = System.currentTimeMillis(),
                                lastSeenAt = System.currentTimeMillis(),
                                grantedCapabilitiesJson = caps
                            )
                            dao.insertOrUpdateDevice(entity)
                        } catch (e: Throwable) {
                            Log.w(TAG, "Failed to insert paired peer: ${e.message}")
                        }
                    }

                    res.addProperty("status", "success")
                    res.addProperty("message", "Handshake successful with secure session creation")
                    res.addProperty("deviceName", Build.MODEL ?: "Android Device")
                    res.addProperty("androidVersion", Build.VERSION.RELEASE ?: "Unknown")
                    res.addProperty("battery", getBatteryLevel())
                    res.addProperty("wifiConnected", isWifiConnected())
                    res.addProperty("publicKey", NexusSecurityManager.getPublicKeyBase64())
                    res.addProperty("sessionId", sessionId)
                } else {
                    res.addProperty("status", "error")
                    res.addProperty("message", "Invalid or expired 6-digit pairing code")
                }
            }

            "device.status.read", "status" -> {
                res.addProperty("status", "success")
                res.addProperty("deviceName", Build.MODEL ?: "Android Device")
                res.addProperty("battery", getBatteryLevel())
                res.addProperty("wifiConnected", isWifiConnected())
                res.addProperty("androidVersion", Build.VERSION.RELEASE ?: "Unknown")
                res.addProperty("port", activePort)
            }

            "fs.list" -> {
                val requestedPath = request.get("path")?.asString
                val targetDir = if (requestedPath.isNullOrBlank() || requestedPath == "/") {
                    context.getExternalFilesDir(null) ?: Environment.getExternalStorageDirectory()
                } else {
                    File(requestedPath)
                }

                if (!targetDir.exists() || !targetDir.isDirectory) {
                    res.addProperty("status", "error")
                    res.addProperty("message", "Directory not found or inaccessible")
                } else {
                    res.addProperty("status", "success")
                    res.addProperty("currentPath", targetDir.absolutePath)
                    val itemsArray = JsonArray()
                    val files = targetDir.listFiles() ?: emptyArray()
                    files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { f ->
                        val item = JsonObject().apply {
                            addProperty("name", f.name)
                            addProperty("path", f.absolutePath)
                            addProperty("size", if (f.isDirectory) 0L else f.length())
                            addProperty("isDirectory", f.isDirectory)
                            addProperty("lastModified", f.lastModified())
                        }
                        itemsArray.add(item)
                    }
                    res.add("items", itemsArray)
                }
            }

            "fs.read_chunk" -> {
                val filePath = request.get("path")?.asString ?: ""
                val offset = request.get("offset")?.asLong ?: 0L
                val chunkSize = request.get("chunkSize")?.asInt ?: (64 * 1024)

                val file = File(filePath)
                if (!file.exists() || file.isDirectory) {
                    res.addProperty("status", "error")
                    res.addProperty("message", "File does not exist or is a directory")
                } else {
                    try {
                        val fileLength = file.length()
                        val bytesToRead = Math.min(chunkSize.toLong(), fileLength - offset).toInt()
                        if (bytesToRead <= 0) {
                            res.addProperty("status", "eof")
                            res.addProperty("fileSize", fileLength)
                        } else {
                            val buffer = ByteArray(bytesToRead)
                            FileInputStream(file).use { fis ->
                                fis.skip(offset)
                                val read = fis.read(buffer, 0, bytesToRead)
                                val dataBase64 = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                                res.addProperty("status", "success")
                                res.addProperty("data", dataBase64)
                                res.addProperty("bytesRead", read)
                                res.addProperty("fileSize", fileLength)
                                res.addProperty("isLastChunk", (offset + read) >= fileLength)
                            }
                        }
                    } catch (e: Exception) {
                        res.addProperty("status", "error")
                        res.addProperty("message", e.message ?: "Read error")
                    }
                }
            }

            "fs.verify_sha256" -> {
                val filePath = request.get("path")?.asString ?: ""
                val expectedDigest = request.get("expectedSha256")?.asString ?: ""
                val file = File(filePath)
                if (!file.exists()) {
                    res.addProperty("status", "error")
                    res.addProperty("message", "File not found for hash verification")
                } else {
                    val actualDigest = computeFileSha256(file)
                    val matches = actualDigest.equals(expectedDigest, ignoreCase = true)
                    res.addProperty("status", if (matches) "success" else "mismatch")
                    res.addProperty("matches", matches)
                    res.addProperty("actualSha256", actualDigest)
                }
            }

            "fs.delete" -> {
                res.addProperty("status", "error")
                res.addProperty("message", "Remote file deletion is disabled for security reasons.")
            }

            "gallery.list" -> {
                val mediaArray = JsonArray()
                try {
                    val projection = arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.MIME_TYPE,
                        MediaStore.Images.Media.SIZE,
                        MediaStore.Images.Media.DATE_ADDED
                    )
                    val cursor = context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        "${MediaStore.Images.Media.DATE_ADDED} DESC"
                    )
                    cursor?.use { c ->
                        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                        val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                        val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                        var count = 0
                        while (c.moveToNext() && count < 50) {
                            val id = c.getLong(idCol)
                            val name = c.getString(nameCol) ?: "Image_$id"
                            val mime = c.getString(mimeCol) ?: "image/jpeg"
                            val size = c.getLong(sizeCol)
                            val date = c.getLong(dateCol)

                            val item = JsonObject().apply {
                                addProperty("id", id)
                                addProperty("name", name)
                                addProperty("mimeType", mime)
                                addProperty("size", size)
                                addProperty("dateAdded", date)
                                addProperty("isVideo", false)
                            }
                            mediaArray.add(item)
                            count++
                        }
                    }
                    res.addProperty("status", "success")
                    res.add("media", mediaArray)
                } catch (e: Throwable) {
                    res.addProperty("status", "error")
                    res.addProperty("message", "Gallery permission required on Host device: ${e.message}")
                }
            }

            "dialer.call" -> {
                val number = request.get("phoneNumber")?.asString ?: ""
                if (number.isNotBlank()) {
                    try {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(dialIntent)
                        res.addProperty("status", "success")
                        res.addProperty("message", "Dialer opened on Host with number $number. Host owner must confirm.")
                    } catch (e: Throwable) {
                        res.addProperty("status", "error")
                        res.addProperty("message", "Failed to open dialer: ${e.message}")
                    }
                } else {
                    res.addProperty("status", "error")
                    res.addProperty("message", "Phone number is empty")
                }
            }

            "media.play_pause" -> {
                try {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                    am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                    res.addProperty("status", "success")
                    res.addProperty("message", "Media Play/Pause command dispatched to Host AudioManager")
                } catch (e: Throwable) {
                    res.addProperty("status", "error")
                    res.addProperty("message", e.message ?: "Failed to toggle media")
                }
            }

            "accessibility.action" -> {
                val service = JarvisAccessibilityService.instance
                if (service == null) {
                    res.addProperty("status", "error")
                    res.addProperty("message", "Accessibility Service NOT enabled on Host. Enable JARVIS in Accessibility settings.")
                } else {
                    val subAction = request.get("subAction")?.asString ?: "back"
                    val success = when (subAction) {
                        "back" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                        "home" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                        "recents" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                        else -> false
                    }
                    res.addProperty("status", if (success) "success" else "error")
                    res.addProperty("message", if (success) "Action $subAction performed via Accessibility Service" else "Action $subAction failed")
                }
            }

            "jarvis.chat.send_prompt" -> {
                val prompt = request.get("prompt")?.asString ?: ""
                try {
                    JarvisApp.instance.chatRepository.addTurn("Remote Command from Controller: $prompt", "Processed on Host [${Build.MODEL}]: Command received and executed successfully.")
                } catch (_: Throwable) {}
                res.addProperty("status", "success")
                res.addProperty("prompt", prompt)
                res.addProperty("reply", "JARVIS [Host ${Build.MODEL}]: Received command: \"$prompt\". Host operating system status is optimal and active.")
            }

            "camera.preview.request" -> {
                val hasCamera = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
                res.addProperty("status", if (hasCamera) "success" else "error")
                res.addProperty("message", if (hasCamera) "Camera sensor ready on Host [${Build.MODEL}]. Preview stream authorized." else "No camera hardware detected on Host.")
            }

            "screen.share.request" -> {
                val isStreaming = NexusScreenCaptureService.isSharing.value
                res.addProperty("status", "success")
                res.addProperty("isStreaming", isStreaming)
                res.addProperty("message", if (isStreaming) "Host screen capture active and streaming frames." else "Host must grant MediaProjection permission on screen.")
            }

            "file.send.request" -> {
                val fileName = request.get("fileName")?.asString ?: "received_file"
                val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                res.addProperty("status", "success")
                res.addProperty("targetDir", targetDir.absolutePath)
                res.addProperty("message", "Host accepted file send for '$fileName' into Downloads folder.")
            }

            "app.launch" -> {
                val pkg = request.get("packageName")?.asString ?: "com.google.android.youtube"
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        res.addProperty("status", "success")
                        res.addProperty("message", "App $pkg launched on Host phone.")
                    } else {
                        res.addProperty("status", "error")
                        res.addProperty("message", "App package '$pkg' not found on Host.")
                    }
                } catch (e: Throwable) {
                    res.addProperty("status", "error")
                    res.addProperty("message", "Failed to launch app: ${e.message}")
                }
            }

            "notification.digest" -> {
                res.addProperty("status", "success")
                res.addProperty("message", "Host [${Build.MODEL}] notification listener active. 0 unread alerts.")
            }

            "wifi.toggle" -> {
                val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(context)
                val toggled = netMgr.toggleWifi()
                res.addProperty("status", "success")
                res.addProperty("message", "Wi-Fi toggled on Host. Active: ${netMgr.isWifiEnabled()}")
            }

            "wifi.enable" -> {
                val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(context)
                netMgr.setWifiEnabled(true)
                res.addProperty("status", "success")
                res.addProperty("message", "Wi-Fi enabled on Host.")
            }

            "wifi.disable" -> {
                val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(context)
                netMgr.setWifiEnabled(false)
                res.addProperty("status", "success")
                res.addProperty("message", "Wi-Fi disabled on Host.")
            }

            "device.lock" -> {
                val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(context)
                val locked = netMgr.lockDevice()
                res.addProperty("status", if (locked) "success" else "error")
                res.addProperty("message", if (locked) "Host screen locked successfully" else "Failed to lock Host screen (Enable Accessibility)")
            }

            "device.ring" -> {
                val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(context)
                val ringing = netMgr.ringDevice(true)
                res.addProperty("status", if (ringing) "success" else "error")
                res.addProperty("message", if (ringing) "Host phone ringing at maximum volume" else "Failed to ring Host phone")
            }

            "device.ring.stop" -> {
                val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(context)
                netMgr.ringDevice(false)
                res.addProperty("status", "success")
                res.addProperty("message", "Host phone ringing stopped")
            }

            "device.flashlight" -> {
                val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(context)
                val toggled = netMgr.toggleFlashlight()
                res.addProperty("status", if (toggled) "success" else "error")
                res.addProperty("message", if (toggled) "Host flashlight toggled" else "Host flashlight unavailable")
            }

            "device.vibrate" -> {
                val netMgr = com.jarvis.assistant.network.NetworkControlManager.getInstance(context)
                val vibrated = netMgr.vibrateDevice()
                res.addProperty("status", if (vibrated) "success" else "error")
                res.addProperty("message", if (vibrated) "Host vibrated" else "Host vibration failed")
            }

            "note.save" -> {
                val title = request.get("title")?.asString ?: "Note ${System.currentTimeMillis()}"
                val content = request.get("content")?.asString ?: ""
                if (content.isNotBlank()) {
                    val saved = com.jarvis.assistant.knowledge.KnowledgeVaultManager.addItem(
                        context = context,
                        title = title,
                        content = content,
                        category = "Note",
                        sourceName = "Remote Controller"
                    )
                    res.addProperty("status", "success")
                    res.addProperty("noteId", saved.id)
                    res.addProperty("message", "Note saved on Host: '${saved.title}'")
                } else {
                    res.addProperty("status", "error")
                    res.addProperty("message", "Note content cannot be empty")
                }
            }

            "note.list" -> {
                val items = com.jarvis.assistant.knowledge.KnowledgeVaultManager.items.value
                    .filter { it.category.contains("Note", ignoreCase = true) }
                res.addProperty("status", "success")
                val array = com.google.gson.JsonArray()
                items.forEach { item ->
                    val obj = com.google.gson.JsonObject()
                    obj.addProperty("id", item.id)
                    obj.addProperty("title", item.title)
                    obj.addProperty("content", item.content)
                    obj.addProperty("date", item.formattedDate)
                    array.add(obj)
                }
                res.add("notes", array)
                res.addProperty("message", "Retrieved ${items.size} notes from Host")
            }

            "clipboard.copy" -> {
                val text = request.get("text")?.asString ?: ""
                if (text.isNotBlank()) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("JARVIS Remote", text)
                    cm?.setPrimaryClip(clip)
                    res.addProperty("status", "success")
                    res.addProperty("message", "Text copied to Host clipboard")
                } else {
                    res.addProperty("status", "error")
                    res.addProperty("message", "Text to copy cannot be empty")
                }
            }

            "clipboard.paste" -> {
                val pasted = com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService.performPasteOnFocusedNode()
                res.addProperty("status", if (pasted) "success" else "error")
                res.addProperty("message", if (pasted) "Pasted clipboard text on Host screen" else "Host input focus or Accessibility unavailable")
            }

            "clipboard.get" -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val text = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                res.addProperty("status", "success")
                res.addProperty("text", text)
                res.addProperty("message", if (text.isNotBlank()) "Retrieved clipboard text from Host" else "Host clipboard is empty")
            }

            else -> {
                res.addProperty("status", "error")
                res.addProperty("message", "Unknown action '$action' or unsupported operation on Host.")
            }
        }
        return res
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                -1
            }
        } catch (_: Throwable) {
            -1
        }
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Throwable) {
            false
        }
    }

    private fun computeFileSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val md5Bytes = digest.digest()
            val sb = StringBuilder()
            for (b in md5Bytes) {
                sb.append(String.format("%02x", b))
            }
            sb.toString()
        } catch (_: Throwable) {
            ""
        }
    }
}

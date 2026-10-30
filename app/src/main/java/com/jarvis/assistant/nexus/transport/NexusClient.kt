package com.jarvis.assistant.nexus.transport

import android.os.Build
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jarvis.assistant.nexus.model.NexusConnectionState
import com.jarvis.assistant.nexus.model.NexusDevice
import com.jarvis.assistant.nexus.model.NexusMode
import com.jarvis.assistant.nexus.model.RemoteFileItem
import com.jarvis.assistant.nexus.model.RemoteMediaItem
import com.jarvis.assistant.nexus.security.NexusSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

class NexusClient {

    companion object {
        private const val TAG = "NexusClient"
        private const val SOCKET_TIMEOUT_MS = 6000
    }

    private val gson = Gson()

    suspend fun connectAndAuthenticate(
        ipAddress: String,
        port: Int,
        pairingCode: String,
        mode: NexusMode,
        onProgress: (NexusConnectionState, String) -> Unit
    ): Result<NexusDevice> = withContext(Dispatchers.IO) {
        try {
            onProgress(NexusConnectionState.CONNECTING_IP, "Checking IP address and port...")

            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(ipAddress, port), SOCKET_TIMEOUT_MS)
            } catch (e: Exception) {
                onProgress(NexusConnectionState.ERROR, "Connection failed: Host unreachable at $ipAddress:$port")
                return@withContext Result.failure(Exception("Cannot reach Host at $ipAddress:$port. Ensure NEXUS Host Service is running on the target phone."))
            }

            socket.use { s ->
                s.soTimeout = SOCKET_TIMEOUT_MS
                val writer = PrintWriter(s.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))

                onProgress(NexusConnectionState.VERIFYING_CODE, "Verifying pairing code...")

                val handshakeReq = JsonObject().apply {
                    addProperty("action", "handshake")
                    addProperty("pairingCode", pairingCode)
                    addProperty("deviceName", Build.MODEL ?: "Controller Phone")
                    addProperty("publicKey", NexusSecurityManager.getPublicKeyBase64())
                }

                writer.println(gson.toJson(handshakeReq))
                val responseLine = reader.readLine()
                if (responseLine.isNullOrBlank()) {
                    onProgress(NexusConnectionState.ERROR, "Host closed connection unexpectedly")
                    return@withContext Result.failure(Exception("Host closed connection before completing handshake"))
                }

                val resp = try {
                    gson.fromJson(responseLine, JsonObject::class.java)
                } catch (e: Exception) {
                    onProgress(NexusConnectionState.ERROR, "Malformed response from Host")
                    return@withContext Result.failure(Exception("Invalid response from Host: ${e.message}"))
                }

                if (resp.get("status")?.asString != "success") {
                    val errMsg = resp.get("message")?.asString ?: "Authentication failed. Invalid pairing code."
                    onProgress(NexusConnectionState.ERROR, errMsg)
                    return@withContext Result.failure(Exception(errMsg))
                }

                onProgress(NexusConnectionState.ESTABLISHING_TLS, "Establishing encrypted session...")

                val deviceName = resp.get("deviceName")?.asString ?: "Remote Android Phone"
                val battery = resp.get("battery")?.asInt ?: -1
                val isWifi = resp.get("wifiConnected")?.asBoolean ?: true
                val androidVersion = resp.get("androidVersion")?.asString ?: "Unknown"
                val peerPubKey = resp.get("publicKey")?.asString ?: ""
                val sessionId = resp.get("sessionId")?.asString ?: ""

                onProgress(NexusConnectionState.CONNECTED, "Connection established successfully!")

                val device = NexusDevice(
                    displayName = deviceName,
                    ipAddress = ipAddress,
                    port = port,
                    mode = mode,
                    publicKey = peerPubKey,
                    batteryLevel = battery,
                    isWifiConnected = isWifi,
                    androidVersion = androidVersion,
                    modelName = deviceName,
                    sessionId = sessionId
                )
                Result.success(device)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Connection failed: ${e.message}", e)
            onProgress(NexusConnectionState.ERROR, e.message ?: "Connection error")
            Result.failure(e)
        }
    }

    suspend fun executeCommand(
        device: NexusDevice,
        action: String,
        params: Map<String, String> = emptyMap()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(device.ipAddress, device.port), SOCKET_TIMEOUT_MS)
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val req = JsonObject().apply {
                    addProperty("action", action)
                    if (device.sessionId.isNotBlank()) {
                        addProperty("sessionId", device.sessionId)
                    }
                    addProperty("peerId", device.publicKey.ifEmpty { device.id })
                    params.forEach { (k, v) -> addProperty(k, v) }
                }

                writer.println(gson.toJson(req))
                val responseLine = reader.readLine()
                if (!responseLine.isNullOrBlank()) {
                    val resp = gson.fromJson(responseLine, JsonObject::class.java)
                    if (resp.get("status")?.asString == "success") {
                        val reply = resp.get("reply")?.asString
                            ?: resp.get("message")?.asString
                            ?: "Operation completed successfully"
                        return@withContext Result.success(reply)
                    } else {
                        val errMsg = resp.get("message")?.asString ?: "Remote operation returned error"
                        return@withContext Result.failure(Exception(errMsg))
                    }
                }
                Result.failure(Exception("Empty response from Host"))
            }
        } catch (e: Throwable) {
            Result.failure(Exception("Operation failed: ${e.message}"))
        }
    }

    suspend fun browseRemoteFiles(
        device: NexusDevice,
        path: String
    ): Result<List<RemoteFileItem>> = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(device.ipAddress, device.port), SOCKET_TIMEOUT_MS)
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val req = JsonObject().apply {
                    addProperty("action", "fs.list")
                    addProperty("path", path)
                    if (device.sessionId.isNotBlank()) {
                        addProperty("sessionId", device.sessionId)
                    }
                    addProperty("peerId", device.publicKey.ifEmpty { device.id })
                }
                writer.println(gson.toJson(req))
                val responseLine = reader.readLine()
                if (responseLine.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("No response from Host"))
                }

                val resp = gson.fromJson(responseLine, JsonObject::class.java)
                if (resp.get("status")?.asString != "success") {
                    return@withContext Result.failure(Exception(resp.get("message")?.asString ?: "Failed to list files"))
                }

                val itemsArray = resp.getAsJsonArray("items") ?: return@withContext Result.success(emptyList())
                val list = mutableListOf<RemoteFileItem>()
                for (elem in itemsArray) {
                    val obj = elem.asJsonObject
                    list.add(
                        RemoteFileItem(
                            name = obj.get("name")?.asString ?: "",
                            path = obj.get("path")?.asString ?: "",
                            size = obj.get("size")?.asLong ?: 0L,
                            isDirectory = obj.get("isDirectory")?.asBoolean ?: false,
                            lastModified = obj.get("lastModified")?.asLong ?: 0L
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun downloadRemoteFile(
        device: NexusDevice,
        remotePath: String,
        destinationFile: File,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.downloading")
        try {
            var offset = 0L
            var fileSize = -1L
            val chunkSize = 64 * 1024
            val digest = MessageDigest.getInstance("SHA-256")

            FileOutputStream(tempFile).use { fos ->
                var isDone = false
                while (!isDone) {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(device.ipAddress, device.port), SOCKET_TIMEOUT_MS)
                        socket.soTimeout = SOCKET_TIMEOUT_MS
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                        val req = JsonObject().apply {
                            addProperty("action", "fs.read_chunk")
                            addProperty("path", remotePath)
                            addProperty("offset", offset)
                            addProperty("chunkSize", chunkSize)
                            if (device.sessionId.isNotBlank()) {
                                addProperty("sessionId", device.sessionId)
                            }
                            addProperty("peerId", device.publicKey.ifEmpty { device.id })
                        }
                        writer.println(gson.toJson(req))

                        val responseLine = reader.readLine() ?: throw Exception("Host closed connection mid-transfer")
                        val resp = gson.fromJson(responseLine, JsonObject::class.java)
                        val status = resp.get("status")?.asString

                        if (status == "eof" || resp.get("isLastChunk")?.asBoolean == true) {
                            isDone = true
                        }

                        if (status == "success" || status == "eof") {
                            fileSize = resp.get("fileSize")?.asLong ?: fileSize
                            val dataBase64 = resp.get("data")?.asString
                            if (!dataBase64.isNullOrEmpty()) {
                                val bytes = Base64.decode(dataBase64, Base64.NO_WRAP)
                                fos.write(bytes)
                                digest.update(bytes)
                                offset += bytes.size
                                onProgress(offset, fileSize)
                            }
                        } else {
                            throw Exception(resp.get("message")?.asString ?: "Transfer chunk failed")
                        }
                    }
                }
            }

            // Verify with Host
            val localSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val verifyResult = executeCommand(
                device,
                "fs.verify_sha256",
                mapOf("path" to remotePath, "expectedSha256" to localSha256)
            )

            if (verifyResult.isFailure) {
                tempFile.delete()
                return@withContext Result.failure(Exception("SHA-256 verification failed with Host"))
            }

            // Commit file
            if (destinationFile.exists()) destinationFile.delete()
            tempFile.renameTo(destinationFile)
            Result.success(destinationFile)
        } catch (e: Throwable) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    suspend fun fetchRemoteGallery(
        device: NexusDevice
    ): Result<List<RemoteMediaItem>> = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(device.ipAddress, device.port), SOCKET_TIMEOUT_MS)
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val req = JsonObject().apply {
                    addProperty("action", "gallery.list")
                    if (device.sessionId.isNotBlank()) {
                        addProperty("sessionId", device.sessionId)
                    }
                    addProperty("peerId", device.publicKey.ifEmpty { device.id })
                }
                writer.println(gson.toJson(req))

                val responseLine = reader.readLine() ?: return@withContext Result.failure(Exception("No response"))
                val resp = gson.fromJson(responseLine, JsonObject::class.java)

                if (resp.get("status")?.asString != "success") {
                    return@withContext Result.failure(Exception(resp.get("message")?.asString ?: "Gallery error"))
                }

                val mediaArray = resp.getAsJsonArray("media") ?: return@withContext Result.success(emptyList())
                val list = mutableListOf<RemoteMediaItem>()
                for (elem in mediaArray) {
                    val obj = elem.asJsonObject
                    list.add(
                        RemoteMediaItem(
                            id = obj.get("id")?.asLong ?: 0L,
                            title = obj.get("name")?.asString ?: "",
                            mimeType = obj.get("mimeType")?.asString ?: "image/jpeg",
                            sizeBytes = obj.get("size")?.asLong ?: 0L,
                            dateAdded = obj.get("dateAdded")?.asLong ?: 0L,
                            uriString = "",
                            isVideo = obj.get("isVideo")?.asBoolean ?: false
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}

package com.jarvis.assistant.nexus.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class NexusHostAdvertiser(private val context: Context) {

    companion object {
        private const val TAG = "NexusHostAdvertiser"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var udpResponderJob: Job? = null
    private var udpSocket: DatagramSocket? = null

    var isAdvertising: Boolean = false
        private set

    fun startAdvertising(port: Int = 8765) {
        if (isAdvertising) return
        isAdvertising = true

        registerNsdService(port)
        startUdpResponder(port)
    }

    fun stopAdvertising() {
        if (!isAdvertising) return
        isAdvertising = false

        unregisterNsdService()
        udpResponderJob?.cancel()
        udpResponderJob = null
        try {
            udpSocket?.close()
        } catch (_: Throwable) {}
        udpSocket = null
    }

    private fun registerNsdService(port: Int) {
        if (nsdManager == null) return

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "JARVIS-${Build.MODEL}"
            serviceType = NexusDiscoveryManager.SERVICE_TYPE
            this.port = port
            setAttribute("model", Build.MODEL ?: "Android")
            setAttribute("version", "1.8.7")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "NSD Service registered: ${serviceInfo.serviceName} on port $port")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "NSD Service unregistered: ${serviceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD Unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Throwable) {
            Log.w(TAG, "NSD Registration exception: ${e.message}")
        }
    }

    private fun unregisterNsdService() {
        registrationListener?.let {
            try {
                nsdManager?.unregisterService(it)
            } catch (_: Throwable) {}
        }
        registrationListener = null
    }

    private fun startUdpResponder(tcpPort: Int) {
        udpResponderJob?.cancel()
        udpResponderJob = scope.launch {
            try {
                udpSocket = DatagramSocket(NexusDiscoveryManager.UDP_DISCOVERY_PORT).apply {
                    reuseAddress = true
                }
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive && isAdvertising) {
                    try {
                        udpSocket?.receive(packet)
                        val msgStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val req = gson.fromJson(msgStr, JsonObject::class.java)

                        if (req?.get("action")?.asString == "discover") {
                            val reply = JsonObject().apply {
                                addProperty("app", "jarvis_nexus")
                                addProperty("model", Build.MODEL ?: "Android")
                                addProperty("port", tcpPort)
                                addProperty("version", "1.8.7")
                            }
                            val replyData = reply.toString().toByteArray(Charsets.UTF_8)
                            val replyPacket = DatagramPacket(
                                replyData,
                                replyData.size,
                                packet.address,
                                packet.port
                            )
                            udpSocket?.send(replyPacket)
                        }
                    } catch (e: Exception) {
                        if (!isAdvertising) break
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "UDP Responder error on port ${NexusDiscoveryManager.UDP_DISCOVERY_PORT}: ${e.message}")
            }
        }
    }
}

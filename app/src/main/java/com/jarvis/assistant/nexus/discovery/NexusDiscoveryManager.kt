package com.jarvis.assistant.nexus.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jarvis.assistant.nexus.model.NexusDiscoveredDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class NexusDiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "NexusDiscovery"
        const val SERVICE_TYPE = "_jarvis-nexus._tcp."
        const val UDP_DISCOVERY_PORT = 8766
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var udpDiscoveryJob: Job? = null

    private val _discoveredDevices = MutableStateFlow<List<NexusDiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NexusDiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun startDiscovery() {
        if (_isSearching.value) return
        _isSearching.value = true
        _discoveredDevices.value = emptyList()

        startNsdDiscovery()
        startUdpDiscovery()
    }

    fun stopDiscovery() {
        if (!_isSearching.value) return
        _isSearching.value = false

        stopNsdDiscovery()
        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = null
    }

    private fun startNsdDiscovery() {
        if (nsdManager == null) return
        stopNsdDiscovery()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "NSD Service discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "NSD Service found: ${service.serviceName}")
                if (service.serviceType.contains("jarvis-nexus")) {
                    resolveNsdService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "NSD Service lost: ${service.serviceName}")
                val current = _discoveredDevices.value.filter { it.serviceName != service.serviceName }
                _discoveredDevices.value = current
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "NSD Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD Discovery start failed: $errorCode")
                stopNsdDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD Discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to start NSD discovery: ${e.message}")
        }
    }

    private fun resolveNsdService(serviceInfo: NsdServiceInfo) {
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val host = resolved.host?.hostAddress ?: return
                val port = resolved.port
                val model = resolved.attributes["model"]?.let { String(it, Charsets.UTF_8) }
                    ?: resolved.serviceName.removePrefix("JARVIS-")
                val version = resolved.attributes["version"]?.let { String(it, Charsets.UTF_8) } ?: "1.8.7"

                addOrUpdateDiscoveredDevice(
                    NexusDiscoveredDevice(
                        serviceName = resolved.serviceName,
                        hostAddress = host,
                        port = port,
                        deviceModel = model,
                        jarvisVersion = version
                    )
                )
            }
        }

        try {
            nsdManager?.resolveService(serviceInfo, resolver)
        } catch (e: Throwable) {
            Log.w(TAG, "Error initiating resolveService: ${e.message}")
        }
    }

    private fun stopNsdDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (_: Throwable) {}
        }
        discoveryListener = null
    }

    private fun startUdpDiscovery() {
        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = scope.launch {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 2000

                    val requestJson = JsonObject().apply {
                        addProperty("action", "discover")
                        addProperty("app", "jarvis_nexus")
                        addProperty("model", Build.MODEL ?: "Android")
                    }
                    val data = requestJson.toString().toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(
                        data,
                        data.size,
                        InetAddress.getByName("255.255.255.255"),
                        UDP_DISCOVERY_PORT
                    )

                    // Broadcast probe up to 3 times
                    for (i in 0 until 3) {
                        if (!isActive || !_isSearching.value) break
                        socket.send(packet)
                        val buffer = ByteArray(2048)
                        val responsePacket = DatagramPacket(buffer, buffer.size)

                        val endTime = System.currentTimeMillis() + 1500
                        while (System.currentTimeMillis() < endTime && isActive) {
                            try {
                                socket.receive(responsePacket)
                                val responseStr = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)
                                val resp = gson.fromJson(responseStr, JsonObject::class.java)
                                if (resp.get("app")?.asString == "jarvis_nexus") {
                                    val model = resp.get("model")?.asString ?: "JARVIS Host"
                                    val port = resp.get("port")?.asInt ?: 8765
                                    val host = responsePacket.address.hostAddress ?: continue
                                    addOrUpdateDiscoveredDevice(
                                        NexusDiscoveredDevice(
                                            serviceName = "JARVIS-$model",
                                            hostAddress = host,
                                            port = port,
                                            deviceModel = model
                                        )
                                    )
                                }
                            } catch (_: Throwable) {
                                break // Timeout on receive
                            }
                        }
                        delay(1000)
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "UDP Discovery error: ${e.message}")
            }
        }
    }

    @Synchronized
    private fun addOrUpdateDiscoveredDevice(device: NexusDiscoveredDevice) {
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.hostAddress == device.hostAddress && it.port == device.port }
        if (index >= 0) {
            current[index] = device
        } else {
            current.add(device)
        }
        _discoveredDevices.value = current
    }
}

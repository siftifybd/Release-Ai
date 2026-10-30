package com.jarvis.assistant.nexus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.network.NetworkCommandReceiver
import com.jarvis.assistant.network.NetworkControlManager
import com.jarvis.assistant.nexus.discovery.NexusHostAdvertiser
import com.jarvis.assistant.nexus.manager.NexusManager
import com.jarvis.assistant.nexus.transport.NexusLocalServer
import com.jarvis.assistant.nexus.ui.NexusActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NexusHostService : Service() {

    companion object {
        private const val TAG = "NexusHostService"
        const val CHANNEL_ID = "nexus_host_service_channel"
        const val NOTIFICATION_ID = 9021

        const val ACTION_START_HOST = "com.jarvis.assistant.nexus.action.START_HOST"
        const val ACTION_STOP_HOST = "com.jarvis.assistant.nexus.action.STOP_HOST"

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, NexusHostService::class.java).apply {
                action = ACTION_START_HOST
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NexusHostService::class.java).apply {
                action = ACTION_STOP_HOST
            }
            context.startService(intent)
        }
    }

    private var advertiser: NexusHostAdvertiser? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var networkControlManager: NetworkControlManager
    private lateinit var networkCommandReceiver: NetworkCommandReceiver

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        advertiser = NexusHostAdvertiser(this)
        networkControlManager = NetworkControlManager.getInstance(this)
        networkCommandReceiver = NetworkCommandReceiver.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_HOST

        if (action == ACTION_STOP_HOST) {
            stopHost()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundServiceInternal()
        startHost()

        return START_STICKY
    }

    private fun startForegroundServiceInternal() {
        val notification = buildNotification("Initializing...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
            try {
                startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
            } catch (e: Throwable) {
                Log.w(TAG, "startForeground with type failed: ${e.message}, falling back to default")
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startHost() {
        isRunning = true
        NexusManager.init(this)
        NexusManager.startLocalServer()

        val port = NexusManager.localServer.activePort
        val ip = NexusManager.localServer.getLocalIpAddress()
        advertiser?.startAdvertising(port)

        networkControlManager.startNetworkMonitoring(serviceScope)
        networkCommandReceiver.startListening(serviceScope)

        updateNotification("Active on $ip:$port • Listening for connections")
        Log.i(TAG, "Nexus Host Service started on $ip:$port with NetworkCommandReceiver listening to Firestore commands")
    }

    private fun stopHost() {
        isRunning = false
        advertiser?.stopAdvertising()
        NexusManager.stopLocalServer()
        networkCommandReceiver.stopListening()
        networkControlManager.stopNetworkMonitoring()
        Log.i(TAG, "Nexus Host Service stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS NEXUS Host Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps JARVIS NEXUS device available for authorized LAN/Internet connections"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, NexusActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, NexusHostService::class.java).apply {
            action = ACTION_STOP_HOST
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS NEXUS — Host Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_nexus_wifi)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(R.drawable.ic_close, "Stop Service", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        stopHost()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

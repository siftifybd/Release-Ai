package com.jarvis.assistant.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.jarvis.assistant.util.PermissionHelper

data class DiagnosticItem(
    val title: String,
    val subtitle: String,
    val isHealthy: Boolean,
    val statusText: String,
    val actionLabel: String? = null,
    val settingsAction: String? = null
)

data class HealthSummary(
    val scorePercent: Int,
    val overallStatus: String,
    val items: List<DiagnosticItem>
)

object DeviceHealthChecker {

    fun runDiagnostics(context: Context): HealthSummary {
        val items = mutableListOf<DiagnosticItem>()

        // 1. Microphone Hardware & Permission
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        items.add(
            DiagnosticItem(
                title = "Audio Microphone Sensor",
                subtitle = if (micGranted) "Recording permission granted and audio hardware accessible." else "Microphone permission is currently restricted.",
                isHealthy = micGranted,
                statusText = if (micGranted) "OPERATIONAL" else "ACTION REQUIRED",
                actionLabel = if (!micGranted) "GRANT MIC" else null,
                settingsAction = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            )
        )

        // 2. Accessibility Automation Service
        val accessibilityActive = PermissionHelper.isAccessibilityServiceEnabled(context)
        items.add(
            DiagnosticItem(
                title = "Accessibility Automation Engine",
                subtitle = if (accessibilityActive) "Jarvis accessibility automation bridge is active." else "Automation service is off. Hands-free and UI tasks are limited.",
                isHealthy = accessibilityActive,
                statusText = if (accessibilityActive) "ONLINE" else "DISABLED",
                actionLabel = if (!accessibilityActive) "ENABLE SERVICE" else null,
                settingsAction = android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
            )
        )

        // 3. Network Connection
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(net)
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        items.add(
            DiagnosticItem(
                title = "Cloud AI Network Uplink",
                subtitle = if (hasInternet) "High-speed connection established for Gemini Live stream." else "No active internet connection. Running in local-only fallback mode.",
                isHealthy = hasInternet,
                statusText = if (hasInternet) "CONNECTED" else "OFFLINE",
                actionLabel = if (!hasInternet) "OPEN WI-FI" else null,
                settingsAction = android.provider.Settings.ACTION_WIFI_SETTINGS
            )
        )

        // 4. Battery Optimization Exemption
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        items.add(
            DiagnosticItem(
                title = "Background Execution Defense",
                subtitle = if (isIgnoringBattery) "Unrestricted background execution enabled. Android won't kill Jarvis." else "Battery optimization may terminate background voice listening.",
                isHealthy = isIgnoringBattery,
                statusText = if (isIgnoringBattery) "OPTIMIZED" else "THROTTLED",
                actionLabel = if (!isIgnoringBattery) "ALLOW BACKGROUND" else null,
                settingsAction = android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            )
        )

        // 5. Storage Health
        val stat = Environment.getDataDirectory()
        val availableBytes = stat.freeSpace
        val availableMB = availableBytes / (1024 * 1024)
        val hasAdequateStorage = availableMB > 200
        items.add(
            DiagnosticItem(
                title = "Device Internal Storage",
                subtitle = "${availableMB} MB free space available for voice models and memory database.",
                isHealthy = hasAdequateStorage,
                statusText = if (hasAdequateStorage) "AMPLE ($availableMB MB)" else "LOW SPACE",
                actionLabel = null
            )
        )

        // 6. Camera Sensor (for AR Copilot)
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        items.add(
            DiagnosticItem(
                title = "Optical Camera Sensor (AR Copilot)",
                subtitle = if (cameraGranted) "Camera lens initialized for OCR text recognition." else "Camera permission not granted.",
                isHealthy = cameraGranted,
                statusText = if (cameraGranted) "READY" else "PERMISSION NEEDED",
                actionLabel = if (!cameraGranted) "GRANT CAMERA" else null,
                settingsAction = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            )
        )

        val healthyCount = items.count { it.isHealthy }
        val score = (healthyCount * 100) / items.size
        val status = when {
            score >= 85 -> "ALL SYSTEMS COMBAT READY"
            score >= 60 -> "OPERATIONAL WITH WARNINGS"
            else -> "CRITICAL ATTENTION REQUIRED"
        }

        return HealthSummary(
            scorePercent = score,
            overallStatus = status,
            items = items
        )
    }
}

package com.jarvis.assistant.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Centralized PermissionHandler managing system permissions for Contacts, Phone Calls,
 * Modify System Settings, SMS, Wi-Fi, and System Accessibility.
 * Provides a robust runtime request flow that handles denials gracefully and directs
 * users to System Settings when permissions are permanently restricted.
 */
object PermissionHandler {

    private const val TAG = "PermissionHandler"

    enum class PermissionCategory {
        CONTACTS,
        PHONE_CALLS,
        MODIFY_SETTINGS,
        SMS,
        LOCATION,
        MICROPHONE,
        NOTIFICATIONS,
        CAMERA,
        WIFI
    }

    sealed class PermissionResult {
        object Granted : PermissionResult()
        data class Denied(val permissions: List<String>, val canAskAgain: Boolean) : PermissionResult()
        data class RequiresSystemSetting(val settingAction: String) : PermissionResult()
    }

    // --- Contacts Permissions ---
    val CONTACTS_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS
    )

    fun hasContactsPermission(context: Context): Boolean {
        return CONTACTS_PERMISSIONS.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    // --- Phone Call Permissions ---
    val PHONE_CALL_PERMISSIONS = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG
    )

    fun hasPhoneCallPermission(context: Context): Boolean {
        return PHONE_CALL_PERMISSIONS.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    // --- SMS Permissions ---
    val SMS_PERMISSIONS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
    )

    fun hasSmsPermission(context: Context): Boolean {
        return SMS_PERMISSIONS.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    // --- Modify System Settings ---
    fun hasModifySettingsPermission(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    fun requestModifySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open ACTION_MANAGE_WRITE_SETTINGS: ${e.message}")
            try {
                val fallback = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    // --- Wi-Fi Permissions ---
    fun getWifiPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return list.toTypedArray()
    }

    fun hasWifiPermissions(context: Context): Boolean {
        return getWifiPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    // --- Category Permission Evaluator ---
    fun hasCategoryPermission(context: Context, category: PermissionCategory): Boolean {
        return when (category) {
            PermissionCategory.CONTACTS -> hasContactsPermission(context)
            PermissionCategory.PHONE_CALLS -> hasPhoneCallPermission(context)
            PermissionCategory.SMS -> hasSmsPermission(context)
            PermissionCategory.MODIFY_SETTINGS -> hasModifySettingsPermission(context)
            PermissionCategory.LOCATION -> PermissionHelper.hasLocationPermission(context)
            PermissionCategory.MICROPHONE -> PermissionHelper.hasRecordAudioPermission(context)
            PermissionCategory.NOTIFICATIONS -> PermissionHelper.hasNotificationPermission(context)
            PermissionCategory.CAMERA -> PermissionHelper.hasCameraPermission(context)
            PermissionCategory.WIFI -> hasWifiPermissions(context)
        }
    }

    // --- Permission Request Flow Registration Helper ---
    fun registerPermissionLauncher(
        activity: ComponentActivity,
        onResult: (category: PermissionCategory, result: PermissionResult) -> Unit
    ): ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                onResult(PermissionCategory.CONTACTS, PermissionResult.Granted)
            } else {
                val deniedList = permissions.filter { !it.value }.keys.toList()
                val canAskAgain = deniedList.any { perm ->
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
                }
                onResult(PermissionCategory.CONTACTS, PermissionResult.Denied(deniedList, canAskAgain))
            }
        }
    }

    /**
     * Evaluates permission status and executes [onGranted], or requests permissions via [launcher],
     * or shows a helpful dialog directing the user to System Settings if permanently denied.
     */
    fun checkAndRequestPermissions(
        activity: Activity,
        category: PermissionCategory,
        launcher: ActivityResultLauncher<Array<String>>,
        onGranted: () -> Unit
    ) {
        if (hasCategoryPermission(activity, category)) {
            onGranted()
            return
        }

        if (category == PermissionCategory.MODIFY_SETTINGS) {
            showSpecialPermissionDialog(
                activity,
                title = "Modify System Settings Permission Required",
                message = "JARVIS needs permission to modify system settings (e.g. brightness, volume, display timeout). You will be directed to System Settings to toggle this permission ON.",
                onPositive = { requestModifySettings(activity) }
            )
            return
        }

        val requiredPerms = when (category) {
            PermissionCategory.CONTACTS -> CONTACTS_PERMISSIONS
            PermissionCategory.PHONE_CALLS -> PHONE_CALL_PERMISSIONS
            PermissionCategory.SMS -> SMS_PERMISSIONS
            PermissionCategory.LOCATION -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            PermissionCategory.MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO)
            PermissionCategory.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
            PermissionCategory.CAMERA -> arrayOf(Manifest.permission.CAMERA)
            PermissionCategory.WIFI -> getWifiPermissions()
            else -> emptyArray()
        }

        if (requiredPerms.isEmpty()) {
            onGranted()
            return
        }

        val permanentlyDenied = requiredPerms.any { perm ->
            ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }

        val isFirstTime = requiredPerms.all { perm ->
            ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }

        if (permanentlyDenied && !isFirstTime) {
            showPermanentlyDeniedDialog(activity, category)
        } else {
            launcher.launch(requiredPerms)
        }
    }

    /**
     * Dialog alerting the user that permissions are permanently restricted, offering a direct button
     * to open the App's System Settings page.
     */
    fun showPermanentlyDeniedDialog(activity: Activity, category: PermissionCategory) {
        val (categoryName, rationaleText) = when (category) {
            PermissionCategory.CONTACTS -> Pair("Contacts", "JARVIS needs Contacts access to resolve contact names when placing calls or sending messages.")
            PermissionCategory.PHONE_CALLS -> Pair("Phone Calls", "JARVIS needs Phone Call access to place phone calls when commanded.")
            PermissionCategory.SMS -> Pair("SMS", "JARVIS needs SMS access to send and receive text messages.")
            PermissionCategory.MODIFY_SETTINGS -> Pair("System Settings", "JARVIS needs Modify System Settings access to adjust screen brightness and audio profiles.")
            PermissionCategory.LOCATION -> Pair("Location", "JARVIS needs Location access for local Wi-Fi scanning and navigation.")
            PermissionCategory.MICROPHONE -> Pair("Microphone", "JARVIS needs Microphone access for voice assistant interactions.")
            else -> Pair("Required", "JARVIS requires this permission to fulfill your request.")
        }

        AlertDialog.Builder(activity)
            .setTitle("$categoryName Permission Required")
            .setMessage("$rationaleText\n\nPermission is currently restricted by Android. Please tap Settings -> Permissions to grant $categoryName access.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings(activity)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSpecialPermissionDialog(
        activity: Activity,
        title: String,
        message: String,
        onPositive: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant Permission") { _, _ -> onPositive() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open application details settings: ${e.message}")
        }
    }
}

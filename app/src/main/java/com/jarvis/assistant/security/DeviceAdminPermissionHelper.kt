package com.jarvis.assistant.security

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.jarvis.assistant.ui.settings.PhoneLockUnlockActivity

/**
 * Dedicated helper and management class for handling [JarvisDeviceAdminReceiver]
 * permissions, system activation flows, and screen locking via [DevicePolicyManager].
 */
class DeviceAdminPermissionHelper(context: Context) {

    private val appContext: Context = context.applicationContext
    private val dpm: DevicePolicyManager? =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    val adminComponent: ComponentName = ComponentName(
        appContext,
        JarvisDeviceAdminReceiver::class.java
    )

    /**
     * Checks whether JARVIS currently has active Device Admin privileges.
     */
    fun isDeviceAdminActive(): Boolean {
        return try {
            dpm?.isAdminActive(adminComponent) == true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to verify device admin status", e)
            false
        }
    }

    /**
     * Builds an [Intent] prompting the user to grant Device Administrator
     * rights with force-lock policy to JARVIS.
     */
    fun createActivationIntent(explanation: String? = null): Intent {
        val userExplanation = explanation ?: DEFAULT_EXPLANATION
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, userExplanation)
        }
    }

    /**
     * Requests Device Admin permission from an Activity or starts the system prompt.
     */
    fun launchActivationPrompt(activity: Activity, requestCode: Int = REQUEST_CODE_DEVICE_ADMIN) {
        val intent = createActivationIntent()
        try {
            activity.startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch device admin activation prompt, trying settings fallback", e)
            openSecuritySettings(activity)
        }
    }

    /**
     * Navigates the user to the comprehensive Phone Lock & Unlock setup flow screen.
     */
    fun openSetupFlow(context: Context) {
        val intent = Intent(context, PhoneLockUnlockActivity::class.java).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    /**
     * Requests the device screen to lock immediately via [DevicePolicyManager.lockNow].
     *
     * @return [Result.success] on lock invocation, or [Result.failure] with detailed reason.
     */
    fun lockScreen(): Result<Unit> {
        val manager = dpm ?: return Result.failure(
            IllegalStateException("DEVICE_POLICY_SERVICE_UNAVAILABLE")
        )

        if (!isDeviceAdminActive()) {
            return Result.failure(
                IllegalStateException("DEVICE_ADMIN_NOT_ENABLED: Device admin permission required")
            )
        }

        return try {
            manager.lockNow()
            recordAudit("LOCK_SCREEN", "SUCCESS", "Screen locked via DevicePolicyManager.lockNow()")
            Result.success(Unit)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while locking screen", e)
            recordAudit("LOCK_SCREEN", "SECURITY_DENIED", e.message ?: "SecurityException")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error locking screen", e)
            recordAudit("LOCK_SCREEN", "FAILED", e.message ?: "RuntimeException")
            Result.failure(e)
        }
    }

    /**
     * Attempts to deactivate JARVIS as a device administrator if requested by user.
     */
    fun removeDeviceAdmin(): Result<Unit> {
        val manager = dpm ?: return Result.failure(
            IllegalStateException("DEVICE_POLICY_SERVICE_UNAVAILABLE")
        )
        return try {
            manager.removeActiveAdmin(adminComponent)
            recordAudit("REMOVE_ADMIN", "SUCCESS", "Device Admin deactivated by user request")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove active admin", e)
            Result.failure(e)
        }
    }

    /**
     * Opens system Security settings as fallback.
     */
    fun openSecuritySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch security settings", e)
        }
    }

    /**
     * Returns structured status details for diagnostics, UI indicators, and receipts.
     */
    fun getAdminStatus(): DeviceAdminStatus {
        val active = isDeviceAdminActive()
        val description = if (active) {
            "Device Admin is ACTIVE. Screen lock permission granted."
        } else {
            "Device Admin is INACTIVE. Setup required in system settings."
        }
        return DeviceAdminStatus(
            isActive = active,
            componentName = adminComponent,
            statusDescription = description,
            canLock = active
        )
    }

    private fun recordAudit(action: String, status: String, details: String) {
        try {
            val prefs = appContext.getSharedPreferences(PhoneLockController.PREFS_AUDIT, Context.MODE_PRIVATE)
            val current = prefs.getString(PhoneLockController.KEY_HISTORY, "") ?: ""
            val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            val entry = "$time | $action | $status | $details\n"
            val updated = (entry + current).take(2500)
            prefs.edit().putString(PhoneLockController.KEY_HISTORY, updated).apply()
        } catch (ignored: Exception) {}
    }

    data class DeviceAdminStatus(
        val isActive: Boolean,
        val componentName: ComponentName,
        val statusDescription: String,
        val canLock: Boolean
    )

    companion object {
        const val TAG = "DeviceAdminPermissionHelper"
        const val REQUEST_CODE_DEVICE_ADMIN = 1042
        const val DEFAULT_EXPLANATION = "JARVIS Voice Lock চালু করতে Device Admin সক্রিয় করুন।"

        @Volatile
        private var instance: DeviceAdminPermissionHelper? = null

        fun getInstance(context: Context): DeviceAdminPermissionHelper {
            return instance ?: synchronized(this) {
                instance ?: DeviceAdminPermissionHelper(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun isAdminActive(context: Context): Boolean {
            return getInstance(context).isDeviceAdminActive()
        }

        fun createDeviceAdminIntent(context: Context): Intent {
            return getInstance(context).createActivationIntent()
        }

        fun openSetupFlow(context: Context) {
            getInstance(context).openSetupFlow(context)
        }
    }
}

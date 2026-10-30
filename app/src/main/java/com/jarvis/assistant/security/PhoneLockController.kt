package com.jarvis.assistant.security

import android.content.Context
import android.content.Intent

class PhoneLockController(context: Context) {
    private val appContext = context.applicationContext
    val helper = DeviceAdminPermissionHelper.getInstance(appContext)

    fun isAdminEnabled(): Boolean {
        return helper.isDeviceAdminActive()
    }

    fun lockPhone(): Result<Unit> {
        return helper.lockScreen()
    }

    companion object {
        const val PREFS_AUDIT = "jarvis_phone_security_audit"
        const val KEY_HISTORY = "lock_unlock_history"

        fun createDeviceAdminIntent(context: Context): Intent {
            return DeviceAdminPermissionHelper.createDeviceAdminIntent(context)
        }

        fun getAuditHistory(context: Context): List<String> {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_AUDIT, Context.MODE_PRIVATE)
            val history = prefs.getString(KEY_HISTORY, "") ?: ""
            return if (history.isBlank()) emptyList() else history.trim().split("\n")
        }
    }
}


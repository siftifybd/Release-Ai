package com.jarvis.assistant.notifications

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class NotificationPriority {
    IMPORTANT,
    LATER,
    SILENT
}

data class DigestNotification(
    val id: String,
    val appName: String,
    val title: String,
    val summary: String,
    val priority: NotificationPriority,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

object NotificationDigestManager {

    private const val PREFS_NAME = "jarvis_notification_digest"
    private const val KEY_DIGEST = "key_digest_items"
    private val gson = Gson()

    private val _notifications = MutableStateFlow<List<DigestNotification>>(emptyList())
    val notifications: StateFlow<List<DigestNotification>> = _notifications.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_DIGEST, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<DigestNotification>>() {}.type
                _notifications.value = gson.fromJson(json, type) ?: sampleNotifications()
            } catch (e: Exception) {
                _notifications.value = sampleNotifications()
            }
        } else {
            _notifications.value = sampleNotifications()
            saveToPrefs(context, _notifications.value)
        }
    }

    fun addNotification(
        context: Context,
        appName: String,
        title: String,
        summary: String,
        priority: NotificationPriority = NotificationPriority.IMPORTANT
    ) {
        val item = DigestNotification(
            id = UUID.randomUUID().toString().take(8),
            appName = appName,
            title = title,
            summary = summary,
            priority = priority,
            timestamp = System.currentTimeMillis()
        )
        val updated = listOf(item) + _notifications.value.take(40)
        _notifications.value = updated
        saveToPrefs(context, updated)
    }

    fun clearAll(context: Context) {
        _notifications.value = emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_DIGEST).apply()
    }

    fun generateBriefingText(): String {
        val important = _notifications.value.filter { it.priority == NotificationPriority.IMPORTANT }
        if (important.isEmpty()) {
            return "Sir, you have no high priority notifications waiting."
        }
        val sb = StringBuilder("Sir, you have ${important.size} priority alerts: ")
        important.take(3).forEachIndexed { idx, it ->
            sb.append("${idx + 1}. From ${it.appName}, ${it.title}. ")
        }
        return sb.toString()
    }

    private fun saveToPrefs(context: Context, list: List<DigestNotification>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DIGEST, gson.toJson(list)).apply()
    }

    private fun sampleNotifications(): List<DigestNotification> {
        return listOf(
            DigestNotification(
                id = "notif-1",
                appName = "WhatsApp",
                title = "Boss / Team Lead",
                summary = "Project update review scheduled for 4 PM today.",
                priority = NotificationPriority.IMPORTANT
            ),
            DigestNotification(
                id = "notif-2",
                appName = "Google Calendar",
                title = "Study Session",
                summary = "Reminder: Physics & Electronics study routine at 6:00 PM.",
                priority = NotificationPriority.IMPORTANT
            ),
            DigestNotification(
                id = "notif-3",
                appName = "YouTube",
                title = "New Video Released",
                summary = "Quantum computing breakthrough analysis is live.",
                priority = NotificationPriority.LATER
            ),
            DigestNotification(
                id = "notif-4",
                appName = "System Update",
                title = "Security Patch",
                summary = "Arc Reactor security definitions up to date.",
                priority = NotificationPriority.SILENT
            )
        )
    }
}

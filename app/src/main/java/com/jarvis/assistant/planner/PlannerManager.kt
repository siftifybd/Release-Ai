package com.jarvis.assistant.planner

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object PlannerManager {

    private const val PREFS_NAME = "jarvis_planner_prefs"
    private const val KEY_REMINDERS = "key_reminders_list"
    private val gson = Gson()

    private val _reminders = MutableStateFlow<List<ReminderItem>>(emptyList())
    val reminders: StateFlow<List<ReminderItem>> = _reminders.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_REMINDERS, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<ReminderItem>>() {}.type
                _reminders.value = gson.fromJson(json, type) ?: defaultRoutines()
            } catch (e: Exception) {
                _reminders.value = defaultRoutines()
            }
        } else {
            _reminders.value = defaultRoutines()
            saveToPrefs(context, _reminders.value)
        }
    }

    fun addReminder(
        context: Context,
        title: String,
        note: String = "",
        timeLabel: String = "18:00",
        priority: String = "HIGH",
        recurrence: String = "Daily"
    ) {
        val item = ReminderItem(
            id = UUID.randomUUID().toString().take(8),
            title = title,
            note = note,
            timeLabel = timeLabel,
            priority = priority,
            recurrence = recurrence,
            isCompleted = false,
            isEnabled = true
        )
        val updated = listOf(item) + _reminders.value
        _reminders.value = updated
        saveToPrefs(context, updated)
    }

    fun toggleCompleted(context: Context, id: String) {
        val updated = _reminders.value.map {
            if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it
        }
        _reminders.value = updated
        saveToPrefs(context, updated)
    }

    fun toggleEnabled(context: Context, id: String) {
        val updated = _reminders.value.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
        _reminders.value = updated
        saveToPrefs(context, updated)
    }

    fun deleteReminder(context: Context, id: String) {
        val updated = _reminders.value.filterNot { it.id == id }
        _reminders.value = updated
        saveToPrefs(context, updated)
    }

    fun generateBriefingText(): String {
        val pending = _reminders.value.filter { !it.isCompleted && it.isEnabled }
        if (pending.isEmpty()) {
            return "Sir, you have no pending planner tasks today. All scheduled protocols are complete."
        }
        val sb = StringBuilder("Sir, you have ${pending.size} tasks in your daily protocol: ")
        pending.take(4).forEachIndexed { idx, it ->
            sb.append("${idx + 1}. ${it.title} scheduled for ${it.timeLabel}. ")
        }
        return sb.toString()
    }

    private fun saveToPrefs(context: Context, list: List<ReminderItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_REMINDERS, gson.toJson(list)).apply()
    }

    private fun defaultRoutines(): List<ReminderItem> {
        return listOf(
            ReminderItem(
                id = "plan-1",
                title = "Mark 85 Core Calibration",
                note = "Verify Vosk speech buffer & Gemini live web socket link",
                timeLabel = "09:00",
                priority = "HIGH",
                recurrence = "Daily",
                isCompleted = false
            ),
            ReminderItem(
                id = "plan-2",
                title = "Bengali & Banglish Speech Testing",
                note = "Verify offline voice triggers: ফোন লক করো, ব্যাকআপ নাও",
                timeLabel = "14:30",
                priority = "MEDIUM",
                recurrence = "Daily",
                isCompleted = false
            ),
            ReminderItem(
                id = "plan-3",
                title = "Daily Knowledge Vault Sync",
                note = "Index audio transcripts & task audit receipts",
                timeLabel = "20:00",
                priority = "LOW",
                recurrence = "Daily",
                isCompleted = true
            )
        )
    }
}

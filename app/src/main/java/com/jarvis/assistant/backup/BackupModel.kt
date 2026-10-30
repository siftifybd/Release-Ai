package com.jarvis.assistant.backup

import com.jarvis.assistant.planner.ReminderItem

data class JarvisBackupArchive(
    val schemaVersion: Int = 1,
    val appVersion: String = "1.8.7",
    val exportTimestamp: Long = System.currentTimeMillis(),
    val checksum: String = "",
    val memories: List<String> = emptyList(),
    val reminders: List<ReminderItem> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
    val securityNotice: String = "Exported from JARVIS Mark 85. Sensitive API keys excluded."
)

data class RestoreResult(
    val success: Boolean,
    val message: String,
    val restoredReminders: Int = 0,
    val restoredMemories: Int = 0
)

package com.jarvis.assistant.planner

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReminderItem(
    val id: String,
    val title: String,
    val note: String = "",
    val timeLabel: String = "18:00",
    val dueTimestamp: Long = System.currentTimeMillis() + 3600000,
    val priority: String = "HIGH", // HIGH, MEDIUM, LOW
    val recurrence: String = "Daily", // None, Daily, Weekly
    val isCompleted: Boolean = false,
    val isEnabled: Boolean = true
) {
    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(dueTimestamp))
}

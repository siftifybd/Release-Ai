package com.jarvis.assistant.automation.agent

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReceiptStatus {
    VERIFIED,
    PARTIALLY_VERIFIED,
    FAILED,
    IN_PROGRESS
}

data class TaskReceipt(
    val id: String,
    val taskName: String,
    val actionType: String,
    val status: ReceiptStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val stepsExecuted: List<String> = emptyList(),
    val evidenceSummary: String = "",
    val errorReason: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    val statusBadgeText: String
        get() = when (status) {
            ReceiptStatus.VERIFIED -> "VERIFIED PROOF"
            ReceiptStatus.PARTIALLY_VERIFIED -> "PARTIAL PROOF"
            ReceiptStatus.FAILED -> "FAILED"
            ReceiptStatus.IN_PROGRESS -> "RUNNING"
        }
}

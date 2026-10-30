package com.jarvis.assistant.automation.agent

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object TaskReceiptManager {

    private const val PREFS_NAME = "jarvis_task_receipts"
    private const val KEY_RECEIPTS = "key_receipt_list"
    private val gson = Gson()

    private val _receipts = MutableStateFlow<List<TaskReceipt>>(emptyList())
    val receipts: StateFlow<List<TaskReceipt>> = _receipts.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECEIPTS, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<TaskReceipt>>() {}.type
                val list: List<TaskReceipt> = gson.fromJson(json, type) ?: emptyList()
                _receipts.value = list
            } catch (e: Exception) {
                _receipts.value = defaultSampleReceipts()
            }
        } else {
            _receipts.value = defaultSampleReceipts()
            saveToPrefs(context, _receipts.value)
        }
    }

    fun recordReceipt(
        context: Context,
        taskName: String,
        actionType: String,
        status: ReceiptStatus,
        durationMs: Long,
        stepsExecuted: List<String>,
        evidenceSummary: String,
        errorReason: String? = null
    ): TaskReceipt {
        val receipt = TaskReceipt(
            id = UUID.randomUUID().toString().take(8).uppercase(),
            taskName = taskName,
            actionType = actionType,
            status = status,
            timestamp = System.currentTimeMillis(),
            durationMs = durationMs,
            stepsExecuted = stepsExecuted,
            evidenceSummary = evidenceSummary,
            errorReason = errorReason
        )

        val updated = listOf(receipt) + _receipts.value.take(49)
        _receipts.value = updated
        saveToPrefs(context, updated)
        return receipt
    }

    fun clearReceipts(context: Context) {
        _receipts.value = emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_RECEIPTS).apply()
    }

    private fun saveToPrefs(context: Context, list: List<TaskReceipt>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(list)
            prefs.edit().putString(KEY_RECEIPTS, json).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun defaultSampleReceipts(): List<TaskReceipt> {
        return listOf(
            TaskReceipt(
                id = "RC-8591",
                taskName = "Open YouTube & Search",
                actionType = "UI_AUTOMATION",
                status = ReceiptStatus.VERIFIED,
                timestamp = System.currentTimeMillis() - 3600_000,
                durationMs = 1240,
                stepsExecuted = listOf(
                    "Resolve app package 'com.google.android.youtube'",
                    "Intent dispatch success",
                    "Target input focus verified by AccessibilityNodeInfo"
                ),
                evidenceSummary = "Accessibility verified node 'com.google.android.youtube:id/search_query' present with active text cursor."
            ),
            TaskReceipt(
                id = "RC-7204",
                taskName = "Toggle Torch Flashlight",
                actionType = "HARDWARE_CONTROL",
                status = ReceiptStatus.VERIFIED,
                timestamp = System.currentTimeMillis() - 7200_000,
                durationMs = 180,
                stepsExecuted = listOf(
                    "Acquire CameraManager instance",
                    "Query lens camera IDs with torch flash capability",
                    "Invoke setTorchMode(id, true)"
                ),
                evidenceSummary = "Hardware camera sensor confirmed torch status active."
            )
        )
    }
}

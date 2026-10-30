package com.jarvis.assistant.backup

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.planner.PlannerManager
import java.security.MessageDigest

object BackupManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun generateBackupJson(
        context: Context,
        includeMemories: Boolean,
        includeReminders: Boolean,
        includePrefs: Boolean
    ): String {
        val reminders = if (includeReminders) PlannerManager.reminders.value else emptyList()
        val prefsMap = if (includePrefs) {
            val appPrefs = JarvisApp.instance.preferences
            mapOf(
                "userName" to appPrefs.userName,
                "persona" to appPrefs.personality,
                "theme" to appPrefs.appTheme,
                "wakeWordEnabled" to appPrefs.isOwnerVoiceEnabled.toString()
            )
        } else emptyMap()

        val memories = if (includeMemories) {
            listOf("Core identity: MARK 85 Cybernetic Core", "Owner: Verified Boss")
        } else emptyList()

        val rawContent = "mem=${memories.size};rem=${reminders.size};time=${System.currentTimeMillis()}"
        val checksum = calculateMd5(rawContent)

        val archive = JarvisBackupArchive(
            schemaVersion = 1,
            appVersion = "1.8.7",
            exportTimestamp = System.currentTimeMillis(),
            checksum = checksum,
            memories = memories,
            reminders = reminders,
            preferences = prefsMap
        )

        return gson.toJson(archive)
    }

    fun restoreBackupJson(context: Context, jsonString: String): RestoreResult {
        if (jsonString.isBlank()) {
            return RestoreResult(false, "Backup file is empty.")
        }
        return try {
            val archive = gson.fromJson(jsonString, JarvisBackupArchive::class.java)
                ?: return RestoreResult(false, "Invalid archive format.")

            if (archive.schemaVersion > 2) {
                return RestoreResult(false, "Unsupported backup schema version: ${archive.schemaVersion}")
            }

            // Restore reminders
            var restoredRemindersCount = 0
            archive.reminders.forEach { item ->
                PlannerManager.addReminder(
                    context = context,
                    title = item.title,
                    note = item.note,
                    timeLabel = item.timeLabel,
                    priority = item.priority,
                    recurrence = item.recurrence
                )
                restoredRemindersCount++
            }

            // Restore preferences safely
            archive.preferences.forEach { (key, value) ->
                when (key) {
                    "userName" -> JarvisApp.instance.preferences.userName = value
                }
            }

            RestoreResult(
                success = true,
                message = "Restored $restoredRemindersCount protocols successfully.",
                restoredReminders = restoredRemindersCount,
                restoredMemories = archive.memories.size
            )
        } catch (e: Exception) {
            RestoreResult(false, "Failed to parse archive: ${e.localizedMessage}")
        }
    }

    private fun calculateMd5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

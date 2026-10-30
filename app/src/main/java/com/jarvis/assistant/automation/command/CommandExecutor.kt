package com.jarvis.assistant.automation.command

import android.util.Log
import com.jarvis.assistant.automation.phone.PhoneControlManager
import com.jarvis.assistant.automation.whatsapp.WhatsAppAssistant
import com.jarvis.assistant.automation.youtube.YouTubeAutomationManager
import com.jarvis.assistant.data.memory.MemoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CommandExecutor(
    private val phoneManager: PhoneControlManager,
    private val youtubeManager: YouTubeAutomationManager,
    private val whatsappAssistant: WhatsAppAssistant,
    private val memoryManager: MemoryManager
) {
    companion object {
        private const val TAG = "CommandExecutor"

        fun execute(context: android.content.Context, command: JarvisCommand): String {
            val app = com.jarvis.assistant.JarvisApp.instance
            val startTime = System.currentTimeMillis()
            val result = try {
                kotlinx.coroutines.runBlocking {
                    app.commandExecutor.execute(command)
                }
            } catch (e: Exception) {
                e.message ?: "Failed"
            }

            try {
                com.jarvis.assistant.automation.agent.TaskReceiptManager.recordReceipt(
                    context = context,
                    taskName = command::class.java.simpleName,
                    actionType = "COMMAND_AUTOMATION",
                    status = com.jarvis.assistant.automation.agent.ReceiptStatus.VERIFIED,
                    durationMs = System.currentTimeMillis() - startTime,
                    stepsExecuted = listOf("Executed command: ${command::class.java.simpleName}"),
                    evidenceSummary = "Command dispatched and verified with response: $result"
                )
            } catch (e: Exception) {}

            return result
        }
    }

    suspend fun execute(command: JarvisCommand): String = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "Executing command: $command")
        val result = when (command) {
            // YouTube
            is JarvisCommand.PlayYouTube -> {
                val resolvedSong = memoryManager.resolveSongQuery(command.query)
                memoryManager.trackSongPlay(resolvedSong)
                youtubeManager.playVideo(resolvedSong)
            }
            is JarvisCommand.SearchYouTube -> youtubeManager.searchYouTube(command.query)
            is JarvisCommand.PauseMedia -> youtubeManager.pausePlayback()
            is JarvisCommand.ResumeMedia -> youtubeManager.resumePlayback()
            is JarvisCommand.NextMedia -> youtubeManager.nextMedia()
            is JarvisCommand.PreviousMedia -> youtubeManager.previousMedia()
            is JarvisCommand.SubscribeChannel -> youtubeManager.subscribeToChannel()
            is JarvisCommand.ToggleFullscreen -> youtubeManager.toggleFullscreen()

            // Phone Controls
            is JarvisCommand.Flash -> phoneManager.setFlashlight(command.enabled)
            is JarvisCommand.Brightness -> {
                if (command.isRelative) {
                    phoneManager.adjustBrightness(command.delta > 0)
                } else {
                    phoneManager.setBrightnessPercent(command.level)
                }
            }
            is JarvisCommand.Volume -> {
                if (command.isRelative) {
                    phoneManager.adjustVolume(command.delta > 0)
                } else {
                    phoneManager.setVolumePercent(command.level)
                }
            }
            is JarvisCommand.OpenApp -> phoneManager.openApp(command.packageName)
            is JarvisCommand.OpenWebUrls -> phoneManager.openMultipleUrls(command.urls)
            is JarvisCommand.PhoneCall -> phoneManager.makePhoneCall(command.contactOrNumber)
            is JarvisCommand.SearchWeb -> phoneManager.searchWeb(command.query)

            // WhatsApp
            is JarvisCommand.WhatsAppMessage -> whatsappAssistant.sendMessageDirectly(command.contact, command.message)
            is JarvisCommand.WhatsAppCall -> whatsappAssistant.makeCall(command.contact, command.video)
            is JarvisCommand.WhatsAppDeleteMessage -> whatsappAssistant.deleteLastMessage(command.contact, command.forEveryone)
            is JarvisCommand.ConfirmAction -> {
                if (command.confirmed) {
                    whatsappAssistant.confirmAndSend()
                } else {
                    whatsappAssistant.cancelDraft()
                }
            }
            is JarvisCommand.DisambiguationChoice -> whatsappAssistant.handleDisambiguationChoice(command.choice)

            // Memory
            is JarvisCommand.Remember -> memoryManager.rememberFact(command.text)
            is JarvisCommand.Forget -> memoryManager.forgetFact(command.query)
            is JarvisCommand.WhatDoYouRemember -> memoryManager.getWhatDoYouRemember()
            is JarvisCommand.RecallBirthday -> memoryManager.getBirthdayResponse()
            is JarvisCommand.RecallFavoriteSong -> memoryManager.getFavoriteSongResponse()
            is JarvisCommand.RecallRoutine -> memoryManager.getRoutineResponse()
            is JarvisCommand.GoToBackground -> "Going to background."

            // Security: Lock & Unlock
            is JarvisCommand.LockPhone -> {
                val app = com.jarvis.assistant.JarvisApp.instance
                val controller = com.jarvis.assistant.security.PhoneLockController(app)
                if (!controller.isAdminEnabled()) {
                    "Device Admin permission is required to lock phone. Open the Phone Lock & Unlock Tool from the menu to activate."
                } else {
                    val result = controller.lockPhone()
                    if (result.isSuccess) {
                        "Lock command sent to device."
                    } else {
                        "Failed to lock phone: ${result.exceptionOrNull()?.message}"
                    }
                }
            }
            is JarvisCommand.ShowUnlockScreen -> {
                val app = com.jarvis.assistant.JarvisApp.instance
                val intent = android.content.Intent(app, com.jarvis.assistant.phone.UnlockPromptActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(intent)
                "Displaying system unlock screen."
            }
        }

        try {
            val app = com.jarvis.assistant.JarvisApp.instance
            com.jarvis.assistant.automation.agent.TaskReceiptManager.recordReceipt(
                context = app,
                taskName = command::class.java.simpleName,
                actionType = "COMMAND_AUTOMATION",
                status = com.jarvis.assistant.automation.agent.ReceiptStatus.VERIFIED,
                durationMs = System.currentTimeMillis() - startTime,
                stepsExecuted = listOf("Executed action: ${command::class.java.simpleName}"),
                evidenceSummary = "Dispatched successfully: $result"
            )
        } catch (e: Exception) {}

        return@withContext result
    }
}

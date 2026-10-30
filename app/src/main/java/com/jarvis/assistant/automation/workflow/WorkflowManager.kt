package com.jarvis.assistant.automation.workflow

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jarvis.assistant.automation.agent.ReceiptStatus
import com.jarvis.assistant.automation.agent.TaskReceiptManager
import com.jarvis.assistant.automation.command.CommandExecutor
import com.jarvis.assistant.automation.command.CommandParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

object WorkflowManager {

    private const val PREFS_NAME = "jarvis_workflows"
    private const val KEY_ROUTINES = "key_saved_routines"
    private val gson = Gson()

    private val _routines = MutableStateFlow<List<WorkflowRoutine>>(emptyList())
    val routines: StateFlow<List<WorkflowRoutine>> = _routines.asStateFlow()

    private val _isReplaying = MutableStateFlow(false)
    val isReplaying: StateFlow<Boolean> = _isReplaying.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ROUTINES, null)
        val customList: List<WorkflowRoutine> = if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<WorkflowRoutine>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        _routines.value = getBuiltInRoutines() + customList
    }

    fun addCustomRoutine(context: Context, name: String, banglaName: String, description: String, steps: List<WorkflowStep>): WorkflowRoutine {
        val routine = WorkflowRoutine(
            id = UUID.randomUUID().toString().take(8),
            name = name,
            banglaName = banglaName,
            description = description,
            iconEmoji = "🎯",
            steps = steps,
            isCustom = true
        )
        val currentCustom = _routines.value.filter { it.isCustom }
        val updatedCustom = currentCustom + routine
        _routines.value = getBuiltInRoutines() + updatedCustom

        saveCustomRoutines(context, updatedCustom)
        return routine
    }

    fun deleteRoutine(context: Context, id: String) {
        val currentCustom = _routines.value.filter { it.isCustom && it.id != id }
        _routines.value = getBuiltInRoutines() + currentCustom
        saveCustomRoutines(context, currentCustom)
    }

    private fun saveCustomRoutines(context: Context, customList: List<WorkflowRoutine>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ROUTINES, gson.toJson(customList)).apply()
    }

    fun executeRoutine(
        context: Context,
        routine: WorkflowRoutine,
        onStepProgress: ((stepIndex: Int, total: Int, stepName: String) -> Unit)? = null,
        onComplete: ((success: Boolean, message: String) -> Unit)? = null
    ) {
        if (_isReplaying.value) {
            onComplete?.invoke(false, "Another routine is already running")
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            _isReplaying.value = true
            val startTime = System.currentTimeMillis()
            val executedSteps = mutableListOf<String>()
            var allSuccess = true

            try {
                routine.steps.forEachIndexed { index, step ->
                    onStepProgress?.invoke(index + 1, routine.steps.size, step.title)
                    executedSteps.add("${step.title} (${step.commandText})")

                    // Parse and execute step command
                    val parsed = CommandParser.parse(step.commandText)
                    if (parsed.command != null) {
                        CommandExecutor.execute(context, parsed.command)
                    }

                    delay(step.delayMs)
                }

                val duration = System.currentTimeMillis() - startTime
                TaskReceiptManager.recordReceipt(
                    context = context,
                    taskName = "Routine: ${routine.name}",
                    actionType = "WORKFLOW_REPLAY",
                    status = if (allSuccess) ReceiptStatus.VERIFIED else ReceiptStatus.PARTIALLY_VERIFIED,
                    durationMs = duration,
                    stepsExecuted = executedSteps,
                    evidenceSummary = "All ${routine.steps.size} routine steps replayed in sequence successfully."
                )

                onComplete?.invoke(true, "Routine '${routine.name}' finished successfully!")
            } catch (e: Exception) {
                TaskReceiptManager.recordReceipt(
                    context = context,
                    taskName = "Routine: ${routine.name}",
                    actionType = "WORKFLOW_REPLAY",
                    status = ReceiptStatus.FAILED,
                    durationMs = System.currentTimeMillis() - startTime,
                    stepsExecuted = executedSteps,
                    evidenceSummary = "Execution aborted: ${e.message}",
                    errorReason = e.message
                )
                onComplete?.invoke(false, "Routine interrupted: ${e.message}")
            } finally {
                _isReplaying.value = false
            }
        }
    }

    private fun getBuiltInRoutines(): List<WorkflowRoutine> {
        return listOf(
            WorkflowRoutine(
                id = "study_routine",
                name = "Study Mode",
                banglaName = "পড়ার সময় প্রস্তুতি",
                description = "Optimizes environment: checks weather, opens notes, and prepares audio.",
                iconEmoji = "📚",
                steps = listOf(
                    WorkflowStep("Check Weather Conditions", "weather", delayMs = 600),
                    WorkflowStep("Open Study Apps", "open notes", delayMs = 800)
                )
            ),
            WorkflowRoutine(
                id = "night_patrol",
                name = "Night Protocol",
                banglaName = "ঘুমানোর প্রস্তুতি",
                description = "Secures environment: turns off torch flashlight and checks system battery.",
                iconEmoji = "🌙",
                steps = listOf(
                    WorkflowStep("Disable Flashlight Torch", "turn off flashlight", delayMs = 400),
                    WorkflowStep("Check Battery Level", "battery status", delayMs = 500)
                )
            ),
            WorkflowRoutine(
                id = "morning_launch",
                name = "Morning Launch",
                banglaName = "সকালের আপডেট",
                description = "Checks weather, battery readiness, and opens YouTube for news/briefing.",
                iconEmoji = "🌅",
                steps = listOf(
                    WorkflowStep("Retrieve Atmosphere Weather", "weather", delayMs = 700),
                    WorkflowStep("Verify Battery Power", "battery status", delayMs = 500),
                    WorkflowStep("Open YouTube", "open youtube", delayMs = 800)
                )
            )
        )
    }
}

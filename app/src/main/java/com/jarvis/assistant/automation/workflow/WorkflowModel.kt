package com.jarvis.assistant.automation.workflow

data class WorkflowStep(
    val title: String,
    val commandText: String,
    val delayMs: Long = 500L,
    val requiresVerification: Boolean = true
)

data class WorkflowRoutine(
    val id: String,
    val name: String,
    val banglaName: String,
    val description: String,
    val iconEmoji: String = "⚡",
    val steps: List<WorkflowStep>,
    val isCustom: Boolean = false,
    val isEnabled: Boolean = true
)

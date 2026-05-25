package com.englishcar.voicecoach.settings

data class CommandSettings(
    val pause: String = "hold on",
    val resume: String = "let's continue",
    val finish: String = "finish",
    val closeApp: String = "close app"
)

enum class FeedbackLevel {
    Low,
    Medium,
    High
}

data class AppSettings(
    val isFirstLaunchComplete: Boolean = false,
    val userName: String = "",
    val activeAssistantId: String = "emma",
    val assistantNames: Map<String, String> = mapOf(
        "emma" to "Emma",
        "sophia" to "Sophia",
        "alex" to "Alex",
        "james" to "James"
    ),
    val backendUrl: String = "",
    val appApiToken: String = "",
    val model: String = "gpt-5-mini",
    val silenceTimeoutMs: Int = 3500,
    val autoPauseTimeoutMs: Int = 60_000,
    val commands: CommandSettings = CommandSettings(),
    val feedbackLevel: FeedbackLevel = FeedbackLevel.Medium
)

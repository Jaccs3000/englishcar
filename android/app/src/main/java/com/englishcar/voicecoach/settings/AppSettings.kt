package com.englishcar.voicecoach.settings

data class CommandSettings(
    val pause: String = "hold on",
    val resume: String = "let's continue",
    val finish: String = "finish",
    val closeApp: String = "close app"
)

data class AppSettings(
    val isFirstLaunchComplete: Boolean = false,
    val userName: String = "",
    val activeAssistantId: String = "female",
    val assistantNames: Map<String, String> = mapOf(
        "female" to "Isa",
        "male" to "Alex"
    ),
    val backendUrl: String = "",
    val appApiToken: String = "",
    val model: String = "gemini-2.5-flash-lite",
    val silenceTimeoutMs: Int = 1600,
    val autoPauseTimeoutMs: Int = 60_000,
    val autoFinishTimeoutMs: Int = 600_000,
    val commands: CommandSettings = CommandSettings()
)

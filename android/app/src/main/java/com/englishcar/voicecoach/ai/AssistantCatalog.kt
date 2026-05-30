package com.englishcar.voicecoach.ai

data class AssistantProfile(
    val id: String,
    val defaultName: String,
    val personality: String
)

object AssistantCatalog {
    private val assistants = listOf(
        AssistantProfile("female", "Isa", "Female voice: clear, neutral, paused, didactic American English"),
        AssistantProfile("male", "Alex", "Male voice: clear, neutral, paused, didactic American English")
    )

    fun find(id: String): AssistantProfile {
        return assistants.firstOrNull { it.id == id } ?: assistants.first()
    }
}

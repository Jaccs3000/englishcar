package com.englishcar.voicecoach.ai

data class AssistantProfile(
    val id: String,
    val defaultName: String,
    val personality: String
)

object AssistantCatalog {
    private val assistants = listOf(
        AssistantProfile("emma", "Emma", "Young and upbeat"),
        AssistantProfile("sophia", "Sophia", "Mature and warm"),
        AssistantProfile("alex", "Alex", "Young and casual"),
        AssistantProfile("james", "James", "Mature and calm")
    )

    fun find(id: String): AssistantProfile {
        return assistants.firstOrNull { it.id == id } ?: assistants.first()
    }
}

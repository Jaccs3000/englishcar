package com.englishcar.voicecoach.ai

data class ContextTurn(
    val role: String,
    val text: String
)

data class ConversationRequest(
    val requestId: String,
    val type: String,
    val userText: String? = null,
    val assistantId: String,
    val assistantName: String,
    val assistantPersonality: String,
    val userName: String? = null,
    val model: String,
    val feedbackLevel: String,
    val locale: String = "en-US",
    val recentContext: List<ContextTurn>
)

data class AiFinalResponse(
    val spokenReply: String,
    val correction: String? = null,
    val naturalAlternative: String? = null,
    val shortExplanation: String? = null,
    val shouldSaveFeedback: Boolean = false
)

data class AvailableModels(
    val models: List<String>,
    val defaultModel: String
)

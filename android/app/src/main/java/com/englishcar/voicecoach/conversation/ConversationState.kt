package com.englishcar.voicecoach.conversation

enum class ConversationState {
    Idle,
    Listening,
    AwaitingUser,
    WaitingAI,
    Speaking,
    Paused,
    Interrupted,
    Error
}

data class ConversationUiState(
    val state: ConversationState = ConversationState.Idle,
    val lastUserText: String = "",
    val userTextStatus: String = "",
    val lastAssistantText: String = "",
    val errorMessage: String? = null,
    val isPermissionRequired: Boolean = false
)

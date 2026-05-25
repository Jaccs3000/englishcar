package com.englishcar.voicecoach.history

data class FeedbackEntry(
    val id: Long = 0,
    val originalPhrase: String,
    val correctedPhrase: String?,
    val naturalAlternative: String?,
    val shortExplanation: String?,
    val assistantId: String,
    val model: String,
    val timestamp: Long
)

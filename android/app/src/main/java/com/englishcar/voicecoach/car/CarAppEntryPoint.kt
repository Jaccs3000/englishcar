package com.englishcar.voicecoach.car

import com.englishcar.voicecoach.conversation.ConversationManager
import com.englishcar.voicecoach.service.VoiceSessionController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CarAppEntryPoint {
    fun conversationManager(): ConversationManager
    fun voiceSessionController(): VoiceSessionController
}

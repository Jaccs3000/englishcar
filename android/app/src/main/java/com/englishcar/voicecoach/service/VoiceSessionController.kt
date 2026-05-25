package com.englishcar.voicecoach.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceSessionController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun start() {
        val intent = Intent(context, VoiceSessionService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop() {
        context.stopService(Intent(context, VoiceSessionService::class.java))
    }
}

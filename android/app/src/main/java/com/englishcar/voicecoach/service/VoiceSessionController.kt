package com.englishcar.voicecoach.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.englishcar.voicecoach.diagnostics.DiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceSessionController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticsLogger: DiagnosticsLogger
) {
    fun start() {
        diagnosticsLogger.add("ServiceCtl", "start requested")
        runCatching {
            val intent = Intent(context, VoiceSessionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }.onSuccess {
            diagnosticsLogger.add("ServiceCtl", "start dispatched")
        }.onFailure { error ->
            diagnosticsLogger.add("ServiceCtl", "start failed ${error.javaClass.simpleName} message=${error.message.orEmpty().take(120)}")
        }
    }

    fun stop() {
        diagnosticsLogger.add("ServiceCtl", "stop requested")
        runCatching {
            context.stopService(Intent(context, VoiceSessionService::class.java))
        }.onFailure { error ->
            diagnosticsLogger.add("ServiceCtl", "stop failed ${error.javaClass.simpleName} message=${error.message.orEmpty().take(120)}")
        }
    }
}

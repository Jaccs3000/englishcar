package com.englishcar.voicecoach.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.englishcar.voicecoach.diagnostics.DiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFocusHandler @Inject constructor(
    @ApplicationContext context: Context,
    private val diagnosticsLogger: DiagnosticsLogger
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var onInterrupted: (() -> Unit)? = null
    private var focusRequest: AudioFocusRequest? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        diagnosticsLogger.add("AudioFocus", "focus change=$change")
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                diagnosticsLogger.add("AudioFocus", "focus loss triggering interruption")
                onInterrupted?.invoke()
            }
        }
    }

    fun request(onInterrupted: () -> Unit): Boolean {
        diagnosticsLogger.add("AudioFocus", "request start sdk=${Build.VERSION.SDK_INT}")
        this.onInterrupted = onInterrupted
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        diagnosticsLogger.add("AudioFocus", "request result=$result granted=$granted")
        return granted
    }

    fun abandon() {
        diagnosticsLogger.add("AudioFocus", "abandon start hasRequest=${focusRequest != null}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
        focusRequest = null
        onInterrupted = null
        diagnosticsLogger.add("AudioFocus", "abandon done")
    }
}

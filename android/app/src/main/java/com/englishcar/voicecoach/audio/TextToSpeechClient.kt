package com.englishcar.voicecoach.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Singleton
class TextToSpeechClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var initResult: CompletableDeferred<Boolean>? = null

    suspend fun speak(text: String): Boolean {
        val isReady = ensureReady()
        if (!isReady) return false
        val engine = tts ?: return false
        chooseVoice(preferMale = null)?.let { engine.voice = it }
        engine.setPitch(1.0f)
        engine.setSpeechRate(0.95f)
        return speakWithEngine(engine, text)
    }

    suspend fun preview(text: String, assistantId: String, preferMale: Boolean): Boolean {
        val isReady = ensureReady()
        if (!isReady) return false
        val engine = tts ?: return false
        val style = voiceStyle(assistantId)
        chooseVoice(preferMale = preferMale, index = style.voiceIndex)?.let { engine.voice = it }
        engine.setPitch(style.pitch)
        engine.setSpeechRate(style.rate)
        return speakWithEngine(engine, text)
    }

    suspend fun speakAsAssistant(text: String, assistantId: String): Boolean {
        val isReady = ensureReady()
        if (!isReady) return false
        val engine = tts ?: return false
        val style = voiceStyle(assistantId)
        chooseVoice(
            preferMale = assistantId == "alex" || assistantId == "james",
            index = style.voiceIndex
        )?.let { engine.voice = it }
        engine.setPitch(style.pitch)
        engine.setSpeechRate(style.rate)
        return speakWithEngine(engine, text)
    }

    private suspend fun speakWithEngine(engine: TextToSpeech, text: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val utteranceId = UUID.randomUUID().toString()
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(doneUtteranceId: String?) {
                    if (doneUtteranceId == utteranceId && continuation.isActive) {
                        continuation.resume(true)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(errorUtteranceId: String?) {
                    if (errorUtteranceId == utteranceId && continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            continuation.invokeOnCancellation { engine.stop() }
        }
    }

    fun stop() {
        tts?.stop()
    }

    private fun chooseVoice(preferMale: Boolean?, index: Int = 0): Voice? {
        val voices = tts?.voices.orEmpty()
            .filter { it.locale.language == Locale.US.language && it.locale.country == Locale.US.country }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.latency })

        if (voices.isEmpty()) return null

        val embedded = voices.filterNot { it.isNetworkConnectionRequired }.ifEmpty { voices }
        val maleHints = listOf("male", "man")
        val femaleHints = listOf("female", "woman", "a", "b", "c", "e", "f")

        fun Voice.matches(hints: List<String>): Boolean {
            val lower = name.lowercase()
            return hints.any { hint ->
                lower.contains("-$hint-") || lower.endsWith("-$hint") || lower.contains("_${hint}_")
            }
        }

        val candidates = when (preferMale) {
            true -> embedded.filter { it.matches(maleHints) }.ifEmpty { listOfNotNull(embedded.lastOrNull()) }
            false -> embedded.filter { it.matches(femaleHints) }.ifEmpty { embedded }
            null -> embedded
        }
        return candidates[index.mod(candidates.size)]
    }

    private data class VoiceStyle(val voiceIndex: Int, val pitch: Float, val rate: Float)

    private fun voiceStyle(assistantId: String): VoiceStyle {
        return when (assistantId) {
            "emma" -> VoiceStyle(voiceIndex = 1, pitch = 1.18f, rate = 1.04f)
            "sophia" -> VoiceStyle(voiceIndex = 0, pitch = 0.94f, rate = 0.88f)
            "alex" -> VoiceStyle(voiceIndex = 0, pitch = 0.86f, rate = 1.00f)
            "james" -> VoiceStyle(voiceIndex = 0, pitch = 0.64f, rate = 0.82f)
            else -> VoiceStyle(voiceIndex = 0, pitch = 1.0f, rate = 0.95f)
        }
    }

    private suspend fun ensureReady(): Boolean {
        initResult?.let { return it.await() }

        val deferred = CompletableDeferred<Boolean>()
        initResult = deferred

        tts = TextToSpeech(context) { status ->
            val engine = tts
            val ready = status == TextToSpeech.SUCCESS && engine != null
            if (ready) {
                engine?.language = Locale.US
                engine?.setSpeechRate(0.95f)
            }
            deferred.complete(ready)
        }

        return deferred.await()
    }
}

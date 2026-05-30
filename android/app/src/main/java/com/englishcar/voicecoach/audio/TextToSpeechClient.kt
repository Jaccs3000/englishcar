package com.englishcar.voicecoach.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.englishcar.voicecoach.diagnostics.DiagnosticsLogger
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
    @ApplicationContext private val context: Context,
    private val diagnosticsLogger: DiagnosticsLogger
) {
    private var tts: TextToSpeech? = null
    private var initResult: CompletableDeferred<Boolean>? = null

    suspend fun warmUp(): Boolean {
        diagnosticsLogger.add("TTS", "warmUp requested")
        return ensureReady()
    }

    suspend fun speak(text: String): Boolean {
        diagnosticsLogger.add("TTS", "speak generic chars=${text.length}")
        val isReady = ensureReady()
        if (!isReady) {
            diagnosticsLogger.add("TTS", "speak generic aborted notReady")
            return false
        }
        val engine = tts ?: run {
            diagnosticsLogger.add("TTS", "speak generic aborted engineMissing")
            return false
        }
        chooseVoice(preferMale = null)?.let { engine.voice = it }
        engine.setPitch(1.0f)
        engine.setSpeechRate(0.95f)
        return speakWithEngine(engine, text)
    }

    suspend fun preview(text: String, assistantId: String, preferMale: Boolean): Boolean {
        diagnosticsLogger.add("TTS", "preview assistant=$assistantId preferMale=$preferMale chars=${text.length}")
        val isReady = ensureReady()
        if (!isReady) {
            diagnosticsLogger.add("TTS", "preview aborted notReady assistant=$assistantId")
            return false
        }
        val engine = tts ?: run {
            diagnosticsLogger.add("TTS", "preview aborted engineMissing assistant=$assistantId")
            return false
        }
        val style = voiceStyle(assistantId)
        chooseVoice(preferMale = preferMale, index = style.voiceIndex)?.let { engine.voice = it }
        engine.setPitch(style.pitch)
        engine.setSpeechRate(style.rate)
        return speakWithEngine(engine, text)
    }

    suspend fun speakAsAssistant(text: String, assistantId: String): Boolean {
        diagnosticsLogger.add("TTS", "speak assistant=$assistantId chars=${text.length}")
        val isReady = ensureReady()
        if (!isReady) {
            diagnosticsLogger.add("TTS", "speak assistant aborted notReady assistant=$assistantId")
            return false
        }
        val engine = tts ?: run {
            diagnosticsLogger.add("TTS", "speak assistant aborted engineMissing assistant=$assistantId")
            return false
        }
        val style = voiceStyle(assistantId)
        chooseVoice(
            preferMale = assistantId == "male",
            index = style.voiceIndex
        )?.let { engine.voice = it }
        engine.setPitch(style.pitch)
        engine.setSpeechRate(style.rate)
        return speakWithEngine(engine, text)
    }

    private suspend fun speakWithEngine(engine: TextToSpeech, text: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val utteranceId = UUID.randomUUID().toString()
            diagnosticsLogger.add("TTS", "utterance start id=${utteranceId.take(8)} chars=${text.length}")
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId != null) {
                        diagnosticsLogger.add("TTS", "utterance speaking id=${utteranceId.take(8)}")
                    }
                }

                override fun onDone(doneUtteranceId: String?) {
                    if (doneUtteranceId == utteranceId && continuation.isActive) {
                        diagnosticsLogger.add("TTS", "utterance done id=${utteranceId.take(8)}")
                        continuation.resume(true)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(errorUtteranceId: String?) {
                    if (errorUtteranceId == utteranceId && continuation.isActive) {
                        diagnosticsLogger.add("TTS", "utterance error id=${utteranceId.take(8)}")
                        continuation.resume(false)
                    }
                }
            })
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            diagnosticsLogger.add("TTS", "utterance enqueue id=${utteranceId.take(8)} result=$result")
            if (result == TextToSpeech.ERROR && continuation.isActive) {
                continuation.resume(false)
            }
            continuation.invokeOnCancellation {
                diagnosticsLogger.add("TTS", "utterance cancelled id=${utteranceId.take(8)}")
                engine.stop()
            }
        }
    }

    fun stop() {
        diagnosticsLogger.add("TTS", "stop requested")
        tts?.stop()
    }

    private fun chooseVoice(preferMale: Boolean?, index: Int = 0): Voice? {
        val voices = tts?.voices.orEmpty()
            .filter { it.locale.language == Locale.US.language && it.locale.country == Locale.US.country }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.latency })

        if (voices.isEmpty()) {
            diagnosticsLogger.add("TTS", "voice selection failed noUsVoices")
            return null
        }

        val maleHints = listOf("male", "man")
        val femaleHints = listOf("female", "woman")

        fun Voice.matches(hints: List<String>): Boolean {
            val lower = name.lowercase()
            return hints.any { hint ->
                lower.contains("-$hint-") || lower.endsWith("-$hint") || lower.contains("_${hint}_")
            }
        }

        fun Voice.naturalScore(): Int {
            val lower = name.lowercase()
            val qualityScore = quality * 100
            val latencyPenalty = latency * 10
            val networkBonus = if (isNetworkConnectionRequired) 18 else 0
            val naturalNameBonus = when {
                "neural" in lower || "wavenet" in lower || "studio" in lower -> 50
                "enhanced" in lower || "premium" in lower -> 30
                else -> 0
            }
            val genderBonus = when {
                preferMale == true && matches(maleHints) -> 80
                preferMale == false && matches(femaleHints) -> 80
                else -> 0
            }
            return qualityScore + naturalNameBonus + genderBonus + networkBonus - latencyPenalty
        }

        val candidates = when (preferMale) {
            true -> voices.filter { it.matches(maleHints) }.ifEmpty {
                voices
                    .filterNot { it.name.contains("sfg", ignoreCase = true) }
                    .ifEmpty { voices }
                    .sortedWith(
                        compareBy<Voice> { it.isNetworkConnectionRequired }
                            .thenByDescending { it.quality }
                            .thenBy { it.latency }
                    )
            }
            false -> voices.filter { it.matches(femaleHints) }.ifEmpty { voices }
            null -> voices
        }
            .sortedByDescending { it.naturalScore() }

        return candidates[index.mod(candidates.size)].also {
            diagnosticsLogger.add(
                "TTS",
                "voice selected name=${it.name} locale=${it.locale} network=${it.isNetworkConnectionRequired} quality=${it.quality} latency=${it.latency}"
            )
        }
    }

    private data class VoiceStyle(val voiceIndex: Int, val pitch: Float, val rate: Float)

    private fun voiceStyle(assistantId: String): VoiceStyle {
        return when (assistantId) {
            "female" -> VoiceStyle(voiceIndex = 0, pitch = 1.0f, rate = 0.9f)
            "male" -> VoiceStyle(voiceIndex = 0, pitch = 0.78f, rate = 0.92f)
            else -> VoiceStyle(voiceIndex = 0, pitch = 1.0f, rate = 0.95f)
        }
    }

    private suspend fun ensureReady(): Boolean {
        initResult?.let {
            diagnosticsLogger.add("TTS", "ensureReady await existing")
            return it.await()
        }

        val deferred = CompletableDeferred<Boolean>()
        initResult = deferred

        diagnosticsLogger.add("TTS", "ensureReady init start")
        tts = TextToSpeech(context) { status ->
            val engine = tts
            val ready = status == TextToSpeech.SUCCESS && engine != null
            if (ready) {
                engine?.language = Locale.US
                engine?.setSpeechRate(0.95f)
            }
            diagnosticsLogger.add("TTS", "ensureReady init result status=$status ready=$ready")
            deferred.complete(ready)
        }

        return deferred.await()
    }
}

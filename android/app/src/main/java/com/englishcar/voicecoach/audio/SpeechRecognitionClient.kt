package com.englishcar.voicecoach.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data object EndOfSpeech : SpeechEvent
    data class Result(val text: String) : SpeechEvent
    data class Error(val code: Int) : SpeechEvent
}

private const val TAG = "EnglishCarSpeech"

@Singleton
class SpeechRecognitionClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SpeechEvent> = _events

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(silenceTimeoutMs: Int) {
        mainHandler.post {
            stopListeningOnMainThread()
            mainHandler.postDelayed({
                val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = speechRecognizer
                speechRecognizer.setRecognitionListener(listener)
                Log.d(TAG, "startListening")
                speechRecognizer.startListening(recognizerIntent(silenceTimeoutMs))
            }, 300L)
        }
    }

    fun stopListening() {
        mainHandler.post {
            stopListeningOnMainThread()
        }
    }

    private fun stopListeningOnMainThread() {
        recognizer?.setRecognitionListener(null)
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        Log.d(TAG, "stopListening")
    }

    private fun recognizerIntent(silenceTimeoutMs: Int): Intent {
        val completeSilence = silenceTimeoutMs.coerceIn(1500, 60_000).toLong()
        val possibleSilence = (completeSilence * 0.7f).toLong().coerceAtLeast(1200L)
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, completeSilence)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, possibleSilence)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
            _events.tryEmit(SpeechEvent.Ready)
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
            _events.tryEmit(SpeechEvent.EndOfSpeech)
        }

        override fun onError(error: Int) {
            Log.d(TAG, "onError code=$error")
            _events.tryEmit(SpeechEvent.Error(error))
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            Log.d(TAG, "onResults text=$text")
            if (text.isNotBlank()) {
                _events.tryEmit(SpeechEvent.Result(text))
            } else {
                _events.tryEmit(SpeechEvent.Error(SpeechRecognizer.ERROR_NO_MATCH))
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            Log.d(TAG, "onPartialResults text=$text")
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

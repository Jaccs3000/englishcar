package com.englishcar.voicecoach.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.englishcar.voicecoach.ai.BackendConversationClient
import com.englishcar.voicecoach.diagnostics.DiagnosticsLogger
import com.englishcar.voicecoach.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Intent
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data object EndOfSpeech : SpeechEvent
    data class Result(val text: String) : SpeechEvent
    data class Error(val code: Int) : SpeechEvent
}

private const val TAG = "EnglishCarSpeech"

@Singleton
class SpeechRecognitionClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backendConversationClient: BackendConversationClient,
    private val diagnosticsLogger: DiagnosticsLogger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SpeechEvent> = _events
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var speechRecognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun startListening(silenceTimeoutMs: Int) {
        diagnosticsLogger.add("Speech", "startListening silenceTimeoutMs=$silenceTimeoutMs")
        stopListening()
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recordingJob = scope.launch(Dispatchers.Main) {
                startPlatformRecognition(silenceTimeoutMs)
            }
            return
        }
        recordingJob = scope.launch {
            recordUntilPhraseComplete(silenceTimeoutMs)
        }
    }

    fun stopListening() {
        recordingJob?.cancel()
        recordingJob = null
        speechRecognizer?.runCatching {
            cancel()
            destroy()
        }
        speechRecognizer = null
        audioRecord?.runCatching {
            stop()
            release()
        }
        audioRecord = null
        Log.d(TAG, "stopListening")
        diagnosticsLogger.add("Speech", "stopListening")
    }

    private fun startPlatformRecognition(silenceTimeoutMs: Int) {
        if (!isAvailable()) {
            diagnosticsLogger.add("Speech", "microphone permission missing")
            _events.tryEmit(SpeechEvent.Error(ERROR_PERMISSION))
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceTimeoutMs.toLong())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceTimeoutMs.toLong())
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                diagnosticsLogger.add("Speech", "platform ready")
                _events.tryEmit(SpeechEvent.Ready)
            }

            override fun onBeginningOfSpeech() {
                diagnosticsLogger.add("Speech", "platform beginning")
            }

            override fun onEndOfSpeech() {
                diagnosticsLogger.add("Speech", "platform end")
                _events.tryEmit(SpeechEvent.EndOfSpeech)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val text = matches.firstOrNull().orEmpty().trim()
                val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull() ?: -1f
                diagnosticsLogger.add("Speech", "platform results count=${matches.size} chars=${text.length} confidence=$confidence text=${text.take(80)}")
                speechRecognizer?.destroy()
                speechRecognizer = null
                if (text.isBlank() || shouldIgnoreAmbientResult(text, confidence)) {
                    _events.tryEmit(SpeechEvent.Error(ERROR_NO_MATCH))
                } else {
                    _events.tryEmit(SpeechEvent.Result(text))
                }
            }

            override fun onError(error: Int) {
                diagnosticsLogger.add("Speech", "platform error code=$error")
                speechRecognizer?.destroy()
                speechRecognizer = null
                val mappedError = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ERROR_NO_MATCH
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ERROR_PERMISSION
                    SpeechRecognizer.ERROR_AUDIO -> ERROR_AUDIO_RECORD
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER -> ERROR_TRANSCRIPTION_FAILED
                    else -> ERROR_NO_MATCH
                }
                _events.tryEmit(SpeechEvent.Error(mappedError))
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        diagnosticsLogger.add("Speech", "platform start")
        recognizer.startListening(intent)
    }

    private fun shouldIgnoreAmbientResult(text: String, confidence: Float): Boolean {
        val normalized = text.lowercase(Locale.US).replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
        val words = normalized.split(" ").filter { it.isNotBlank() }
        val knownSingleWordCommands = setOf("pause", "resume", "finish", "stop", "close", "exit")
        if (words.size <= 1 && normalized !in knownSingleWordCommands) {
            diagnosticsLogger.add("Speech", "ignore ambient singleWord=$normalized confidence=$confidence")
            return true
        }
        if (confidence in 0f..0.38f && words.size <= 2 && normalized !in knownSingleWordCommands) {
            diagnosticsLogger.add("Speech", "ignore low confidence text=$normalized confidence=$confidence")
            return true
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordUntilPhraseComplete(silenceTimeoutMs: Int) {
        if (!isAvailable()) {
            diagnosticsLogger.add("Speech", "microphone permission missing")
            _events.tryEmit(SpeechEvent.Error(ERROR_PERMISSION))
            return
        }

        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            diagnosticsLogger.add("Speech", "AudioRecord minBuffer invalid value=$minBuffer")
            _events.tryEmit(SpeechEvent.Error(ERROR_AUDIO_RECORD))
            return
        }

        val bufferSize = minBuffer.coerceAtLeast(sampleRate)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            diagnosticsLogger.add("Speech", "AudioRecord not initialized state=${recorder.state}")
            recorder.release()
            _events.tryEmit(SpeechEvent.Error(ERROR_AUDIO_RECORD))
            return
        }
        audioRecord = recorder

        try {
            recorder.startRecording()
            Log.d(TAG, "audioRecord start")
            diagnosticsLogger.add(
                "Speech",
                "audioRecord start sampleRate=$sampleRate bufferSize=$bufferSize startThreshold=$SPEECH_START_THRESHOLD continueThreshold=$SPEECH_CONTINUE_THRESHOLD peakStart=$PEAK_START_THRESHOLD peakStartMinAvg=$PEAK_START_MIN_AVERAGE"
            )
            _events.tryEmit(SpeechEvent.Ready)

            val buffer = ShortArray(1024)
            val speechBytes = ByteArrayOutputStream()
            val preRollBytes = ByteArrayOutputStream()
            var hasSpeech = false
            var silenceMs = 0L
            var speechMs = 0L
            var chunks = 0
            var peakLevel = 0
            var listeningMs = 0L
            var startCandidateMs = 0L
            var voicedMs = 0L
            var noiseFloor = 120f
            val maxSpeechMs = MAX_SPEECH_MS
            val silenceLimitMs = silenceTimeoutMs.coerceIn(MIN_UTTERANCE_SILENCE_MS, MAX_UTTERANCE_SILENCE_MS).toLong()

            while (recordingJob?.isActive == true) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val pcmChunk = buffer.toPcm16Bytes(read)
                val chunkMs = (read * 1000L) / sampleRate
                val level = averageAmplitude(buffer, read)
                val chunkPeak = peakAmplitude(buffer, read)
                listeningMs += chunkMs
                chunks += 1
                peakLevel = maxOf(peakLevel, level)
                if (!hasSpeech && startCandidateMs == 0L) {
                    noiseFloor = (noiseFloor * 0.94f) + (level * 0.06f)
                }
                val effectiveStartThreshold = maxOf(SPEECH_START_THRESHOLD, (noiseFloor * START_NOISE_MULTIPLIER).toInt())
                val effectiveContinueThreshold = maxOf(SPEECH_CONTINUE_THRESHOLD, (noiseFloor * CONTINUE_NOISE_MULTIPLIER).toInt())
                if (chunks % 24 == 0) {
                    Log.d(
                        TAG,
                        "audio level=$level chunkPeak=$chunkPeak peak=$peakLevel noise=${noiseFloor.toInt()} startThreshold=$effectiveStartThreshold continueThreshold=$effectiveContinueThreshold candidateMs=$startCandidateMs hasSpeech=$hasSpeech speechMs=$speechMs silenceMs=$silenceMs voicedMs=$voicedMs"
                    )
                    diagnosticsLogger.add(
                        "Speech",
                        "level=$level chunkPeak=$chunkPeak peak=$peakLevel noise=${noiseFloor.toInt()} startTh=$effectiveStartThreshold contTh=$effectiveContinueThreshold candidateMs=$startCandidateMs hasSpeech=$hasSpeech speechMs=$speechMs silenceMs=$silenceMs voicedMs=$voicedMs"
                    )
                }
                val isStartCandidate = listeningMs >= START_ARMING_DELAY_MS &&
                    (level > effectiveStartThreshold || (chunkPeak > PEAK_START_THRESHOLD && level > PEAK_START_MIN_AVERAGE))
                val continuesSpeech = level > effectiveContinueThreshold

                if (!hasSpeech) {
                    preRollBytes.write(pcmChunk)
                    preRollBytes.trimToLastBytes(PRE_ROLL_BYTES)
                }

                if (!hasSpeech && isStartCandidate) {
                    startCandidateMs += chunkMs
                    if (startCandidateMs == chunkMs) {
                        diagnosticsLogger.add(
                            "Speech",
                            "start candidate level=$level chunkPeak=$chunkPeak noise=${noiseFloor.toInt()} startTh=$effectiveStartThreshold"
                        )
                    }
                } else if (!hasSpeech) {
                    if (startCandidateMs > 0L) {
                        diagnosticsLogger.add("Speech", "start candidate reset candidateMs=$startCandidateMs level=$level startTh=$effectiveStartThreshold")
                    }
                    startCandidateMs = 0L
                }

                if (!hasSpeech && startCandidateMs >= MIN_START_CANDIDATE_MS) {
                    Log.d(TAG, "speech started level=$level")
                    diagnosticsLogger.add("Speech", "speech started level=$level chunkPeak=$chunkPeak noise=${noiseFloor.toInt()} candidateMs=$startCandidateMs")
                    hasSpeech = true
                    silenceMs = 0
                    speechBytes.write(preRollBytes.toByteArray())
                } else if (hasSpeech && continuesSpeech) {
                    silenceMs = 0
                } else if (hasSpeech) {
                    silenceMs += chunkMs
                }

                if (hasSpeech) {
                    speechMs += chunkMs
                    if (continuesSpeech) {
                        voicedMs += chunkMs
                    }
                    speechBytes.write(pcmChunk)
                }

                if (hasSpeech && speechMs >= MIN_UTTERANCE_MS && (silenceMs >= silenceLimitMs || speechMs >= maxSpeechMs)) {
                    Log.d(TAG, "speech complete bytes=${speechBytes.size()} peak=$peakLevel silenceMs=$silenceMs speechMs=$speechMs voicedMs=$voicedMs")
                    diagnosticsLogger.add("Speech", "speech complete bytes=${speechBytes.size()} peak=$peakLevel silenceMs=$silenceMs speechMs=$speechMs voicedMs=$voicedMs")
                    _events.tryEmit(SpeechEvent.EndOfSpeech)
                    transcribeAndEmit(speechBytes.toByteArray(), sampleRate, voicedMs, peakLevel)
                    break
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "AudioRecord failed", error)
            diagnosticsLogger.add("Speech", "AudioRecord failed ${error.javaClass.simpleName}")
            _events.tryEmit(SpeechEvent.Error(ERROR_AUDIO_RECORD))
        } finally {
            recorder.runCatching {
                stop()
                release()
            }
            if (audioRecord == recorder) {
                audioRecord = null
            }
            Log.d(TAG, "audioRecord stop")
            diagnosticsLogger.add("Speech", "audioRecord stop")
        }
    }

    private suspend fun transcribeAndEmit(pcm: ByteArray, sampleRate: Int, voicedMs: Long, peakLevel: Int) {
        if (pcm.size < MIN_SPEECH_BYTES) {
            diagnosticsLogger.add("Speech", "speech too short bytes=${pcm.size}")
            _events.tryEmit(SpeechEvent.Error(ERROR_NO_MATCH))
            return
        }
        diagnosticsLogger.add("Speech", "send transcription voicedMs=$voicedMs peak=$peakLevel")

        val settings = settingsRepository.currentSettings()
        if (settings.backendUrl.isBlank() || settings.appApiToken.isBlank()) {
            diagnosticsLogger.add("Speech", "backend config missing for transcription")
            _events.tryEmit(SpeechEvent.Error(ERROR_BACKEND_NOT_CONFIGURED))
            return
        }

        val trimmedPcm = trimTrailingSilence(pcm)
        val startedAt = System.currentTimeMillis()
        val text = try {
            withContext(Dispatchers.IO) {
                backendConversationClient.transcribe(
                    backendUrl = settings.backendUrl,
                    appApiToken = settings.appApiToken,
                    wavAudio = wavFromPcm(trimmedPcm, sampleRate)
                )
            }
        } catch (error: Exception) {
            Log.e(TAG, "transcription failed", error)
            diagnosticsLogger.add("Speech", "transcription failed ${error.javaClass.simpleName}")
            _events.tryEmit(SpeechEvent.Error(ERROR_TRANSCRIPTION_FAILED))
            return
        }
        Log.d(TAG, "transcription durationMs=${System.currentTimeMillis() - startedAt} bytes=${trimmedPcm.size}")
        diagnosticsLogger.add("Speech", "transcription durationMs=${System.currentTimeMillis() - startedAt} bytes=${trimmedPcm.size} chars=${text.length}")

        if (text.isBlank()) {
            Log.d(TAG, "empty transcription")
            diagnosticsLogger.add("Speech", "empty transcription")
            _events.tryEmit(SpeechEvent.Error(ERROR_NO_MATCH))
        } else {
            Log.d(TAG, "transcription chars=${text.length}")
            _events.tryEmit(SpeechEvent.Result(text))
        }
    }

    private fun averageAmplitude(buffer: ShortArray, read: Int): Int {
        var total = 0L
        for (index in 0 until read) {
            total += abs(buffer[index].toInt())
        }
        return (total / read).toInt()
    }

    private fun peakAmplitude(buffer: ShortArray, read: Int): Int {
        var peak = 0
        for (index in 0 until read) {
            peak = maxOf(peak, abs(buffer[index].toInt()))
        }
        return peak
    }

    private fun ByteArrayOutputStream.writePcm16(buffer: ShortArray, read: Int) {
        write(buffer.toPcm16Bytes(read))
    }

    private fun ShortArray.toPcm16Bytes(read: Int): ByteArray {
        val bytes = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until read) {
            bytes.putShort(this[index])
        }
        return bytes.array()
    }

    private fun ByteArrayOutputStream.trimToLastBytes(maxBytes: Int) {
        if (size() <= maxBytes) return
        val bytes = toByteArray()
        reset()
        write(bytes.copyOfRange(bytes.size - maxBytes, bytes.size))
    }

    private fun wavFromPcm(pcm: ByteArray, sampleRate: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val byteRate = sampleRate * 2
        val dataSize = pcm.size
        val totalSize = 36 + dataSize

        fun writeString(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
        fun writeInt(value: Int) {
            output.write(byteArrayOf(
                (value and 0xff).toByte(),
                ((value shr 8) and 0xff).toByte(),
                ((value shr 16) and 0xff).toByte(),
                ((value shr 24) and 0xff).toByte()
            ))
        }
        fun writeShort(value: Int) {
            output.write(byteArrayOf(
                (value and 0xff).toByte(),
                ((value shr 8) and 0xff).toByte()
            ))
        }

        writeString("RIFF")
        writeInt(totalSize)
        writeString("WAVE")
        writeString("fmt ")
        writeInt(16)
        writeShort(1)
        writeShort(1)
        writeInt(sampleRate)
        writeInt(byteRate)
        writeShort(2)
        writeShort(16)
        writeString("data")
        writeInt(dataSize)
        output.write(pcm)
        return output.toByteArray()
    }

    private fun trimTrailingSilence(pcm: ByteArray): ByteArray {
        val keepTailBytes = 12_000
        var lastSpeechByte = pcm.size
        var index = 0
        while (index + 1 < pcm.size) {
            val sample = ((pcm[index + 1].toInt() shl 8) or (pcm[index].toInt() and 0xff)).toShort()
            if (abs(sample.toInt()) > SPEECH_CONTINUE_THRESHOLD) {
                lastSpeechByte = index + keepTailBytes
            }
            index += 2
        }
        return pcm.copyOf(lastSpeechByte.coerceIn(0, pcm.size))
    }

    companion object {
        const val ERROR_AUDIO_RECORD = 1001
        const val ERROR_NO_MATCH = 1002
        const val ERROR_PERMISSION = 1003
        const val ERROR_BACKEND_NOT_CONFIGURED = 1004
        const val ERROR_TRANSCRIPTION_FAILED = 1005
        private const val MIN_UTTERANCE_MS = 900L
        private const val START_ARMING_DELAY_MS = 200L
        private const val MIN_START_CANDIDATE_MS = 32L
        private const val MIN_UTTERANCE_SILENCE_MS = 900
        private const val MAX_SPEECH_MS = 90_000L
        private const val MAX_UTTERANCE_SILENCE_MS = 8_000
        private const val SPEECH_START_THRESHOLD = 220
        private const val SPEECH_CONTINUE_THRESHOLD = 180
        private const val START_NOISE_MULTIPLIER = 1.25f
        private const val CONTINUE_NOISE_MULTIPLIER = 1.35f
        private const val PEAK_START_THRESHOLD = 900
        private const val PEAK_START_MIN_AVERAGE = 120
        private const val PRE_ROLL_BYTES = 16_000
        private const val MIN_SPEECH_BYTES = 2_400
    }
}

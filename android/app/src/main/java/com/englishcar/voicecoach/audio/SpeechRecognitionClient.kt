package com.englishcar.voicecoach.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.englishcar.voicecoach.ai.BackendConversationClient
import com.englishcar.voicecoach.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    private val backendConversationClient: BackendConversationClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SpeechEvent> = _events
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    fun isAvailable(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun startListening(silenceTimeoutMs: Int) {
        stopListening()
        recordingJob = scope.launch {
            recordUntilPhraseComplete(silenceTimeoutMs)
        }
    }

    fun stopListening() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.runCatching {
            stop()
            release()
        }
        audioRecord = null
        Log.d(TAG, "stopListening")
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordUntilPhraseComplete(silenceTimeoutMs: Int) {
        if (!isAvailable()) {
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
            _events.tryEmit(SpeechEvent.Error(ERROR_AUDIO_RECORD))
            return
        }

        val bufferSize = minBuffer.coerceAtLeast(sampleRate)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord = recorder

        try {
            recorder.startRecording()
            Log.d(TAG, "audioRecord start")
            _events.tryEmit(SpeechEvent.Ready)

            val buffer = ShortArray(1024)
            val speechBytes = ByteArrayOutputStream()
            var hasSpeech = false
            var silenceMs = 0L
            var speechMs = 0L
            val maxSpeechMs = 45_000L
            val silenceLimitMs = silenceTimeoutMs.coerceIn(700, 300_000).toLong()

            while (recordingJob?.isActive == true) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val chunkMs = (read * 1000L) / sampleRate
                val level = averageAmplitude(buffer, read)
                val isSpeech = level > SPEECH_THRESHOLD

                if (isSpeech) {
                    hasSpeech = true
                    silenceMs = 0
                } else if (hasSpeech) {
                    silenceMs += chunkMs
                }

                if (hasSpeech) {
                    speechMs += chunkMs
                    speechBytes.writePcm16(buffer, read)
                }

                if (hasSpeech && (silenceMs >= silenceLimitMs || speechMs >= maxSpeechMs)) {
                    _events.tryEmit(SpeechEvent.EndOfSpeech)
                    transcribeAndEmit(speechBytes.toByteArray(), sampleRate)
                    break
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "AudioRecord failed", error)
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
        }
    }

    private suspend fun transcribeAndEmit(pcm: ByteArray, sampleRate: Int) {
        if (pcm.size < MIN_SPEECH_BYTES) {
            _events.tryEmit(SpeechEvent.Error(ERROR_NO_MATCH))
            return
        }

        val settings = settingsRepository.currentSettings()
        if (settings.backendUrl.isBlank() || settings.appApiToken.isBlank()) {
            _events.tryEmit(SpeechEvent.Error(ERROR_BACKEND_NOT_CONFIGURED))
            return
        }

        val text = withContext(Dispatchers.IO) {
            backendConversationClient.transcribe(
                backendUrl = settings.backendUrl,
                appApiToken = settings.appApiToken,
                wavAudio = wavFromPcm(pcm, sampleRate)
            )
        }

        if (text.isBlank()) {
            _events.tryEmit(SpeechEvent.Error(ERROR_NO_MATCH))
        } else {
            Log.d(TAG, "transcription=$text")
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

    private fun ByteArrayOutputStream.writePcm16(buffer: ShortArray, read: Int) {
        val bytes = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until read) {
            bytes.putShort(buffer[index])
        }
        write(bytes.array())
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

    companion object {
        const val ERROR_AUDIO_RECORD = 1001
        const val ERROR_NO_MATCH = 1002
        const val ERROR_PERMISSION = 1003
        const val ERROR_BACKEND_NOT_CONFIGURED = 1004
        private const val SPEECH_THRESHOLD = 850
        private const val MIN_SPEECH_BYTES = 8_000
    }
}

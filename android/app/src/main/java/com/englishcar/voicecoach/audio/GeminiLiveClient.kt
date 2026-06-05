package com.englishcar.voicecoach.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.englishcar.voicecoach.BuildConfig
import com.englishcar.voicecoach.diagnostics.DiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

sealed interface GeminiLiveEvent {
    data object Connected : GeminiLiveEvent
    data object Listening : GeminiLiveEvent
    data class UserTranscript(val text: String) : GeminiLiveEvent
    data class AssistantTranscript(val text: String) : GeminiLiveEvent
    data object TurnComplete : GeminiLiveEvent
    data class Error(val message: String) : GeminiLiveEvent
}

@Singleton
class GeminiLiveClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticsLogger: DiagnosticsLogger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder().build()
    private val _events = MutableSharedFlow<GeminiLiveEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<GeminiLiveEvent> = _events

    private var webSocket: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var captureJob: Job? = null
    private var running = false
    private var setupSent = false
    private var inboundMessages = 0
    private var outboundRealtimeMessages = 0
    private var playbackQuietUntilMs = 0L

    fun isAvailable(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED && BuildConfig.GEMINI_API_KEY.isNotBlank()
    }

    fun start(systemInstruction: String, audioResponses: Boolean = true) {
        if (running) stop()
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            _events.tryEmit(GeminiLiveEvent.Error("Gemini API key is missing."))
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _events.tryEmit(GeminiLiveEvent.Error("Microphone permission is missing."))
            return
        }

        running = true
        setupSent = false
        inboundMessages = 0
        outboundRealtimeMessages = 0
        startPlayer()
        val model = BuildConfig.GEMINI_LIVE_MODEL.ifBlank { "gemini-3.1-flash-live-preview" }
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=${BuildConfig.GEMINI_API_KEY}"
        diagnosticsLogger.add("GeminiLive", "connect model=$model voice=${BuildConfig.GEMINI_LIVE_VOICE} audioResponses=$audioResponses")
        val request = Request.Builder().url(url).build()
        webSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    diagnosticsLogger.add("GeminiLive", "websocket open code=${response.code}")
                    sendSetup(webSocket, model, systemInstruction, audioResponses)
                    _events.tryEmit(GeminiLiveEvent.Connected)
                    scope.launch {
                        delay(350)
                        if (running && recorder == null) {
                            diagnosticsLogger.add("GeminiLive", "start capture after setup wait")
                            startCapture()
                            _events.tryEmit(GeminiLiveEvent.Listening)
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleMessage(bytes.utf8())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    diagnosticsLogger.add("GeminiLive", "websocket failed ${t.javaClass.simpleName} ${t.message.orEmpty().take(120)}")
                    _events.tryEmit(GeminiLiveEvent.Error("Gemini Live failed."))
                    stop()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    diagnosticsLogger.add("GeminiLive", "websocket closed code=$code reason=${reason.take(80)}")
                    stop()
                }
            }
        )
    }

    fun stop() {
        running = false
        captureJob?.cancel()
        captureJob = null
        recorder?.runCatching {
            stop()
            release()
        }
        recorder = null
        noiseSuppressor?.runCatching {
            enabled = false
            release()
        }
        noiseSuppressor = null
        echoCanceler?.runCatching {
            enabled = false
            release()
        }
        echoCanceler = null
        automaticGainControl?.runCatching {
            enabled = false
            release()
        }
        automaticGainControl = null
        player?.runCatching {
            stop()
            release()
        }
        player = null
        webSocket?.runCatching { close(1000, "stop") }
        webSocket = null
        diagnosticsLogger.add("GeminiLive", "stop")
    }

    private fun sendSetup(webSocket: WebSocket, model: String, systemInstruction: String, audioResponses: Boolean) {
        if (setupSent) return
        setupSent = true
        val setup = JSONObject()
            .put("model", "models/$model")
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put(if (audioResponses) "AUDIO" else "TEXT"))
                    .put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put(
                                "prebuiltVoiceConfig",
                                JSONObject().put("voiceName", BuildConfig.GEMINI_LIVE_VOICE.ifBlank { "Kore" })
                            )
                        )
                    )
            )
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            .put(
                "realtimeInputConfig",
                JSONObject().put(
                    "automaticActivityDetection",
                    JSONObject()
                        .put("disabled", true)
                )
            )
        val sent = webSocket.send(JSONObject().put("setup", setup).toString())
        diagnosticsLogger.add("GeminiLive", "setup sent ok=$sent vad=local audioResponses=$audioResponses voice=${BuildConfig.GEMINI_LIVE_VOICE.ifBlank { "Kore" }}")
    }

    @SuppressLint("MissingPermission")
    private fun startCapture() {
        captureJob = scope.launch {
            val sampleRate = 16_000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBuffer.coerceAtLeast(3200)
            diagnosticsLogger.add(
                "GeminiLive",
                "capture init source=MIC sampleRate=$sampleRate minBuffer=$minBuffer bufferSize=$bufferSize gain=2 vad=local gated=true"
            )
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                diagnosticsLogger.add("GeminiLive", "AudioRecord not initialized")
                _events.tryEmit(GeminiLiveEvent.Error("Audio recording error."))
                return@launch
            }
            recorder = audioRecord
            enableAudioEffects(audioRecord)
            audioRecord.startRecording()
            diagnosticsLogger.add("GeminiLive", "capture start state=${audioRecord.recordingState} sampleRate=$sampleRate bufferSize=$bufferSize")

            val buffer = ByteArray(1600)
            var chunks = 0
            var sentBytes = 0L
            var activityOpen = false
            var silenceMs = 0
            var speechMs = 0
            var speechStartedAtChunk = 0
            val preRoll = ArrayDeque<ByteArray>()
            val maxPreRollChunks = 6
            while (running && webSocket != null) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val rawLevel = averagePcm16Level(buffer, read)
                    val rawPeak = peakPcm16(buffer, read)
                    val outboundAudio = amplifyPcm16(buffer, read, gain = 2)
                    val sendLevel = averagePcm16Level(outboundAudio, outboundAudio.size)
                    val sendPeak = peakPcm16(outboundAudio, outboundAudio.size)
                    val frameMs = ((read / 2f) / sampleRate * 1000).toInt().coerceAtLeast(1)
                    val playbackQuiet = SystemClock.elapsedRealtime() < playbackQuietUntilMs
                    val voiced = !playbackQuiet && (rawLevel >= 350 || rawPeak >= 2_500)

                    chunks += 1
                    sentBytes += read
                    if (!activityOpen) {
                        preRoll.addLast(outboundAudio)
                        while (preRoll.size > maxPreRollChunks) preRoll.removeFirst()
                    }
                    if (voiced) {
                        speechMs += frameMs
                        silenceMs = 0
                    } else if (activityOpen) {
                        silenceMs += frameMs
                    }
                    if (!activityOpen && speechMs >= 250) {
                        activityOpen = true
                        speechStartedAtChunk = chunks
                        sendRealtimeInput(JSONObject().put("activityStart", JSONObject()))
                        diagnosticsLogger.add("GeminiLive", "activity start chunk=$chunks rawLevel=$rawLevel rawPeak=$rawPeak sendLevel=$sendLevel sendPeak=$sendPeak preRoll=${preRoll.size}")
                        preRoll.forEach { sendAudioChunk(it) }
                        preRoll.clear()
                    }
                    if (chunks % 50 == 0) {
                        diagnosticsLogger.add(
                            "GeminiLive",
                            "audio frame chunks=$chunks bytes=$sentBytes rawLevel=$rawLevel rawPeak=$rawPeak sendLevel=$sendLevel sendPeak=$sendPeak activity=$activityOpen silenceMs=$silenceMs speechMs=$speechMs playbackQuiet=$playbackQuiet"
                        )
                    }
                    if (activityOpen) {
                        val sent = sendAudioChunk(outboundAudio)
                        if (!sent) {
                            diagnosticsLogger.add("GeminiLive", "audio send failed chunk=$chunks")
                        }
                    }
                    if (activityOpen && silenceMs >= 850 && chunks - speechStartedAtChunk >= 8) {
                        sendRealtimeInput(JSONObject().put("activityEnd", JSONObject()))
                        diagnosticsLogger.add("GeminiLive", "activity end chunk=$chunks silenceMs=$silenceMs speechMs=$speechMs")
                        activityOpen = false
                        silenceMs = 0
                        speechMs = 0
                    }
                    if (!activityOpen && !voiced) {
                        speechMs = 0
                    }
                    delay(8)
                } else {
                    diagnosticsLogger.add("GeminiLive", "audio read result=$read recordingState=${audioRecord.recordingState}")
                    delay(40)
                }
            }
        }
    }

    private fun enableAudioEffects(audioRecord: AudioRecord) {
        val sessionId = audioRecord.audioSessionId
        noiseSuppressor = if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(sessionId)?.also {
                it.enabled = true
                diagnosticsLogger.add("GeminiLive", "noise suppressor enabled=${it.enabled} session=$sessionId")
            }
        } else {
            diagnosticsLogger.add("GeminiLive", "noise suppressor unavailable session=$sessionId")
            null
        }
        echoCanceler = if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(sessionId)?.also {
                it.enabled = true
                diagnosticsLogger.add("GeminiLive", "echo canceler enabled=${it.enabled} session=$sessionId")
            }
        } else {
            diagnosticsLogger.add("GeminiLive", "echo canceler unavailable session=$sessionId")
            null
        }
        automaticGainControl = if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(sessionId)?.also {
                it.enabled = true
                diagnosticsLogger.add("GeminiLive", "automatic gain control enabled=${it.enabled} session=$sessionId")
            }
        } else {
            diagnosticsLogger.add("GeminiLive", "automatic gain control unavailable session=$sessionId")
            null
        }
    }

    private fun sendAudioChunk(audio: ByteArray): Boolean {
        val encoded = Base64.encodeToString(audio, Base64.NO_WRAP)
        return sendRealtimeInput(
            JSONObject().put(
                "audio",
                JSONObject()
                    .put("mimeType", "audio/pcm;rate=16000")
                    .put("data", encoded)
            )
        )
    }

    private fun sendRealtimeInput(input: JSONObject): Boolean {
        outboundRealtimeMessages += 1
        val type = when {
            input.has("activityStart") -> "activityStart"
            input.has("activityEnd") -> "activityEnd"
            input.has("audio") -> "audio"
            input.has("audioStreamEnd") -> "audioStreamEnd"
            input.has("text") -> "text"
            else -> input.keys().asSequence().joinToString(",").ifBlank { "unknown" }
        }
        val sent = webSocket?.send(JSONObject().put("realtimeInput", input).toString()) == true
        if (!sent || type != "audio" || outboundRealtimeMessages % 100 == 0) {
            diagnosticsLogger.add("GeminiLive", "send realtime #$outboundRealtimeMessages type=$type ok=$sent")
        }
        return sent
    }

    private fun startPlayer() {
        val sampleRate = 24_000
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer.coerceAtLeast(9600))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        player?.play()
        diagnosticsLogger.add("GeminiLive", "player start sampleRate=$sampleRate bufferSize=$minBuffer")
    }

    private fun handleMessage(text: String) {
        runCatching {
            inboundMessages += 1
            val root = JSONObject(text)
            if (inboundMessages <= 5 || inboundMessages % 20 == 0) {
                diagnosticsLogger.add("GeminiLive", "message #$inboundMessages keys=${root.keys().asSequence().joinToString(",").take(120)}")
            }
            if (root.has("setupComplete") || root.has("setup_complete")) {
                diagnosticsLogger.add("GeminiLive", "setup complete message=$inboundMessages")
                if (recorder == null) startCapture()
                _events.tryEmit(GeminiLiveEvent.Listening)
                return
            }
            val serverContent = root.optJSONObject("serverContent") ?: run {
                diagnosticsLogger.add("GeminiLive", "message keys=${root.keys().asSequence().joinToString(",").take(120)}")
                return
            }
            serverContent.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }?.let {
                diagnosticsLogger.add("GeminiLive", "input transcript chars=${it.length}")
                _events.tryEmit(GeminiLiveEvent.UserTranscript(it))
            }
            serverContent.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }?.let {
                diagnosticsLogger.add("GeminiLive", "output transcript chars=${it.length}")
                _events.tryEmit(GeminiLiveEvent.AssistantTranscript(it))
            }
            val parts = serverContent.optJSONObject("modelTurn")?.optJSONArray("parts")
            if (parts != null) {
                var audioParts = 0
                var audioBytes = 0
                for (index in 0 until parts.length()) {
                    val part = parts.optJSONObject(index)
                    part?.optString("text")?.takeIf { it.isNotBlank() }?.let {
                        diagnosticsLogger.add("GeminiLive", "output text part chars=${it.length}")
                        _events.tryEmit(GeminiLiveEvent.AssistantTranscript(it))
                    }
                    val inlineData = part?.optJSONObject("inlineData")
                    val data = inlineData?.optString("data").orEmpty()
                    if (data.isNotBlank()) {
                        val audio = Base64.decode(data, Base64.DEFAULT)
                        audioParts += 1
                        audioBytes += audio.size
                        val durationMs = ((audio.size / 2f) / 24_000 * 1000).toLong()
                        playbackQuietUntilMs = maxOf(playbackQuietUntilMs, SystemClock.elapsedRealtime() + durationMs + 900)
                        player?.write(audio, 0, audio.size)
                    }
                }
                if (audioParts > 0) {
                    diagnosticsLogger.add("GeminiLive", "output audio parts=$audioParts bytes=$audioBytes")
                }
            }
            if (serverContent.optBoolean("turnComplete", false)) {
                diagnosticsLogger.add("GeminiLive", "turn complete")
                _events.tryEmit(GeminiLiveEvent.TurnComplete)
                _events.tryEmit(GeminiLiveEvent.Listening)
            }
            if (serverContent.optBoolean("interrupted", false)) {
                diagnosticsLogger.add("GeminiLive", "interrupted")
                player?.flush()
            }
        }.onFailure {
            Log.e("EnglishCarGeminiLive", "message parse failed", it)
            diagnosticsLogger.add("GeminiLive", "message parse failed ${it.javaClass.simpleName}")
        }
    }

    private fun averagePcm16Level(buffer: ByteArray, read: Int): Int {
        var total = 0L
        var samples = 0
        var index = 0
        while (index + 1 < read) {
            val sample = ((buffer[index + 1].toInt() shl 8) or (buffer[index].toInt() and 0xff)).toShort().toInt()
            total += abs(sample)
            samples += 1
            index += 2
        }
        return if (samples == 0) 0 else (total / samples).toInt()
    }

    private fun amplifyPcm16(buffer: ByteArray, read: Int, gain: Int): ByteArray {
        val output = ByteArray(read)
        var index = 0
        while (index + 1 < read) {
            val sample = ((buffer[index + 1].toInt() shl 8) or (buffer[index].toInt() and 0xff)).toShort().toInt()
            val amplified = (sample * gain).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[index] = (amplified and 0xff).toByte()
            output[index + 1] = ((amplified shr 8) and 0xff).toByte()
            index += 2
        }
        return output
    }

    private fun peakPcm16(buffer: ByteArray, read: Int): Int {
        var peak = 0
        var index = 0
        while (index + 1 < read) {
            val sample = ((buffer[index + 1].toInt() shl 8) or (buffer[index].toInt() and 0xff)).toShort().toInt()
            peak = maxOf(peak, abs(sample))
            index += 2
        }
        return peak
    }
}

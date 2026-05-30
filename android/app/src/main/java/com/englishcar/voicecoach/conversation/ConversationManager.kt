package com.englishcar.voicecoach.conversation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import com.englishcar.voicecoach.ai.AiFinalResponse
import com.englishcar.voicecoach.ai.AssistantCatalog
import com.englishcar.voicecoach.ai.BackendConversationClient
import com.englishcar.voicecoach.ai.BackendException
import com.englishcar.voicecoach.ai.ContextTurn
import com.englishcar.voicecoach.ai.ConversationRequest
import com.englishcar.voicecoach.audio.AudioFocusHandler
import com.englishcar.voicecoach.audio.SpeechEvent
import com.englishcar.voicecoach.audio.SpeechRecognitionClient
import com.englishcar.voicecoach.audio.TextToSpeechClient
import com.englishcar.voicecoach.diagnostics.DiagnosticsLogger
import com.englishcar.voicecoach.history.FeedbackEntry
import com.englishcar.voicecoach.history.FeedbackRepository
import com.englishcar.voicecoach.service.VoiceSessionController
import com.englishcar.voicecoach.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class ConversationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speechRecognitionClient: SpeechRecognitionClient,
    private val textToSpeechClient: TextToSpeechClient,
    private val audioFocusHandler: AudioFocusHandler,
    private val settingsRepository: SettingsRepository,
    private val backendConversationClient: BackendConversationClient,
    private val feedbackRepository: FeedbackRepository,
    private val voiceSessionController: VoiceSessionController,
    private val diagnosticsLogger: DiagnosticsLogger
) {
    private companion object {
        const val TAG = "EnglishCarConversation"
        const val MAX_PAUSED_COMMAND_ATTEMPTS = 1
        const val MIN_LISTEN_WINDOW_MS = 8_000L
        const val MAX_SHORT_NO_MATCH_RETRIES = 3
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Unhandled conversation error", error)
        _uiState.update { it.copy(state = ConversationState.Error, errorMessage = "Something went wrong.") }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)
    private val recentContext = ArrayDeque<ContextTurn>()
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<ConversationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ConversationEvent> = _events.asSharedFlow()

    private var isSessionActive = false
    private var pausedCommandMode = false
    private var pausedCommandListenAttempts = 0
    private var currentSilenceTimeoutMs = 2400
    private var currentAutoPauseTimeoutMs = 60_000
    private var currentAutoFinishTimeoutMs = 600_000
    private var lastInteractionMs = 0L
    private var inactivityJob: Job? = null
    private var finishJob: Job? = null
    private var conversationJob: Job? = null
    private var generation = 0L
    private var ignoreSpeechErrorsUntilMs = 0L
    private var consecutiveNoMatchCount = 0
    private var listenStartedAtMs = 0L
    private var shortNoMatchRetries = 0

    init {
        scope.launch {
            speechRecognitionClient.events.collect { handleSpeechEvent(it) }
        }
    }

    fun start(hasRecordAudioPermission: Boolean) {
        diagnosticsLogger.add("Conversation", "start permission=$hasRecordAudioPermission")
        if (!hasRecordAudioPermission) {
            _uiState.update { it.copy(state = ConversationState.Idle, isPermissionRequired = true, errorMessage = null) }
            return
        }
        if (!speechRecognitionClient.isAvailable()) {
            _uiState.update { it.copy(state = ConversationState.Error, errorMessage = "Microphone permission is missing.") }
            return
        }

        scope.launch {
            audioFocusHandler.request { pauseForInterruption("We can continue whenever you're ready.") }
            val settings = settingsRepository.currentSettings()
            currentSilenceTimeoutMs = settings.silenceTimeoutMs
            currentAutoPauseTimeoutMs = settings.autoPauseTimeoutMs
            currentAutoFinishTimeoutMs = settings.autoFinishTimeoutMs
            isSessionActive = true
            pausedCommandMode = false
            pausedCommandListenAttempts = 0
            lastInteractionMs = SystemClock.elapsedRealtime()
            speakThenListen("I'm ready", resetInactivityTimer = true)
        }
    }

    fun finish() {
        diagnosticsLogger.add("Conversation", "finish")
        isSessionActive = false
        pausedCommandMode = false
        generation += 1
        conversationJob?.cancel()
        conversationJob = null
        inactivityJob?.cancel()
        finishJob?.cancel()
        speechRecognitionClient.stopListening()
        textToSpeechClient.stop()
        audioFocusHandler.abandon()
        voiceSessionController.stop()
        recentContext.clear()
        _uiState.value = ConversationUiState()
    }

    fun finishWithGoodbye(closeApp: Boolean = false) {
        if (!isSessionActive) {
            if (closeApp) _events.tryEmit(ConversationEvent.CloseApp)
            return
        }
        speechRecognitionClient.stopListening()
        scope.launch {
            speakOnly("See you later.")
            finish()
            if (closeApp) _events.tryEmit(ConversationEvent.CloseApp)
        }
    }

    fun retry(hasRecordAudioPermission: Boolean) {
        finish()
        start(hasRecordAudioPermission)
    }

    fun pause(spoken: Boolean = false) {
        if (!isSessionActive) return
        if (pausedCommandMode || _uiState.value.state == ConversationState.Paused) {
            diagnosticsLogger.add("Conversation", "pause ignored already paused")
            return
        }
        diagnosticsLogger.add("Conversation", "pause")
        generation += 1
        conversationJob?.cancel()
        conversationJob = null
        inactivityJob?.cancel()
        finishJob?.cancel()
        pausedCommandMode = true
        pausedCommandListenAttempts = 0
        ignoreSpeechErrorsUntilMs = SystemClock.elapsedRealtime() + 2_000L
        speechRecognitionClient.stopListening()
        textToSpeechClient.stop()
        scope.launch {
            _uiState.update { it.copy(state = ConversationState.Paused, errorMessage = null) }
            if (spoken) {
                speakOnly("Do you want to continue?")
                if (isSessionActive && pausedCommandMode) listenForPausedCommand()
            }
        }
    }

    fun pauseForInterruption(message: String = "We can continue whenever you're ready.") {
        if (!isSessionActive) return
        speechRecognitionClient.stopListening()
        textToSpeechClient.stop()
        inactivityJob?.cancel()
        finishJob?.cancel()
        generation += 1
        conversationJob?.cancel()
        conversationJob = null
        pausedCommandMode = true
        _uiState.update { it.copy(state = ConversationState.Paused, lastAssistantText = message, errorMessage = null) }
    }

    fun resume(hasRecordAudioPermission: Boolean) {
        if (!hasRecordAudioPermission) {
            _uiState.update { it.copy(isPermissionRequired = true) }
            return
        }
        if (!isSessionActive) {
            start(true)
            return
        }
        scope.launch {
            pausedCommandMode = false
            speakThenListen("I'm ready", resetInactivityTimer = true)
        }
    }

    private suspend fun speakThenListen(text: String, resetInactivityTimer: Boolean) {
        speakOnly(text)
        if (isSessionActive) listen(resetInactivityTimer)
    }

    private suspend fun speakOnly(text: String) {
        val settings = settingsRepository.currentSettings()
        val assistant = AssistantCatalog.find(settings.activeAssistantId)
        _uiState.update { it.copy(state = ConversationState.Speaking, lastAssistantText = text, errorMessage = null) }
        val spoken = withTimeoutOrNull(12_000L) {
            textToSpeechClient.speakAsAssistant(text, assistant.id)
        }
        if (spoken == null) {
            diagnosticsLogger.add("Conversation", "tts timed out; continue to listening")
            textToSpeechClient.stop()
        }
    }

    private fun listen(resetInactivityTimer: Boolean) {
        if (resetInactivityTimer) lastInteractionMs = SystemClock.elapsedRealtime()
        scheduleTimers()
        pausedCommandMode = false
        pausedCommandListenAttempts = 0
        listenStartedAtMs = SystemClock.elapsedRealtime()
        _uiState.update { it.copy(state = ConversationState.Listening, errorMessage = null) }
        speechRecognitionClient.startListening(currentSilenceTimeoutMs)
    }

    private fun listenForPausedCommand() {
        pausedCommandMode = true
        pausedCommandListenAttempts += 1
        _uiState.update { it.copy(state = ConversationState.Paused, errorMessage = null) }
        speechRecognitionClient.startListening(10_000)
    }

    private fun handleSpeechEvent(event: SpeechEvent) {
        when (event) {
            SpeechEvent.Ready -> Unit
            SpeechEvent.EndOfSpeech -> Unit
            is SpeechEvent.Result -> handleUserText(event.text)
            is SpeechEvent.Error -> handleSpeechError(event.code)
        }
    }

    private fun handleUserText(text: String) {
        conversationJob?.cancel()
        val turnGeneration = generation
        conversationJob = scope.launch {
            diagnosticsLogger.add("Conversation", "user text chars=${text.length} value=${text.take(120)}")
            consecutiveNoMatchCount = 0
            inactivityJob?.cancel()
            finishJob?.cancel()
            speechRecognitionClient.stopListening()
            if (handleLocalCommand(text)) return@launch

            if (pausedCommandMode || _uiState.value.state == ConversationState.Paused) {
                diagnosticsLogger.add("Conversation", "paused ignored non-command text=${text.take(80)}")
                pausedCommandMode = false
                _uiState.update { it.copy(state = ConversationState.Paused, errorMessage = null) }
                return@launch
            }

            lastInteractionMs = SystemClock.elapsedRealtime()
            _uiState.update { it.copy(state = ConversationState.WaitingAI, lastUserText = text, errorMessage = null) }
            if (!hasUsableNetwork()) {
                speakOnly("Give me a moment, internet is slow.")
                if (waitForNetwork()) {
                    speakOnly("We can continue now.")
                }
            }
            val response = requestAssistantReply("conversation_turn", text)
            if (!isSessionActive || pausedCommandMode || generation != turnGeneration) {
                diagnosticsLogger.add("Conversation", "turn ignored active=$isSessionActive paused=$pausedCommandMode generation=$generation turnGeneration=$turnGeneration")
                return@launch
            }
            speakOnly(response.spokenReply.ifBlank { "Can you repeat, please?" })
            rememberContext("user", text)
            rememberContext("assistant", response.spokenReply)
            if (isSessionActive && !pausedCommandMode && generation == turnGeneration) {
                listen(resetInactivityTimer = true)
            }
        }
    }

    private fun scheduleTimers() {
        inactivityJob?.cancel()
        finishJob?.cancel()
        if (!isSessionActive || pausedCommandMode) return
        if (currentAutoPauseTimeoutMs > 0) {
            inactivityJob = scope.launch {
                delay(currentAutoPauseTimeoutMs.toLong())
                if (isSessionActive && !pausedCommandMode && _uiState.value.state == ConversationState.Listening) pause(spoken = true)
            }
        }
        if (currentAutoFinishTimeoutMs > 0) {
            finishJob = scope.launch {
                delay(currentAutoFinishTimeoutMs.toLong())
                if (isSessionActive && SystemClock.elapsedRealtime() - lastInteractionMs >= currentAutoFinishTimeoutMs) {
                    finishWithGoodbye()
                }
            }
        }
    }

    private suspend fun handleLocalCommand(text: String): Boolean {
        val settings = settingsRepository.currentSettings()
        return when (matchCommand(text, settings.commands.pause, settings.commands.resume, settings.commands.finish, settings.commands.closeApp)) {
            LocalCommand.Pause -> {
                pause(spoken = false)
                true
            }
            LocalCommand.Resume -> {
                pausedCommandMode = false
                speakThenListen("I'm ready", resetInactivityTimer = true)
                true
            }
            LocalCommand.Finish -> {
                finishWithGoodbye()
                true
            }
            LocalCommand.CloseApp -> {
                finishWithGoodbye(closeApp = true)
                true
            }
            null -> false
        }
    }

    private suspend fun requestAssistantReply(type: String, userText: String?): AiFinalResponse {
        val settings = settingsRepository.currentSettings()
        val assistant = AssistantCatalog.find(settings.activeAssistantId)
        val assistantName = settings.assistantNames[assistant.id] ?: assistant.defaultName
        if (settings.backendUrl.isBlank() || settings.appApiToken.isBlank()) return AiFinalResponse("Can you repeat, please?")
        val model = when (settings.model) {
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite" -> settings.model
            else -> "gemini-2.5-flash-lite"
        }

        suspend fun sendToBackend(): AiFinalResponse {
            val response = backendConversationClient.send(
                backendUrl = settings.backendUrl,
                appApiToken = settings.appApiToken,
                request = ConversationRequest(
                    requestId = UUID.randomUUID().toString(),
                    type = type,
                    userText = userText,
                    assistantId = assistant.id,
                    assistantName = assistantName,
                    assistantPersonality = assistant.personality,
                    userName = settings.userName.ifBlank { null },
                    model = model,
                    recentContext = recentContext.toList()
                )
            )
            saveFeedbackIfNeeded(type, userText, response, assistant.id, model)
            return response
        }

        return try {
            sendToBackend()
        } catch (error: Exception) {
            diagnosticsLogger.add("Conversation", "backend failed ${error.javaClass.simpleName}")
            when (BackendException.fromThrowable(error)) {
                BackendException.InternetUnavailable,
                BackendException.Timeout -> {
                    speakOnly("Give me a moment, internet is slow.")
                    if (waitForNetwork()) {
                        speakOnly("We can continue now.")
                        runCatching { sendToBackend() }.getOrElse {
                            diagnosticsLogger.add("Conversation", "backend retry failed ${it.javaClass.simpleName}")
                            AiFinalResponse("Can you repeat, please?")
                        }
                    } else {
                        AiFinalResponse("Can you repeat, please?")
                    }
                }
                BackendException.BackendUnavailable -> AiFinalResponse("The assistant is not available right now.")
                BackendException.Unauthorized -> AiFinalResponse("Backend token is not valid.")
            }
        }
    }

    private suspend fun saveFeedbackIfNeeded(type: String, userText: String?, response: AiFinalResponse, assistantId: String, model: String) {
        val correction = cleanFeedbackValue(response.correction)
        val naturalAlternative = cleanFeedbackValue(response.naturalAlternative)
        val shortExplanation = cleanFeedbackValue(response.shortExplanation)
        val hasFeedback = correction != null || naturalAlternative != null || shortExplanation != null
        if (type == "conversation_turn" && userText != null && (response.shouldSaveFeedback || hasFeedback)) {
            feedbackRepository.save(
                FeedbackEntry(
                    originalPhrase = userText,
                    correctedPhrase = correction,
                    naturalAlternative = naturalAlternative,
                    shortExplanation = shortExplanation,
                    assistantId = assistantId,
                    model = model,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    private fun handleSpeechError(code: Int) {
        if (SystemClock.elapsedRealtime() < ignoreSpeechErrorsUntilMs) return
        if (pausedCommandMode || _uiState.value.state == ConversationState.Paused) {
            diagnosticsLogger.add("Conversation", "paused command listen ended code=$code attempts=$pausedCommandListenAttempts")
            pausedCommandMode = false
            _uiState.update { it.copy(state = ConversationState.Paused, errorMessage = null) }
            return
        }
        if (code == SpeechRecognitionClient.ERROR_NO_MATCH) {
            scope.launch {
                consecutiveNoMatchCount += 1
                val listenedMs = SystemClock.elapsedRealtime() - listenStartedAtMs
                if (listenedMs < MIN_LISTEN_WINDOW_MS && shortNoMatchRetries < MAX_SHORT_NO_MATCH_RETRIES) {
                    shortNoMatchRetries += 1
                    diagnosticsLogger.add("Conversation", "short no match listenedMs=$listenedMs retry=$shortNoMatchRetries")
                    delay(400)
                    if (isSessionActive && _uiState.value.state == ConversationState.Listening) {
                        listen(resetInactivityTimer = false)
                    }
                    return@launch
                }
                shortNoMatchRetries = 0
                diagnosticsLogger.add("Conversation", "no match count=$consecutiveNoMatchCount; awaiting user tap")
                speechRecognitionClient.stopListening()
                _uiState.update { it.copy(state = ConversationState.AwaitingUser, lastUserText = "", errorMessage = null) }
            }
            return
        }
        if (code == SpeechRecognitionClient.ERROR_TRANSCRIPTION_FAILED) {
            scope.launch {
                speakOnly("Can you repeat, please?")
                if (isSessionActive) listen(resetInactivityTimer = false)
            }
            return
        }
        _uiState.update { it.copy(state = ConversationState.Error, errorMessage = speechErrorMessage(code)) }
    }

    private suspend fun waitForNetwork(): Boolean {
        repeat(10) {
            if (hasUsableNetwork()) return true
            delay(1_500)
        }
        return false
    }

    private fun hasUsableNetwork(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun rememberContext(role: String, text: String) {
        recentContext.addLast(ContextTurn(role, text))
        while (recentContext.size > 12) recentContext.removeFirst()
    }

    private fun matchCommand(text: String, pause: String, resume: String, finish: String, closeApp: String): LocalCommand? {
        val normalizedText = normalizeCommand(text)
        return listOf(
            LocalCommand.Pause to normalizeCommand(pause),
            LocalCommand.Resume to normalizeCommand(resume),
            LocalCommand.Finish to normalizeCommand(finish),
            LocalCommand.CloseApp to normalizeCommand(closeApp)
        ).firstOrNull { (_, command) ->
            command.isNotBlank() && (normalizedText == command || normalizedText.contains(" $command ") || normalizedText.startsWith("$command ") || normalizedText.endsWith(" $command"))
        }?.first
    }

    private fun normalizeCommand(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun cleanFeedbackValue(value: String?): String? {
        val cleaned = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalized = cleaned.lowercase().trim('.', ':', '-', ' ')
        return when (normalized) {
            "none", "no", "n/a", "na", "null", "ninguna", "ninguno", "no correction", "no corrections" -> null
            else -> cleaned
        }
    }

    private fun speechErrorMessage(code: Int): String {
        return when (code) {
            SpeechRecognitionClient.ERROR_AUDIO_RECORD -> "Audio recording error."
            SpeechRecognitionClient.ERROR_PERMISSION -> "Microphone permission is missing."
            SpeechRecognitionClient.ERROR_BACKEND_NOT_CONFIGURED -> "Backend is required."
            SpeechRecognitionClient.ERROR_TRANSCRIPTION_FAILED -> "Can you repeat, please?"
            SpeechRecognitionClient.ERROR_NO_MATCH -> "Can you repeat, please?"
            else -> "Speech recognition failed with code $code."
        }
    }

    private enum class LocalCommand { Pause, Resume, Finish, CloseApp }
}

sealed interface ConversationEvent {
    data object CloseApp : ConversationEvent
}

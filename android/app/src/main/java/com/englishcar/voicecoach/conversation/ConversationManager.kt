package com.englishcar.voicecoach.conversation

import android.util.Log
import android.os.SystemClock
import com.englishcar.voicecoach.ai.AssistantCatalog
import com.englishcar.voicecoach.ai.AiFinalResponse
import com.englishcar.voicecoach.ai.BackendException
import com.englishcar.voicecoach.ai.BackendConversationClient
import com.englishcar.voicecoach.ai.ContextTurn
import com.englishcar.voicecoach.ai.ConversationRequest
import com.englishcar.voicecoach.audio.AudioFocusHandler
import com.englishcar.voicecoach.audio.SpeechEvent
import com.englishcar.voicecoach.audio.SpeechRecognitionClient
import com.englishcar.voicecoach.audio.TextToSpeechClient
import com.englishcar.voicecoach.history.FeedbackEntry
import com.englishcar.voicecoach.history.FeedbackRepository
import com.englishcar.voicecoach.settings.SettingsRepository
import com.englishcar.voicecoach.service.VoiceSessionController
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class ConversationManager @Inject constructor(
    private val speechRecognitionClient: SpeechRecognitionClient,
    private val textToSpeechClient: TextToSpeechClient,
    private val audioFocusHandler: AudioFocusHandler,
    private val settingsRepository: SettingsRepository,
    private val backendConversationClient: BackendConversationClient,
    private val feedbackRepository: FeedbackRepository,
    private val voiceSessionController: VoiceSessionController
) {
    private companion object {
        const val TAG = "EnglishCarConversation"
        const val MAX_PAUSED_COMMAND_ATTEMPTS = 12
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var retryCount = 0

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<ConversationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ConversationEvent> = _events.asSharedFlow()
    private val recentContext = ArrayDeque<ContextTurn>()
    private var currentSilenceTimeoutMs = 3500
    private var currentAutoPauseTimeoutMs = 60_000
    private var isSessionActive = false
    private var pausedCommandMode = false
    private var pausedCommandListenAttempts = 0
    private var inactivityJob: Job? = null
    private var ignoreSpeechErrorsUntilMs = 0L
    private var lastInteractionMs = 0L

    init {
        scope.launch {
            speechRecognitionClient.events.collect { event ->
                handleSpeechEvent(event)
            }
        }
    }

    fun start(hasRecordAudioPermission: Boolean) {
        Log.d(TAG, "start permission=$hasRecordAudioPermission")
        if (!hasRecordAudioPermission) {
            _uiState.update {
                it.copy(
                    state = ConversationState.Idle,
                    isPermissionRequired = true,
                    errorMessage = null
                )
            }
            return
        }

        if (!speechRecognitionClient.isAvailable()) {
            _uiState.update {
                it.copy(
                    state = ConversationState.Error,
                    errorMessage = "Speech recognition is not available on this device."
                )
            }
            return
        }

        scope.launch {
            audioFocusHandler.request {
                pauseForInterruption("We can continue whenever you're ready.")
            }
            isSessionActive = true
            pausedCommandMode = false
            pausedCommandListenAttempts = 0
            retryCount = 0
            val settings = settingsRepository.currentSettings()
            val assistant = AssistantCatalog.find(settings.activeAssistantId)
            currentSilenceTimeoutMs = settings.silenceTimeoutMs
            currentAutoPauseTimeoutMs = settings.autoPauseTimeoutMs
            val greeting = requestAssistantReply(type = "start_conversation", userText = null).spokenReply
            _uiState.update {
                it.copy(
                    state = ConversationState.Speaking,
                    lastAssistantText = greeting,
                    isPermissionRequired = false,
                    errorMessage = null
                )
            }
            val spoken = textToSpeechClient.speakAsAssistant(greeting, assistant.id)
            if (!spoken) {
                _uiState.update {
                    it.copy(errorMessage = "Google TTS is not available or could not speak.")
                }
            }
            listen(resetInactivityTimer = true)
        }
    }

    fun finish() {
        Log.d(TAG, "finish")
        isSessionActive = false
        pausedCommandMode = false
        pausedCommandListenAttempts = 0
        audioFocusHandler.abandon()
        inactivityJob?.cancel()
        speechRecognitionClient.stopListening()
        textToSpeechClient.stop()
        voiceSessionController.stop()
        retryCount = 0
        recentContext.clear()
        _uiState.value = ConversationUiState()
    }

    fun finishWithGoodbye(closeApp: Boolean = false) {
        if (!isSessionActive) {
            if (closeApp) _events.tryEmit(ConversationEvent.CloseApp)
            return
        }
        Log.d(TAG, "finishWithGoodbye closeApp=$closeApp")
        speechRecognitionClient.stopListening()
        scope.launch {
            val settings = settingsRepository.currentSettings()
            val assistant = AssistantCatalog.find(settings.activeAssistantId)
            val goodbye = "See you later."
            _uiState.update {
                it.copy(
                    state = ConversationState.Speaking,
                    lastAssistantText = goodbye,
                    errorMessage = null
                )
            }
            textToSpeechClient.speakAsAssistant(goodbye, assistant.id)
            finish()
            if (closeApp) {
                _events.tryEmit(ConversationEvent.CloseApp)
            }
        }
    }

    fun retry(hasRecordAudioPermission: Boolean) {
        finish()
        start(hasRecordAudioPermission)
    }

    fun pause(spoken: Boolean = false) {
        if (!isSessionActive) return
        Log.d(TAG, "pause")
        inactivityJob?.cancel()
        pausedCommandMode = true
        pausedCommandListenAttempts = 0
        ignoreSpeechErrorsUntilMs = SystemClock.elapsedRealtime() + 2_000L
        speechRecognitionClient.stopListening()
        scope.launch {
            _uiState.update {
                it.copy(
                    state = ConversationState.Paused,
                    errorMessage = null,
                    lastAssistantText = if (spoken) "Conversation paused." else it.lastAssistantText
                )
            }
            if (spoken) {
                val assistant = AssistantCatalog.find(settingsRepository.currentSettings().activeAssistantId)
                textToSpeechClient.speakAsAssistant("Conversation paused.", assistant.id)
                listenForPausedCommand()
            } else {
                textToSpeechClient.stop()
                listenForPausedCommand()
            }
        }
    }

    fun pauseForInterruption(message: String = "We can continue whenever you're ready.") {
        if (!isSessionActive) return
        Log.d(TAG, "pauseForInterruption")
        speechRecognitionClient.stopListening()
        textToSpeechClient.stop()
        inactivityJob?.cancel()
        pausedCommandMode = false
        pausedCommandListenAttempts = 0
        _uiState.update {
            it.copy(
                state = ConversationState.Paused,
                lastAssistantText = message,
                errorMessage = null
            )
        }
    }

    fun resume(hasRecordAudioPermission: Boolean) {
        if (!hasRecordAudioPermission) {
            _uiState.update { it.copy(isPermissionRequired = true) }
            return
        }
        if (!isSessionActive) {
            start(hasRecordAudioPermission)
            return
        }
        Log.d(TAG, "resume")
        scope.launch {
            currentSilenceTimeoutMs = settingsRepository.currentSettings().silenceTimeoutMs
            currentAutoPauseTimeoutMs = settingsRepository.currentSettings().autoPauseTimeoutMs
            scheduleInactivityPause()
            _uiState.update {
                it.copy(
                    state = ConversationState.Speaking,
                    lastAssistantText = "We can continue whenever you're ready.",
                    isPermissionRequired = false,
                    errorMessage = null
                )
            }
            val assistant = AssistantCatalog.find(settingsRepository.currentSettings().activeAssistantId)
            textToSpeechClient.speakAsAssistant("We can continue whenever you're ready.", assistant.id)
            delay(900)
            listen(resetInactivityTimer = true)
        }
    }

    private fun listen(resetInactivityTimer: Boolean = true) {
        Log.d(TAG, "listen")
        pausedCommandMode = false
        pausedCommandListenAttempts = 0
        if (resetInactivityTimer) {
            lastInteractionMs = SystemClock.elapsedRealtime()
        }
        scheduleInactivityPause()
        _uiState.update { it.copy(state = ConversationState.Listening, errorMessage = null) }
        speechRecognitionClient.startListening(currentSilenceTimeoutMs)
    }

    private fun listenForPausedCommand() {
        Log.d(TAG, "listenForPausedCommand")
        pausedCommandMode = true
        pausedCommandListenAttempts += 1
        _uiState.update { it.copy(state = ConversationState.Paused, errorMessage = null) }
        speechRecognitionClient.startListening(10_000)
    }

    private fun handleSpeechEvent(event: SpeechEvent) {
        Log.d(TAG, "speechEvent=$event")
        when (event) {
            SpeechEvent.Ready -> {
                if (pausedCommandMode) {
                    _uiState.update { it.copy(state = ConversationState.Paused) }
                } else {
                    _uiState.update { it.copy(state = ConversationState.Listening) }
                }
            }
            SpeechEvent.EndOfSpeech -> {
                if (pausedCommandMode) {
                    _uiState.update { it.copy(state = ConversationState.Paused) }
                } else {
                    _uiState.update { it.copy(state = ConversationState.WaitingAI) }
                }
            }
            is SpeechEvent.Result -> handleUserText(event.text)
            is SpeechEvent.Error -> handleSpeechError(event.code)
        }
    }

    private fun handleUserText(text: String) {
        Log.d(TAG, "handleUserText text=$text")
        scope.launch {
            inactivityJob?.cancel()
            retryCount = 0
            speechRecognitionClient.stopListening()
            if (handleLocalCommand(text)) {
                return@launch
            }
            if (pausedCommandMode || _uiState.value.state == ConversationState.Paused) {
                val shouldKeepListeningForCommands = pausedCommandListenAttempts < MAX_PAUSED_COMMAND_ATTEMPTS
                _uiState.update {
                    it.copy(
                        state = ConversationState.Paused,
                        errorMessage = null
                    )
                }
                if (shouldKeepListeningForCommands) {
                    delay(600)
                    if (isSessionActive && pausedCommandMode) {
                        listenForPausedCommand()
                    }
                } else {
                    pausedCommandMode = false
                }
                return@launch
            }
            pausedCommandMode = false
            _uiState.update {
                it.copy(
                    state = ConversationState.WaitingAI,
                    lastUserText = text,
                    errorMessage = null
                )
            }
            delay(350)
            val aiResponse = requestAssistantReply(type = "conversation_turn", userText = text)
            val reply = aiResponse.spokenReply
            val assistant = AssistantCatalog.find(settingsRepository.currentSettings().activeAssistantId)
            _uiState.update {
                it.copy(
                    state = ConversationState.Speaking,
                    lastAssistantText = reply
                )
            }
            val spoken = textToSpeechClient.speakAsAssistant(reply, assistant.id)
            if (!spoken) {
                _uiState.update {
                    it.copy(errorMessage = "Google TTS is not available or could not speak.")
                }
            }
            rememberContext("user", text)
            rememberContext("assistant", reply)
            delay(900)
            listen(resetInactivityTimer = true)
        }
    }

    private fun scheduleInactivityPause() {
        inactivityJob?.cancel()
        if (!isSessionActive || pausedCommandMode || currentAutoPauseTimeoutMs <= 0) return
        if (lastInteractionMs == 0L) {
            lastInteractionMs = SystemClock.elapsedRealtime()
        }
        val elapsedMs = SystemClock.elapsedRealtime() - lastInteractionMs
        val remainingMs = (currentAutoPauseTimeoutMs - elapsedMs).coerceAtLeast(0)
        inactivityJob = scope.launch {
            delay(remainingMs.toLong())
            if (isSessionActive && !pausedCommandMode && _uiState.value.state == ConversationState.Listening) {
                pause(spoken = true)
            }
        }
    }

    private suspend fun handleLocalCommand(text: String): Boolean {
        val settings = settingsRepository.currentSettings()
        return when (
            matchCommand(
                text = text,
                pause = settings.commands.pause,
                resume = settings.commands.resume,
                finish = settings.commands.finish,
                closeApp = settings.commands.closeApp
            )
        ) {
            LocalCommand.Pause -> {
                pause(spoken = true)
                true
            }
            LocalCommand.Resume -> {
                pausedCommandMode = false
                pausedCommandListenAttempts = 0
                resume(hasRecordAudioPermission = true)
                true
            }
            LocalCommand.Finish -> {
                pausedCommandListenAttempts = 0
                finishWithGoodbye()
                true
            }
            LocalCommand.CloseApp -> {
                pausedCommandListenAttempts = 0
                finishWithGoodbye(closeApp = true)
                true
            }
            null -> false
        }
    }

    private fun matchCommand(
        text: String,
        pause: String,
        resume: String,
        finish: String,
        closeApp: String
    ): LocalCommand? {
        val normalizedText = normalizeCommand(text)
        val options = listOf(
            LocalCommand.Pause to normalizeCommand(pause),
            LocalCommand.Resume to normalizeCommand(resume),
            LocalCommand.Finish to normalizeCommand(finish),
            LocalCommand.CloseApp to normalizeCommand(closeApp)
        )
        return options.firstOrNull { (_, command) ->
            command.isNotBlank() && commandMatches(normalizedText, command)
        }?.first
    }

    private fun commandMatches(text: String, command: String): Boolean {
        if (text == command) return true
        if (command.length < 4) return false
        return text.startsWith("$command ") ||
            text.endsWith(" $command") ||
            text.contains(" $command ")
    }

    private fun normalizeCommand(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun handleSpeechError(code: Int) {
        if (SystemClock.elapsedRealtime() < ignoreSpeechErrorsUntilMs) {
            _uiState.update { it.copy(state = ConversationState.Paused, errorMessage = null) }
            return
        }
        retryCount += 1
        val message = speechErrorMessage(code)
        if (pausedCommandMode || _uiState.value.state == ConversationState.Paused) {
            _uiState.update {
                it.copy(state = ConversationState.Paused, errorMessage = null)
            }
            if (shouldRetry(code) && pausedCommandListenAttempts < MAX_PAUSED_COMMAND_ATTEMPTS) {
                scope.launch {
                    delay(800)
                    if (isSessionActive && pausedCommandMode) {
                        listenForPausedCommand()
                    }
                }
            } else {
                speechRecognitionClient.stopListening()
            }
            return
        }
        if (
            isSessionActive &&
            !pausedCommandMode &&
            code == SpeechRecognitionClient.ERROR_NO_MATCH
        ) {
            scope.launch {
                delay(450L)
                if (isSessionActive && !pausedCommandMode) {
                    listen(resetInactivityTimer = false)
                }
            }
            return
        }
        if (retryCount <= 3 && _uiState.value.state != ConversationState.Idle && shouldRetry(code)) {
            scope.launch {
                delay(retryDelayMs())
                if (isSessionActive && !pausedCommandMode) {
                    listen(resetInactivityTimer = false)
                }
            }
            return
        }

        _uiState.update {
            it.copy(
                state = ConversationState.Error,
                errorMessage = message
            )
        }
    }

    private fun retryDelayMs(): Long {
        return when (retryCount) {
            1 -> 450L
            2 -> 900L
            else -> 1_400L
        }
    }

    private suspend fun requestAssistantReply(type: String, userText: String?): AiFinalResponse {
        val settings = settingsRepository.currentSettings()
        val assistant = AssistantCatalog.find(settings.activeAssistantId)
        val assistantName = settings.assistantNames[assistant.id] ?: assistant.defaultName

        if (settings.backendUrl.isBlank() || settings.appApiToken.isBlank()) {
            val fallback = if (type == "start_conversation") {
                "Hi. I'm ready. Let's practice natural American English."
            } else {
                "I heard: ${userText.orEmpty()}. Nice. Tell me a little more about that."
            }
            return AiFinalResponse(spokenReply = fallback)
        }

        return try {
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
                    model = settings.model,
                    feedbackLevel = settings.feedbackLevel.name.lowercase(),
                    recentContext = recentContext.toList()
                )
            )
            val correction = cleanFeedbackValue(response.correction)
            val naturalAlternative = cleanFeedbackValue(response.naturalAlternative)
            val shortExplanation = cleanFeedbackValue(response.shortExplanation)
            val hasFeedback = !correction.isNullOrBlank() ||
                !naturalAlternative.isNullOrBlank() ||
                !shortExplanation.isNullOrBlank()

            if (type == "conversation_turn" && userText != null && (response.shouldSaveFeedback || hasFeedback)) {
                if (hasFeedback) {
                    feedbackRepository.save(
                        FeedbackEntry(
                            originalPhrase = userText,
                            correctedPhrase = correction,
                            naturalAlternative = naturalAlternative,
                            shortExplanation = shortExplanation,
                            assistantId = assistant.id,
                            model = settings.model,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
            response
        } catch (error: Exception) {
            Log.e(TAG, "Backend request failed", error)
            val message = backendErrorMessage(error)
            _uiState.update {
                it.copy(errorMessage = message)
            }
            val fallback = if (type == "start_conversation") {
                message
            } else {
                "$message Please try again in a moment."
            }
            AiFinalResponse(spokenReply = fallback)
        }
    }

    private fun backendErrorMessage(error: Exception): String {
        return when (BackendException.fromThrowable(error)) {
            BackendException.InternetUnavailable -> "Internet connection lost."
            BackendException.Timeout -> "AI response timed out."
            BackendException.Unauthorized -> "Backend token is not valid."
            BackendException.BackendUnavailable -> "AI backend is temporarily unavailable."
        }
    }

    private fun rememberContext(role: String, text: String) {
        recentContext.addLast(ContextTurn(role = role, text = text))
        while (recentContext.size > 12) {
            recentContext.removeFirst()
        }
    }

    private fun cleanFeedbackValue(value: String?): String? {
        val cleaned = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalized = cleaned.lowercase().trim('.', ':', '-', ' ')
        return when (normalized) {
            "none", "no", "n/a", "na", "null", "ninguna", "ninguno", "no correction", "no corrections" -> null
            else -> cleaned
        }
    }

    private fun shouldRetry(code: Int): Boolean {
        return code == SpeechRecognitionClient.ERROR_NO_MATCH
    }

    private fun speechErrorMessage(code: Int): String {
        return when (code) {
            SpeechRecognitionClient.ERROR_AUDIO_RECORD -> "Audio recording error."
            SpeechRecognitionClient.ERROR_PERMISSION -> "Microphone permission is missing."
            SpeechRecognitionClient.ERROR_BACKEND_NOT_CONFIGURED -> "Backend is required for continuous speech recognition."
            SpeechRecognitionClient.ERROR_NO_MATCH -> "I did not catch that."
            else -> "Speech recognition failed with code $code."
        }
    }

    private enum class LocalCommand {
        Pause,
        Resume,
        Finish,
        CloseApp
    }

}

sealed interface ConversationEvent {
    data object CloseApp : ConversationEvent
}

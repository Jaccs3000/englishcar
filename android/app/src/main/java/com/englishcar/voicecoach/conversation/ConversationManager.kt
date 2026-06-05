package com.englishcar.voicecoach.conversation

import android.os.SystemClock
import android.util.Log
import com.englishcar.voicecoach.ai.AssistantCatalog
import com.englishcar.voicecoach.audio.AudioFocusHandler
import com.englishcar.voicecoach.audio.GeminiLiveClient
import com.englishcar.voicecoach.audio.GeminiLiveEvent
import com.englishcar.voicecoach.diagnostics.DiagnosticsLogger
import com.englishcar.voicecoach.service.VoiceSessionController
import com.englishcar.voicecoach.settings.SettingsRepository
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

@Singleton
class ConversationManager @Inject constructor(
    private val geminiLiveClient: GeminiLiveClient,
    private val audioFocusHandler: AudioFocusHandler,
    private val settingsRepository: SettingsRepository,
    private val voiceSessionController: VoiceSessionController,
    private val diagnosticsLogger: DiagnosticsLogger
) {
    private companion object {
        const val TAG = "EnglishCarConversation"
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Unhandled conversation error", error)
        _uiState.update { it.copy(state = ConversationState.Error, errorMessage = "Something went wrong.") }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<ConversationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ConversationEvent> = _events.asSharedFlow()

    private var isSessionActive = false
    private var pausedCommandMode = false
    private var currentAutoPauseTimeoutMs = 60_000
    private var currentAutoFinishTimeoutMs = 600_000
    private var lastInteractionMs = 0L
    private var inactivityJob: Job? = null
    private var finishJob: Job? = null
    private var currentUserTranscript = StringBuilder()
    private var currentAssistantTranscript = StringBuilder()

    init {
        scope.launch {
            geminiLiveClient.events.collect { handleGeminiEvent(it) }
        }
    }

    fun start(hasRecordAudioPermission: Boolean) {
        diagnosticsLogger.add("Conversation", "start live permission=$hasRecordAudioPermission")
        if (!hasRecordAudioPermission) {
            _uiState.update { it.copy(state = ConversationState.Idle, isPermissionRequired = true, errorMessage = null) }
            return
        }
        if (!geminiLiveClient.isAvailable()) {
            _uiState.update { it.copy(state = ConversationState.Error, errorMessage = "Gemini API key or microphone permission is missing.") }
            return
        }

        scope.launch {
            audioFocusHandler.request { pauseForInterruption("We can continue whenever you're ready.") }
            val settings = settingsRepository.currentSettings()
            currentAutoPauseTimeoutMs = settings.autoPauseTimeoutMs
            currentAutoFinishTimeoutMs = settings.autoFinishTimeoutMs
            isSessionActive = true
            pausedCommandMode = false
            currentUserTranscript.clear()
            currentAssistantTranscript.clear()
            lastInteractionMs = SystemClock.elapsedRealtime()
            voiceSessionController.start()
            _uiState.update {
                it.copy(
                    state = ConversationState.Listening,
                    lastAssistantText = "I'm ready",
                    errorMessage = null,
                    isPermissionRequired = false
                )
            }
            geminiLiveClient.start(buildSystemInstruction(), audioResponses = true)
            scheduleTimers()
        }
    }

    fun finish() {
        diagnosticsLogger.add("Conversation", "finish")
        isSessionActive = false
        pausedCommandMode = false
        inactivityJob?.cancel()
        finishJob?.cancel()
        geminiLiveClient.stop()
        audioFocusHandler.abandon()
        voiceSessionController.stop()
        currentUserTranscript.clear()
        currentAssistantTranscript.clear()
        _uiState.value = ConversationUiState()
    }

    fun finishWithGoodbye(closeApp: Boolean = false) {
        diagnosticsLogger.add("Conversation", "finishWithGoodbye closeApp=$closeApp")
        _uiState.update { it.copy(state = ConversationState.Speaking, lastAssistantText = "See you later.") }
        finish()
        if (closeApp) _events.tryEmit(ConversationEvent.CloseApp)
    }

    fun retry(hasRecordAudioPermission: Boolean) {
        finish()
        start(hasRecordAudioPermission)
    }

    fun pause(spoken: Boolean = false) {
        if (!isSessionActive) return
        diagnosticsLogger.add("Conversation", "pause live spoken=$spoken")
        pausedCommandMode = true
        inactivityJob?.cancel()
        finishJob?.cancel()
        geminiLiveClient.stop()
        scope.launch {
            geminiLiveClient.start(buildPausedCommandInstruction(), audioResponses = false)
        }
        _uiState.update {
            it.copy(
                state = ConversationState.Paused,
                lastAssistantText = if (spoken) "Do you want to continue?" else it.lastAssistantText,
                errorMessage = null
            )
        }
    }

    fun pauseForInterruption(message: String = "We can continue whenever you're ready.") {
        if (!isSessionActive) return
        diagnosticsLogger.add("Conversation", "pause interruption")
        pausedCommandMode = true
        geminiLiveClient.stop()
        inactivityJob?.cancel()
        finishJob?.cancel()
        scope.launch {
            geminiLiveClient.start(buildPausedCommandInstruction(), audioResponses = false)
        }
        _uiState.update { it.copy(state = ConversationState.Paused, lastAssistantText = message, errorMessage = null) }
    }

    fun resume(hasRecordAudioPermission: Boolean) {
        diagnosticsLogger.add("Conversation", "resume live permission=$hasRecordAudioPermission")
        if (!hasRecordAudioPermission) {
            _uiState.update { it.copy(isPermissionRequired = true) }
            return
        }
        if (!isSessionActive) {
            start(true)
            return
        }
        pausedCommandMode = false
        currentUserTranscript.clear()
        currentAssistantTranscript.clear()
        _uiState.update { it.copy(state = ConversationState.Listening, lastAssistantText = "I'm ready", errorMessage = null) }
        scope.launch {
            geminiLiveClient.start(buildSystemInstruction(), audioResponses = true)
            scheduleTimers()
        }
    }

    private fun handleGeminiEvent(event: GeminiLiveEvent) {
        when (event) {
            GeminiLiveEvent.Connected -> diagnosticsLogger.add("Conversation", "live connected")
            GeminiLiveEvent.Listening -> {
                if (isSessionActive && !pausedCommandMode) {
                    _uiState.update { it.copy(state = ConversationState.Listening, errorMessage = null) }
                }
            }
            is GeminiLiveEvent.UserTranscript -> handleUserTranscript(event.text)
            is GeminiLiveEvent.AssistantTranscript -> handleAssistantTranscript(event.text)
            GeminiLiveEvent.TurnComplete -> handleTurnComplete()
            is GeminiLiveEvent.Error -> {
                _uiState.update { it.copy(state = ConversationState.Error, errorMessage = event.message) }
            }
        }
    }

    private fun handleUserTranscript(text: String) {
        if (!isSessionActive) return
        currentUserTranscript.append(text)
        val fullText = currentUserTranscript.toString().normalizeSpaces()
        diagnosticsLogger.add("Conversation", "live user transcript chars=${fullText.length} value=${fullText.take(120)}")
        if (!pausedCommandMode) {
            _uiState.update { it.copy(lastUserText = fullText, userTextStatus = "") }
        }
        lastInteractionMs = SystemClock.elapsedRealtime()
        if (!pausedCommandMode) scheduleTimers()
        scope.launch {
            val handledCommand = handleLocalCommand(fullText)
            if (handledCommand || pausedCommandMode) currentUserTranscript.clear()
        }
    }

    private fun handleAssistantTranscript(text: String) {
        if (!isSessionActive) return
        if (pausedCommandMode) {
            scope.launch {
                when (text.normalizeSpaces().uppercase()) {
                    "RESUME" -> resume(true)
                    "FINISH" -> finishWithGoodbye()
                    "CLOSE_APP" -> finishWithGoodbye(closeApp = true)
                    else -> diagnosticsLogger.add("Conversation", "paused assistant ignored value=${text.take(40)}")
                }
            }
            return
        }
        currentAssistantTranscript.append(text)
        val fullText = currentAssistantTranscript.toString().normalizeSpaces()
        diagnosticsLogger.add("Conversation", "live assistant transcript chars=${fullText.length} value=${fullText.take(120)}")
        _uiState.update { it.copy(state = ConversationState.Speaking, lastAssistantText = fullText, errorMessage = null) }
    }

    private fun handleTurnComplete() {
        diagnosticsLogger.add("Conversation", "live turn complete")
        currentUserTranscript.clear()
        currentAssistantTranscript.clear()
        if (isSessionActive && !pausedCommandMode) {
            _uiState.update { it.copy(state = ConversationState.Listening, errorMessage = null) }
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
                resume(true)
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

    private suspend fun buildSystemInstruction(): String {
        val settings = settingsRepository.currentSettings()
        val assistant = AssistantCatalog.find(settings.activeAssistantId)
        val assistantName = settings.assistantNames[assistant.id] ?: assistant.defaultName
        return listOf(
            "You are $assistantName, an American English conversation coach for a hands-free driving app.",
            "Start the session by saying exactly: I'm ready.",
            "Use American English. Speak calmly, clearly, neutrally, and didactically.",
            "Correct only clear grammar, word-order, vocabulary, meaning, or pronunciation mistakes.",
            "Do not correct punctuation, capitalization, commas, periods, or a missing question mark when spoken word order is correct.",
            "When correcting, begin with one of these phrases: You should say: | A better way to say that is: | The correct form is: | More natural to say: | You can say: | The correct pronunciation is:",
            "After a correction, continue the conversation naturally with a short sentence.",
            "Keep replies short: usually under 18 words.",
            "If you truly cannot understand the user, say exactly: Can you repeat, please?",
            "Never interrupt the user.",
            "Voice commands: pause=${settings.commands.pause}; resume=${settings.commands.resume}; finish=${settings.commands.finish}; close app=${settings.commands.closeApp}.",
            settings.userName.takeIf { it.isNotBlank() }?.let { "User name: $it." } ?: "User name: unknown."
        ).joinToString("\n")
    }

    private suspend fun buildPausedCommandInstruction(): String {
        val settings = settingsRepository.currentSettings()
        return listOf(
            "You are a silent voice-command detector for a paused hands-free app.",
            "Do not have a conversation.",
            "If the user says '${settings.commands.resume}', 'continue', 'resume', or 'start', respond with exactly: RESUME",
            "If the user says '${settings.commands.finish}', respond with exactly: FINISH",
            "If the user says '${settings.commands.closeApp}', respond with exactly: CLOSE_APP",
            "For any other speech, noise, music, or unclear audio, respond with exactly: IGNORE"
        ).joinToString("\n")
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

    private fun String.normalizeSpaces(): String = replace(Regex("\\s+"), " ").trim()

    private enum class LocalCommand { Pause, Resume, Finish, CloseApp }
}

sealed interface ConversationEvent {
    data object CloseApp : ConversationEvent
}

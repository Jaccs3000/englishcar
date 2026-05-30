package com.englishcar.voicecoach.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishcar.voicecoach.ai.BackendConversationClient
import com.englishcar.voicecoach.ai.BackendException
import com.englishcar.voicecoach.audio.TextToSpeechClient
import com.englishcar.voicecoach.conversation.ConversationManager
import com.englishcar.voicecoach.diagnostics.DiagnosticsLogger
import com.englishcar.voicecoach.settings.AppSettings
import com.englishcar.voicecoach.settings.CommandSettings
import com.englishcar.voicecoach.settings.SettingsRepository
import com.englishcar.voicecoach.service.VoiceSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val conversationManager: ConversationManager,
    private val textToSpeechClient: TextToSpeechClient,
    private val backendConversationClient: BackendConversationClient,
    private val voiceSessionController: VoiceSessionController,
    private val diagnosticsLogger: DiagnosticsLogger
) : ViewModel() {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val conversationState = conversationManager.uiState
    val conversationEvents = conversationManager.events
    val diagnosticEvents = diagnosticsLogger.events
    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()
    private val _modelOptions = MutableStateFlow(ModelOptionsState())
    val modelOptions: StateFlow<ModelOptionsState> = _modelOptions.asStateFlow()

    init {
        viewModelScope.launch {
            textToSpeechClient.warmUp()
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { loadedSettings ->
                _settings.value = loadedSettings
                _settingsLoaded.value = true
            }
        }
    }

    fun completeFirstLaunch(userName: String, assistantId: String) {
        viewModelScope.launch {
            settingsRepository.completeFirstLaunch(userName, assistantId)
        }
    }

    fun refreshModelOptions() {
        viewModelScope.launch {
            val current = settingsRepository.currentSettings()
            if (current.backendUrl.isBlank() || current.appApiToken.isBlank()) {
                _modelOptions.value = ModelOptionsState()
                return@launch
            }

            _modelOptions.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val availableModels = backendConversationClient.fetchModels(
                    backendUrl = current.backendUrl,
                    appApiToken = current.appApiToken
                )
                val models = availableModels.models
                    .filter { it.startsWith("gemini-") }
                    .ifEmpty { ModelOptionsState.DefaultModels }
                val defaultModel = availableModels.defaultModel
                    .takeIf { it in models }
                    ?: models.first()
                _modelOptions.value = ModelOptionsState(
                    models = models,
                    defaultModel = defaultModel,
                    isLoading = false,
                    errorMessage = null
                )
                if (current.model !in models) {
                    settingsRepository.saveModel(defaultModel)
                }
            } catch (error: Exception) {
                _modelOptions.value = ModelOptionsState(
                    isLoading = false,
                    errorMessage = when (BackendException.fromThrowable(error)) {
                        BackendException.InternetUnavailable -> "Could not load models: internet connection lost."
                        BackendException.Timeout -> "Could not load models: request timed out."
                        BackendException.Unauthorized -> "Could not load models: backend token is not valid."
                        BackendException.BackendUnavailable -> "Could not load models from backend."
                    }
                )
            }
        }
    }

    fun saveBackendConfig(url: String, token: String) {
        viewModelScope.launch {
            settingsRepository.saveBackendConfig(url, token)
        }
    }

    fun saveUserName(userName: String) {
        viewModelScope.launch {
            settingsRepository.saveUserName(userName)
        }
    }

    fun saveActiveAssistant(assistantId: String) {
        viewModelScope.launch {
            settingsRepository.saveActiveAssistant(assistantId)
        }
    }

    fun saveAssistantName(assistantId: String, name: String) {
        viewModelScope.launch {
            settingsRepository.saveAssistantName(assistantId, name)
        }
    }

    fun saveModel(model: String) {
        viewModelScope.launch {
            settingsRepository.saveModel(model)
        }
    }

    fun saveSilenceTimeout(timeoutMs: Int) {
        viewModelScope.launch {
            settingsRepository.saveSilenceTimeout(timeoutMs)
        }
    }

    fun saveSettings(
        userName: String,
        assistantId: String,
        assistantName: String,
        model: String,
        silenceTimeoutMs: Int,
        autoPauseTimeoutMs: Int,
        autoFinishTimeoutMs: Int,
        backendUrl: String,
        appApiToken: String,
        commands: CommandSettings
    ) {
        viewModelScope.launch {
            settingsRepository.saveUserName(userName)
            settingsRepository.saveActiveAssistant(assistantId)
            settingsRepository.saveAssistantName(assistantId, assistantName)
            settingsRepository.saveModel(model)
            settingsRepository.saveSilenceTimeout(silenceTimeoutMs)
            settingsRepository.saveAutoPauseTimeout(autoPauseTimeoutMs)
            settingsRepository.saveAutoFinishTimeout(autoFinishTimeoutMs)
            settingsRepository.saveBackendConfig(backendUrl, appApiToken)
            settingsRepository.saveCommands(commands)
        }
    }

    fun previewAssistant(assistantId: String, name: String, isMale: Boolean) {
        viewModelScope.launch {
            textToSpeechClient.stop()
            textToSpeechClient.preview(
                text = "Hi, my name is $name. I'll help you practice natural American English conversations while keeping things relaxed and easy to follow.",
                assistantId = assistantId,
                preferMale = isMale
            )
        }
    }

    fun startConversation(hasRecordAudioPermission: Boolean) {
        diagnosticsLogger.add("MainVM", "startConversation permission=$hasRecordAudioPermission")
        runCatching {
            if (hasRecordAudioPermission) {
                diagnosticsLogger.add("MainVM", "start service before conversation")
                voiceSessionController.start()
            }
            diagnosticsLogger.add("MainVM", "call conversationManager.start")
            conversationManager.start(hasRecordAudioPermission)
        }.onFailure { error ->
            diagnosticsLogger.add("MainVM", "startConversation failed ${error.javaClass.simpleName} message=${error.message.orEmpty().take(120)}")
        }
    }

    fun finishConversation() {
        conversationManager.finish()
        voiceSessionController.stop()
    }

    fun closeApp() {
        conversationManager.finishWithGoodbye(closeApp = true)
        voiceSessionController.stop()
    }

    fun pauseConversation() {
        conversationManager.pause(spoken = false)
    }

    fun resumeConversation(hasRecordAudioPermission: Boolean) {
        conversationManager.resume(hasRecordAudioPermission)
    }

    fun retryConversation(hasRecordAudioPermission: Boolean) {
        if (hasRecordAudioPermission) {
            voiceSessionController.start()
        }
        conversationManager.retry(hasRecordAudioPermission)
    }

    fun clearDiagnostics() {
        diagnosticsLogger.clear()
    }
}

data class ModelOptionsState(
    val models: List<String> = DefaultModels,
    val defaultModel: String = "gemini-2.5-flash-lite",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        val DefaultModels = listOf("gemini-2.5-flash-lite", "gemini-2.0-flash-lite", "gemini-2.0-flash", "gemini-2.5-flash")
    }
}

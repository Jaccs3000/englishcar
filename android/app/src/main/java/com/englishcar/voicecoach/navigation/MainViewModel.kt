package com.englishcar.voicecoach.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    init {
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

    fun saveSilenceTimeout(timeoutMs: Int) {
        viewModelScope.launch {
            settingsRepository.saveSilenceTimeout(timeoutMs)
        }
    }

    fun saveSettings(
        userName: String,
        assistantId: String,
        assistantName: String,
        silenceTimeoutMs: Int,
        autoPauseTimeoutMs: Int,
        autoFinishTimeoutMs: Int,
        commands: CommandSettings
    ) {
        viewModelScope.launch {
            settingsRepository.saveUserName(userName)
            settingsRepository.saveActiveAssistant(assistantId)
            settingsRepository.saveAssistantName(assistantId, assistantName)
            settingsRepository.saveSilenceTimeout(silenceTimeoutMs)
            settingsRepository.saveAutoPauseTimeout(autoPauseTimeoutMs)
            settingsRepository.saveAutoFinishTimeout(autoFinishTimeoutMs)
            settingsRepository.saveCommands(commands)
        }
    }

    fun previewAssistant(assistantId: String, name: String, isMale: Boolean) {
        diagnosticsLogger.add("MainVM", "previewAssistant skipped live assistant=$assistantId name=$name male=$isMale")
    }

    fun startConversation(hasRecordAudioPermission: Boolean, source: String = "unknown") {
        diagnosticsLogger.add("MainVM", "startConversation source=$source permission=$hasRecordAudioPermission")
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
        diagnosticsLogger.add("MainVM", "finishConversation source=home_finish")
        conversationManager.finish()
        voiceSessionController.stop()
    }

    fun closeApp() {
        diagnosticsLogger.add("MainVM", "closeApp source=home_exit")
        conversationManager.finishWithGoodbye(closeApp = true)
        voiceSessionController.stop()
    }

    fun pauseConversation() {
        diagnosticsLogger.add("MainVM", "pauseConversation source=home_pause")
        conversationManager.pause(spoken = false)
    }

    fun resumeConversation(hasRecordAudioPermission: Boolean, source: String = "unknown") {
        diagnosticsLogger.add("MainVM", "resumeConversation source=$source permission=$hasRecordAudioPermission")
        conversationManager.resume(hasRecordAudioPermission)
    }

    fun retryConversation(hasRecordAudioPermission: Boolean, source: String = "unknown") {
        diagnosticsLogger.add("MainVM", "retryConversation source=$source permission=$hasRecordAudioPermission")
        if (hasRecordAudioPermission) {
            voiceSessionController.start()
        }
        conversationManager.retry(hasRecordAudioPermission)
    }

    fun clearDiagnostics() {
        diagnosticsLogger.clear()
    }
}

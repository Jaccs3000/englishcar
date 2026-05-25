package com.englishcar.voicecoach.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "english_car_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val FirstLaunchComplete = booleanPreferencesKey("first_launch_complete")
        val UserName = stringPreferencesKey("user_name")
        val ActiveAssistantId = stringPreferencesKey("active_assistant_id")
        val BackendUrl = stringPreferencesKey("backend_url")
        val AppApiToken = stringPreferencesKey("app_api_token")
        val Model = stringPreferencesKey("model")
        val SilenceTimeoutMs = intPreferencesKey("silence_timeout_ms")
        val AutoPauseTimeoutMs = intPreferencesKey("auto_pause_timeout_ms")
        val FeedbackLevel = stringPreferencesKey("feedback_level")
        val PauseCommand = stringPreferencesKey("command_pause")
        val ResumeCommand = stringPreferencesKey("command_resume")
        val FinishCommand = stringPreferencesKey("command_finish")
        val CloseAppCommand = stringPreferencesKey("command_close_app")
        val EmmaName = stringPreferencesKey("assistant_name_emma")
        val SophiaName = stringPreferencesKey("assistant_name_sophia")
        val OliviaName = stringPreferencesKey("assistant_name_olivia")
        val AlexName = stringPreferencesKey("assistant_name_alex")
        val JamesName = stringPreferencesKey("assistant_name_james")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            isFirstLaunchComplete = prefs[Keys.FirstLaunchComplete] ?: false,
            userName = prefs[Keys.UserName].orEmpty(),
            activeAssistantId = prefs[Keys.ActiveAssistantId] ?: "emma",
            assistantNames = mapOf(
                "emma" to (prefs[Keys.EmmaName] ?: "Emma"),
                "sophia" to (prefs[Keys.SophiaName] ?: "Sophia"),
                "olivia" to (prefs[Keys.OliviaName] ?: "Olivia"),
                "alex" to (prefs[Keys.AlexName] ?: "Alex"),
                "james" to (prefs[Keys.JamesName] ?: "James")
            ),
            backendUrl = prefs[Keys.BackendUrl].orEmpty(),
            appApiToken = prefs[Keys.AppApiToken].orEmpty(),
            model = prefs[Keys.Model] ?: "gpt-5-mini",
            silenceTimeoutMs = prefs[Keys.SilenceTimeoutMs] ?: 3500,
            autoPauseTimeoutMs = prefs[Keys.AutoPauseTimeoutMs] ?: 60_000,
            commands = CommandSettings(
                pause = prefs[Keys.PauseCommand] ?: "hold on",
                resume = prefs[Keys.ResumeCommand] ?: "let's continue",
                finish = prefs[Keys.FinishCommand] ?: "finish",
                closeApp = prefs[Keys.CloseAppCommand] ?: "close app"
            ),
            feedbackLevel = parseFeedbackLevel(prefs[Keys.FeedbackLevel])
        )
    }

    suspend fun completeFirstLaunch(userName: String, assistantId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FirstLaunchComplete] = true
            prefs[Keys.UserName] = userName.trim()
            prefs[Keys.ActiveAssistantId] = assistantId
        }
    }

    suspend fun saveUserName(userName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.UserName] = userName.trim()
        }
    }

    suspend fun saveActiveAssistant(assistantId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ActiveAssistantId] = assistantId
        }
    }

    suspend fun saveAssistantName(assistantId: String, name: String) {
        context.dataStore.edit { prefs ->
            val key = when (assistantId) {
                "emma" -> Keys.EmmaName
                "sophia" -> Keys.SophiaName
                "olivia" -> Keys.OliviaName
                "alex" -> Keys.AlexName
                "james" -> Keys.JamesName
                else -> return@edit
            }
            prefs[key] = name.trim().ifBlank { defaultAssistantName(assistantId) }
        }
    }

    suspend fun saveModel(model: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.Model] = model
        }
    }

    suspend fun saveSilenceTimeout(timeoutMs: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SilenceTimeoutMs] = timeoutMs
        }
    }

    suspend fun saveAutoPauseTimeout(timeoutMs: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AutoPauseTimeoutMs] = timeoutMs
        }
    }

    suspend fun saveBackendConfig(url: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BackendUrl] = url.trim()
            prefs[Keys.AppApiToken] = token.trim()
        }
    }

    suspend fun saveCommands(commands: CommandSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PauseCommand] = cleanCommand(commands.pause, "hold on")
            prefs[Keys.ResumeCommand] = cleanCommand(commands.resume, "let's continue")
            prefs[Keys.FinishCommand] = cleanCommand(commands.finish, "finish")
            prefs[Keys.CloseAppCommand] = cleanCommand(commands.closeApp, "close app")
        }
    }

    suspend fun saveFeedbackLevel(level: FeedbackLevel) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FeedbackLevel] = level.name.lowercase()
        }
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    private fun defaultAssistantName(assistantId: String): String {
        return when (assistantId) {
            "emma" -> "Emma"
            "sophia" -> "Sophia"
            "olivia" -> "Olivia"
            "alex" -> "Alex"
            "james" -> "James"
            else -> "Emma"
        }
    }

    private fun cleanCommand(value: String, fallback: String): String {
        return value.trim().replace(Regex("\\s+"), " ").ifBlank { fallback }
    }

    private fun parseFeedbackLevel(value: String?): FeedbackLevel {
        return when (value?.lowercase()) {
            "low" -> FeedbackLevel.Low
            "high" -> FeedbackLevel.High
            else -> FeedbackLevel.Medium
        }
    }
}

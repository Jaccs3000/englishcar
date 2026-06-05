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
        val GeminiVoice = stringPreferencesKey("gemini_voice")
        val AutoPauseTimeoutMs = intPreferencesKey("auto_pause_timeout_ms")
        val AutoFinishTimeoutMs = intPreferencesKey("auto_finish_timeout_ms")
        val PauseCommand = stringPreferencesKey("command_pause")
        val ResumeCommand = stringPreferencesKey("command_resume")
        val FinishCommand = stringPreferencesKey("command_finish")
        val CloseAppCommand = stringPreferencesKey("command_close_app")
        val FemaleName = stringPreferencesKey("assistant_name_female")
        val MaleName = stringPreferencesKey("assistant_name_male")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            isFirstLaunchComplete = prefs[Keys.FirstLaunchComplete] ?: false,
            userName = prefs[Keys.UserName].orEmpty(),
            activeAssistantId = normalizeAssistantId(prefs[Keys.ActiveAssistantId]),
            assistantNames = mapOf(
                "female" to "Isa",
                "male" to "Alex"
            ),
            geminiVoice = normalizeGeminiVoice(prefs[Keys.GeminiVoice]),
            backendUrl = prefs[Keys.BackendUrl].orEmpty(),
            appApiToken = prefs[Keys.AppApiToken].orEmpty(),
            model = normalizeModel(prefs[Keys.Model]),
            silenceTimeoutMs = (prefs[Keys.SilenceTimeoutMs] ?: 1600).coerceIn(900, 8000),
            autoPauseTimeoutMs = prefs[Keys.AutoPauseTimeoutMs] ?: 60_000,
            autoFinishTimeoutMs = prefs[Keys.AutoFinishTimeoutMs] ?: 600_000,
            commands = CommandSettings(
                pause = prefs[Keys.PauseCommand] ?: "hold on",
                resume = prefs[Keys.ResumeCommand] ?: "let's continue",
                finish = prefs[Keys.FinishCommand] ?: "finish",
                closeApp = prefs[Keys.CloseAppCommand] ?: "close app"
            )
        )
    }

    suspend fun completeFirstLaunch(userName: String, assistantId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FirstLaunchComplete] = true
            prefs[Keys.UserName] = userName.toDisplayName()
            prefs[Keys.ActiveAssistantId] = normalizeAssistantId(assistantId)
        }
    }

    suspend fun saveUserName(userName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.UserName] = userName.toDisplayName()
        }
    }

    suspend fun saveActiveAssistant(assistantId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ActiveAssistantId] = normalizeAssistantId(assistantId)
        }
    }

    suspend fun saveAssistantName(assistantId: String, name: String) {
        context.dataStore.edit { prefs ->
            val key = when (assistantId) {
                "female" -> Keys.FemaleName
                "male" -> Keys.MaleName
                else -> return@edit
            }
            prefs[key] = defaultAssistantName(assistantId)
        }
    }

    suspend fun saveModel(model: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.Model] = normalizeModel(model)
        }
    }

    suspend fun saveSilenceTimeout(timeoutMs: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SilenceTimeoutMs] = timeoutMs.coerceIn(900, 8000)
        }
    }

    suspend fun saveGeminiVoice(voice: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GeminiVoice] = normalizeGeminiVoice(voice)
        }
    }

    suspend fun saveAutoPauseTimeout(timeoutMs: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AutoPauseTimeoutMs] = timeoutMs
        }
    }

    suspend fun saveAutoFinishTimeout(timeoutMs: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AutoFinishTimeoutMs] = timeoutMs
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

    suspend fun currentSettings(): AppSettings = settings.first()

    private fun defaultAssistantName(assistantId: String): String {
        return when (assistantId) {
            "male" -> "Alex"
            else -> "Isa"
        }
    }

    private fun normalizeAssistantId(value: String?): String {
        return when (value) {
            "male", "alex", "james" -> "male"
            else -> "female"
        }
    }

    private fun normalizeModel(value: String?): String {
        return when (value) {
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite" -> value
            else -> "gemini-2.5-flash-lite"
        }
    }

    private fun normalizeGeminiVoice(value: String?): String {
        return geminiVoiceOptions.firstOrNull { it.equals(value, ignoreCase = true) } ?: "Kore"
    }

    private fun cleanCommand(value: String, fallback: String): String {
        return value.trim().replace(Regex("\\s+"), " ").ifBlank { fallback }
    }

    private fun String.toDisplayName(): String {
        return trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
    }

}

val geminiVoiceOptions = listOf(
    "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede",
    "Callirrhoe", "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba",
    "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar",
    "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi",
    "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat"
)

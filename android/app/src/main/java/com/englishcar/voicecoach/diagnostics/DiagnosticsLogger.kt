package com.englishcar.voicecoach.diagnostics

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiagnosticEvent(
    val timestamp: String,
    val category: String,
    val message: String
)

@Singleton
class DiagnosticsLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val preferences = context.getSharedPreferences("english_car_diagnostics", Context.MODE_PRIVATE)
    private val _events = MutableStateFlow<List<DiagnosticEvent>>(loadInitialEvents())
    val events: StateFlow<List<DiagnosticEvent>> = _events.asStateFlow()

    fun add(category: String, message: String) {
        val cleanCategory = category.take(32)
        val cleanMessage = message
            .replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]+"), "Bearer [redacted]")
            .replace(Regex("ek_[A-Za-z0-9._\\-]+"), "ek_[redacted]")
            .take(240)
        val event = DiagnosticEvent(
            timestamp = formatter.format(Date()),
            category = cleanCategory,
            message = cleanMessage
        )
        Log.d(TAG, "${event.timestamp} ${event.category}: ${event.message}")
        _events.value = (_events.value + event).takeLast(MAX_EVENTS)
        persistRecentEvents()
    }

    fun clear() {
        _events.value = emptyList()
        preferences.edit()
            .remove("recent_events")
            .remove("last_crash")
            .apply()
    }

    private fun loadInitialEvents(): List<DiagnosticEvent> {
        val restoredEvents = preferences.getString("recent_events", null)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split("|", limit = 3)
                if (parts.size == 3) DiagnosticEvent(parts[0], parts[1], parts[2]) else null
            }
            ?.toList()
            .orEmpty()

        val crash = preferences.getString("last_crash", null)
            ?.takeIf { it.isNotBlank() }
            ?.let {
                DiagnosticEvent(
                    timestamp = formatter.format(Date()),
                    category = "Crash",
                    message = it.take(240)
                )
            }

        return (restoredEvents + listOfNotNull(crash)).takeLast(MAX_EVENTS)
    }

    private fun persistRecentEvents() {
        val text = _events.value.takeLast(PERSISTED_EVENTS).joinToString(separator = "\n") { event ->
            listOf(
                event.timestamp.sanitizePersistedField(),
                event.category.sanitizePersistedField(),
                event.message.sanitizePersistedField()
            ).joinToString("|")
        }
        preferences.edit().putString("recent_events", text).apply()
    }

    private fun String.sanitizePersistedField(): String {
        return replace("|", "/").replace("\n", " ").replace("\r", " ")
    }

    private companion object {
        const val TAG = "EnglishCarDiagnostics"
        const val MAX_EVENTS = 1_500
        const val PERSISTED_EVENTS = 500
    }
}

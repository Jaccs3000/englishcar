package com.englishcar.voicecoach.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

@Singleton
class FeedbackRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dbHelper = FeedbackDbHelper(context)
    private val _entries = MutableStateFlow(loadEntries())
    val entries: Flow<List<FeedbackEntry>> = _entries

    suspend fun save(entry: FeedbackEntry) {
        if (!entry.hasUsefulFeedback()) return
        withContext(Dispatchers.IO) {
            dbHelper.writableDatabase.insert(
                "feedback_entries",
                null,
                ContentValues().apply {
                    put("originalPhrase", entry.originalPhrase)
                    put("correctedPhrase", entry.correctedPhrase)
                    put("naturalAlternative", entry.naturalAlternative)
                    put("shortExplanation", entry.shortExplanation)
                    put("assistantId", entry.assistantId)
                    put("model", entry.model)
                    put("timestamp", entry.timestamp)
                }
            )
            _entries.value = loadEntries()
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            dbHelper.writableDatabase.delete("feedback_entries", null, null)
            _entries.value = emptyList()
        }
    }

    suspend fun cleanInvalidEntries() {
        withContext(Dispatchers.IO) {
            val invalidIds = loadEntries(includeInvalid = true)
                .filterNot { it.hasUsefulFeedback() }
                .map { it.id.toString() }
            if (invalidIds.isNotEmpty()) {
                dbHelper.writableDatabase.delete(
                    "feedback_entries",
                    "id IN (${invalidIds.joinToString(",")})",
                    null
                )
            }
            _entries.value = loadEntries()
        }
    }

    private fun loadEntries(includeInvalid: Boolean = false): List<FeedbackEntry> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "feedback_entries",
            null,
            null,
            null,
            null,
            null,
            "timestamp DESC"
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val entry = FeedbackEntry(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        originalPhrase = it.getString(it.getColumnIndexOrThrow("originalPhrase")),
                        correctedPhrase = cleanFeedbackValue(it.getStringOrNull("correctedPhrase")),
                        naturalAlternative = cleanFeedbackValue(it.getStringOrNull("naturalAlternative")),
                        shortExplanation = cleanFeedbackValue(it.getStringOrNull("shortExplanation")),
                        assistantId = it.getString(it.getColumnIndexOrThrow("assistantId")),
                        model = it.getString(it.getColumnIndexOrThrow("model")),
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                    )
                    if (includeInvalid || entry.hasUsefulFeedback()) {
                        add(entry)
                    }
                }
            }
        }
    }

    private fun android.database.Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun FeedbackEntry.hasUsefulFeedback(): Boolean {
        return !correctedPhrase.isNullOrBlank() ||
            !naturalAlternative.isNullOrBlank() ||
            !shortExplanation.isNullOrBlank()
    }

    private fun cleanFeedbackValue(value: String?): String? {
        val cleaned = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalized = cleaned.lowercase().trim('.', ':', '-', ' ')
        return when (normalized) {
            "none", "no", "n/a", "na", "null", "ninguna", "ninguno", "no correction", "no corrections" -> null
            else -> cleaned
        }
    }

    private class FeedbackDbHelper(context: Context) : SQLiteOpenHelper(context, "english_car.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE feedback_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    originalPhrase TEXT NOT NULL,
                    correctedPhrase TEXT,
                    naturalAlternative TEXT,
                    shortExplanation TEXT,
                    assistantId TEXT NOT NULL,
                    model TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}

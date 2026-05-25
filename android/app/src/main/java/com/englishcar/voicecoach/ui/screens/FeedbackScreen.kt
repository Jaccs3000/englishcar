package com.englishcar.voicecoach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.englishcar.voicecoach.history.FeedbackEntry
import java.text.DateFormat
import java.util.Date

@Composable
fun FeedbackScreen(
    entries: List<FeedbackEntry>,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredEntries = remember(entries, query) {
        val cleanedQuery = query.trim()
        if (cleanedQuery.isBlank()) {
            entries
        } else {
            entries.filter { it.matches(cleanedQuery) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF03070C), Color(0xFF07111A))))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Feedback",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onBack) {
                Text("Back", color = MaterialTheme.colorScheme.primary)
            }
        }

        if (entries.isEmpty()) {
            Text(
                "Corrections and natural alternatives will appear here after conversation.",
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search feedback", color = MaterialTheme.colorScheme.onSurface) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFF52606D),
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    feedbackCountLabel(filteredEntries.size, entries.size, query),
                    color = Color(0xFF9DAAB6),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }
            if (filteredEntries.isEmpty()) {
                Text(
                    "No feedback matches that search.",
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        FeedbackCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(entry: FeedbackEntry) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LabelValue("Original", entry.originalPhrase)
            entry.correctedPhrase?.let { LabelValue("Correction", it) }
            entry.naturalAlternative?.let { LabelValue("Natural", it) }
            entry.shortExplanation?.let { LabelValue("Why", it) }
            Spacer(Modifier.height(2.dp))
            Text(
                "${entry.assistantId.replaceFirstChar { it.uppercase() }} - ${entry.model}",
                color = Color(0xFF9DAAB6),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp)),
                color = Color(0xFF9DAAB6),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(value, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun feedbackCountLabel(visibleCount: Int, totalCount: Int, query: String): String {
    val suffix = if (totalCount == 1) "saved correction" else "saved corrections"
    return if (query.isBlank()) {
        "$totalCount $suffix"
    } else {
        "$visibleCount of $totalCount"
    }
}

private fun FeedbackEntry.matches(query: String): Boolean {
    return listOfNotNull(
        originalPhrase,
        correctedPhrase,
        naturalAlternative,
        shortExplanation,
        assistantId,
        model
    ).any { it.contains(query, ignoreCase = true) }
}

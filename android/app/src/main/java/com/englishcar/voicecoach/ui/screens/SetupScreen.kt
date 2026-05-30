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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class SetupAssistant(val id: String, val name: String, val voice: String)

private val assistants = listOf(
    SetupAssistant("female", "Isa", "Female"),
    SetupAssistant("male", "Alex", "Male")
)

@Composable
fun SetupScreen(
    onContinue: (String, String) -> Unit,
    onPreviewAssistant: (String, String, Boolean) -> Unit
) {
    var userName by remember { mutableStateOf("") }
    var assistantId by remember { mutableStateOf("female") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF02060B), Color(0xFF08131E), Color(0xFF03070C))
                )
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "English Car",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Quick setup",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose how your driving conversation starts.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFC7D5DD)
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = userName,
            onValueChange = { userName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your name", color = MaterialTheme.colorScheme.onSurface) },
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
        Spacer(Modifier.height(20.dp))
        Text(
            "Assistant",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            assistants.take(3).forEach { assistant ->
                SetupAssistantChip(
                    selected = assistantId == assistant.id,
                    text = assistant.name,
                    onClick = {
                        assistantId = assistant.id
                        onPreviewAssistant(assistant.id, assistant.name, assistant.id == "male")
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            assistants.drop(3).forEach { assistant ->
                SetupAssistantChip(
                    selected = assistantId == assistant.id,
                    text = assistant.name,
                    onClick = {
                        assistantId = assistant.id
                        onPreviewAssistant(assistant.id, assistant.name, assistant.id == "male")
                    }
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { onContinue(userName.ifBlank { "Friend" }, assistantId) },
            modifier = Modifier.align(Alignment.End),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun SetupAssistantChip(selected: Boolean, text: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

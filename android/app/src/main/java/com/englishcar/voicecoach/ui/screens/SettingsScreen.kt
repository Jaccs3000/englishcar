package com.englishcar.voicecoach.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.englishcar.voicecoach.navigation.ModelOptionsState
import com.englishcar.voicecoach.settings.AppSettings
import com.englishcar.voicecoach.settings.CommandSettings
import com.englishcar.voicecoach.settings.FeedbackLevel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data class AssistantOption(val id: String, val label: String, val gender: String, val personality: String)
private data class CommandOption(val key: String, val label: String)

private val assistantOptions = listOf(
    AssistantOption("emma", "Emma", "Female", "Young"),
    AssistantOption("sophia", "Sophia", "Female", "Mature"),
    AssistantOption("alex", "Alex", "Male", "Young"),
    AssistantOption("james", "James", "Male", "Mature")
)

private val commandOptions = listOf(
    CommandOption("pause", "Pause"),
    CommandOption("resume", "Resume"),
    CommandOption("finish", "Finish"),
    CommandOption("close", "Close app")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    modelOptions: ModelOptionsState,
    onSaveSettings: (String, String, String, String, Int, Int, String, String, CommandSettings, FeedbackLevel) -> Unit,
    onPreviewAssistant: (String, String, Boolean) -> Unit,
    onRefreshModels: () -> Unit,
    onBack: () -> Unit
) {
    var backendUrl by remember { mutableStateOf(settings.backendUrl) }
    var token by remember { mutableStateOf(settings.appApiToken) }
    var userName by remember { mutableStateOf(settings.userName) }
    var assistantId by remember { mutableStateOf(settings.activeAssistantId.takeIf { it != "olivia" } ?: "emma") }
    var assistantName by remember { mutableStateOf(settings.assistantNames[assistantId].orEmpty()) }
    var model by remember { mutableStateOf(settings.model) }
    var silenceTimeoutMs by remember { mutableStateOf(settings.silenceTimeoutMs) }
    var autoPauseTimeoutMs by remember { mutableStateOf(settings.autoPauseTimeoutMs) }
    var selectedCommand by remember { mutableStateOf(commandOptions.first()) }
    var pauseCommand by remember { mutableStateOf(settings.commands.pause) }
    var resumeCommand by remember { mutableStateOf(settings.commands.resume) }
    var finishCommand by remember { mutableStateOf(settings.commands.finish) }
    var closeAppCommand by remember { mutableStateOf(settings.commands.closeApp) }
    var feedbackLevel by remember { mutableStateOf(settings.feedbackLevel) }
    var backendExpanded by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activeAssistant = assistantOptions.firstOrNull { it.id == assistantId } ?: assistantOptions.first()

    fun currentCommands() = CommandSettings(
        pause = pauseCommand,
        resume = resumeCommand,
        finish = finishCommand,
        closeApp = closeAppCommand
    )

    fun commandValue(): String {
        return when (selectedCommand.key) {
            "pause" -> pauseCommand
            "resume" -> resumeCommand
            "finish" -> finishCommand
            else -> closeAppCommand
        }
    }

    fun updateCommand(value: String) {
        when (selectedCommand.key) {
            "pause" -> pauseCommand = value
            "resume" -> resumeCommand = value
            "finish" -> finishCommand = value
            else -> closeAppCommand = value
        }
    }

    fun save() {
        onSaveSettings(
            userName,
            assistantId,
            assistantName,
            model,
            silenceTimeoutMs,
            autoPauseTimeoutMs,
            backendUrl,
            token,
            currentCommands(),
            feedbackLevel
        )
        scope.launch { snackbarHostState.showSnackbar("Settings saved") }
    }

    fun hasUnsavedChanges(): Boolean {
        val savedAssistantId = settings.activeAssistantId.takeIf { it != "olivia" } ?: "emma"
        return userName != settings.userName ||
            assistantId != savedAssistantId ||
            assistantName != settings.assistantNames[savedAssistantId].orEmpty() ||
            model != settings.model ||
            silenceTimeoutMs != settings.silenceTimeoutMs ||
            autoPauseTimeoutMs != settings.autoPauseTimeoutMs ||
            backendUrl != settings.backendUrl ||
            token != settings.appApiToken ||
            currentCommands() != settings.commands ||
            feedbackLevel != settings.feedbackLevel
    }

    fun requestExit() {
        if (hasUnsavedChanges()) showDiscardDialog = true else onBack()
    }

    LaunchedEffect(settings) {
        backendUrl = settings.backendUrl
        token = settings.appApiToken
        userName = settings.userName
        assistantId = settings.activeAssistantId.takeIf { it != "olivia" } ?: "emma"
        assistantName = settings.assistantNames[assistantId].orEmpty()
        model = settings.model
        silenceTimeoutMs = settings.silenceTimeoutMs
        autoPauseTimeoutMs = settings.autoPauseTimeoutMs
        pauseCommand = settings.commands.pause
        resumeCommand = settings.commands.resume
        finishCommand = settings.commands.finish
        closeAppCommand = settings.commands.closeApp
        feedbackLevel = settings.feedbackLevel
    }

    BackHandler { requestExit() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF03070C), Color(0xFF06101A))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { requestExit() }) {
                    Text("Back", color = MaterialTheme.colorScheme.primary)
                }
            }

            SectionTitle("Profile")
            SettingsField(value = userName, onValueChange = { userName = it }, label = "Your name")

            SectionTitle("Assistant")
            Text("${activeAssistant.gender} voice - ${activeAssistant.personality}", color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                assistantOptions.forEach { option ->
                    SettingsChip(
                        selected = assistantId == option.id,
                        text = "${settings.assistantNames[option.id] ?: option.label} (${option.gender})",
                        onClick = {
                            assistantId = option.id
                            assistantName = settings.assistantNames[option.id] ?: option.label
                            onPreviewAssistant(option.id, assistantName, option.gender == "Male")
                        }
                    )
                }
            }
            SettingsField(value = assistantName, onValueChange = { assistantName = it }, label = "Assistant name")

            SectionTitle("AI model")
            Text(
                when {
                    modelOptions.isLoading -> "Loading models from backend..."
                    modelOptions.errorMessage != null -> modelOptions.errorMessage
                    else -> "Models are loaded from your backend allowlist."
                },
                color = MaterialTheme.colorScheme.onSurface
            )
            DropdownField(
                label = "AI model",
                value = model,
                options = modelOptions.models,
                onSelected = { model = it }
            )
            TextButton(onClick = onRefreshModels) {
                Text("Refresh models", color = MaterialTheme.colorScheme.primary)
            }

            SectionTitle("Correction level")
            Text(feedbackLevelDescription(feedbackLevel), color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedbackLevel.entries.forEach { level ->
                    SettingsChip(selected = feedbackLevel == level, text = levelLabel(level), onClick = { feedbackLevel = level })
                }
            }

            SectionTitle("Silence timeout")
            TimeSlider(
                valueMs = silenceTimeoutMs,
                label = "Current: ${formatDuration(silenceTimeoutMs)}",
                minMs = 1_500,
                maxMs = 300_000,
                onValueChange = { silenceTimeoutMs = it }
            )

            SectionTitle("Auto pause")
            TimeSlider(
                valueMs = autoPauseTimeoutMs,
                label = if (autoPauseTimeoutMs <= 0) "Auto pause is off." else "Pause after ${formatDuration(autoPauseTimeoutMs)}.",
                minMs = 0,
                maxMs = 300_000,
                onValueChange = { autoPauseTimeoutMs = it }
            )

            SectionTitle("Voice commands")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(0.9f)) {
                    DropdownField(
                        label = "Command",
                        value = selectedCommand.label,
                        options = commandOptions.map { it.label },
                        onSelected = { label -> selectedCommand = commandOptions.first { it.label == label } }
                    )
                }
                Box(modifier = Modifier.weight(1.1f)) {
                    SettingsField(value = commandValue(), onValueChange = { updateCommand(it) }, label = "Phrase")
                }
            }

            TextButton(onClick = { backendExpanded = !backendExpanded }) {
                Text(if (backendExpanded) "Hide backend" else "Show backend", color = MaterialTheme.colorScheme.primary)
            }
            if (backendExpanded) {
                SectionTitle("Backend")
                SettingsField(value = backendUrl, onValueChange = { backendUrl = it }, label = "Worker URL")
                SettingsField(value = token, onValueChange = { token = it }, label = "App API token")
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF07111A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { save() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFC857),
                        contentColor = Color(0xFF07111A)
                    )
                ) {
                    Text("Save settings")
                }
                Button(
                    onClick = { requestExit() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB94A48),
                        contentColor = Color.White
                    )
                ) {
                    Text("Back")
                }
            }
            SnackbarHost(hostState = snackbarHostState)
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Unsaved changes") },
            text = { Text("Do you want to save your settings before leaving?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        save()
                        showDiscardDialog = false
                        onBack()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text("Stay")
                    }
                    TextButton(
                        onClick = {
                            showDiscardDialog = false
                            onBack()
                        }
                    ) {
                        Text("Leave")
                    }
                }
            }
        )
    }
}

private fun levelLabel(level: FeedbackLevel): String = when (level) {
    FeedbackLevel.Low -> "Low"
    FeedbackLevel.Medium -> "Medium"
    FeedbackLevel.High -> "High"
}

private fun feedbackLevelDescription(level: FeedbackLevel): String = when (level) {
    FeedbackLevel.Low -> "Only important corrections."
    FeedbackLevel.Medium -> "Important corrections and natural phrasing."
    FeedbackLevel.High -> "Mention every useful correction."
}

private fun formatDuration(ms: Int): String {
    if (ms <= 0) return "Off"
    return if (ms < 60_000) "${ms / 1000}s" else "${ms / 60_000}m ${((ms % 60_000) / 1000)}s"
}

@Composable
private fun TimeSlider(valueMs: Int, label: String, minMs: Int, maxMs: Int, onValueChange: (Int) -> Unit) {
    Text(label, color = MaterialTheme.colorScheme.onSurface)
    Slider(
        value = valueMs.toFloat(),
        onValueChange = { value ->
            val step = 5_000
            val rounded = (value / step).roundToInt() * step
            onValueChange(rounded.coerceIn(minMs, maxMs))
        },
        valueRange = minMs.toFloat()..maxMs.toFloat(),
        steps = ((maxMs - minMs) / 5_000).coerceAtLeast(0)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, value: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            readOnly = true,
            label = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            colors = textFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingsField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        colors = textFieldColors()
    )
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color(0xFF52606D),
    cursorColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun SettingsChip(selected: Boolean, text: String, onClick: () -> Unit) {
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

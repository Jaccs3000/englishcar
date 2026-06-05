package com.englishcar.voicecoach.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.englishcar.voicecoach.settings.AppSettings
import com.englishcar.voicecoach.settings.CommandSettings
import com.englishcar.voicecoach.settings.geminiVoiceOptions
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data class AssistantOption(val id: String, val label: String, val gender: String)
private data class CommandOption(val id: String, val label: String)

private val assistantOptions = listOf(
    AssistantOption("female", "Isa", "Female"),
    AssistantOption("male", "Alex", "Male")
)

private val commandOptions = listOf(
    CommandOption("pause", "Pause"),
    CommandOption("resume", "Resume"),
    CommandOption("finish", "Finalizar chat"),
    CommandOption("close", "Close app")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSaveSettings: (String, String, String, Int, Int, Int, CommandSettings, String) -> Unit,
    onPreviewAssistant: (String, String, Boolean) -> Unit,
    onPreviewVoice: (String) -> Unit,
    onStopPreview: () -> Unit,
    onBack: () -> Unit
) {
    var userName by remember { mutableStateOf(settings.userName) }
    var assistantId by remember { mutableStateOf(settings.activeAssistantId) }
    var assistantName by remember { mutableStateOf(settings.assistantNames[assistantId].orEmpty()) }
    var silenceTimeoutMs by remember { mutableStateOf(settings.silenceTimeoutMs) }
    var geminiVoice by remember { mutableStateOf(settings.geminiVoice) }
    var autoPauseTimeoutMs by remember { mutableStateOf(settings.autoPauseTimeoutMs) }
    var autoFinishTimeoutMs by remember { mutableStateOf(settings.autoFinishTimeoutMs) }
    var pauseCommand by remember { mutableStateOf(settings.commands.pause) }
    var resumeCommand by remember { mutableStateOf(settings.commands.resume) }
    var finishCommand by remember { mutableStateOf(settings.commands.finish) }
    var closeAppCommand by remember { mutableStateOf(settings.commands.closeApp) }
    var selectedCommandId by remember { mutableStateOf("pause") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun commands() = CommandSettings(pauseCommand, resumeCommand, finishCommand, closeAppCommand)
    fun selectedCommandValue(): String {
        return when (selectedCommandId) {
            "resume" -> resumeCommand
            "finish" -> finishCommand
            "close" -> closeAppCommand
            else -> pauseCommand
        }
    }
    fun updateSelectedCommand(value: String) {
        when (selectedCommandId) {
            "resume" -> resumeCommand = value
            "finish" -> finishCommand = value
            "close" -> closeAppCommand = value
            else -> pauseCommand = value
        }
    }
    fun save() {
        val fixedAssistantName = if (assistantId == "male") "Alex" else "Isa"
        onSaveSettings(userName, assistantId, fixedAssistantName, silenceTimeoutMs, autoPauseTimeoutMs, autoFinishTimeoutMs, commands(), geminiVoice)
        scope.launch { snackbarHostState.showSnackbar("Settings saved") }
    }

    LaunchedEffect(settings) {
        userName = settings.userName
        assistantId = settings.activeAssistantId
        assistantName = if (assistantId == "male") "Alex" else "Isa"
        silenceTimeoutMs = settings.silenceTimeoutMs
        geminiVoice = settings.geminiVoice
        autoPauseTimeoutMs = settings.autoPauseTimeoutMs
        autoFinishTimeoutMs = settings.autoFinishTimeoutMs
        pauseCommand = settings.commands.pause
        resumeCommand = settings.commands.resume
        finishCommand = settings.commands.finish
        closeAppCommand = settings.commands.closeApp
    }

    DisposableEffect(Unit) {
        onDispose { onStopPreview() }
    }

    BackHandler {
        onStopPreview()
        onBack()
    }

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
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)

            SectionTitle("Profile")
            SettingsField(userName, { userName = it }, "Your name")

            SectionTitle("Assistant")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                assistantOptions.forEach { option ->
                    SettingsChip(
                        selected = assistantId == option.id,
                        text = option.label,
                        onClick = {
                            assistantId = option.id
                            assistantName = option.label
                            onPreviewAssistant(option.id, assistantName, option.gender == "Male")
                        }
                    )
                }
            }

            SectionTitle("Gemini voices")
            Text("Tap Play to hear a sample.", color = Color(0xFF9DAAB6), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                geminiVoiceOptions.forEach { voice ->
                    SettingsChip(
                        selected = geminiVoice == voice,
                        text = "$voice Play",
                        onClick = {
                            geminiVoice = voice
                            onPreviewVoice(voice)
                        }
                    )
                }
            }

            SectionTitle("Turn timing")
            TimeSlider(autoPauseTimeoutMs, "Ask to continue after ${formatDuration(autoPauseTimeoutMs)}.", 15_000, 600_000, 5_000) { autoPauseTimeoutMs = it }
            TimeSlider(autoFinishTimeoutMs, "Finish app after ${formatDuration(autoFinishTimeoutMs)} inactive.", 60_000, 1_800_000, 30_000) { autoFinishTimeoutMs = it }

            SectionTitle("Voice commands")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                DropdownField(
                    label = "Action",
                    value = commandOptions.first { it.id == selectedCommandId }.label,
                    options = commandOptions.map { it.label },
                    modifier = Modifier.weight(0.75f)
                ) { label ->
                    selectedCommandId = commandOptions.first { it.label == label }.id
                }
                OutlinedTextField(
                    value = selectedCommandValue(),
                    onValueChange = { updateSelectedCommand(it) },
                    modifier = Modifier.weight(1.4f),
                    label = { Text("Voice phrase") },
                    singleLine = true,
                    colors = textFieldColors()
                )
            }

        }

        Card(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF07111A))
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { save() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC857), contentColor = Color(0xFF07111A))
                ) { Text("Save") }
                Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

private fun formatDuration(ms: Int): String = if (ms < 60_000) "${ms / 1000}s" else "${ms / 60_000}m ${((ms % 60_000) / 1000)}s"

@Composable
private fun TimeSlider(valueMs: Int, label: String, minMs: Int, maxMs: Int, stepMs: Int, onValueChange: (Int) -> Unit) {
    Text(label, color = MaterialTheme.colorScheme.onSurface)
    Slider(
        value = valueMs.toFloat(),
        onValueChange = { value -> onValueChange(((value / stepMs).roundToInt() * stepMs).coerceIn(minMs, maxMs)) },
        valueRange = minMs.toFloat()..maxMs.toFloat(),
        steps = ((maxMs - minMs) / stepMs - 1).coerceAtLeast(0)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = modifier.menuAnchor(),
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = textFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelected(option)
                    expanded = false
                })
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
        label = { Text(label) },
        singleLine = true,
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

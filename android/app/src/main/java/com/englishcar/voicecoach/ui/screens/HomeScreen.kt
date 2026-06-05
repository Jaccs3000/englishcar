package com.englishcar.voicecoach.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.englishcar.voicecoach.conversation.ConversationState
import com.englishcar.voicecoach.conversation.ConversationUiState

@Composable
fun HomeScreen(
    assistantId: String,
    assistantName: String,
    conversationUiState: ConversationUiState,
    onStartConversation: (Boolean) -> Unit,
    onFinishConversation: () -> Unit,
    onPauseConversation: () -> Unit,
    onResumeConversation: (Boolean) -> Unit,
    onRetryConversation: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenLogs: () -> Unit,
    onCloseApp: () -> Unit
) {
    val context = LocalContext.current
    var pendingRetry by remember { mutableStateOf(false) }
    var startRequested by remember { mutableStateOf(false) }

    LaunchedEffect(conversationUiState.state, conversationUiState.isPermissionRequired) {
        if (conversationUiState.state != ConversationState.Idle) {
            startRequested = false
        }
        if (conversationUiState.isPermissionRequired) {
            startRequested = false
        }
    }

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun requiredPermissions(): Array<String> {
        return buildList {
            if (!hasMicPermission()) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNotifications = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasNotifications) add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.RECORD_AUDIO] == true || hasMicPermission()
        if (pendingRetry) {
            onRetryConversation(granted)
        } else if (conversationUiState.state == ConversationState.Paused) {
            onResumeConversation(granted)
        } else {
            onStartConversation(granted)
        }
        pendingRetry = false
    }

    fun requestStart(retry: Boolean = false) {
        if (startRequested) return
        startRequested = true
        val hasPermission = hasMicPermission()
        if (hasPermission) {
            if (retry) onRetryConversation(true) else onStartConversation(true)
        } else {
            pendingRetry = retry
            permissionLauncher.launch(requiredPermissions())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF02060B), Color(0xFF08131E), Color(0xFF03070C))
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            VoiceWave(state = conversationUiState.state)
            Text(
                statusLabel(conversationUiState.state),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (
                conversationUiState.state == ConversationState.Idle ||
                conversationUiState.state == ConversationState.Error ||
                conversationUiState.state == ConversationState.AwaitingUser
            ) {
                FilledIconButton(
                    enabled = !startRequested,
                    onClick = {
                        if (conversationUiState.state == ConversationState.AwaitingUser) {
                            onResumeConversation(hasMicPermission())
                        } else {
                            requestStart(retry = conversationUiState.state == ConversationState.Error)
                        }
                    },
                    modifier = Modifier
                        .size(184.dp)
                        .shadow(24.dp, CircleShape)
                ) {
                    Text(if (conversationUiState.state == ConversationState.AwaitingUser) "LISTEN" else "PLAY", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            } else if (conversationUiState.state == ConversationState.Paused) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onResumeConversation(hasMicPermission()) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Resume")
                    }
                    TextButton(onClick = onFinishConversation) {
                        Text("Finish")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPauseConversation,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Pause")
                    }
                    TextButton(onClick = onFinishConversation) {
                        Text("Finish")
                    }
                }
            }
            Text(
                "$assistantName ready",
                color = Color(0xFFC7D5DD),
                style = MaterialTheme.typography.titleMedium
            )
            val userDisplayText = conversationUiState.lastUserText.ifBlank { conversationUiState.userTextStatus }
            if (userDisplayText.isNotBlank()) {
                TranscriptLine(label = "You", text = userDisplayText)
            }
            if (conversationUiState.lastAssistantText.isNotBlank()) {
                TranscriptLine(label = assistantName, text = conversationUiState.lastAssistantText)
            }
            conversationUiState.errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
            if (conversationUiState.isPermissionRequired) {
                Text("Microphone permission is required.")
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenFeedback) {
                    Text("Feedback")
                }
                TextButton(onClick = onOpenLogs) {
                    Text("Logs")
                }
                TextButton(onClick = onOpenSettings) {
                    Text("Settings")
                }
                TextButton(onClick = onCloseApp) {
                    Text("Exit")
                }
            }
        }
    }
}

@Composable
private fun VoiceWave(state: ConversationState) {
    val active = state == ConversationState.Listening ||
        state == ConversationState.Speaking ||
        state == ConversationState.WaitingAI
    val transition = rememberInfiniteTransition(label = "voice-wave")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (active) 1f else 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 720 else 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val color = when (state) {
        ConversationState.Paused -> Color(0xFFFFD166)
        ConversationState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val centerY = size.height / 2f
        val width = size.width
        val bars = 13
        val gap = width / (bars + 4)
        for (index in 0 until bars) {
            val distance = kotlin.math.abs(index - bars / 2f)
            val base = (1f - distance / bars).coerceAtLeast(0.25f)
            val height = size.height * (0.18f + base * 0.46f * pulse)
            val x = gap * (index + 2)
            drawLine(
                color = color.copy(alpha = 0.42f + base * 0.45f),
                start = androidx.compose.ui.geometry.Offset(x, centerY - height / 2f),
                end = androidx.compose.ui.geometry.Offset(x, centerY + height / 2f),
                strokeWidth = 10.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun TranscriptLine(label: String, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF101923).copy(alpha = 0.92f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                label,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun statusLabel(state: ConversationState): String {
    return when (state) {
        ConversationState.Idle -> "Assistant Ready"
        ConversationState.Listening -> "Listening..."
        ConversationState.AwaitingUser -> "Ready"
        ConversationState.WaitingAI -> "Thinking..."
        ConversationState.Speaking -> "Speaking..."
        ConversationState.Paused -> "Paused"
        ConversationState.Interrupted -> "Interrupted"
        ConversationState.Error -> "Something went wrong"
    }
}

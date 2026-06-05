package com.englishcar.voicecoach.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.englishcar.voicecoach.ui.screens.HomeScreen
import com.englishcar.voicecoach.ui.screens.FeedbackScreen
import com.englishcar.voicecoach.ui.screens.DiagnosticsLogScreen
import com.englishcar.voicecoach.ui.screens.SetupScreen
import com.englishcar.voicecoach.ui.screens.SettingsScreen
import com.englishcar.voicecoach.conversation.ConversationEvent

private object Routes {
    const val Setup = "setup"
    const val Home = "home"
    const val Settings = "settings"
    const val Feedback = "feedback"
    const val Logs = "logs"
}

@Composable
fun EnglishCarApp(
    onCloseApp: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val settingsLoaded by viewModel.settingsLoaded.collectAsState()
    val conversationState by viewModel.conversationState.collectAsState()
    val diagnosticEvents by viewModel.diagnosticEvents.collectAsState()
    val navController = rememberNavController()
    if (!settingsLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val startDestination = if (settings.isFirstLaunchComplete) Routes.Home else Routes.Setup

    LaunchedEffect(Unit) {
        viewModel.conversationEvents.collect { event ->
            when (event) {
                ConversationEvent.CloseApp -> onCloseApp()
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.Setup) {
            SetupScreen(
                onContinue = { userName, assistantId ->
                    viewModel.completeFirstLaunch(userName, assistantId)
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Setup) { inclusive = true }
                    }
                },
                onPreviewAssistant = viewModel::previewAssistant
            )
        }
        composable(Routes.Home) {
            HomeScreen(
                assistantId = settings.activeAssistantId,
                assistantName = settings.assistantNames[settings.activeAssistantId] ?: settings.activeAssistantId,
                conversationUiState = conversationState,
                onStartConversation = { granted -> viewModel.startConversation(granted, source = "home_start") },
                onFinishConversation = viewModel::finishConversation,
                onPauseConversation = viewModel::pauseConversation,
                onResumeConversation = { granted -> viewModel.resumeConversation(granted, source = "home_resume") },
                onRetryConversation = { granted -> viewModel.retryConversation(granted, source = "home_retry") },
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onOpenFeedback = { navController.navigate(Routes.Feedback) },
                onOpenLogs = { navController.navigate(Routes.Logs) },
                onCloseApp = viewModel::closeApp
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                settings = settings,
                onSaveSettings = viewModel::saveSettings,
                onPreviewAssistant = viewModel::previewAssistant,
                onPreviewVoice = viewModel::previewGeminiVoice,
                onStopPreview = viewModel::stopPreview,
                onBack = {
                    viewModel.stopPreview()
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.Feedback) {
            val feedbackViewModel: FeedbackViewModel = hiltViewModel()
            val entries by feedbackViewModel.entries.collectAsState()
            FeedbackScreen(
                entries = entries,
                onClear = feedbackViewModel::clear,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.Logs) {
            DiagnosticsLogScreen(
                events = diagnosticEvents,
                onClear = viewModel::clearDiagnostics,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

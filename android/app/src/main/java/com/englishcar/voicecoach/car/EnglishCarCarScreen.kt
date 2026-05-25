package com.englishcar.voicecoach.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.englishcar.voicecoach.conversation.ConversationManager
import com.englishcar.voicecoach.conversation.ConversationState
import com.englishcar.voicecoach.service.VoiceSessionController
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class EnglishCarCarScreen(
    carContext: CarContext,
    private val conversationManager: ConversationManager,
    private val voiceSessionController: VoiceSessionController
) : Screen(carContext), DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        stateJob = scope.launch {
            conversationManager.uiState.drop(1).collect {
                invalidate()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        stateJob?.cancel()
        stateJob = null
    }

    override fun onGetTemplate(): Template {
        val state = conversationManager.uiState.value
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(statusTitle(state.state))
                    .addText(statusDetail(state.state))
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("English Car")
            .setActionStrip(buildActions(state.state))
            .build()
    }

    private fun buildActions(state: ConversationState): ActionStrip {
        val builder = ActionStrip.Builder()
        when (state) {
            ConversationState.Idle,
            ConversationState.Error -> builder.addAction(carAction("Start") {
                voiceSessionController.start()
                conversationManager.start(hasRecordAudioPermission = true)
                invalidate()
            })
            ConversationState.Paused -> {
                builder.addAction(carAction("Resume") {
                    conversationManager.resume(hasRecordAudioPermission = true)
                    invalidate()
                })
                builder.addAction(carAction("Finish") {
                    conversationManager.finish()
                    voiceSessionController.stop()
                    invalidate()
                })
            }
            else -> {
                builder.addAction(carAction("Pause") {
                    conversationManager.pause(spoken = true)
                    invalidate()
                })
                builder.addAction(carAction("Finish") {
                    conversationManager.finish()
                    voiceSessionController.stop()
                    invalidate()
                })
            }
        }
        return builder.build()
    }

    private fun carAction(title: String, onClick: () -> Unit): Action {
        return Action.Builder()
            .setTitle(title)
            .setBackgroundColor(CarColor.BLUE)
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, android.R.drawable.ic_btn_speak_now)
                ).build()
            )
            .setOnClickListener(onClick)
            .build()
    }

    private fun statusTitle(state: ConversationState): CarText {
        return CarText.create(
            when (state) {
                ConversationState.Idle -> "Ready"
                ConversationState.Listening -> "Listening"
                ConversationState.WaitingAI -> "Thinking"
                ConversationState.Speaking -> "Speaking"
                ConversationState.Paused -> "Paused"
                ConversationState.Interrupted -> "Interrupted"
                ConversationState.Error -> "Something went wrong"
            }
        )
    }

    private fun statusDetail(state: ConversationState): CarText {
        return CarText.create(
            when (state) {
                ConversationState.Paused -> "Conversation is paused."
                ConversationState.Error -> "Use Start to retry."
                else -> "Practice American English hands-free."
            }
        )
    }
}

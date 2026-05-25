package com.englishcar.voicecoach

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.englishcar.voicecoach.conversation.ConversationManager
import com.englishcar.voicecoach.navigation.EnglishCarApp
import com.englishcar.voicecoach.ui.theme.EnglishCarTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var conversationManager: ConversationManager
    private var noisyAudioReceiverRegistered = false

    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                conversationManager.pauseForInterruption("Audio changed. We can continue whenever you're ready.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnglishCarTheme {
                EnglishCarApp(
                    onCloseApp = { finishAndRemoveTask() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            noisyAudioReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyAudioReceiverRegistered = true
    }

    override fun onStop() {
        if (noisyAudioReceiverRegistered) {
            unregisterReceiver(noisyAudioReceiver)
            noisyAudioReceiverRegistered = false
        }
        super.onStop()
    }
}

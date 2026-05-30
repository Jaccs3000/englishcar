package com.englishcar.voicecoach.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ServiceCompat
import com.englishcar.voicecoach.MainActivity
import com.englishcar.voicecoach.conversation.ConversationState
import com.englishcar.voicecoach.conversation.ConversationManager
import com.englishcar.voicecoach.diagnostics.DiagnosticsLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VoiceSessionService : Service() {
    @Inject lateinit var conversationManager: ConversationManager
    @Inject lateinit var diagnosticsLogger: DiagnosticsLogger
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var noisyAudioReceiverRegistered = false

    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                conversationManager.pauseForInterruption("Audio changed. We can continue whenever you're ready.")
                startForegroundSession()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        diagnosticsLogger.add("Service", "onCreate")
        ensureNotificationChannel()
        startForegroundSession()
        registerNoisyAudioReceiver()
        scope.launch {
            conversationManager.uiState.collectLatest { state ->
                if (state.state != ConversationState.Idle) {
                    updateNotification(state.state)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action ?: "start"} state=${conversationManager.uiState.value.state}")
        diagnosticsLogger.add("Service", "onStartCommand action=${intent?.action ?: "start"} state=${conversationManager.uiState.value.state}")
        when (intent?.action) {
            ACTION_PAUSE -> {
                conversationManager.pause(spoken = true)
                startForegroundSession()
            }
            ACTION_RESUME -> {
                conversationManager.resume(hasRecordAudioPermission = true)
                startForegroundSession()
            }
            ACTION_STOP -> {
                conversationManager.finish()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startForegroundSession()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        diagnosticsLogger.add("Service", "onDestroy")
        scope.cancel()
        unregisterNoisyAudioReceiver()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSession() {
        Log.d(TAG, "startForegroundSession state=${conversationManager.uiState.value.state}")
        diagnosticsLogger.add("Service", "startForeground state=${conversationManager.uiState.value.state}")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(conversationManager.uiState.value.state),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
        )
    }

    private fun updateNotification(state: ConversationState) {
        Log.v(TAG, "updateNotification state=$state")
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: ConversationState): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(notificationTitle(state))
            .setContentText(notificationText(state))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when (state) {
            ConversationState.Paused -> builder.addAction(0, "Resume", serviceIntent(ACTION_RESUME, 2))
            ConversationState.Idle,
            ConversationState.Error -> Unit
            else -> builder.addAction(0, "Pause", serviceIntent(ACTION_PAUSE, 3))
        }

        builder.addAction(0, "Finish", serviceIntent(ACTION_STOP, 1))
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, VoiceSessionService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active conversation",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun registerNoisyAudioReceiver() {
        if (noisyAudioReceiverRegistered) return
        Log.d(TAG, "registerNoisyAudioReceiver")
        ContextCompat.registerReceiver(
            this,
            noisyAudioReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyAudioReceiverRegistered = true
    }

    private fun unregisterNoisyAudioReceiver() {
        if (!noisyAudioReceiverRegistered) return
        Log.d(TAG, "unregisterNoisyAudioReceiver")
        unregisterReceiver(noisyAudioReceiver)
        noisyAudioReceiverRegistered = false
    }

    private fun notificationTitle(state: ConversationState): String {
        return when (state) {
            ConversationState.Listening -> "English Car is listening"
            ConversationState.AwaitingUser -> "English Car is ready"
            ConversationState.WaitingAI -> "English Car is thinking"
            ConversationState.Speaking -> "English Car is speaking"
            ConversationState.Paused -> "English Car is paused"
            ConversationState.Interrupted -> "English Car was interrupted"
            ConversationState.Error -> "English Car needs attention"
            else -> "English Car is active"
        }
    }

    private fun notificationText(state: ConversationState): String {
        return when (state) {
            ConversationState.Paused -> "Tap Resume or return to the app."
            ConversationState.AwaitingUser -> "Tap Resume when you want to speak."
            ConversationState.Interrupted -> "Listening for your next phrase."
            ConversationState.Error -> "Open the app to retry."
            else -> "Conversation stays active while the phone is locked."
        }
    }

    private companion object {
        const val TAG = "EnglishCarService"
        const val CHANNEL_ID = "active_conversation"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "com.englishcar.voicecoach.action.PAUSE_SESSION"
        const val ACTION_RESUME = "com.englishcar.voicecoach.action.RESUME_SESSION"
        const val ACTION_STOP = "com.englishcar.voicecoach.action.STOP_SESSION"
    }
}

package com.englishcar.voicecoach.car

import android.content.Context
import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import dagger.hilt.android.EntryPointAccessors

class EnglishCarCarSession(
    private val appContext: Context
) : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val entryPoint = EntryPointAccessors.fromApplication(appContext, CarAppEntryPoint::class.java)
        return EnglishCarCarScreen(
            carContext = carContext,
            conversationManager = entryPoint.conversationManager(),
            voiceSessionController = entryPoint.voiceSessionController()
        )
    }
}

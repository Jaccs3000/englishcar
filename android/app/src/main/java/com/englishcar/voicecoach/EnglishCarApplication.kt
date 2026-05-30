package com.englishcar.voicecoach

import android.app.Application
import java.io.PrintWriter
import java.io.StringWriter
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EnglishCarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stackTrace = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }.toString()
                getSharedPreferences("english_car_diagnostics", MODE_PRIVATE)
                    .edit()
                    .putString(
                        "last_crash",
                        "thread=${thread.name} type=${throwable.javaClass.simpleName} message=${throwable.message.orEmpty()} stack=${stackTrace.take(3500)}"
                    )
                    .apply()
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}

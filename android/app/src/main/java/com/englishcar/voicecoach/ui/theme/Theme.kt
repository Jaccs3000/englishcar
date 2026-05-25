package com.englishcar.voicecoach.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = darkColorScheme(
    primary = Color(0xFF66E3FF),
    secondary = Color(0xFFB7F36B),
    background = Color(0xFF080B10),
    surface = Color(0xFF111722),
    onPrimary = Color(0xFF001F2A),
    onSecondary = Color(0xFF142000),
    onBackground = Color(0xFFEAF4F8),
    onSurface = Color(0xFFEAF4F8)
)

@Composable
fun EnglishCarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

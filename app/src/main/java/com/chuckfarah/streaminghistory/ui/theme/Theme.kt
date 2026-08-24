package com.chuckfarah.streaminghistory.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary          = Color(0xFF1565C0),
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    surface          = Color.White,
    background       = Color(0xFFF5F5F5),
)

private val DarkColors = darkColorScheme(
    primary          = Color(0xFF90CAF9),
    onPrimary        = Color(0xFF003064),
    primaryContainer = Color(0xFF004494),
    surface          = Color(0xFF1C1B1F),
    background       = Color(0xFF121212),
)

@Composable
fun StreamingHistoryTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content     = content,
    )
}

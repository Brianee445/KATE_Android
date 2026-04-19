package com.kate.assistant.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KateDarkColors = darkColorScheme(
    primary        = Color(0xFF00E5FF),
    onPrimary      = Color(0xFF001F26),
    background     = Color(0xFF060B10),
    surface        = Color(0xFF0D1B2A),
    onBackground   = Color(0xFFE0F7FA),
    onSurface      = Color(0xFFB2EBF2),
)

@Composable
fun KateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KateDarkColors, content = content)
}

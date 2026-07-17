package com.dti.kate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Purple70,
    onPrimary = TextPrimary,
    primaryContainer = Purple90,
    onPrimaryContainer = TextPrimary,
    secondary = LimeAccent,
    onSecondary = Background,
    tertiary = Purple30,
    onTertiary = Background,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = Error,
    onError = TextPrimary,
)

@Composable
fun KateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = KateTypography,
        shapes = KateShapes,
        content = content,
    )
}

package com.dti.kate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration

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
    divider = Divider,
)

@Composable
fun KateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KateTypography,
        shapes = KateShapes,
        content = content,
    )
}

// Theme Extension Properties
object KateTheme {
    val colors: androidx.compose.material3.ColorScheme
        @Composable get() = MaterialTheme.colorScheme
    
    val typography: androidx.compose.material3.Typography
        @Composable get() = MaterialTheme.typography
    
    val shapes: androidx.compose.material3.Shapes
        @Composable get() = MaterialTheme.shapes
}

// Screen density helpers
@Composable
fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp

@Composable
fun screenHeightDp(): Int = LocalConfiguration.current.screenHeightDp

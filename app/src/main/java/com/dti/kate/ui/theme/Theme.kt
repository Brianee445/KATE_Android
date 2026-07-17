package com.dti.kate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// ==================== Colors (already defined in Color.kt) ====================
// Ensure Color.kt exists with these definitions:
// Purple70, Purple90, Purple30, LimeAccent, Background, Surface, SurfaceVariant,
// TextPrimary, TextSecondary, TextTertiary, Error, etc.

// For safety, define them here if missing, but they should be in Color.kt.

// ==================== Shapes ====================
val KateShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// Shape constants (used in components)
object KateShape {
    val None = RoundedCornerShape(0.dp)
    val XS = RoundedCornerShape(4.dp)
    val SM = RoundedCornerShape(8.dp)
    val MD = RoundedCornerShape(12.dp)
    val LG = RoundedCornerShape(16.dp)
    val XL = RoundedCornerShape(24.dp)
    val XXL = RoundedCornerShape(32.dp)
    val Circle = RoundedCornerShape(50)
    val Pill = RoundedCornerShape(100)
}

// ==================== Color Scheme ====================
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

// ==================== Theme Composable ====================
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

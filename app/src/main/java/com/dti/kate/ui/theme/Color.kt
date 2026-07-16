package com.dti.kate.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Brand Colors
val Purple70 = Color(0xFF7C3AED)      // Primary
val Purple90 = Color(0xFF4C1D95)      // Primary Deep
val Purple30 = Color(0xFFA78BFA)      // Primary Light
val Purple10 = Color(0xFFEDE7F6)      // Primary Surface

// Accent
val LimeAccent = Color(0xFFD4FF4F)    // Accent

// Neutrals (Dark Theme)
val Background = Color(0xFF0F0D14)    // Very Dark
val Surface = Color(0xFF1B1720)       // Dark Surface
val SurfaceVariant = Color(0xFF252030) // Slightly lighter surface
val Divider = Color(0xFF322B3D)       // Muted Purple

// Text Colors
val TextPrimary = Color(0xFFF5F3F7)   // White
val TextSecondary = Color(0xFFA79AB8) // Muted Purple
val TextTertiary = Color(0xFF6D5D7E)  // Even more muted

// Semantic Colors
val Success = Color(0xFF4CAF50)
val Warning = Color(0xFFFFC107)
val Error = Color(0xFFFF5470)
val Info = Color(0xFF2196F3)

// Overlay Colors
val OverlayBackground = Color(0xCC0F0D14) // Semi-transparent
val OverlayScrim = Color(0x80000000)

// Gradient Tokens
val GradientBrand = listOf(Purple70, Purple90)
val GradientAccent = listOf(LimeAccent, Color(0xFFB8FF00))

// Kate Specific Colors
val KateListening = LimeAccent
val KateThinking = Purple70
val KateSpeaking = Color(0xFFFF6B9D)
val KateIdle = TextSecondary

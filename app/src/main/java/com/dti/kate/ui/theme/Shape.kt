package com.dti.kate.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val KateShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// Shape Constants
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
    val KateAvatar = RoundedCornerShape(28.dp) // Custom
}

// Elevation
object KateElevation {
    val None = 0.dp
    val XS = 2.dp
    val SM = 4.dp
    val MD = 8.dp
    val LG = 16.dp
    val XL = 24.dp
    val KateGlow = 32.dp // For avatar glow effect
}

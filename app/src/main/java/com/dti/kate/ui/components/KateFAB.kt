package com.dti.kate.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Close
import com.dti.kate.ui.theme.
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FloatingActionButtonDefaults

enum class KateFABState {
    Idle, Listening, Processing, Speaking
}

@Composable
fun KateFAB(
    state: KateFABState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 72.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_animation")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    
    val (backgroundColor, icon, iconColor) = when (state) {
        KateFABState.Idle -> Triple(
            Purple70,
            Icons.Rounded.Mic,
            TextPrimary,
        )
        KateFABState.Listening -> Triple(
            KateListening,
            Icons.Rounded.Mic,
            Background,
        )
        KateFABState.Processing -> Triple(
            KateThinking,
            Icons.Rounded.Mic,
            TextPrimary,
        )
        KateFABState.Speaking -> Triple(
            KateSpeaking,
            Icons.Rounded.Mic,
            TextPrimary,
        )
    }
    
    // Glow effect (outer shadow)
    Box(
        modifier = modifier
            .size(size * 1.4f)
            .then(
                if (state != KateFABState.Idle) {
                    Modifier
                } else {
                    Modifier
                }
            )
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .then(
                    if (state != KateFABState.Idle) {
                        Modifier
                    } else {
                        Modifier
                    }
                ),
            containerColor = backgroundColor,
            shape = KateShape.Circle,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = if (state != KateFABState.Idle) 16.dp else 8.dp,
                pressedElevation = 24.dp,
            ),
        ) {
            Icon(
                imageVector = if (state == KateFABState.Idle) {
                    Icons.Rounded.Mic
                } else if (state == KateFABState.Processing) {
                    Icons.Rounded.Close
                } else {
                    Icons.Rounded.Mic
                },
                contentDescription = when (state) {
                    KateFABState.Idle -> "Wake Kate"
                    KateFABState.Listening -> "Listening"
                    KateFABState.Processing -> "Processing"
                    KateFABState.Speaking -> "Speaking"
                },
                tint = iconColor,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

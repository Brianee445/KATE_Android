package com.dti.kate.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.dti.kate.ui.theme.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
@Composable
fun KateCard(
    modifier: Modifier = Modifier,
    shape: Shape = KateShape.MD,
    elevation: androidx.compose.ui.unit.Dp = KateElevation.SM,
    backgroundColor: androidx.compose.ui.graphics.Color = Surface,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

// Kate Card with glass effect (for premium feel)
@Composable
fun KateGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = KateShape.LG,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Surface.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .then(modifier),
        ) {
            content()
        }
    }
}

// Kate Conversation Bubble
@Composable
fun KateConversationBubble(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = KateShape.LG,
        colors = CardDefaults.cardColors(
            containerColor = if (isUser) Purple70 else SurfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser) TextPrimary else TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

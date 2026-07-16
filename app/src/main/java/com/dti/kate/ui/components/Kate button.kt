package com.dti.kate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.dti.kate.ui.theme.*

// Button Sizes
enum class KateButtonSize {
    Small, Medium, Large
}

// Button Types
enum class KateButtonType {
    Primary, Secondary, Outline, Ghost, Accent
}

@Composable
fun KateButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: KateButtonType = KateButtonType.Primary,
    size: KateButtonSize = KateButtonSize.Medium,
    shape: Shape = KateShape.MD,
    isLoading: Boolean = false,
    isEnabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = KateButtonDefaults.contentPadding(size),
) {
    val colors = when (type) {
        KateButtonType.Primary -> KateButtonDefaults.primaryColors()
        KateButtonType.Secondary -> KateButtonDefaults.secondaryColors()
        KateButtonType.Outline -> KateButtonDefaults.outlineColors()
        KateButtonType.Ghost -> KateButtonDefaults.ghostColors()
        KateButtonType.Accent -> KateButtonDefaults.accentColors()
    }
    
    Button(
        onClick = onClick,
        modifier = modifier
            .height(KateButtonDefaults.height(size))
            .then(modifier),
        enabled = isEnabled && !isLoading,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        elevation = KateButtonDefaults.elevation(type),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(KateButtonDefaults.loaderSize(size)),
                color = colors.contentColor?.value ?: TextPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            icon?.let {
                it()
                Spacer(Modifier.padding(8.dp))
            }
            Text(text, style = KateButtonDefaults.textStyle(size))
        }
    }
}

// Kate Gradient Button (brand style)
@Composable
fun KateGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: List<Color> = GradientBrand,
    size: KateButtonSize = KateButtonSize.Medium,
    shape: Shape = KateShape.MD,
    isLoading: Boolean = false,
    isEnabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(KateButtonDefaults.height(size))
            .then(modifier),
        enabled = isEnabled && !isLoading,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = TextPrimary,
            disabledContentColor = TextSecondary,
        ),
        contentPadding = KateButtonDefaults.contentPadding(size),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp,
        ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.horizontalGradient(gradient),
                    shape = shape,
                )
        )
        
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(KateButtonDefaults.loaderSize(size)),
                color = TextPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text, style = KateButtonDefaults.textStyle(size))
        }
    }
}

// Kate Text Button (Ghost)
@Composable
fun KateTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Purple70,
    isEnabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = isEnabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = color,
            disabledContentColor = TextSecondary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// Button Defaults
object KateButtonDefaults {
    @Composable
    fun primaryColors() = ButtonDefaults.buttonColors(
        containerColor = Purple70,
        contentColor = TextPrimary,
        disabledContainerColor = Purple30.copy(alpha = 0.5f),
        disabledContentColor = TextSecondary,
    )
    
    @Composable
    fun secondaryColors() = ButtonDefaults.buttonColors(
        containerColor = SurfaceVariant,
        contentColor = TextPrimary,
        disabledContainerColor = SurfaceVariant.copy(alpha = 0.5f),
        disabledContentColor = TextSecondary,
    )
    
    @Composable
    fun outlineColors() = ButtonDefaults.outlinedButtonColors(
        containerColor = Color.Transparent,
        contentColor = Purple70,
        disabledContentColor = TextSecondary,
    )
    
    @Composable
    fun ghostColors() = ButtonDefaults.textButtonColors(
        contentColor = Purple70,
        disabledContentColor = TextSecondary,
    )
    
    @Composable
    fun accentColors() = ButtonDefaults.buttonColors(
        containerColor = LimeAccent,
        contentColor = Background,
        disabledContainerColor = LimeAccent.copy(alpha = 0.5f),
        disabledContentColor = TextSecondary,
    )
    
    fun height(size: KateButtonSize) = when (size) {
        KateButtonSize.Small -> 36.dp
        KateButtonSize.Medium -> 48.dp
        KateButtonSize.Large -> 56.dp
    }
    
    fun contentPadding(size: KateButtonSize) = PaddingValues(
        horizontal = when (size) {
            KateButtonSize.Small -> 16.dp
            KateButtonSize.Medium -> 24.dp
            KateButtonSize.Large -> 32.dp
        },
        vertical = when (size) {
            KateButtonSize.Small -> 4.dp
            KateButtonSize.Medium -> 8.dp
            KateButtonSize.Large -> 12.dp
        },
    )
    
    fun textStyle(size: KateButtonSize) = when (size) {
        KateButtonSize.Small -> MaterialTheme.typography.labelMedium
        KateButtonSize.Medium -> MaterialTheme.typography.labelLarge
        KateButtonSize.Large -> MaterialTheme.typography.titleMedium
    }
    
    fun loaderSize(size: KateButtonSize) = when (size) {
        KateButtonSize.Small -> 16.dp
        KateButtonSize.Medium -> 24.dp
        KateButtonSize.Large -> 28.dp
    }
    
    @Composable
    fun elevation(type: KateButtonType) = when (type) {
        KateButtonType.Ghost -> ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        )
        else -> ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp,
        )
    }
}

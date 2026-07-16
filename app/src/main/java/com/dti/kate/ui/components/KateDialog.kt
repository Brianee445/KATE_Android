package com.dti.kate.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dti.kate.ui.theme.*

enum class KateDialogType {
    Info, Warning, Error, Success, Confirmation
}

@Composable
fun KateDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    type: KateDialogType = KateDialogType.Info,
    confirmText: String = "OK",
    dismissText: String = "Cancel",
    icon: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
        },
        text = {
            Column {
                icon?.let {
                    it()
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        },
        confirmButton = {
            KateButton(
                text = confirmText,
                onClick = onConfirm,
                type = when (type) {
                    KateDialogType.Error -> KateButtonType.Primary
                    else -> KateButtonType.Accent
                },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        },
        dismissButton = {
            KateTextButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        },
        shape = KateShape.LG,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
    )
}

// Kate Error Dialog (Backend Error Display)
@Composable
fun KateErrorDialog(
    error: BackendError,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    KateDialog(
        title = error.title,
        message = error.message,
        onDismiss = onDismiss,
        onConfirm = onRetry ?: onDismiss,
        type = KateDialogType.Error,
        confirmText = if (onRetry != null) "Retry" else "Dismiss",
        icon = {
            Text(
                text = error.icon,
                style = MaterialTheme.typography.displayMedium,
            )
        },
    )
}

// Backend Error Model
data class BackendError(
    val code: String,
    val title: String,
    val message: String,
    val icon: String = "⚠️",
    val shouldRetry: Boolean = false,
) {
    companion object {
        fun fromErrorCode(code: String, message: String): BackendError {
            return when (code) {
                "NETWORK_ERROR" -> BackendError(
                    code = code,
                    title = "Network Error",
                    message = "Please check your internet connection and try again.",
                    icon = "📡",
                    shouldRetry = true,
                )
                "SERVER_ERROR" -> BackendError(
                    code = code,
                    title = "Server Error",
                    message = "Something went wrong on our end. Please try again later.",
                    icon = "🔧",
                    shouldRetry = true,
                )
                "AUTH_ERROR" -> BackendError(
                    code = code,
                    title = "Authentication Error",
                    message = "Please log in again to continue.",
                    icon = "🔐",
                    shouldRetry = false,
                )
                "RATE_LIMIT" -> BackendError(
                    code = code,
                    title = "Rate Limited",
                    message = "Too many requests. Please wait a moment.",
                    icon = "⏳",
                    shouldRetry = true,
                )
                "TIMEOUT" -> BackendError(
                    code = code,
                    title = "Request Timeout",
                    message = "The request took too long. Please try again.",
                    icon = "⏰",
                    shouldRetry = true,
                )
                "PAYMENT_FAILED" -> BackendError(
                    code = code,
                    title = "Payment Failed",
                    message = "Your payment could not be processed. Please try again.",
                    icon = "💳",
                    shouldRetry = true,
                )
                "SUBSCRIPTION_EXPIRED" -> BackendError(
                    code = code,
                    title = "Subscription Expired",
                    message = "Your subscription has expired. Please renew to continue.",
                    icon = "💎",
                    shouldRetry = false,
                )
                "MICROPHONE_BUSY" -> BackendError(
                    code = code,
                    title = "Microphone Busy",
                    message = "Another app is using the microphone. Please try again.",
                    icon = "🎤",
                    shouldRetry = true,
                )
                "SPEECH_RECOGNITION_FAILED" -> BackendError(
                    code = code,
                    title = "Speech Recognition Failed",
                    message = "I couldn't understand that. Please try again.",
                    icon = "🗣️",
                    shouldRetry = true,
                )
                "MODEL_LOADING_FAILED" -> BackendError(
                    code = code,
                    title = "Model Loading Failed",
                    message = "The AI model couldn't load. Please restart the app.",
                    icon = "🧠",
                    shouldRetry = false,
                )
                else -> BackendError(
                    code = code,
                    title = "Something went wrong",
                    message = message.ifEmpty { "An unexpected error occurred." },
                    icon = "❌",
                    shouldRetry = true,
                )
            }
        }
    }
}

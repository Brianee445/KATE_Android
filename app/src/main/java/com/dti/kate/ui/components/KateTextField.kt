package com.dti.kate.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dti.kate.ui.theme.*

@Composable
fun KateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    isPassword: Boolean = false,
    isEnabled: Boolean = true,
    readOnly: Boolean = false,
    shape: Shape = KateShape.MD,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = TextSecondary) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        enabled = isEnabled,
        readOnly = readOnly,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple70,
            unfocusedBorderColor = Divider,
            errorBorderColor = Error,
            focusedLabelColor = Purple70,
            unfocusedLabelColor = TextSecondary,
            errorLabelColor = Error,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            errorTextColor = Error,
            focusedSupportingTextColor = TextSecondary,
            unfocusedSupportingTextColor = TextSecondary,
            errorSupportingTextColor = Error,
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
            errorContainerColor = Surface,
            cursorColor = Purple70,
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge,
        supportingText = errorMessage?.let { { Text(it) } },
    )
}

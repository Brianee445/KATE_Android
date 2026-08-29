package com.dti.kate.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.R
import com.dti.kate.service.KateForegroundService
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight

@Composable
fun RegisterScreen(
    navController: NavController,
    viewModel: AuthViewModel = AuthViewModel(LocalContext.current),
) {
    val context = LocalContext.current

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.dti.kate.ui.components.KateAvatar(
            size = 64.dp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Text(
            text = "Start your journey with Kate",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        KateTextField(
            value = fullName,
            onValueChange = { fullName = it; errorMessage = null },
            label = "Full Name",
            placeholder = "John Doe",
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null,
        )
        Spacer(modifier = Modifier.height(16.dp))

        KateTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = "Email Address",
            placeholder = "you@example.com",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = errorMessage != null,
        )
        Spacer(modifier = Modifier.height(16.dp))

        KateTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = "Password",
            placeholder = "Min 6 characters",
            modifier = Modifier.fillMaxWidth(),
            isPassword = !passwordVisible,
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = TextSecondary,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = errorMessage != null,
        )
        Spacer(modifier = Modifier.height(16.dp))

        KateTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; errorMessage = null },
            label = "Confirm Password",
            placeholder = "Confirm your password",
            modifier = Modifier.fillMaxWidth(),
            isPassword = !passwordVisible,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = errorMessage != null,
        )
        Spacer(modifier = Modifier.height(24.dp))

        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        KateGradientButton(
            text = "Create Account",
            onClick = {
                if (validateInput(fullName, email, password, confirmPassword)) {
                    isLoading = true
                    viewModel.register(email, password, fullName) { success, error ->
                        isLoading = false
                        if (success) {
                            val hasMicPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasMicPermission) {
                                val serviceIntent = Intent(context, KateForegroundService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                            }

                            navController.navigate("user_agreement") {
                                popUpTo("register") { inclusive = true }
                            }
                        } else {
                            errorMessage = error ?: "Registration failed"
                        }
                    }
                } else {
                    errorMessage = when {
                        fullName.length < 2 -> "Full name must be at least 2 characters"
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Please enter a valid email address"
                        password.length < 6 -> "Password must be at least 6 characters"
                        password != confirmPassword -> "Passwords do not match"
                        else -> "Please check your details and try again"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            isLoading = isLoading,
            isEnabled = !isLoading,
            size = KateButtonSize.Large,
        )

        if (isLoading) {
            Text(
                text = "Connecting to Kate's servers...",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Already have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            KateTextButton(
                text = "Sign In",
                onClick = { navController.navigate("login") },
                color = Purple70,
            )
        }
    }
}

private fun validateInput(
    fullName: String,
    email: String,
    password: String,
    confirmPassword: String,
): Boolean {
    if (fullName.length < 2) return false
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return false
    if (password.length < 6) return false
    if (password != confirmPassword) return false
    return true
}

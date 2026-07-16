package com.dti.kate.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: AuthViewModel = viewModel(),
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
        // Logo
        Image(
            painter = painterResource(R.drawable.kate_logo_medium),
            contentDescription = "Kate Assistant",
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
        )
        
        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        
        Text(
            text = "Sign in to continue using Kate",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp),
        )
        
        // Email Field
        KateTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = "Email Address",
            placeholder = "you@example.com",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = errorMessage != null,
            errorMessage = errorMessage,
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Password Field
        KateTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = "Password",
            placeholder = "Enter your password",
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
            errorMessage = errorMessage,
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Forgot Password
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            KateTextButton(
                text = "Forgot Password?",
                onClick = { navController.navigate("forgot_password") },
                color = Purple30,
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Login Button
        KateGradientButton(
            text = "Sign In",
            onClick = {
                if (validateInput(email, password)) {
                    isLoading = true
                    viewModel.login(email, password) { success, error ->
                        isLoading = false
                        if (success) {
                            navController.navigate("home") {
                                popUpTo("login") { inclusive = true }
                            }
                        } else {
                            errorMessage = error ?: "Invalid email or password"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            isLoading = isLoading,
            isEnabled = !isLoading,
            size = KateButtonSize.Large,
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Divider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Divider)
            )
            Text(
                text = "OR",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Divider)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Google Sign In
        KateButton(
            text = "Continue with Google",
            onClick = { viewModel.loginWithGoogle() },
            modifier = Modifier.fillMaxWidth(),
            type = KateButtonType.Secondary,
            size = KateButtonSize.Large,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_google),
                    contentDescription = "Google",
                    modifier = Modifier.size(20.dp),
                )
            },
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Register link
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Don't have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            KateTextButton(
                text = "Sign Up",
                onClick = { navController.navigate("register") },
                color = Purple70,
            )
        }
    }
}

private fun validateInput(email: String, password: String): Boolean {
    return email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
           password.isNotEmpty() && password.length >= 6
}

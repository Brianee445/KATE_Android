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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.R
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*

@Composable
fun ForgotPasswordScreen(
    navController: NavController,
    viewModel: AuthViewModel = viewModel(),
) {
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Icon
        Image(
            painter = painterResource(R.drawable.kate_lock_icon),
            contentDescription = "Reset Password",
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 24.dp),
        )
        
        Text(
            text = "Reset Password",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        
        Text(
            text = "Enter your email address and we'll send you a reset link",
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
            isEnabled = !isSuccess,
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (isSuccess) {
            // Success message
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Success.copy(alpha = 0.1f),
                ),
                shape = KateShape.MD,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "Success",
                        tint = Success,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Password reset link sent to your email",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Success,
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            KateButton(
                text = "Back to Login",
                onClick = { navController.navigate("login") },
                modifier = Modifier.fillMaxWidth(),
                type = KateButtonType.Primary,
                size = KateButtonSize.Large,
            )
        } else {
            // Reset Button
            KateGradientButton(
                text = "Send Reset Link",
                onClick = {
                    if (validateInput(email)) {
                        isLoading = true
                        viewModel.resetPassword(email) { success, error ->
                            isLoading = false
                            if (success) {
                                isSuccess = true
                            } else {
                                errorMessage = error ?: "Failed to send reset link"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                isLoading = isLoading,
                isEnabled = !isLoading,
                size = KateButtonSize.Large,
            )
        }
    }
}

private fun validateInput(email: String): Boolean {
    return email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

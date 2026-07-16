package com.dti.kate.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.R
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*

@Composable
fun PaymentResultScreen(
    navController: NavController,
    viewModel: PaymentResultViewModel = viewModel(),
) {
    val result by viewModel.result.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadResult()
    }
    
    Scaffold(
        modifier = Modifier.background(Background),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (result?.success == true) "Success!" else "Payment Failed",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                KateLoadingState(message = "Processing payment...")
            } else if (result != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Icon
                    AnimatedContent(
                        targetState = result!!.success,
                        transitionSpec = {
                            fadeIn() + scaleIn() with fadeOut() + scaleOut()
                        },
                    ) { success ->
                        Image(
                            painter = painterResource(
                                if (success) R.drawable.ic_success else R.drawable.ic_failure
                            ),
                            contentDescription = if (success) "Success" else "Failure",
                            modifier = Modifier.size(120.dp),
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Title
                    Text(
                        text = if (result!!.success) "🎉 Payment Successful!" else "❌ Payment Failed",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = if (result!!.success) Success else Error,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Message
                    Text(
                        text = if (result!!.success) {
                            "You're now a ${result!!.tier?.uppercase()} user! Enjoy all premium features."
                        } else {
                            result!!.message ?: "Something went wrong. Please try again."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Actions
                    if (result!!.success) {
                        KateGradientButton(
                            text = "Go to Home",
                            onClick = {
                                navController.navigate("home") {
                                    popUpTo("premium") { inclusive = true }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            size = KateButtonSize.Large,
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            KateButton(
                                text = "Try Again",
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.fillMaxWidth(),
                                type = KateButtonType.Primary,
                                size = KateButtonSize.Large,
                            )
                            KateButton(
                                text = "Back to Premium",
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.fillMaxWidth(),
                                type = KateButtonType.Secondary,
                                size = KateButtonSize.Large,
                            )
                        }
                    }
                }
            }
        }
    }
}

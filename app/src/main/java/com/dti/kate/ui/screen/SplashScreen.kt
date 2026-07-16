package com.dti.kate.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.R
import com.dti.kate.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: SplashViewModel = viewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Animation states
    var logoScale by remember { mutableStateOf(0.5f) }
    var logoAlpha by remember { mutableStateOf(0f) }
    var textAlpha by remember { mutableStateOf(0f) }
    var bottomTextAlpha by remember { mutableStateOf(0f) }
    var isVisible by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        // Enter animation
        logoAlpha = 1f
        logoScale = 1f
        
        delay(300)
        textAlpha = 1f
        
        delay(500)
        bottomTextAlpha = 1f
        
        delay(800)
        
        // Check authentication and navigate
        val isLoggedIn = viewModel.checkAuth()
        
        // Exit animation
        isVisible = false
        
        delay(400)
        
        if (isLoggedIn) {
            navController.navigate("home") {
                popUpTo("splash") { inclusive = true }
            }
        } else {
            navController.navigate("onboarding") {
                popUpTo("splash") { inclusive = true }
            }
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(300)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Purple90, Background)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Kate Logo/Avatar
                Image(
                    painter = painterResource(R.drawable.kate_splash_logo),
                    contentDescription = "Kate Assistant",
                    modifier = Modifier
                        .size(180.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha),
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Brand Name
                AnimatedVisibility(
                    visible = textAlpha > 0.5f,
                    enter = fadeIn() + slideInVertically(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "KATE",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                            ),
                            textAlign = TextAlign.Center,
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "AI Voice Assistant",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Purple30,
                            ),
                            textAlign = TextAlign.Center,
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Divider with accent
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(3.dp)
                                .background(LimeAccent, KateShape.Pill)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(64.dp))
                
                // Bottom text
                AnimatedVisibility(
                    visible = bottomTextAlpha > 0.5f,
                    enter = fadeIn() + slideInVertically(),
                ) {
                    Text(
                        text = "A D.T.I Company",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontFamily = Manrope,
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(bottomTextAlpha),
                    )
                }
            }
        }
    }
}

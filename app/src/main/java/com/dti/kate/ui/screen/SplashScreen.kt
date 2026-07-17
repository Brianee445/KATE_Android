// app/src/main/java/com/dti/kate/ui/screen/SplashScreen.kt
package com.dti.kate.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.R
import com.dti.kate.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Placeholder ViewModel - replace with actual implementation
class SplashViewModel {
    fun checkAuth(): Boolean = false
}

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: SplashViewModel = SplashViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    var logoScale by remember { mutableStateOf(0.5f) }
    var logoAlpha by remember { mutableStateOf(0f) }
    var textAlpha by remember { mutableStateOf(0f) }
    var bottomTextAlpha by remember { mutableStateOf(0f) }
    var isVisible by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        logoAlpha = 1f
        logoScale = 1f
        delay(300)
        textAlpha = 1f
        delay(500)
        bottomTextAlpha = 1f
        delay(800)
        
        val isLoggedIn = viewModel.checkAuth()
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
                // Use a placeholder drawable if kate_splash_logo doesn't exist
                Image(
                    painter = painterResource(R.drawable.kate_avatar_idle), // fallback
                    contentDescription = "Kate Assistant",
                    modifier = Modifier
                        .size(180.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha),
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
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
                        
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(3.dp)
                                .background(LimeAccent, KateShape.Pill)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(64.dp))
                
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

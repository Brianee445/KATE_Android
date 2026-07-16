package com.dti.kate.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.R
import com.dti.kate.ui.components.KateButton
import com.dti.kate.ui.components.KateButtonType
import com.dti.kate.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 3 },
    )
    
    val onboardingPages = listOf(
        OnboardingPage(
            title = "Meet Kate",
            description = "Your offline-first AI voice assistant. Smart. Private. Always ready.",
            icon = R.drawable.kate_onboarding_1,
        ),
        OnboardingPage(
            title = "Wake with Ease",
            description = "Raise your phone, tap the mic, or say \"Hey Kate\" to get started.",
            icon = R.drawable.kate_onboarding_2,
        ),
        OnboardingPage(
            title = "Privacy First",
            description = "Everything stays on your device. No cloud required. Your data, your rules.",
            icon = R.drawable.kate_onboarding_3,
        ),
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Skip button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            KateButton(
                text = "Skip",
                onClick = { navController.navigate("login") },
                type = KateButtonType.Ghost,
                size = KateButtonSize.Small,
            )
        }
        
        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            OnboardingPageContent(page = onboardingPages[page])
        }
        
        // Indicators + Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Indicators
            Row(
                modifier = Modifier.padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (pagerState.currentPage == index) 32.dp else 8.dp,
                                height = 8.dp,
                            )
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) Purple70
                                else Divider
                            )
                    )
                }
            }
            
            // Next / Get Started button
            KateButton(
                text = if (pagerState.currentPage == 2) "Get Started" else "Next",
                onClick = {
                    if (pagerState.currentPage == 2) {
                        navController.navigate("login")
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                type = KateButtonType.Primary,
                size = KateButtonSize.Large,
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icon/Illustration
        Image(
            painter = painterResource(page.icon),
            contentDescription = page.title,
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = 48.dp),
        )
        
        // Title
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        
        // Description
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = TextSecondary,
                lineHeight = 28.sp,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: Int,
)

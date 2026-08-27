package com.dti.kate.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.dti.kate.R
import com.dti.kate.core.LocalSettingsStore
import com.dti.kate.ui.components.KateButton
import com.dti.kate.ui.components.KateButtonSize
import com.dti.kate.ui.components.KateButtonType
import com.dti.kate.ui.components.KateTextField
import com.dti.kate.ui.theme.*
import kotlinx.coroutines.launch

// ==================== DATA CLASS ====================
data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: Int, // drawable resource ID
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val settings = remember { LocalSettingsStore(context) }
    var userName by remember { mutableStateOf("") }

    // 4 pages now: the original 3 info slides plus a name-capture step at
    // the end. Name is asked last (not first) so it doesn't front-load a
    // text-input chore before the user has any sense of what the app is -
    // by the "Privacy Matters" slide they've already committed to
    // continuing, so asking then reads as "getting acquainted" rather than
    // a form.
    val pageCount = 4
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pageCount },
    )
    val coroutineScope = rememberCoroutineScope()

    // Uses the single source avatar image until per-state art exists
    val onboardingPages = listOf(
        OnboardingPage(
            title = "Meet Kate",
            description = "Your AI voice assistant. Smart, personal, and always ready to help.",
            icon = R.drawable.kate_avatar_source,
        ),
        OnboardingPage(
            title = "Wake with Ease",
            description = "Raise your phone, tap the mic, or say \"Hey Kate\" to get started.",
            icon = R.drawable.kate_avatar_source,
        ),
        OnboardingPage(
            title = "Privacy Matters",
            description = "Your voice is only captured while actively listening for a command. " +
                "Offline mode is coming soon - stay tuned.",
            icon = R.drawable.kate_avatar_source,
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
                onClick = {
                    if (userName.isNotBlank()) settings.setUserName(userName)
                    navController.navigate("login")
                },
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
            if (page < onboardingPages.size) {
                OnboardingPageContent(page = onboardingPages[page])
            } else {
                NameCaptureContent(name = userName, onNameChange = { userName = it })
            }
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
                repeat(pageCount) { index ->
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
            val isLastPage = pagerState.currentPage == pageCount - 1
            KateButton(
                text = if (isLastPage) "Get Started" else "Next",
                onClick = {
                    if (isLastPage) {
                        // Blank name is fine - getUserName() treats blank as
                        // "not set" (see LocalSettingsStore doc comment), so
                        // Kate just skips personalized greetings rather than
                        // blocking onboarding on a required field.
                        if (userName.isNotBlank()) settings.setUserName(userName)
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
private fun NameCaptureContent(name: String, onNameChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        com.dti.kate.ui.components.KateAvatar(
            size = 160.dp,
            modifier = Modifier.padding(bottom = 40.dp),
        )

        Text(
            text = "What should I call you?",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Text(
            text = "So conversations feel a little more like talking to a friend.",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = TextSecondary,
                lineHeight = 24.sp,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        KateTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = "Your name",
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "You can skip this - Kate works fine without it.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
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
        // Was a raw Image(painterResource(page.icon)) with no clip -
        // rendered kate_avatar_source.png as an unmasked square (visible
        // in testing on the onboarding screen specifically). Every other
        // Compose screen (Login, Home, Splash, Register, ForgotPassword)
        // already used the shared KateAvatar component, which clips to a
        // circle correctly - this screen was the one that didn't.
        com.dti.kate.ui.components.KateAvatar(
            size = 200.dp,
            modifier = Modifier.padding(bottom = 48.dp),
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

package com.dti.kate.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.ui.theme.*

@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    LegalScreenScaffold(navController = navController, title = "Privacy Policy") {
        LegalSection(
            heading = "What we collect",
            body = "When you create an account, we store your email address, full name (if provided), and a securely hashed version of your password - we never store your password in plain text. We also store your conversations with Kate and basic usage statistics (how many requests you've made) to operate your account and the free/paid tiers."
        )
        LegalSection(
            heading = "Device permissions",
            body = "Kate uses your microphone only while actively listening for a command, your device's motion sensors to detect the Raise-to-Wake and Shake gestures (processed entirely on-device, never uploaded), and approximate location only when you ask a weather-related question."
        )
        LegalSection(
            heading = "Third-party services",
            body = "Weather lookups use Open-Meteo. Web search answers use DuckDuckGo's Instant Answer API. Neither service receives your account identity - only the text of your query."
        )
        LegalSection(
            heading = "Data sharing",
            body = "We do not sell your data to anyone. Conversation data is used only to operate and improve Kate's responses, and you can disable this sharing at any time from Settings > Sync Training Data."
        )
        LegalSection(
            heading = "Your controls",
            body = "You can clear locally cached data, export your conversation history, or delete your account entirely from the Settings screen."
        )
    }
}

@Composable
fun TermsOfServiceScreen(navController: NavController) {
    LegalScreenScaffold(navController = navController, title = "Terms of Service") {
        LegalSection(
            heading = "Using Kate",
            body = "Kate is provided as-is, without warranty of any kind. Kate is an assistant that can control certain device features (torch, Wi-Fi, Bluetooth, volume, calling) on your instruction - you are responsible for the commands you give it."
        )
        LegalSection(
            heading = "Accounts",
            body = "You're responsible for keeping your account credentials secure. You may delete your account at any time from Settings."
        )
        LegalSection(
            heading = "Subscriptions",
            body = "Premium tiers are billed through Google Play or, outside the Play Store, through Flutterwave. Subscriptions can be cancelled at any time and will remain active until the end of the current billing period."
        )
        LegalSection(
            heading = "Acceptable use",
            body = "You agree not to use Kate for any unlawful purpose, including using its calling or messaging features to harass others."
        )
        LegalSection(
            heading = "Changes",
            body = "We may update these terms as Kate's features evolve. Continued use of the app after changes constitutes acceptance of the updated terms."
        )
    }
}

@Composable
private fun LegalScreenScaffold(
    navController: NavController,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            content = content,
        )
    }
}

@Composable
private fun LegalSection(heading: String, body: String) {
    Text(
        text = heading,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
    )
}

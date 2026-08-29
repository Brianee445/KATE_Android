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
            body = "When you create an account, we store your email address, your name (if you give it to us), and a securely scrambled version of your password - we never store your actual password. We also store your conversations with Kate, any reminders you ask her to set (the text and time, so she can remind you), and basic usage numbers (like how many requests you've made) so we can run your account and the free and paid plans."
        )
        LegalSection(
            heading = "Device permissions",
            body = "Kate uses your microphone only while she's actively listening for something you say. She uses your device's motion sensors to notice the Raise-to-Wake and Shake gestures - this happens entirely on your device and is never sent anywhere. She uses your approximate location only when you ask about the weather. If you turn on Accessibility access, she uses it to do things like type text, open apps, or lock your screen when you ask her to."
        )
        LegalSection(
            heading = "How your speech is turned into text",
            body = "By default, Kate uses your device's own built-in speech engine to understand what you say - your conversation stays on your device and is never sent anywhere for this. If you're using Kate Pro, your speech may instead be sent to a secure third-party service so Kate can understand you more accurately."
        )
        LegalSection(
            heading = "Third-party services",
            body = "Kate relies on trusted third-party services for a few things: answering general search and knowledge questions, looking up the weather, and telling jokes. Kate Pro also uses a third-party service for more accurate speech processing, as explained above. When any of these are used, only the text of your question (or your general area, for weather) is shared - never your name, email, or account details. Math and calculations are always worked out directly on your device and never leave it."
        )
        LegalSection(
            heading = "Data sharing",
            body = "We do not sell your data to anyone, ever. Your conversation data is only used to run Kate and improve how she responds, and you can turn this off at any time from Settings."
        )
        LegalSection(
            heading = "Your controls",
            body = "You can clear locally stored data, export your conversation history, remove individual reminders, or delete your account entirely, all from the Settings screen."
        )
    }
}

@Composable
fun TermsOfServiceScreen(navController: NavController) {
    LegalScreenScaffold(navController = navController, title = "Terms of Service") {
        LegalSection(
            heading = "Using Kate",
            body = "Kate is provided as-is, without any guarantees. Kate can control certain features on your device - like your flashlight, Wi-Fi, Bluetooth, volume, and phone calls - when you ask her to. You're responsible for the instructions you give her."
        )
        LegalSection(
            heading = "Your account",
            body = "You're responsible for keeping your account details safe. You can delete your account at any time from Settings."
        )
        LegalSection(
            heading = "Subscriptions",
            body = "Premium plans are billed through Google Play or, outside the Play Store, through Flutterwave. You can cancel a subscription at any time, and it will stay active until the end of the period you've already paid for."
        )
        LegalSection(
            heading = "Acceptable use",
            body = "You agree not to use Kate for anything unlawful, including using her calling or messaging features to harass other people."
        )
        LegalSection(
            heading = "Changes",
            body = "We may update these terms as Kate's features change over time. Continuing to use the app after we make changes means you accept the updated terms."
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

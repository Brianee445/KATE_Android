package com.dti.kate.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.core.LocalSettingsStore
import com.dti.kate.ui.components.KateAvatar
import com.dti.kate.ui.components.KateButton
import com.dti.kate.ui.components.KateButtonSize
import com.dti.kate.ui.components.KateButtonType
import com.dti.kate.ui.theme.*

/**
 * Shown once per account, before Home is reached for the first time -
 * see LocalSettingsStore.hasAgreedToTerms(). Previously there was no
 * explicit consent step anywhere in the app: Register created an account
 * with no acknowledgment of what data gets collected, and the full
 * Privacy Policy / Terms screens were only reachable if a user went
 * looking for them in Settings. This is a short, plain-language summary
 * with an explicit choice, plus links to the full documents for anyone
 * who wants the details.
 */
@Composable
fun UserAgreementScreen(navController: NavController) {
    val context = LocalContext.current
    val localSettings = remember { LocalSettingsStore(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        KateAvatar(size = 72.dp)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Before you start",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Here's a quick, plain-language summary of how Kate handles your data. " +
                "You can read the full documents any time from Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        Spacer(modifier = Modifier.height(24.dp))

        AgreementPoint(
            title = "What Kate stores",
            body = "Your account details, your conversations with Kate, and any reminders you ask her to set - so she can actually remind you.",
        )
        AgreementPoint(
            title = "How she understands your speech",
            body = "By default, your device's own speech engine turns your voice into text, and it never leaves your device. On Kate Pro, your speech may be sent to a secure third-party service for more accurate results.",
        )
        AgreementPoint(
            title = "Third-party services",
            body = "For search, weather, and jokes, Kate uses trusted third-party services - only the text of your question or your general area is shared, never your identity.",
        )
        AgreementPoint(
            title = "Your control",
            body = "You can clear your data, export your history, or delete your account at any time from Settings.",
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row {
            Text(
                text = "Full Privacy Policy",
                style = MaterialTheme.typography.bodySmall,
                color = LimeAccent,
                modifier = Modifier.clickableText { navController.navigate("privacy_policy") },
            )
            Text(
                text = "   •   ",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Text(
                text = "Terms of Service",
                style = MaterialTheme.typography.bodySmall,
                color = LimeAccent,
                modifier = Modifier.clickableText { navController.navigate("terms_of_service") },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        KateButton(
            text = "I Agree & Continue",
            onClick = {
                localSettings.setAgreedToTerms(true)
                navController.navigate("home") { popUpTo("user_agreement") { inclusive = true } }
            },
            modifier = Modifier.fillMaxWidth(),
            type = KateButtonType.Primary,
            size = KateButtonSize.Large,
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = {
            // Declining means Kate can't operate (there's no account-less
            // mode) - routing back to the auth flow rather than exiting
            // the app outright, so a user who changes their mind isn't
            // dumped out entirely.
            navController.navigate("login") { popUpTo("user_agreement") { inclusive = true } }
        }) {
            Text(text = "Decline", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun AgreementPoint(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

package com.kate.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kate.assistant.ui.theme.KateText

@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Settings", color = KateText, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Text("Wake word enrollment, voice preferences, and more coming soon.", color = KateText.copy(alpha = 0.7f))
    }
}

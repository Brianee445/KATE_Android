package com.dti.kate.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.R
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val user by viewModel.user.collectAsState()
    
    Scaffold(
        modifier = Modifier.background(Background),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
        bottomBar = {
            KateBottomNavigation(
                items = bottomNavItems,
                currentRoute = "settings",
                onItemClick = { route ->
                    when (route) {
                        "home" -> navController.navigate("home") { popUpTo("settings") { inclusive = true } }
                        "history" -> navController.navigate("history")
                        "premium" -> navController.navigate("premium")
                        "settings" -> { /* Already here */ }
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Profile section
            item {
                ProfileCard(user = user)
            }
            
            // Tier section
            item {
                TierCard(
                    tier = user?.tier ?: "free",
                    onUpgrade = { navController.navigate("premium") },
                )
            }
            
            // Wake Triggers
            item {
                SettingsSectionHeader(title = "Wake Triggers")
            }
            
            items(settings.wakeTriggers) { trigger ->
                SettingsSwitchItem(
                    title = trigger.label,
                    description = trigger.description,
                    checked = trigger.enabled,
                    onCheckedChange = { viewModel.toggleWakeTrigger(trigger.id) },
                )
            }
            
            // Personality
            item {
                SettingsSectionHeader(title = "Personality")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = KateShape.MD,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = "Tone: ${settings.toneLevel.toInt()}% Sass",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                        Slider(
                            value = settings.toneLevel,
                            onValueChange = { viewModel.updateTone(it) },
                            valueRange = 0f..1f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = Purple70,
                                activeTrackColor = Purple70,
                                inactiveTrackColor = Divider,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Professional", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("Balanced", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("Maximum Sass", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
            
            // Listening
            item {
                SettingsSectionHeader(title = "Listening")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = KateShape.MD,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = "Timeout: ${settings.timeoutSeconds}s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                        Slider(
                            value = settings.timeoutSeconds.toFloat(),
                            onValueChange = { viewModel.updateTimeout(it.toInt()) },
                            valueRange = 5f..30f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = Purple70,
                                activeTrackColor = Purple70,
                                inactiveTrackColor = Divider,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("5s", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("15s", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("30s", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
                SettingsSwitchItem(
                    title = "Offline Mode",
                    description = "Use cached responses when offline",
                    checked = settings.offlineMode,
                    onCheckedChange = { viewModel.toggleOfflineMode() },
                )
            }
            
            // Privacy
            item {
                SettingsSectionHeader(title = "Privacy")
                SettingsSwitchItem(
                    title = "Sync Training Data",
                    description = "Help improve Kate by sharing anonymized data",
                    checked = settings.syncTraining,
                    onCheckedChange = { viewModel.toggleSyncTraining() },
                )
                SettingsButtonItem(
                    title = "Clear Local Data",
                    description = "Remove all cached conversations and data",
                    onClick = { viewModel.clearLocalData() },
                    color = Error,
                )
                SettingsButtonItem(
                    title = "Export Data",
                    description = "Download all your conversations",
                    onClick = { viewModel.exportData() },
                )
            }
            
            // About
            item {
                SettingsSectionHeader(title = "About")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = KateShape.MD,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "Kate Assistant v1.0.0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = "A D.T.I Company",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            KateTextButton(
                                text = "Privacy Policy",
                                onClick = { /* Open privacy policy */ },
                            )
                            KateTextButton(
                                text = "Terms of Service",
                                onClick = { /* Open terms */ },
                            )
                        }
                    }
                }
            }
            
            // Sign Out
            item {
                Spacer(modifier = Modifier.height(8.dp))
                KateButton(
                    text = "Sign Out",
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.fillMaxWidth(),
                    type = KateButtonType.Primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProfileCard(user: User?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Surface,
        ),
        shape = KateShape.MD,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Purple70.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = user?.fullName?.firstOrNull()?.uppercase() ?: "U",
                    style = MaterialTheme.typography.titleLarge,
                    color = Purple70,
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = user?.fullName ?: "User",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Text(
                    text = user?.email ?: "user@example.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            
            // Tier badge
            Surface(
                shape = KateShape.Pill,
                color = when (user?.tier) {
                    "free" -> SurfaceVariant
                    "premium" -> Purple70
                    "pro" -> Purple70
                    "lifetime" -> LimeAccent
                    else -> SurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = user?.tier?.uppercase() ?: "FREE",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (user?.tier) {
                        "free" -> TextSecondary
                        "lifetime" -> Background
                        else -> TextPrimary
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TierCard(
    tier: String,
    onUpgrade: () -> Unit,
) {
    if (tier == "free") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Purple70.copy(alpha = 0.1f),
            ),
            shape = KateShape.MD,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "💎 Unlock Premium",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Get unlimited cloud requests & more",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                KateButton(
                    text = "Upgrade",
                    onClick = onUpgrade,
                    type = KateButtonType.Accent,
                    size = KateButtonSize.Small,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
        ),
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = KateShape.MD,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Purple70,
                    checkedTrackColor = Purple70.copy(alpha = 0.5f),
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = Divider,
                ),
            )
        }
    }
}

@Composable
private fun SettingsButtonItem(
    title: String,
    description: String,
    onClick: () -> Unit,
    color: Color = TextPrimary,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = KateShape.MD,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
            )
        }
    }
}

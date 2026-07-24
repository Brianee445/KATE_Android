package com.dti.kate.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.BuildConfig
import com.dti.kate.repository.Repository
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*
import kotlinx.coroutines.launch

// Data classes
data class WakeTrigger(
    val id: String,
    val label: String,
    val description: String,
    val enabled: Boolean,
)

data class SettingsState(
    val toneLevel: Float = 0.5f,
    val timeoutSeconds: Int = 10,
    val offlineMode: Boolean = false,
    val syncTraining: Boolean = true,
    val wakeTriggers: List<WakeTrigger> = listOf(
        WakeTrigger("raise", "Raise to Wake", "Raise phone to activate Kate", true),
        WakeTrigger("double_tap", "Double Tap", "Double tap screen", false),
        WakeTrigger("shake", "Shake", "Shake device", false),
        WakeTrigger("button", "Home Button", "Press home button", false),
    ),
)

data class SettingsUser(
    val fullName: String = "",
    val email: String = "",
    val tier: String = "free",
)

class SettingsViewModel(context: Context) {

    private val repository = Repository(context.applicationContext)
    private val localStore = LocalSettingsStore(context)

    private val _settings = mutableStateOf(
        SettingsState(
            toneLevel = localStore.getToneLevel(),
            timeoutSeconds = localStore.getTimeoutSeconds(),
            offlineMode = localStore.getOfflineMode(),
        )
    )
    val settings = _settings

    private val _user = mutableStateOf(SettingsUser())
    val user = _user

    private val _isLoading = mutableStateOf(true)
    val isLoading = _isLoading

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage = _errorMessage

    suspend fun loadProfile() {
        _isLoading.value = true
        repository.getCurrentUser().fold(
            onSuccess = { profile ->
                _user.value = SettingsUser(
                    fullName = profile.fullName ?: "User",
                    email = profile.email,
                    tier = profile.tier,
                )
                _settings.value = _settings.value.copy(syncTraining = profile.syncTrainingEnabled)
                _errorMessage.value = null
            },
            onFailure = { error ->
                _errorMessage.value = error.message ?: "Failed to load profile"
            },
        )
        _isLoading.value = false
    }

    suspend fun toggleSyncTraining() {
        val newValue = !_settings.value.syncTraining
        _settings.value = _settings.value.copy(syncTraining = newValue)

        repository.updateProfile(syncTraining = newValue).fold(
            onSuccess = { },
            onFailure = {
                _settings.value = _settings.value.copy(syncTraining = !newValue)
            },
        )
    }

    fun toggleWakeTrigger(id: String) {
        val newTriggers = settings.value.wakeTriggers.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
        _settings.value = settings.value.copy(wakeTriggers = newTriggers)
    }

    fun updateTone(value: Float) {
        _settings.value = settings.value.copy(toneLevel = value)
        localStore.setToneLevel(value)
    }

    fun updateTimeout(value: Int) {
        _settings.value = settings.value.copy(timeoutSeconds = value)
        localStore.setTimeoutSeconds(value)
    }

    fun toggleOfflineMode() {
        val newValue = !settings.value.offlineMode
        _settings.value = settings.value.copy(offlineMode = newValue)
        localStore.setOfflineMode(newValue)
    }

    fun clearLocalData() {}
    fun exportData() {}

    suspend fun signOut(): Boolean {
        return repository.logout().fold(
            onSuccess = { true },
            onFailure = {
                repository.logoutLocal()
                true
            },
        )
    }
}

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = SettingsViewModel(LocalContext.current),
) {
    val settings by viewModel.settings
    val user by viewModel.user
    val isLoading by viewModel.isLoading
    val errorMessage by viewModel.errorMessage
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

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
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Purple70)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            item { ProfileCard(user = user) }
            item { TierCard(tier = user.tier, onUpgrade = { navController.navigate("premium") }) }

            item { SettingsSectionHeader(title = "Wake Triggers") }
            items(settings.wakeTriggers) { trigger ->
                SettingsSwitchItem(
                    title = trigger.label,
                    description = trigger.description,
                    checked = trigger.enabled,
                    onCheckedChange = { viewModel.toggleWakeTrigger(trigger.id) },
                )
            }

            item { SettingsSectionHeader(title = "Personality") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = KateShape.MD,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Tone: ${(settings.toneLevel * 100).toInt()}% Sass",
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

            item { SettingsSectionHeader(title = "Listening") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = KateShape.MD,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
            }
            item {
                SettingsSwitchItem(
                    title = "Offline Mode",
                    description = "Use cached responses when offline",
                    checked = settings.offlineMode,
                    onCheckedChange = { viewModel.toggleOfflineMode() },
                )
            }

            item { SettingsSectionHeader(title = "Privacy") }
            item {
                SettingsSwitchItem(
                    title = "Sync Training Data",
                    description = "Help improve Kate by sharing anonymized data",
                    checked = settings.syncTraining,
                    onCheckedChange = { coroutineScope.launch { viewModel.toggleSyncTraining() } },
                )
            }
            item {
                SettingsButtonItem(
                    title = "Clear Local Data",
                    description = "Remove all cached conversations and data",
                    onClick = { viewModel.clearLocalData() },
                    color = Error,
                )
            }
            item {
                SettingsButtonItem(
                    title = "Export Data",
                    description = "Download all your conversations",
                    onClick = { viewModel.exportData() },
                )
            }

            item { SettingsSectionHeader(title = "About") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = KateShape.MD,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Kate Assistant v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = "A D.T.I Company",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            KateTextButton(text = "Privacy Policy", onClick = {})
                            KateTextButton(text = "Terms of Service", onClick = {})
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                KateButton(
                    text = "Sign Out",
                    onClick = {
                        coroutineScope.launch {
                            viewModel.signOut()
                            navController.navigate("login") {
                                popUpTo(0)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    type = KateButtonType.Primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ---- Helper composables ----
@Composable
private fun ProfileCard(user: SettingsUser) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = KateShape.MD,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Purple70.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = user.fullName.firstOrNull()?.uppercase() ?: "U",
                    style = MaterialTheme.typography.titleLarge,
                    color = Purple70,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = user.fullName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(text = user.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Surface(
                shape = KateShape.Pill,
                color = when (user.tier) {
                    "free" -> SurfaceVariant
                    "premium" -> Purple70
                    "pro" -> Purple70
                    "lifetime" -> LimeAccent
                    else -> SurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = user.tier.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (user.tier) {
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
private fun TierCard(tier: String, onUpgrade: () -> Unit) {
    if (tier == "free") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Purple70.copy(alpha = 0.1f)),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, color = color)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

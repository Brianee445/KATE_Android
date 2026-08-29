package com.dti.kate.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.dti.kate.BuildConfig
import com.dti.kate.core.DebugLog
import com.dti.kate.core.LocalSettingsStore
import com.dti.kate.core.SecurePreferences
import com.dti.kate.repository.Repository
import com.dti.kate.service.KateAccessibilityService
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
    val wakeTriggers: List<WakeTrigger> = emptyList(),
    val sttMode: String = "classic",
)

data class SettingsUser(
    val fullName: String = "",
    val email: String = "",
    val tier: String = "free",
)

class SettingsViewModel(private val context: Context) {

    private val repository = Repository(context.applicationContext)
    private val localStore = LocalSettingsStore(context)

    private val _settings = mutableStateOf(
        SettingsState(
            toneLevel = localStore.getToneLevel(),
            timeoutSeconds = localStore.getTimeoutSeconds(),
            offlineMode = localStore.getOfflineMode(),
            sttMode = localStore.getSttMode(),
            wakeTriggers = listOf(
                WakeTrigger("raise", "Raise to Wake", "Raise your phone to activate Kate", localStore.getRaiseToWakeEnabled()),
                WakeTrigger("shake", "Shake", "Shake your phone to activate Kate", localStore.getShakeEnabled()),
                WakeTrigger("wakeword", "\"Hey Kate\"", "Say the wake word to activate Kate", localStore.getWakeWordEnabled()),
            ),
        )
    )
    val settings = _settings

    private val _user = mutableStateOf(SettingsUser())
    val user = _user

    private val _isLoading = mutableStateOf(true)
    val isLoading = _isLoading

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage = _errorMessage

    private val _actionMessage = mutableStateOf<String?>(null)
    val actionMessage = _actionMessage

    suspend fun loadProfile() {
        _isLoading.value = true
        repository.getCurrentUser().fold(
            onSuccess = { profile ->
                _user.value = SettingsUser(
                    fullName = profile.fullName ?: "User",
                    email = profile.email,
                    tier = profile.tier,
                )
                // Mirror the backend's tier into the fast local read used
                // by FeatureGate checks (jokes, tone slider, wake word) -
                // see EntitlementStore's doc comment for why this needs
                // syncing rather than being read from the backend directly
                // on every gate check.
                com.dti.kate.billing.EntitlementStore(context)
                    .setTier(com.dti.kate.billing.SubscriptionTier.fromId(profile.tier))
                _settings.value = _settings.value.copy(syncTraining = profile.syncTrainingEnabled)
                LocalSettingsStore(context).setSyncTrainingEnabled(profile.syncTrainingEnabled)
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
        LocalSettingsStore(context).setSyncTrainingEnabled(newValue)
        repository.updateProfile(syncTraining = newValue).fold(
            onSuccess = { },
            onFailure = {
                _settings.value = _settings.value.copy(syncTraining = !newValue)
                LocalSettingsStore(context).setSyncTrainingEnabled(!newValue)
            },
        )
    }

    fun toggleWakeTrigger(id: String) {
        val newTriggers = settings.value.wakeTriggers.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
        _settings.value = settings.value.copy(wakeTriggers = newTriggers)

        val raise = newTriggers.firstOrNull { it.id == "raise" }?.enabled ?: true
        val shake = newTriggers.firstOrNull { it.id == "shake" }?.enabled ?: false
        val wakeWord = newTriggers.firstOrNull { it.id == "wakeword" }?.enabled ?: true
        localStore.setRaiseToWakeEnabled(raise)
        localStore.setShakeEnabled(shake)
        localStore.setWakeWordEnabled(wakeWord)
    }

    /** Re-opens the OEM autostart settings screen (Transsion HiOS/XOS etc.) - for when the user dismissed the one-time prompt from KateActivity and background wake triggers keep dying. No-op button hidden entirely on non-Transsion devices, see SettingsScreen. */
    fun fixBackgroundReliability() {
        val deviceControl = com.dti.kate.utils.DeviceControlManager(context)
        deviceControl.requestIgnoreBatteryOptimizations()
        deviceControl.requestAutostartPermission()
    }

    fun isTranssionDevice(): Boolean = com.dti.kate.utils.DeviceControlManager(context).isTranssionDevice()

    fun updateTone(value: Float) {
        _settings.value = settings.value.copy(toneLevel = value)
        localStore.setToneLevel(value)
    }

    fun updateTimeout(value: Int) {
        _settings.value = settings.value.copy(timeoutSeconds = value)
        localStore.setTimeoutSeconds(value)
    }

    fun updateSttMode(mode: String) {
        _settings.value = settings.value.copy(sttMode = mode)
        localStore.setSttMode(mode)
    }

    fun toggleOfflineMode() {
        val newValue = !settings.value.offlineMode
        _settings.value = settings.value.copy(offlineMode = newValue)
        localStore.setOfflineMode(newValue)
    }

    fun clearLocalData() {
        try {
            context.cacheDir.deleteRecursively()
            localStore.resetToDefaults()
            _actionMessage.value = "Local data cleared"
        } catch (e: Exception) {
            _actionMessage.value = "Failed to clear local data"
        }
    }

    fun exportDebugLog(): Intent? {
        return try {
            DebugLog.exportShareIntent(context)
        } catch (e: Exception) {
            _actionMessage.value = "Failed to export debug log"
            null
        }
    }

    suspend fun exportData(): Intent? {
        return repository.getChatHistory(limit = 1000).fold(
            onSuccess = { history ->
                try {
                    val jsonArray = JSONArray()
                    history.conversations.forEach { conv ->
                        val obj = JSONObject()
                        obj.put("query", conv.query)
                        obj.put("response", conv.response)
                        obj.put("intent", conv.intent)
                        obj.put("createdAt", conv.createdAt)
                        jsonArray.put(obj)
                    }

                    val exportDir = File(context.cacheDir, "exports")
                    exportDir.mkdirs()
                    val file = File(exportDir, "kate_conversations.json")
                    file.writeText(jsonArray.toString(2))

                    val uri = FileProvider.getUriForFile(
                        context, "${BuildConfig.APPLICATION_ID}.fileprovider", file
                    )

                    _actionMessage.value = null
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } catch (e: Exception) {
                    _actionMessage.value = "Failed to prepare export"
                    null
                }
            },
            onFailure = {
                _actionMessage.value = "Failed to fetch conversation history"
                null
            },
        )
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = SettingsViewModel(LocalContext.current),
) {
    val context = LocalContext.current
    val settings by viewModel.settings
    val user by viewModel.user
    val isLoading by viewModel.isLoading
    val errorMessage by viewModel.errorMessage
    val actionMessage by viewModel.actionMessage
    // Re-read fresh each recomposition rather than cached in remember{} -
    // this is a cheap SharedPreferences read, and staying live means the
    // gates below unlock immediately after a purchase/tier-sync without
    // needing this screen to be reopened.
    val entitlements = com.dti.kate.billing.EntitlementStore(context)
    val toneUnlocked = entitlements.isUnlocked(com.dti.kate.billing.GatedFeature.TONE_SLIDER)
    val wakeWordUnlocked = entitlements.isUnlocked(com.dti.kate.billing.GatedFeature.WAKE_WORD)
    val coroutineScope = rememberCoroutineScope()

    var showClearConfirm by remember { mutableStateOf(false) }
    var showAdminPasscodeDialog by remember { mutableStateOf(false) }
    var adminPasscodeError by remember { mutableStateOf<String?>(null) }
    var isAdminUnlocked by remember { mutableStateOf(SecurePreferences(context).isAdmin()) }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear local data?") },
            text = { Text("This clears cached files and resets your local settings on this device. It doesn't delete your account or server-side conversation history.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLocalData()
                    showClearConfirm = false
                }) { Text("Clear", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showAdminPasscodeDialog) {
        var passcodeInput by remember { mutableStateOf("") }
        var isVerifying by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isVerifying) { showAdminPasscodeDialog = false; adminPasscodeError = null } },
            title = { Text("Admin Access") },
            text = {
                Column {
                    OutlinedTextField(
                        value = passcodeInput,
                        onValueChange = { passcodeInput = it; adminPasscodeError = null },
                        label = { Text("Passcode") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isVerifying,
                    )
                    adminPasscodeError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, color = Error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passcodeInput.isNotBlank() && !isVerifying,
                    onClick = {
                        isVerifying = true
                        coroutineScope.launch {
                            com.dti.kate.repository.Repository(context).verifyAdmin(passcodeInput).fold(
                                onSuccess = { response ->
                                    isVerifying = false
                                    if (response.valid) {
                                        isAdminUnlocked = true
                                        showAdminPasscodeDialog = false
                                    } else {
                                        adminPasscodeError = response.message
                                    }
                                },
                                onFailure = { error ->
                                    isVerifying = false
                                    adminPasscodeError = error.message ?: "Verification failed"
                                },
                            )
                        }
                    },
                ) { Text(if (isVerifying) "Checking..." else "Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showAdminPasscodeDialog = false; adminPasscodeError = null }, enabled = !isVerifying) { Text("Cancel") }
            },
        )
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
                    Text(text = message, style = MaterialTheme.typography.bodySmall, color = Error, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            actionMessage?.let { message ->
                item {
                    Text(text = message, style = MaterialTheme.typography.bodySmall, color = LimeAccent, modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            item { ProfileCard(user = user) }
            item { TierCard(tier = user.tier, onUpgrade = { navController.navigate("premium") }) }

            item { SettingsSectionHeader(title = "Permissions") }
            item {
                // Re-checked every recomposition (cheap reads), same
                // reasoning as the entitlement gates above - status should
                // update live once the user comes back from the system
                // Accessibility screen without reopening this one.
                //
                // isEnabled() (Settings.Secure) and isAccessibilityServiceRunning()
                // (live service instance) can disagree: Android can silently
                // kill the connected service process - especially under
                // aggressive OEM battery management (Transsion, flagged
                // elsewhere in this screen) - without removing it from the
                // enabled list. That "enabled but disconnected" state is
                // what actually produces the intermittent "accessibility
                // malfunctioning" reports, and toggling the switch off/on
                // is what forces Android to reconnect it - opening Settings
                // when it's already listed as enabled doesn't visibly help,
                // which is why the two states get different guidance below.
                val settingEnabled = KateAccessibilityService.isEnabled(context)
                val serviceConnected = com.dti.kate.utils.DeviceControlManager(context)
                    .isAccessibilityServiceRunning()
                val accessibilityEnabled = settingEnabled && serviceConnected
                val staleConnection = settingEnabled && !serviceConnected
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { KateAccessibilityService.openAccessibilitySettings(context) },
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = KateShape.MD,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Accessibility access",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = when {
                                    accessibilityEnabled -> "On"
                                    staleConnection -> "Reconnect needed"
                                    else -> "Off"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (accessibilityEnabled) LimeAccent else TextSecondary,
                            )
                        }
                        Text(
                            text = "Needed for typing, opening recents, locking the screen, " +
                                "taking screenshots, and declining calls by voice.",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                        )
                        if (staleConnection) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Kate's accessibility connection dropped in the " +
                                    "background - this happens sometimes on this device. " +
                                    "Tap to open Accessibility settings, then turn Kate's " +
                                    "switch off and back on to reconnect it.",
                                style = MaterialTheme.typography.labelSmall, color = LimeAccent,
                            )
                        } else if (!accessibilityEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap to open Accessibility settings and turn Kate on. " +
                                    "If the switch looks greyed out and won't turn on, open " +
                                    "Android's Settings app -> Apps -> Kate -> tap the icon " +
                                    "in the top-right corner -> \"Allow restricted settings\" " +
                                    "first, then come back here.",
                                style = MaterialTheme.typography.labelSmall, color = LimeAccent,
                            )
                        }
                    }
                }
            }

            item { SettingsSectionHeader(title = "Wake Triggers") }
            items(settings.wakeTriggers) { trigger ->
                // "Hey Kate" wake word is Premium+ (see billing.FeatureGate) -
                // raise-to-wake and shake stay free. Locked instead of
                // hidden so free users know the feature exists and can
                // upgrade to it, rather than wondering where it went.
                val isWakeWordRow = trigger.id == "wakeword"
                val locked = isWakeWordRow && !wakeWordUnlocked
                SettingsSwitchItem(
                    title = trigger.label,
                    description = if (locked) "Premium feature - tap to upgrade" else trigger.description,
                    checked = trigger.enabled && !locked,
                    onCheckedChange = {
                        if (locked) navController.navigate("premium")
                        else viewModel.toggleWakeTrigger(trigger.id)
                    },
                )
            }

            if (viewModel.isTranssionDevice()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.fixBackgroundReliability() },
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        shape = KateShape.MD,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Fix background reliability", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Your device's power saver can kill wake triggers in the background. Tap to re-open the permission screens that keep Kate running.",
                                style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                            )
                        }
                    }
                }
            }

            item { SettingsSectionHeader(title = "Personality") }
            item {
                // Tone slider is Premium+ (see billing.FeatureGate) - shown
                // but disabled with an upgrade prompt for free users, same
                // "visible but locked" approach as the wake-word row above.
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!toneUnlocked) Modifier.clickable { navController.navigate("premium") }
                            else Modifier
                        ),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = KateShape.MD,
                ) {
                    Column(modifier = Modifier.padding(16.dp).alpha(if (toneUnlocked) 1f else 0.5f)) {
                        Text(
                            text = if (toneUnlocked) "Tone: ${(settings.toneLevel * 100).toInt()}% Sass"
                                else "Tone slider - Premium feature",
                            style = MaterialTheme.typography.bodyMedium, color = TextPrimary,
                        )
                        Slider(
                            value = settings.toneLevel,
                            onValueChange = { viewModel.updateTone(it) },
                            valueRange = 0f..1f,
                            steps = 4,
                            enabled = toneUnlocked,
                            colors = SliderDefaults.colors(thumbColor = Purple70, activeTrackColor = Purple70, inactiveTrackColor = Divider),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Professional", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("Balanced", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("Maximum Sass", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        if (!toneUnlocked) {
                            Text(
                                "Tap to upgrade",
                                style = MaterialTheme.typography.labelSmall,
                                color = LimeAccent,
                                modifier = Modifier.padding(top = 4.dp),
                            )
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
                        Text(text = "Recognition mode", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            text = "How Kate listens - higher modes may use your connection",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf(
                            Triple("classic", "Kate Classic", "Free, uses your device's speech service"),
                            Triple("pro", "Kate Pro", "Best accuracy, uses your connection"),
                        ).forEach { (id, label, description) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updateSttMode(id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = settings.sttMode == id,
                                    onClick = { viewModel.updateSttMode(id) },
                                    colors = RadioButtonDefaults.colors(selectedColor = Purple70),
                                )
                                Column {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    Text(description, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = KateShape.MD,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Timeout: ${settings.timeoutSeconds}s", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Slider(
                            value = settings.timeoutSeconds.toFloat(),
                            onValueChange = { viewModel.updateTimeout(it.toInt()) },
                            valueRange = 5f..30f,
                            steps = 5,
                            colors = SliderDefaults.colors(thumbColor = Purple70, activeTrackColor = Purple70, inactiveTrackColor = Divider),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("5s", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("15s", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("30s", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
            item {
                // This toggle previously wrote a boolean to prefs that
                // nothing else in the app read - flipping it changed
                // nothing about Kate's actual behavior (speech recognition
                // still requires connectivity regardless). Disabled with
                // honest copy until real offline STT/response-caching
                // ships, rather than leaving a switch that silently does
                // nothing - see docs/ROADMAP.md.
                SettingsSwitchItem(
                    title = "Offline Mode",
                    description = "Coming soon - stay tuned",
                    checked = false,
                    onCheckedChange = {},
                    enabled = false,
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
                    description = "Remove cached files and reset local settings on this device",
                    onClick = { showClearConfirm = true },
                    color = Error,
                )
            }
            item {
                SettingsButtonItem(
                    title = "Export Data",
                    description = "Download all your conversations",
                    onClick = {
                        coroutineScope.launch {
                            val shareIntent = viewModel.exportData()
                            shareIntent?.let {
                                context.startActivity(Intent.createChooser(it, "Export conversations"))
                            }
                        }
                    },
                )
            }
            item {
                SettingsButtonItem(
                    title = "Export Debug Log",
                    description = "Share diagnostic logs for the voice engine (for troubleshooting)",
                    enabled = false,
                    onClick = {
                        val shareIntent = viewModel.exportDebugLog()
                        if (shareIntent != null) {
                            context.startActivity(Intent.createChooser(shareIntent, "Export debug log"))
                        }
                    },
                )
            }

            item { SettingsSectionHeader(title = "About") }
            item {
                if (isAdminUnlocked) {
                    SettingsButtonItem(
                        title = "Admin Dashboard",
                        description = "Users, revenue, and error stats",
                        onClick = { navController.navigate("admin_dashboard") },
                    )
                }
            }
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
                            // Long-press is the "hidden gesture" the backend's
                            // /admin/verify endpoint was built expecting (see
                            // its own doc comment) - previously nothing in the
                            // app ever called it, so no passcode could ever be
                            // entered and isAdmin() could never become true no
                            // matter what was set in the database.
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { showAdminPasscodeDialog = true },
                            ),
                        )
                        Text(text = "A D.T.I Company", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            KateTextButton(text = "Privacy Policy", onClick = { navController.navigate("privacy_policy") })
                            KateTextButton(text = "Terms of Service", onClick = { navController.navigate("terms_of_service") })
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
                            navController.navigate("login") { popUpTo(0) }
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

@Composable
private fun ProfileCard(user: SettingsUser) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = KateShape.MD,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(Purple70.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = user.fullName.firstOrNull()?.uppercase() ?: "U", style = MaterialTheme.typography.titleLarge, color = Purple70)
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
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = "💎 Unlock Premium", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(text = "Get unlimited cloud requests & more", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                KateButton(text = "Upgrade", onClick = onUpgrade, type = KateButtonType.Accent, size = KateButtonSize.Small)
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextSecondary),
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    enabled: Boolean = true,
) {
    // Dimmed when disabled (e.g. "Offline Mode - coming soon") so a
    // not-yet-functional toggle reads as unavailable rather than as a
    // switch that silently does nothing when tapped.
    val contentAlpha = if (enabled) 1f else 0.5f
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = KateShape.MD,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).alpha(contentAlpha)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange() },
                enabled = enabled,
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
    enabled: Boolean = true,
) {
    val rowModifier = if (enabled) {
        Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp)
    } else {
        Modifier.fillMaxWidth().padding(16.dp)
    }
    val effectiveColor = if (enabled) color else TextSecondary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = KateShape.MD,
    ) {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, color = effectiveColor)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary.copy(alpha = if (enabled) 1f else 0.4f))
        }
    }
}

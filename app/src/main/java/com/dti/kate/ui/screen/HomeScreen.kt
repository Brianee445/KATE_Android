package com.dti.kate.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.R
import com.dti.kate.core.VoskManager
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*
import kotlinx.coroutines.launch

// ==================== DATA CLASSES ====================
data class QuickAction(
    val label: String,
    val icon: Int,
    val type: KateButtonType,
)

data class Conversation(
    val id: String,
    val query: String,
    val response: String,
    val timestamp: Long,
)

enum class KateAvatarState {
    IDLE, LISTENING, THINKING, SPEAKING, SLEEPING, PRODUCTIVE, ERROR
}

// ==================== VIEW MODEL ====================
class HomeViewModel {
    private val _uiState = mutableStateOf(HomeUiState())
    val uiState = _uiState

    private val _conversations = mutableStateOf<List<Conversation>>(emptyList())
    val conversations = _conversations

    fun startListening() {
        _uiState.value = _uiState.value.copy(isProcessing = false)
    }

    fun processTranscription(text: String) {
        if (text.isNotEmpty()) {
            val newConv = Conversation(
                id = System.currentTimeMillis().toString(),
                query = text,
                response = "Processing: $text",
                timestamp = System.currentTimeMillis()
            )
            _conversations.value = _conversations.value + newConv
            _uiState.value = _uiState.value.copy(isProcessing = false, isSpeaking = true)
        }
    }

    fun openApp() { /* TODO */ }
    fun startTyping() { /* TODO */ }
    fun showHelp() { /* TODO */ }
}

data class HomeUiState(
    val isProcessing: Boolean = false,
    val isSpeaking: Boolean = false,
)

// ==================== SCREEN ====================
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = HomeViewModel(),
    voskManager: VoskManager? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState
    val conversations by viewModel.conversations

    // Quick actions
    val quickActions = listOf(
        QuickAction("Open App", R.drawable.ic_apps, KateButtonType.Primary),
        QuickAction("Type", R.drawable.ic_type, KateButtonType.Secondary),
        QuickAction("Help", R.drawable.ic_help, KateButtonType.Ghost),
    )

    Scaffold(
        modifier = Modifier.background(Background),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.kate_avatar_idle),
                            contentDescription = "Kate",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kate",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            ),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        bottomBar = {
            // You can add bottom navigation here if needed
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            // Main content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (conversations.isEmpty() && !uiState.isProcessing) {
                    // Empty state
                    EmptyState(
                        onQuickAction = { action ->
                            when (action) {
                                "Open App" -> viewModel.openApp()
                                "Type" -> viewModel.startTyping()
                                "Help" -> viewModel.showHelp()
                                else -> {}
                            }
                        },
                    )
                } else {
                    // Conversation list
                    ConversationList(
                        conversations = conversations,
                        isListening = false,
                    )
                }
            }

            // Quick Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickActions.forEach { action ->
                    KateButton(
                        text = action.label,
                        onClick = {
                            when (action.label) {
                                "Open App" -> viewModel.openApp()
                                "Type" -> viewModel.startTyping()
                                "Help" -> viewModel.showHelp()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        type = action.type,
                        size = KateButtonSize.Small,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // FAB
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val fabState = when {
                    uiState.isProcessing -> KateFABState.Processing
                    else -> KateFABState.Idle
                }

                KateFAB(
                    state = fabState,
                    onClick = {
                        coroutineScope.launch {
                            // Handle mic action
                        }
                    },
                )
            }

            // Status text
            Text(
                text = when {
                    uiState.isProcessing -> "Thinking..."
                    else -> if (conversations.isNotEmpty()) "Tap mic or raise phone" else "Tap mic to start"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }
    }
}

// ==================== COMPOSABLES ====================
@Composable
private fun EmptyState(
    onQuickAction: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Purple70.copy(alpha = 0.3f), Color.Transparent),
                        radius = 120f,
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.kate_avatar_idle),
                contentDescription = "Kate",
                modifier = Modifier
                    .size(80.dp)
                    .animateContentSize(),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "How can I help?",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tap the mic, raise your phone, or say \"Hey Kate\"",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Quick action chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { onQuickAction("Open App") },
                label = { Text("📱 Open App") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = SurfaceVariant,
                    labelColor = TextPrimary,
                ),
            )
            AssistChip(
                onClick = { onQuickAction("Help") },
                label = { Text("❓ Help") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = SurfaceVariant,
                    labelColor = TextPrimary,
                ),
            )
        }
    }
}

@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    isListening: Boolean,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        reverseLayout = false,
    ) {
        items(conversations) { conv ->
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                // User message
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    KateConversationBubble(
                        text = conv.query,
                        isUser = true,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Kate response
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    KateConversationBubble(
                        text = conv.response,
                        isUser = false,
                    )
                }
            }
        }
    }
}

// Bottom navigation items (if needed)
val bottomNavItems = listOf(
    BottomNavItem("home", Icons.Filled.Home, "Home", Icons.Filled.Home),
    BottomNavItem("history", Icons.Outlined.History, "History"),
    BottomNavItem("premium", Icons.Filled.Star, "Premium", Icons.Filled.Star),
    BottomNavItem("settings", Icons.Filled.Settings, "Settings", Icons.Filled.Settings),
)

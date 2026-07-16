package com.dti.kate.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.R
import com.dti.kate.core.VoskManager
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(),
    voskManager: VoskManager,
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val isListening by voskManager.isListening.collectAsState()
    val transcription by voskManager.transcription.collectAsState()
    
    // Avatar state
    var avatarState by remember { mutableStateOf(KateAvatarState.IDLE) }
    var isProcessing by remember { mutableStateOf(false) }
    
    // Quick actions
    val quickActions = listOf(
        QuickAction("Open App", R.drawable.ic_apps, KateButtonType.Primary),
        QuickAction("Type", R.drawable.ic_type, KateButtonType.Secondary),
        QuickAction("Help", R.drawable.ic_help, KateButtonType.Ghost),
    )
    
    // Update avatar state based on listening/processing
    LaunchedEffect(isListening, uiState.isProcessing) {
        when {
            uiState.isProcessing -> avatarState = KateAvatarState.THINKING
            isListening -> avatarState = KateAvatarState.LISTENING
            uiState.isSpeaking -> avatarState = KateAvatarState.SPEAKING
            else -> avatarState = KateAvatarState.IDLE
        }
    }
    
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
                    // Admin gesture detector will be added here
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
            KateBottomNavigation(
                items = bottomNavItems,
                currentRoute = "home",
                onItemClick = { route ->
                    when (route) {
                        "home" -> { /* Already here */ }
                        "history" -> navController.navigate("history")
                        "premium" -> navController.navigate("premium")
                        "settings" -> navController.navigate("settings")
                    }
                },
            )
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
                if (conversations.isEmpty() && !isListening && !uiState.isProcessing) {
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
                        transcription = if (isListening) transcription else null,
                        isListening = isListening,
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
                    isListening -> KateFABState.Listening
                    uiState.isSpeaking -> KateFABState.Speaking
                    else -> KateFABState.Idle
                }
                
                KateFAB(
                    state = fabState,
                    onClick = {
                        coroutineScope.launch {
                            if (isListening) {
                                voskManager.stopListening()
                                viewModel.processTranscription(transcription)
                            } else {
                                if (voskManager.startListening()) {
                                    viewModel.startListening()
                                }
                            }
                        }
                    },
                )
            }
            
            // Status text
            Text(
                text = when {
                    uiState.isProcessing -> "Thinking..."
                    isListening -> "Listening..."
                    uiState.isSpeaking -> "Speaking..."
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
        // Animated avatar
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
                    .scale(1f)
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
            Chip(
                onClick = { onQuickAction("Open App") },
                label = { Text("📱 Open App") },
                colors = ChipDefaults.chipColors(
                    containerColor = SurfaceVariant,
                    labelColor = TextPrimary,
                ),
            )
            Chip(
                onClick = { onQuickAction("Help") },
                label = { Text("❓ Help") },
                colors = ChipDefaults.chipColors(
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
    transcription: String?,
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
        
        // Live transcription
        if (isListening && !transcription.isNullOrEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    KateConversationBubble(
                        text = "💬 $transcription",
                        isUser = false,
                    )
                }
            }
        }
    }
}

// Data classes
data class QuickAction(
    val label: String,
    val icon: Int,
    val type: KateButtonType,
)

enum class KateAvatarState {
    IDLE, LISTENING, THINKING, SPEAKING, SLEEPING, PRODUCTIVE, ERROR
}

data class Conversation(
    val id: String,
    val query: String,
    val response: String,
    val timestamp: Long,
)

// Bottom navigation items
val bottomNavItems = listOf(
    BottomNavItem("home", Icons.Outlined.Home, "Home", Icons.Filled.Home),
    BottomNavItem("history", Icons.Outlined.History, "History"),
    BottomNavItem("premium", Icons.Outlined.Star, "Premium", Icons.Filled.Star),
    BottomNavItem("settings", Icons.Outlined.Settings, "Settings", Icons.Filled.Settings),
)

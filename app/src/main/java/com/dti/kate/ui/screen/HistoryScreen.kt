package com.dti.kate.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*

// ==================== DATA CLASSES ====================
data class HistoryConversation(
    val id: String,
    val query: String,
    val response: String,
    val intent: String,
    val usedCloud: Boolean,
    val timestamp: String,
)

// ==================== VIEW MODEL ====================
class HistoryViewModel {
    private val _conversations = mutableStateOf(
        listOf(
            HistoryConversation(
                id = "1",
                query = "Open Spotify",
                response = "Opening Spotify...",
                intent = "open_app",
                usedCloud = false,
                timestamp = "2025-01-15 14:30",
            ),
            HistoryConversation(
                id = "2",
                query = "What's the weather?",
                response = "I can't check weather offline yet.",
                intent = "search",
                usedCloud = true,
                timestamp = "2025-01-15 12:10",
            ),
        )
    )
    val conversations = _conversations

    private val _isLoading = mutableStateOf(false)
    val isLoading = _isLoading

    private val _searchQuery = mutableStateOf("")
    val searchQuery = _searchQuery

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearHistory() {
        _conversations.value = emptyList()
    }
}

// ==================== SCREEN ====================
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = HistoryViewModel(),
) {
    val conversations by viewModel.conversations
    val isLoading by viewModel.isLoading
    val searchQuery by viewModel.searchQuery

    var selectedFilter by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.background(Background),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "History",
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
                actions = {
                    if (conversations.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        bottomBar = {
            // You can add bottom navigation if needed
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            // Search bar
            KateTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = "Search conversations...",
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = "Search", tint = TextSecondary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = KateShape.Pill,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Filter chips
            if (conversations.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = selectedFilter == "open_app",
                        onClick = { selectedFilter = "open_app" },
                        label = { Text("📱 Apps") },
                    )
                    FilterChip(
                        selected = selectedFilter == "type_text",
                        onClick = { selectedFilter = "type_text" },
                        label = { Text("⌨️ Type") },
                    )
                    FilterChip(
                        selected = selectedFilter == "search",
                        onClick = { selectedFilter = "search" },
                        label = { Text("🔍 Search") },
                    )
                }
            }

            // Content
            if (isLoading) {
                KateLoadingState(message = "Loading history...")
            } else if (conversations.isEmpty()) {
                EmptyHistoryState()
            } else {
                // Filter conversations by selected filter and search query
                val filtered = conversations.filter { conv ->
                    val matchesFilter = selectedFilter == null || conv.intent == selectedFilter
                    val matchesSearch = searchQuery.isEmpty() ||
                            conv.query.contains(searchQuery, ignoreCase = true) ||
                            conv.response.contains(searchQuery, ignoreCase = true)
                    matchesFilter && matchesSearch
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered) { conversation ->
                        HistoryItem(conversation = conversation)
                    }
                }
            }
        }
    }
}

// ==================== COMPOSABLES ====================
@Composable
private fun HistoryItem(
    conversation: HistoryConversation,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Surface,
        ),
        shape = KateShape.MD,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = conversation.query,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = conversation.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = conversation.response,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Intent chip
                AssistChip(
                    onClick = { /* No action */ },
                    label = {
                        Text(
                            conversation.intent.replace("_", " ").uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Purple70.copy(alpha = 0.15f),
                        labelColor = Purple70,
                    ),
                )

                if (conversation.usedCloud) {
                    AssistChip(
                        onClick = { /* No action */ },
                        label = {
                            Text("☁️ Cloud", style = MaterialTheme.typography.labelSmall)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = LimeAccent.copy(alpha = 0.15f),
                            labelColor = LimeAccent,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "📜",
            style = MaterialTheme.typography.displayMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No conversations yet",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Start chatting with Kate to see your history here",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        KateButton(
            text = "Go Home",
            onClick = { /* Navigate to home */ },
            type = KateButtonType.Primary,
        )
    }
}

package com.dti.kate.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.repository.Repository
import com.dti.kate.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

private data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val requiresPayment: Boolean = false,
)

@Composable
fun ChatScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember { Repository(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    val listState: LazyListState = rememberLazyListState()

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    fun send() {
        val query = inputText.trim()
        if (query.isBlank() || isSending) return
        inputText = ""
        messages = messages + ChatMessage(text = query, isUser = true)
        isSending = true

        coroutineScope.launch {
            val result = repository.chat(query)
            result.onSuccess { response ->
                messages = messages + ChatMessage(
                    text = response.response,
                    isUser = false,
                    requiresPayment = response.requiresPayment,
                )
            }.onFailure {
                messages = messages + ChatMessage(
                    text = "Sorry, I couldn't reach the server. Check your connection and try again.",
                    isUser = false,
                )
            }
            isSending = false
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Chat with Kate",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Background),
        ) {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Ask Kate anything - this goes through her full backend, not just on-device commands.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatBubble(message = message, onUpgradeClick = { navController.navigate("premium") })
                    }
                    if (isSending) {
                        item {
                            Text("Kate is thinking...", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask Kate anything...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { send() }, enabled = inputText.isNotBlank() && !isSending) {
                    Icon(Icons.Outlined.Send, contentDescription = "Send", tint = Purple70)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, onUpgradeClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = if (message.isUser) Purple70 else Surface,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(12.dp)
                .widthIn(max = 280.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser) androidx.compose.ui.graphics.Color.White else TextPrimary,
            )

            if (message.requiresPayment) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onUpgradeClick) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = LimeAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Unlock with Premium", color = LimeAccent, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

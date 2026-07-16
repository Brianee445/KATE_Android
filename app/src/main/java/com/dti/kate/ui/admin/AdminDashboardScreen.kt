package com.dti.kate.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dti.kate.network.models.*
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*

@Composable
fun AdminDashboardScreen(
    navController: NavController,
    viewModel: AdminDashboardViewModel = viewModel(),
) {
    val stats by viewModel.stats.collectAsState()
    val errors by viewModel.errors.collectAsState()
    val activity by viewModel.activity.collectAsState()
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadDashboard()
    }
    
    Scaffold(
        modifier = Modifier.background(Background),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "📊 Admin Dashboard",
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
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Refresh",
                            tint = TextSecondary,
                        )
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            Icons.Outlined.Logout,
                            contentDescription = "Logout",
                            tint = TextSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { paddingValues ->
        if (isLoading) {
            KateLoadingState(message = "Loading dashboard...")
        } else if (stats != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
            ) {
                // Tab row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Purple70,
                        )
                    },
                ) {
                    listOf("Overview", "Errors", "Users", "System").forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { viewModel.selectTab(index) },
                            text = {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selectedTab == index) Purple70 else TextSecondary,
                                )
                            },
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content based on tab
                when (selectedTab) {
                    0 -> OverviewTab(stats = stats!!, activity = activity)
                    1 -> ErrorsTab(errors = errors, onRefresh = { viewModel.loadErrors() })
                    2 -> UsersTab(users = users, onLoadMore = { viewModel.loadMoreUsers() })
                    3 -> SystemTab()
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(
    stats: AdminDashboardStats,
    activity: List<AdminActivityItem>?,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Stats row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AdminStatCard(
                    title = "Users",
                    value = stats.users.total.toString(),
                    subtitle = "👤 ${stats.users.active24h} active",
                    color = Purple70,
                    modifier = Modifier.weight(1f),
                )
                AdminStatCard(
                    title = "Conversations",
                    value = stats.conversations.total.toString(),
                    subtitle = "📝 ${stats.conversations.today} today",
                    color = Purple30,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AdminStatCard(
                    title = "Accuracy",
                    value = "${stats.conversations.intentAccuracy.toInt()}%",
                    subtitle = "Intent classification",
                    color = LimeAccent,
                    modifier = Modifier.weight(1f),
                )
                AdminStatCard(
                    title = "Premium",
                    value = stats.users.premium.toString(),
                    subtitle = "💰 Active subscribers",
                    color = Success,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        
        // Revenue card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Purple70.copy(alpha = 0.1f),
                ),
                shape = KateShape.MD,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = "💰 Revenue (30 days)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Text(
                        text = "$${String.format("%.2f", stats.revenue.totalRevenue)}",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = LimeAccent,
                        ),
                    )
                    Text(
                        text = "Growth: ${String.format("%.1f", stats.revenue.growthRate)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (stats.revenue.growthRate >= 0) Success else Error,
                    )
                }
            }
        }
        
        // Model status
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Surface,
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
                            text = "🧠 Model",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stats.models.activeVersion,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            if (stats.models.isOutdated) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = KateShape.Pill,
                                    color = Warning.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        text = "Update available",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Warning,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = KateShape.Pill,
                                    color = Success.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        text = "✅ Latest",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Success,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (stats.models.isOutdated) {
                        KateButton(
                            text = "Update",
                            onClick = { /* Trigger update */ },
                            type = KateButtonType.Accent,
                            size = KateButtonSize.Small,
                        )
                    }
                }
            }
        }
        
        // Activity chart (simplified)
        if (!activity.isNullOrEmpty()) {
            item {
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
                            text = "📈 Activity (7 days)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            activity.takeLast(7).forEach { item ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    // Bar
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .height((item.conversations / 2f).dp.coerceAtLeast(4.dp))
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(
                                                if (item.conversations > 0) Purple70 else Divider
                                            ),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.date.takeLast(5),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        fontSize = 8.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AdminStatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = KateShape.MD,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = color,
                ),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun ErrorsTab(
    errors: List<AdminErrorItem>?,
    onRefresh: () -> Unit,
) {
    if (errors.isNullOrEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "✅",
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No errors found",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            Text(
                text = "All systems are running smoothly",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(errors) { error ->
                AdminErrorCard(error = error)
            }
        }
    }
}

@Composable
private fun AdminErrorCard(error: AdminErrorItem) {
    val severityColor = when (error.severity) {
        "critical", "fatal" -> Error
        "error" -> Warning
        "warning" -> Color(0xFFFF9800)
        else -> TextSecondary
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.1f),
        ),
        shape = KateShape.MD,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(severityColor),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error.errorCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = severityColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = error.timestamp.take(19).replace("T", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (error.category.isNotEmpty()) {
                    AssistChip(
                        onClick = { },
                        label = { Text(error.category, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = SurfaceVariant,
                            labelColor = TextSecondary,
                        ),
                    )
                }
                if (error.path != null) {
                    AssistChip(
                        onClick = { },
                        label = { Text(error.path, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = SurfaceVariant,
                            labelColor = TextSecondary,
                        ),
                    )
                }
                if (error.userId != null) {
                    AssistChip(
                        onClick = { },
                        label = { Text("👤 ${error.userId.take(8)}", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = SurfaceVariant,
                            labelColor = TextSecondary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun UsersTab(
    users: List<AdminUserItem>?,
    onLoadMore: () -> Unit,
) {
    if (users.isNullOrEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxS

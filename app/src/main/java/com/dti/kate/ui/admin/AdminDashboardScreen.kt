package com.dti.kate.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dti.kate.R
import com.dti.kate.network.models.*
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*

@Composable
fun AdminDashboardScreen(
    navController: NavController,
    viewModel: AdminDashboardViewModel = hiltViewModel(),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_admin_dashboard),
                            contentDescription = null,
                            tint = Purple70,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Admin Dashboard",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            ),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_admin_back),
                            contentDescription = "Back",
                            tint = TextPrimary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_admin_refresh),
                            contentDescription = "Refresh",
                            tint = TextSecondary,
                        )
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_admin_logout),
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
                // Tab row with real icons
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Purple70,
                            height = 3.dp,
                        )
                    },
                ) {
                    listOf(
                        Triple("Overview", Icons.Outlined.Dashboard, 0),
                        Triple("Errors", Icons.Outlined.Error, 1),
                        Triple("Users", Icons.Outlined.People, 2),
                        Triple("System", Icons.Outlined.Settings, 3),
                    ).forEach { (title, icon, index) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { viewModel.selectTab(index) },
                            icon = {
                                Icon(
                                    icon,
                                    contentDescription = title,
                                    tint = if (selectedTab == index) Purple70 else TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
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
                    icon = Icons.Outlined.People,
                    title = "Users",
                    value = stats.users.total.toString(),
                    subtitle = "${stats.users.active24h} active",
                    color = Purple70,
                    modifier = Modifier.weight(1f),
                )
                AdminStatCard(
                    icon = Icons.Outlined.Chat,
                    title = "Conversations",
                    value = stats.conversations.total.toString(),
                    subtitle = "${stats.conversations.today} today",
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
                    icon = Icons.Outlined.Analytics,
                    title = "Accuracy",
                    value = "${stats.conversations.intentAccuracy.toInt()}%",
                    subtitle = "Intent classification",
                    color = LimeAccent,
                    modifier = Modifier.weight(1f),
                )
                AdminStatCard(
                    icon = Icons.Outlined.Star,
                    title = "Premium",
                    value = stats.users.premium.toString(),
                    subtitle = "Active subscribers",
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Payments,
                        contentDescription = null,
                        tint = LimeAccent,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Revenue (30 days)",
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
        }
        
        // Model status
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = KateShape.MD,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Psychology,
                            contentDescription = null,
                            tint = Purple70,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Model",
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
                                            text = "Latest",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Success,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
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
        
        // Activity chart
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.ShowChart,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Activity (7 days)",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            activity.takeLast(7).forEach { item ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    // Bar with real color
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
    icon: ImageVector,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
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
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "No errors",
                tint = Success,
                modifier = Modifier.size(64.dp),
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
            Spacer(modifier = Modifier.height(16.dp))
            KateButton(
                text = "Refresh",
                onClick = onRefresh,
                type = KateButtonType.Secondary,
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${errors.size} errors found",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                KateButton(
                    text = "Refresh",
                    onClick = onRefresh,
                    type = KateButtonType.Ghost,
                    size = KateButtonSize.Small,
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(errors) { error ->
                    AdminErrorCard(error = error)
                }
            }
        }
    }
}

@Composable
private fun AdminErrorCard(error: AdminErrorItem) {
    val severityColor = when (error.severity) {
        "critical", "fatal" -> Error
        "error" -> Color(0xFFFF6B6B)
        "warning" -> Color(0xFFFFB74D)
        else -> TextSecondary
    }
    
    val severityIcon = when (error.severity) {
        "critical", "fatal" -> Icons.Outlined.Error
        "error" -> Icons.Outlined.Warning
        "warning" -> Icons.Outlined.WarningAmber
        else -> Icons.Outlined.Info
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.08f),
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
                    Icon(
                        severityIcon,
                        contentDescription = null,
                        tint = severityColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error.errorCode.replace("_", " "),
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
            
            Spacer(modifier = Modifier.height(4.dp))
            
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
                        label = { 
                            Text(
                                error.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = SurfaceVariant,
                            labelColor = TextSecondary,
                        ),
                    )
                }
                if (error.path != null) {
                    AssistChip(
                        onClick = { },
                        label = { 
                            Text(
                                error.path.take(20),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = SurfaceVariant,
                            labelColor = TextSecondary,
                        ),
                    )
                }
                if (error.userId != null) {
                    AssistChip(
                        onClick = { },
                        icon = {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = TextSecondary,
                            )
                        },
                        label = { 
                            Text(
                                error.userId.take(8),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
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
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.People,
                contentDescription = "No users",
                tint = TextSecondary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No users found",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(users) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = KateShape.MD,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Avatar placeholder
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Purple70.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = (user.fullName ?: user.email).firstOrNull()?.uppercase() ?: "U",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Purple70,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = user.fullName ?: user.email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Surface(
                                        shape = KateShape.Pill,
                                        color = when (user.tier) {
                                            "free" -> SurfaceVariant
                                            "premium" -> Purple70.copy(alpha = 0.2f)
                                            "pro" -> Purple70.copy(alpha = 0.3f)
                                            "lifetime" -> LimeAccent.copy(alpha = 0.2f)
                                            else -> SurfaceVariant
                                        },
                                    ) {
                                        Text(
                                            text = user.tier.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when (user.tier) {
                                                "free" -> TextSecondary
                                                "lifetime" -> LimeAccent
                                                else -> Purple70
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
                                    Text(
                                        text = "${user.usageCount} req",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                    )
                                }
                            }
                        }
                        Text(
                            text = user.createdAt.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
            
            item {
                KateButton(
                    text = "Load More",
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth(),
                    type = KateButtonType.Secondary,
                )
            }
        }
    }
}

@Composable
private fun SystemTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Devices,
                        contentDescription = null,
                        tint = Purple70,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                SystemInfoRow(
                    icon = Icons.Outlined.Info,
                    label = "App Version",
                    value = BuildConfig.VERSION_NAME,
                )
                SystemInfoRow(
                    icon = Icons.Outlined.Public,
                    label = "Environment",
                    value = BuildConfig.BACKEND_URL,
                )
                SystemInfoRow(
                    icon = Icons.Outlined.PhoneAndroid,
                    label = "Device",
                    value = android.os.Build.MODEL,
                )
                SystemInfoRow(
                    icon = Icons.Outlined.Android,
                    label = "Android",
                    value = android.os.Build.VERSION.RELEASE,
                )
                SystemInfoRow(
                    icon = Icons.Outlined.Settings,
                    label = "SDK",
                    value = android.os.Build.VERSION.SDK_INT.toString(),
                )
            }
        }
        
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = Warning,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                KateButton(
                    text = "Clear Error Logs",
                    onClick = { /* Clear errors */ },
                    modifier = Modifier.fillMaxWidth(),
                    type = KateButtonType.Secondary,
                    icon = {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                KateButton(
                    text = "Trigger Model Training",
                    onClick = { /* Trigger training */ },
                    modifier = Modifier.fillMaxWidth(),
                    type = KateButtonType.Secondary,
                    icon = {
                        Icon(
                            Icons.Outlined.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                KateButton(
                    text = "Force Sync All Users",
                    onClick = { /* Force sync */ },
                    modifier = Modifier.fillMaxWidth(),
                    type = KateButtonType.Secondary,
                    icon = {
                        Icon(
                            Icons.Outlined.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SystemInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
        )
    }
}

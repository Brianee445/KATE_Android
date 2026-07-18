package com.dti.kate.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
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

    val tabTitles = listOf("Overview", "Errors", "Activity", "Users")

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Admin Dashboard",
                        style = MaterialTheme.typography.titleLarge.copy(
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
                actions = {
                    IconButton(onClick = {
                        viewModel.logout()
                        navController.navigate("login") {
                            popUpTo(0)
                        }
                    }) {
                        Icon(Icons.Outlined.Logout, contentDescription = "Log out", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Background)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Background,
                contentColor = Purple70,
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }

            if (isLoading && stats == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Purple70)
                }
            } else {
                when (selectedTab) {
                    0 -> OverviewTab(stats)
                    1 -> ErrorsTab(errors)
                    2 -> ActivityTab(activity)
                    3 -> UsersTab(users, onLoadMore = { viewModel.loadMoreUsers() })
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(stats: AdminDashboardStats?) {
    if (stats == null) return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatCard(
                title = "Users",
                lines = listOf(
                    "Total: ${stats.users.total}",
                    "New today: ${stats.users.newToday}",
                    "Active (24h): ${stats.users.active24h}",
                    "Premium: ${stats.users.premium}",
                ),
            )
        }
        item {
            StatCard(
                title = "Conversations",
                lines = listOf(
                    "Total: ${stats.conversations.total}",
                    "Today: ${stats.conversations.today}",
                    "Intent accuracy: ${(stats.conversations.intentAccuracy * 100).toInt()}%",
                ),
            )
        }
        item {
            StatCard(
                title = "Revenue",
                lines = listOf(
                    "Total: $${stats.revenue.totalRevenue}",
                    "This month: $${stats.revenue.monthlyRevenue}",
                    "Growth: ${stats.revenue.growthRate}%",
                ),
            )
        }
        item {
            StatCard(
                title = "Errors",
                lines = listOf(
                    "Total: ${stats.errors.total}",
                    "Critical: ${stats.errors.critical}",
                    "Warnings: ${stats.errors.warnings}",
                    "Last 24h: ${stats.errors.last24h}",
                ),
            )
        }
        item {
            StatCard(
                title = "Model",
                lines = listOf(
                    "Active version: ${stats.models.activeVersion}",
                    "Latest version: ${stats.models.latestVersion}",
                    "Outdated: ${if (stats.models.isOutdated) "Yes" else "No"}",
                ),
            )
        }
    }
}

@Composable
private fun StatCard(title: String, lines: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = KateShape.MD,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ErrorsTab(errors: List<AdminErrorItem>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (errors.isEmpty()) {
            item {
                Text("No errors reported", color = TextSecondary, modifier = Modifier.padding(16.dp))
            }
        }
        items(errors) { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = KateShape.MD,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (error.severity.lowercase()) {
                                "critical" -> Error
                                "warning" -> LimeAccent
                                else -> SurfaceVariant
                            },
                        ) {
                            Text(
                                text = error.severity.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Background,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error.message, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text(
                        text = "${error.category} · ${error.timestamp}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityTab(activity: List<AdminActivityItem>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (activity.isEmpty()) {
            item {
                Text("No activity data", color = TextSecondary, modifier = Modifier.padding(16.dp))
            }
        }
        items(activity) { day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(day.date, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text("${day.conversations} conversations", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun UsersTab(users: List<AdminUserItem>, onLoadMore: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.fullName ?: user.email, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text(user.email, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Text(user.tier.uppercase(), style = MaterialTheme.typography.labelSmall, color = Purple70)
                }
            }
        }
        item {
            if (users.isNotEmpty()) {
                KateTextButton(text = "Load more", onClick = onLoadMore)
            }
        }
    }
}

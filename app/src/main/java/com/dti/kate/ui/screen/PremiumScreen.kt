package com.dti.kate.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*

@Composable
fun PremiumScreen(
    navController: NavController,
    viewModel: PremiumViewModel = viewModel(),
) {
    val selectedTier by viewModel.selectedTier.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val userTier by viewModel.userTier.collectAsState()
    
    val tiers = listOf(
        PremiumTier(
            id = "free",
            name = "Free",
            price = "\$0",
            period = "forever",
            features = listOf(
                "50 cloud requests/month",
                "Offline mode",
                "Basic wake triggers",
            ),
            popular = false,
        ),
        PremiumTier(
            id = "premium",
            name = "Premium",
            price = "\$4.99",
            period = "/month",
            features = listOf(
                "Unlimited cloud requests",
                "All wake triggers",
                "Faster model updates",
                "Priority support",
            ),
            popular = true,
        ),
        PremiumTier(
            id = "pro",
            name = "Pro",
            price = "\$9.99",
            period = "/month",
            features = listOf(
                "Everything in Premium",
                "Custom wake word",
                "Advanced NLU",
                "Sync across devices",
            ),
            popular = false,
        ),
        PremiumTier(
            id = "lifetime",
            name = "Lifetime",
            price = "\$49.99",
            period = "one-time",
            features = listOf(
                "All Pro features",
                "Lifetime access",
                "Priority support",
                "Early access to new features",
            ),
            popular = false,
        ),
    )
    
    val providers = listOf(
        PaymentProvider("stripe", "💳", "Stripe", "International payments"),
        PaymentProvider("paystack", "🇳🇬", "Paystack", "African payments"),
    )
    
    Scaffold(
        modifier = Modifier.background(Background),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Premium",
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
                currentRoute = "premium",
                onItemClick = { route ->
                    when (route) {
                        "home" -> navController.navigate("home") { popUpTo("premium") { inclusive = true } }
                        "history" -> navController.navigate("history")
                        "premium" -> { /* Already here */ }
                        "settings" -> navController.navigate("settings")
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                ) {
                    Text(
                        text = "💎",
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Unlock Kate's Full Potential",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Text(
                        text = "Choose a plan that works for you",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            
            // Features grid
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = KateShape.MD,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = "✨ Features",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FeatureRow("Unlimited Cloud Requests")
                            FeatureRow("All Wake Triggers")
                            FeatureRow("Custom Wake Word")
                            FeatureRow("Priority Support")
                            FeatureRow("Sync Across Devices")
                        }
                    }
                }
            }
            
            // Tier cards
            items(tiers) { tier ->
                TierCard(
                    tier = tier,
                    isSelected = selectedTier == tier.id,
                    isCurrentTier = userTier == tier.id,
                    onSelect = { viewModel.selectTier(tier.id) },
                )
            }
            
            // Payment providers
            item {
                Text(
                    text = "Pay with",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    providers.forEach { provider ->
                        PaymentProviderChip(
                            provider = provider,
                            isSelected = selectedProvider == provider.id,
                            onSelect = { viewModel.selectProvider(provider.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Update the providers list in PremiumScreen.kt

val providers = listOf(
    PaymentProvider(
        id = "flutterwave",
        icon = R.drawable.ic_flutterwave,
        name = "Flutterwave",
        description = "African payments",
    ),
    PaymentProvider(
        id = "stripe",
        icon = R.drawable.ic_stripe,
        name = "Stripe",
        description = "International cards",
    ),
)
                }
            }
            
            // Subscribe button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                
                if (userTier != "free" && userTier == selectedTier) {
                    // Already subscribed
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Success.copy(alpha = 0.1f),
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
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Active",
                                tint = Success,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "You're already on the ${selectedTier.uppercase()} plan!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Success,
                            )
                        }
                    }
                } else if (selectedTier != "free") {
                    KateGradientButton(
                        text = "Subscribe Now — ${tiers.find { it.id == selectedTier }?.price ?: ""}",
                        onClick = {
                            viewModel.startCheckout(
                                tier = selectedTier,
                                provider = selectedProvider,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isLoading = isLoading,
                        isEnabled = !isLoading,
                        size = KateButtonSize.Large,
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "7-day free trial. Cancel anytime.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    KateButton(
                        text = "Free Plan Selected",
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        type = KateButtonType.Secondary,
                        isEnabled = false,
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Check,
            contentDescription = null,
            tint = LimeAccent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )
    }
}

@Composable
private fun TierCard(
    tier: PremiumTier,
    isSelected: Boolean,
    isCurrentTier: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Purple70.copy(alpha = 0.15f) else Surface,
        ),
        shape = KateShape.MD,
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, Purple70)
        } else if (tier.popular) {
            androidx.compose.foundation.BorderStroke(1.dp, LimeAccent.copy(alpha = 0.5f))
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tier.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) Purple70 else TextPrimary,
                        )
                        if (tier.popular) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = KateShape.Pill,
                                color = LimeAccent.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = "Popular",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LimeAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (isCurrentTier) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = KateShape.Pill,
                                color = Success.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = "Current",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Success,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "${tier.price} ${tier.period}",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Purple70 else TextPrimary,
                        ),
                    )
                }
                
                // Radio indicator
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Purple70,
                        unselectedColor = TextSecondary,
                    ),
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            tier.features.forEach { feature ->
                FeatureRow(feature)
            }
        }
    }
}

@Composable
private fun PaymentProviderChip(
    provider: PaymentProvider,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Purple70.copy(alpha = 0.15f) else Surface,
        ),
        shape = KateShape.MD,
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, Purple70)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = provider.icon,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = provider.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) Purple70 else TextPrimary,
            )
            Text(
                text = provider.description,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

data class PremiumTier(
    val id: String,
    val name: String,
    val price: String,
    val period: String,
    val features: List<String>,
    val popular: Boolean,
)

data class PaymentProvider(
    val id: String,
    val icon: String,
    val name: String,
    val description: String,
)

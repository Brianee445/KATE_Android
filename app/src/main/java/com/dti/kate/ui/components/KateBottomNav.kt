package com.dti.kate.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dti.kate.ui.theme.*

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
    val iconFilled: ImageVector? = null,
)

@Composable
fun KateBottomNavigation(
    items: List<BottomNavItem>,
    currentRoute: String,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = Surface,
        tonalElevation = 8.dp,
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (selected && item.iconFilled != null) {
                            item.iconFilled
                        } else {
                            item.icon
                        },
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
                selected = selected,
                onClick = { onItemClick(item.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Purple70,
                    selectedTextColor = Purple70,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = Purple70.copy(alpha = 0.12f),
                ),
            )
        }
    }
}

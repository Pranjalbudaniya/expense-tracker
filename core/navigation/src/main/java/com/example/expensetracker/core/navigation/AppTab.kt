package com.example.expensetracker.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destination for the Budgets placeholder.
 */
@Serializable
data object BudgetsDestination : AppDestination

/**
 * Type-safe navigation destination for the Settings placeholder.
 */
@Serializable
data object SettingsDestination : AppDestination

/**
 * Bottom navigation tabs for the primary application shell.
 *
 * @property label The display name for the tab.
 * @property icon The Material icon representation.
 * @property contentDescription Accessibility text description.
 * @property destination The associated type-safe destination.
 */
enum class AppTab(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
    val destination: Any
) {
    HOME(
        label = "Home",
        icon = Icons.Default.Home,
        contentDescription = "Home tab",
        destination = AppDestination.Home
    ),
    TRANSACTIONS(
        label = "Transactions",
        icon = Icons.AutoMirrored.Filled.List,
        contentDescription = "Transactions tab",
        destination = AppDestination.Transactions
    ),
    STATISTICS(
        label = "Analytics",
        icon = Icons.Default.DateRange,
        contentDescription = "Analytics tab",
        destination = AppDestination.Statistics
    ),
    BUDGETS(
        label = "Budgets",
        icon = Icons.Default.ShoppingCart,
        contentDescription = "Budgets tab",
        destination = BudgetsDestination
    )
}

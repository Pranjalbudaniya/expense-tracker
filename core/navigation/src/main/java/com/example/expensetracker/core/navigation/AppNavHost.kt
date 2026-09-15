package com.example.expensetracker.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.expensetracker.feature.accounts.AccountsScreen
import com.example.expensetracker.feature.addtransaction.AddTransactionScreen
import com.example.expensetracker.feature.budgets.BudgetsScreen
import com.example.expensetracker.feature.categories.CategoriesScreen
import com.example.expensetracker.feature.recurring.RecurringScreen
import com.example.expensetracker.feature.home.HomeScreen
import com.example.expensetracker.feature.settings.SettingsScreen
import com.example.expensetracker.feature.transactions.TransactionDetailsScreen
import com.example.expensetracker.feature.transactions.TransactionsScreen
import com.example.expensetracker.feature.statistics.StatisticsScreen

/**
 * Root navigation host for the application.
 *
 * @param navController The [NavHostController] managing app navigation.
 * @param modifier The [Modifier] to apply to the host layout.
 * @param startDestination The start destination, defaulting to [AppDestination.Home].
 * @param destinationBuilder Optional lambda allowing feature modules to contribute destinations.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Any = AppDestination.Home,
    destinationBuilder: (NavGraphBuilder.() -> Unit)? = null
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<AppDestination.Home> {
            HomeScreen(
                onSettingsClick = {
                    navController.navigate(SettingsDestination) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAddTransactionClick = {
                    navController.navigate(AppDestination.AddTransaction)
                },
                onNavigateToTransactions = {
                    navController.navigate(AppDestination.Transactions) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToBudgets = {
                    navController.navigate(BudgetsDestination) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToStatistics = {
                    navController.navigate(AppDestination.Statistics) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToRecurring = {
                    navController.navigate(AppDestination.Recurring)
                },
                onTransactionClick = { txId ->
                    navController.navigate(AppDestination.TransactionDetails(txId))
                }
            )
        }
        composable<AppDestination.AddTransaction> {
            AddTransactionScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<AppDestination.Transactions> {
            TransactionsScreen(
                onNavigateUp = { navController.navigateUp() },
                onTransactionClick = { txId ->
                    navController.navigate(AppDestination.TransactionDetails(txId))
                },
                onNavigateToTrash = {
                    navController.navigate(AppDestination.Trash)
                }
            )
        }
        composable<AppDestination.TransactionDetails> {
            TransactionDetailsScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<BudgetsDestination> {
            BudgetsScreen()
        }

        composable<SettingsDestination> {
            SettingsScreen(
                onNavigateToAccounts = {
                    navController.navigate(AppDestination.Accounts)
                },
                onNavigateToCategories = {
                    navController.navigate(AppDestination.Categories)
                },
                onNavigateToRecurring = {
                    navController.navigate(AppDestination.Recurring)
                },
                onNavigateToExport = {
                    navController.navigate(AppDestination.Export)
                },
                onNavigateToSecurity = {
                    navController.navigate(AppDestination.Security)
                },
                onNavigateToNotifications = {
                    navController.navigate(AppDestination.Notifications)
                },
                onNavigateToBackup = {
                    navController.navigate(AppDestination.Backup)
                }
            )
        }
        composable<AppDestination.Accounts> {
            AccountsScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<AppDestination.Categories> {
            CategoriesScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<AppDestination.Recurring> {
            RecurringScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<AppDestination.Statistics> {
            StatisticsScreen()
        }
        composable<AppDestination.Export> {
            com.example.expensetracker.feature.export.ExportScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<AppDestination.Trash> {
            com.example.expensetracker.feature.transactions.TrashScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<AppDestination.Security> {
            com.example.expensetracker.feature.security.SecurityScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<AppDestination.Notifications> {
            com.example.expensetracker.feature.notifications.NotificationPreferencesScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<AppDestination.Backup> {
            com.example.expensetracker.feature.backup.BackupScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        destinationBuilder?.invoke(this)
    }
}

/**
 * Minimal placeholder screen indicating that a feature is planned but not yet implemented.
 */
@Composable
fun FeaturePlaceholderScreen(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

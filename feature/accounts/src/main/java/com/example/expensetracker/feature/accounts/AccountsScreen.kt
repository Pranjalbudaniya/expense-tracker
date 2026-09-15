package com.example.expensetracker.feature.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.money.Money
import java.util.Locale

/**
 * Stateful entry point for the Accounts feature.
 */
@Composable
fun AccountsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(enabled = uiState.isFormOpen) {
        viewModel.closeForm()
    }

    if (uiState.isFormOpen && uiState.formState != null) {
        AccountFormScreen(
            formState = uiState.formState!!,
            availableCurrencies = uiState.availableCurrencies,
            availableTypes = uiState.availableTypes,
            onNameChange = viewModel::onFormNameChange,
            onTypeChange = viewModel::onFormTypeChange,
            onCurrencyChange = viewModel::onFormCurrencyChange,
            onInitialBalanceChange = viewModel::onFormInitialBalanceChange,
            onArchivedChange = viewModel::onFormArchivedChange,
            onSave = viewModel::saveAccount,
            onDismiss = viewModel::closeForm,
            modifier = modifier
        )
    } else {
        AccountsListContent(
            uiState = uiState,
            onNavigateBack = onNavigateBack,
            onAddAccount = { viewModel.openCreateForm() },
            onEditAccount = viewModel::openEditForm,
            onRequestArchive = viewModel::requestArchiveAccount,
            onRequestUnarchive = viewModel::requestUnarchiveAccount,
            onRequestDelete = viewModel::requestDeleteAccount,
            onToggleShowArchived = viewModel::toggleShowArchived,
            modifier = modifier
        )
    }

    // Archive Confirmation Dialog
    uiState.archiveConfirmationAccount?.let { account ->
        AlertDialog(
            onDismissRequest = viewModel::dismissArchiveDialog,
            title = { Text("Archive Account") },
            text = {
                Text("Archiving \"${account.name}\" hides it when recording new transactions. All historical transactions will remain completely preserved.")
            },
            confirmButton = {
                Button(onClick = viewModel::confirmArchiveAccount) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissArchiveDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Unarchive Confirmation Dialog
    uiState.unarchiveConfirmationAccount?.let { account ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUnarchiveDialog,
            title = { Text("Unarchive Account") },
            text = {
                Text("Restore \"${account.name}\" so it can be selected for new transactions again?")
            },
            confirmButton = {
                Button(onClick = viewModel::confirmUnarchiveAccount) {
                    Text("Unarchive")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissUnarchiveDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    uiState.deleteConfirmationAccount?.let { account ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = { Text("Delete Account") },
            text = {
                Text("Are you sure you want to permanently delete \"${account.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDeleteAccount,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Main content layout for displaying accounts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsListContent(
    uiState: AccountsUiState,
    onNavigateBack: () -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (Account) -> Unit,
    onRequestArchive: (Account) -> Unit,
    onRequestUnarchive: (Account) -> Unit,
    onRequestDelete: (Account) -> Unit,
    onToggleShowArchived: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Accounts",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAccount,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Account"
                )
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.activeAccounts.isEmpty() && uiState.archivedAccounts.isEmpty()) {
            EmptyAccountsState(
                onAddAccount = onAddAccount,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Payment Sources & Balances",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Track bank accounts, cards, UPI, cash, and digital wallets. Balances update in real time with your transactions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Active Accounts Section
                item {
                    Text(
                        text = "Active Accounts (${uiState.activeAccounts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (uiState.activeAccounts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No active accounts. Tap + to add one.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.activeAccounts,
                        key = { it.account.id.value }
                    ) { item ->
                        AccountCard(
                            item = item,
                            onEdit = { onEditAccount(item.account) },
                            onArchive = { onRequestArchive(item.account) },
                            onDelete = { onRequestDelete(item.account) }
                        )
                    }
                }

                // Archived Accounts Section
                if (uiState.archivedAccounts.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleShowArchived() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Archived Accounts (${uiState.archivedAccounts.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (uiState.showArchived) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (uiState.showArchived) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (uiState.showArchived) {
                        items(
                            items = uiState.archivedAccounts,
                            key = { it.account.id.value }
                        ) { item ->
                            AccountCard(
                                item = item,
                                onEdit = { onEditAccount(item.account) },
                                onUnarchive = { onRequestUnarchive(item.account) },
                                onDelete = { onRequestDelete(item.account) }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

/**
 * Card displaying an individual account with its live calculated balance.
 */
@Composable
fun AccountCard(
    item: AccountItemUiState,
    onEdit: () -> Unit,
    onArchive: (() -> Unit)? = null,
    onUnarchive: (() -> Unit)? = null,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isArchived) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isArchived) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top row: Name, Type Badge, and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.account.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isArchived) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = item.account.type.toDisplayName(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                        if (item.isArchived) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "ARCHIVED",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Account",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (onArchive != null) {
                        IconButton(onClick = onArchive) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Archive Account",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (onUnarchive != null) {
                        IconButton(onClick = onUnarchive) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Unarchive Account",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Bottom row: Live Balance & Opening Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Current Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatMoney(item.calculatedBalance),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (item.calculatedBalance.isNegative) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Opening Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatMoney(item.account.initialBalance),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Empty state shown when no accounts exist.
 */
@Composable
fun EmptyAccountsState(
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Accounts Found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Create accounts for your cash, bank accounts, UPI, and credit cards to track balances accurately.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onAddAccount) {
            Text("Create Account")
        }
    }
}

/**
 * Formats a [Money] object with its currency symbol and grouped decimals.
 */
fun formatMoney(money: Money): String {
    val symbol = money.currency.symbol
    val isNegative = money.isNegative
    val absAmount = money.amount.abs()
    val formatted = "%,.2f".format(Locale.US, absAmount)
    return if (isNegative) "-$symbol$formatted" else "$symbol$formatted"
}

package com.example.expensetracker.feature.recurring

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.recurring.RecurringTransaction
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy")

/**
 * Stateful entry point for the Recurring Transactions feature.
 */
@Composable
fun RecurringScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(enabled = uiState.isFormOpen) {
        viewModel.closeForm()
    }

    if (uiState.isFormOpen && uiState.formState != null) {
        RecurringFormScreen(
            formState = uiState.formState!!,
            accounts = uiState.accounts,
            categories = uiState.categories,
            currencies = uiState.currencies,
            onAmountChange = viewModel::onFormAmountChange,
            onCurrencyChange = viewModel::onFormCurrencyChange,
            onTypeChange = viewModel::onFormTypeChange,
            onSourceAccountChange = viewModel::onFormSourceAccountChange,
            onDestinationAccountChange = viewModel::onFormDestinationAccountChange,
            onCategoryChange = viewModel::onFormCategoryChange,
            onNoteChange = viewModel::onFormNoteChange,
            onFrequencyChange = viewModel::onFormFrequencyChange,
            onStartDateChange = viewModel::onFormStartDateChange,
            onNextOccurrenceChange = viewModel::onFormNextOccurrenceChange,
            onHasEndDateChange = viewModel::onFormHasEndDateChange,
            onEndDateChange = viewModel::onFormEndDateChange,
            onEnabledChange = viewModel::onFormEnabledChange,
            onSave = viewModel::saveRecurring,
            onDismiss = viewModel::closeForm,
            modifier = modifier
        )
    } else {
        RecurringListContent(
            uiState = uiState,
            onNavigateBack = onNavigateBack,
            onSelectTab = viewModel::selectTab,
            onAddRecurring = viewModel::openCreateForm,
            onEditRecurring = viewModel::openEditForm,
            onToggleEnabled = viewModel::toggleEnabled,
            onRequestDelete = viewModel::requestDelete,
            modifier = modifier
        )
    }

    // Delete Confirmation Dialog
    uiState.recurringToDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = { Text("Delete Recurring Transaction") },
            text = {
                Text(
                    "Delete this recurring ${rec.type.name.lowercase()}? Existing transactions already generated will remain intact."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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
 * Main content layout for displaying recurring transaction lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringListContent(
    uiState: RecurringUiState,
    onNavigateBack: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onAddRecurring: () -> Unit,
    onEditRecurring: (RecurringTransaction) -> Unit,
    onToggleEnabled: (RecurringTransaction) -> Unit,
    onRequestDelete: (RecurringTransaction) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Recurring Transactions",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                onClick = onAddRecurring,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Recurring"
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab Row for Active vs Paused/Completed
                TabRow(
                    selectedTabIndex = uiState.selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = uiState.selectedTab == 0,
                        onClick = { onSelectTab(0) },
                        text = { Text("Active (${uiState.activeItems.size})") }
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = { onSelectTab(1) },
                        text = { Text("Paused / Ended (${uiState.pausedItems.size})") }
                    )
                }

                val currentList = if (uiState.selectedTab == 0) uiState.activeItems else uiState.pausedItems

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentList.isEmpty()) {
                        item {
                            EmptyRecurringState(
                                isPausedTab = uiState.selectedTab == 1,
                                onAdd = onAddRecurring,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp)
                            )
                        }
                    } else {
                        items(
                            items = currentList,
                            key = { it.recurring.id.value }
                        ) { itemUi ->
                            RecurringCard(
                                itemUi = itemUi,
                                onEdit = { onEditRecurring(itemUi.recurring) },
                                onToggleEnabled = { onToggleEnabled(itemUi.recurring) },
                                onDelete = { onRequestDelete(itemUi.recurring) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card presentation for a single recurring transaction definition.
 */
@Composable
fun RecurringCard(
    itemUi: RecurringItemUiState,
    onEdit: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rec = itemUi.recurring

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rec.isEnabled && !itemUi.isCompleted) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (rec.isEnabled) 1.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Badges & Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (rec.type) {
                            TransactionType.EXPENSE -> MaterialTheme.colorScheme.errorContainer
                            TransactionType.INCOME -> MaterialTheme.colorScheme.primaryContainer
                            TransactionType.TRANSFER -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = rec.type.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (rec.type) {
                                TransactionType.EXPENSE -> MaterialTheme.colorScheme.onErrorContainer
                                TransactionType.INCOME -> MaterialTheme.colorScheme.onPrimaryContainer
                                TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }

                    // Frequency Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = rec.frequency.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Due Badge
                    if (itemUi.isDue) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = "DUE",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }

                    // Completed Badge
                    if (itemUi.isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        ) {
                            Text(
                                text = "ENDED",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Enable/Disable switch
                Switch(
                    checked = rec.isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            // Middle: Amount & Category/Accounts Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatMoney(rec.amount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (rec.type) {
                            TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                            TransactionType.INCOME -> MaterialTheme.colorScheme.primary
                            TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    if (rec.type == TransactionType.TRANSFER) {
                        Text(
                            text = "${itemUi.sourceAccountName} → ${itemUi.destinationAccountName ?: "Unknown"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val catLabel = itemUi.categoryName?.let { "$it • " } ?: ""
                        Text(
                            text = "$catLabel${itemUi.sourceAccountName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (rec.note.isNotBlank()) {
                        Text(
                            text = rec.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Bottom Row: Next occurrence, End Date, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Next: ${rec.nextOccurrence.format(DATE_FORMATTER)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (itemUi.isDue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    rec.endDate?.let { end ->
                        Text(
                            text = "Ends: ${end.format(DATE_FORMATTER)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Recurring",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Recurring",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty state for recurring transactions list.
 */
@Composable
fun EmptyRecurringState(
    isPausedTab: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isPausedTab) "No Paused Recurring Transactions" else "No Active Recurring Transactions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isPausedTab) {
                "Recurring transactions that are disabled or have completed their end date will appear here."
            } else {
                "Set up recurring subscriptions, bills, salaries, or regular savings transfers."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!isPausedTab) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAdd) {
                Text("Create Recurring Transaction")
            }
        }
    }
}

/**
 * Formats a [Money] value into a currency string without Float/Double conversions.
 */
private fun formatMoney(money: Money): String {
    val symbol = money.currency.symbol
    val formatted = "%,.2f".format(Locale.US, money.amount)
    return "$symbol$formatted"
}

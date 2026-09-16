package com.example.expensetracker.feature.budgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.expensetracker.core.model.budget.Budget
import java.time.format.DateTimeFormatter

/**
 * Stateful entry point for the Budgets feature.
 */
@Composable
fun BudgetsScreen(
    modifier: Modifier = Modifier,
    viewModel: BudgetsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val undoBudget by viewModel.undoDeleteBudgetEvent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(undoBudget) {
        undoBudget?.let { budget ->
            val result = snackbarHostState.showSnackbar(
                message = "Budget '${budget.name}' deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDeleteBudget(budget)
            } else {
                viewModel.clearUndoDeleteBudgetEvent()
            }
        }
    }

    BackHandler(enabled = uiState.isFormOpen) {
        viewModel.closeForm()
    }

    if (uiState.isFormOpen && uiState.formState != null) {
        BudgetFormScreen(
            formState = uiState.formState!!,
            availableCategories = uiState.availableCategories,
            availableCurrencies = uiState.availableCurrencies,
            onNameChange = viewModel::onFormNameChange,
            onAmountChange = viewModel::onFormAmountChange,
            onCurrencyChange = viewModel::onFormCurrencyChange,
            onTypeChange = viewModel::onFormTypeChange,
            onCategoryChange = viewModel::onFormCategoryChange,
            onStartDateChange = viewModel::onFormStartDateChange,
            onEndDateChange = viewModel::onFormEndDateChange,
            onEnabledChange = viewModel::onFormEnabledChange,
            onSave = viewModel::saveBudget,
            onDismiss = viewModel::closeForm,
            modifier = modifier
        )
    } else {
        BudgetsListContent(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            onAddBudgetClick = { viewModel.openCreateForm() },
            onEditBudgetClick = viewModel::openEditForm,
            onDeleteBudgetClick = viewModel::showDeleteConfirmation,
            onToggleEnabled = viewModel::toggleBudgetEnabled,
            modifier = modifier
        )
    }

    // Delete confirmation dialog
    uiState.deleteConfirmationBudget?.let { budget ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirmation,
            title = { Text("Delete Budget?") },
            text = { Text("Are you sure you want to delete '${budget.name}'?") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteBudget) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirmation) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Stateless list view of all created budgets with empty-state and progress summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsListContent(
    uiState: BudgetsUiState,
    snackbarHostState: SnackbarHostState,
    onAddBudgetClick: () -> Unit,
    onEditBudgetClick: (Budget) -> Unit,
    onDeleteBudgetClick: (Budget) -> Unit,
    onToggleEnabled: (Budget) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 86.dp)
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Budgets",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading && uiState.budgets.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onAddBudgetClick,
                    modifier = Modifier.padding(bottom = 76.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create new budget")
                }
            }
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.budgets.isEmpty() -> {
                EmptyBudgetsState(
                    onAddBudgetClick = onAddBudgetClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.budgets, key = { it.budget.id.value }) { item ->
                        BudgetItemCard(
                            item = item,
                            onEditClick = { onEditBudgetClick(item.budget) },
                            onDeleteClick = { onDeleteBudgetClick(item.budget) },
                            onToggleEnabled = { onToggleEnabled(item.budget) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty state informing the user that budgeting is completely optional and easy to set up.
 */
@Composable
private fun EmptyBudgetsState(
    onAddBudgetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "Track Your Goals with Budgets",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Budgeting is completely optional. Create overall spending caps or target specific categories like Groceries or Entertainment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onAddBudgetClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Create Budget")
            }
        }
    }
}

/**
 * Individual budget card showing title, scope, date period, progress bar, amounts, and overspending.
 */
@Composable
private fun BudgetItemCard(
    item: BudgetProgressItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd") }
    val periodText = "${item.budget.startDate.format(dateFormatter)} - ${item.budget.endDate.format(dateFormatter)}"

    val isOverspent = item.isOverspent
    val isEnabled = item.budget.isEnabled

    val cardAlpha = if (isEnabled) 1.0f else 0.6f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverspent && isEnabled) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f * cardAlpha)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Scope Chip + Title + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = item.categoryName ?: "Overall",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                        Text(
                            text = periodText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = item.budget.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit budget ${item.budget.name}",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete budget ${item.budget.name}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Progress Bar
            LinearProgressIndicator(
                progress = { item.progressClampedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isOverspent) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            // Spending and Limit summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Spent: ${item.spent.currency.symbol}${item.spent.amount}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Limit: ${item.target.currency.symbol}${item.target.amount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (isOverspent) {
                        val overspentAmount = item.spent.amount.subtract(item.target.amount)
                        Text(
                            text = "Over by ${item.target.currency.symbol}$overspentAmount",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "${item.progressPercentage}% spent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Left: ${item.remaining.currency.symbol}${item.remaining.amount}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${item.progressPercentage}% spent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Multi-currency notice if transactions in other currencies exist
            if (item.excludedDifferentCurrencyCount > 0) {
                Text(
                    text = "Note: ${item.excludedDifferentCurrencyCount} transaction(s) in other currencies were excluded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Enabled / Disabled status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnabled) "Status: Active" else "Status: Inactive (paused)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.size(width = 44.dp, height = 24.dp)
                )
            }
        }
    }
}

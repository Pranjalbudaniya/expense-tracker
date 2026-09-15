package com.example.expensetracker.feature.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Stateful entry point for the Transaction Details and Editing screen.
 */
@Composable
fun TransactionDetailsScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isPermanentlyDeleted) {
        if (uiState.isPermanentlyDeleted) {
            onNavigateUp()
        }
    }

    var prevDeleted by remember { mutableStateOf(uiState.isDeleted) }
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted && !prevDeleted) {
            val result = snackbarHostState.showSnackbar(
                message = "Transaction moved to trash",
                actionLabel = "Undo",
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.restoreFromTrash()
            }
        }
        prevDeleted = uiState.isDeleted
    }

    LaunchedEffect(uiState.generalError) {
        uiState.generalError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearGeneralError()
        }
    }

    // Intercept hardware back button when in edit mode with unsaved changes
    BackHandler(enabled = uiState.isEditing) {
        viewModel.cancelEditing()
    }

    TransactionDetailsContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onStartEditing = viewModel::startEditing,
        onCancelEditing = viewModel::cancelEditing,
        onConfirmDiscard = viewModel::confirmDiscard,
        onDismissDiscard = viewModel::dismissDiscardDialog,
        onSaveChanges = viewModel::saveChanges,
        onAmountChanged = viewModel::onAmountChanged,
        onCategorySelected = viewModel::onCategorySelected,
        onSourceAccountSelected = viewModel::onSourceAccountSelected,
        onDestinationAccountSelected = viewModel::onDestinationAccountSelected,
        onTimestampChanged = viewModel::onTimestampChanged,
        onNoteChanged = viewModel::onNoteChanged,
        onMoveToTrash = viewModel::moveToTrash,
        onRestoreFromTrash = viewModel::restoreFromTrash,
        onDeletePermanently = viewModel::deletePermanently,
        onSetShowDeleteConfirmation = viewModel::setShowDeleteConfirmation,
        modifier = modifier
    )
}

/**
 * Stateless UI content for Transaction Details and Editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsContent(
    uiState: TransactionDetailsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onSaveChanges: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onCategorySelected: (EntityId?) -> Unit,
    onSourceAccountSelected: (EntityId?) -> Unit,
    onDestinationAccountSelected: (EntityId?) -> Unit,
    onTimestampChanged: (Instant) -> Unit,
    onNoteChanged: (String) -> Unit,
    onMoveToTrash: () -> Unit,
    onRestoreFromTrash: () -> Unit,
    onDeletePermanently: () -> Unit,
    onSetShowDeleteConfirmation: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditing) "Edit Transaction" else "Transaction Details",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isEditing) {
                            onCancelEditing()
                        } else {
                            onNavigateUp()
                        }
                    }) {
                        Icon(
                            imageVector = if (uiState.isEditing) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (uiState.isEditing) "Cancel Edit" else "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(
                            onClick = onSaveChanges,
                            enabled = !uiState.isSaving
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save Changes",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (uiState.transaction != null) {
                        // Edit button (only if not in trash)
                        if (!uiState.isDeleted) {
                            IconButton(onClick = onStartEditing) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Transaction"
                                )
                            }
                        }

                        // Trash / Delete button
                        if (uiState.isDeleted) {
                            IconButton(onClick = { onSetShowDeleteConfirmation(true) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Permanently",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            IconButton(onClick = onMoveToTrash) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Move to Trash"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                uiState.transactionNotFound -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Transaction Not Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "This transaction may have been permanently deleted or does not exist.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(onClick = onNavigateUp) {
                            Text("Return to Transactions")
                        }
                    }
                }

                uiState.isEditing -> {
                    // Editing Form
                    EditingContent(
                        uiState = uiState,
                        onAmountChanged = onAmountChanged,
                        onCategorySelected = onCategorySelected,
                        onSourceAccountSelected = onSourceAccountSelected,
                        onDestinationAccountSelected = onDestinationAccountSelected,
                        onOpenDatePicker = { showDatePicker = true },
                        onNoteChanged = onNoteChanged,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    // Read-only Details View
                    DetailsViewContent(
                        uiState = uiState,
                        onRestoreFromTrash = onRestoreFromTrash,
                        onDeletePermanently = { onSetShowDeleteConfirmation(true) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.editedTimestamp.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onTimestampChanged(Instant.ofEpochMilli(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Discard Unsaved Changes Confirmation Dialog
    if (uiState.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text("Discard Unsaved Changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                Button(
                    onClick = onConfirmDiscard,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscard) {
                    Text("Keep Editing")
                }
            }
        )
    }

    // Permanent Delete Confirmation Dialog
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { onSetShowDeleteConfirmation(false) },
            title = { Text("Delete Permanently?") },
            text = { Text("This will permanently remove this transaction from the database. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = onDeletePermanently,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { onSetShowDeleteConfirmation(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Details view displaying all transaction attributes in Material 3 cards.
 */
@Composable
private fun DetailsViewContent(
    uiState: TransactionDetailsUiState,
    onRestoreFromTrash: () -> Unit,
    onDeletePermanently: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tx = uiState.transaction ?: return
    val scrollState = rememberScrollState()

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.getDefault())
    }
    val formattedDateTime = tx.timestamp.atZone(ZoneId.systemDefault()).format(timeFormatter)

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Trash status banner if deleted
        if (uiState.isDeleted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "This transaction is in the trash",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "It is excluded from active balances and reports.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onRestoreFromTrash) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restore",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDeletePermanently) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Permanently",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // Amount & Type Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (tx.type) {
                    TransactionType.EXPENSE -> MaterialTheme.colorScheme.errorContainer
                    TransactionType.INCOME -> MaterialTheme.colorScheme.primaryContainer
                    TransactionType.TRANSFER -> MaterialTheme.colorScheme.tertiaryContainer
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val typeName = tx.type.name.lowercase().replaceFirstChar { it.uppercase() }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = typeName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                val prefix = when (tx.type) {
                    TransactionType.EXPENSE -> "- "
                    TransactionType.INCOME -> "+ "
                    TransactionType.TRANSFER -> "⇄ "
                }

                Text(
                    text = "$prefix${tx.amount.currency.symbol} ${tx.amount.amount.toPlainString()}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Details Information List
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category (for Expense & Income)
                if (tx.type != TransactionType.TRANSFER) {
                    val catName = uiState.category?.name ?: "Uncategorized"
                    val isArchived = uiState.category?.isArchived == true
                    DetailItemRow(
                        label = "Category",
                        value = if (isArchived) "$catName (Archived)" else catName
                    )
                }

                // Account(s)
                if (tx.type == TransactionType.TRANSFER) {
                    val fromName = uiState.sourceAccount?.name ?: "Unknown"
                    val toName = uiState.destinationAccount?.name ?: "Unknown"
                    val fromArchived = uiState.sourceAccount?.isArchived == true
                    val toArchived = uiState.destinationAccount?.isArchived == true

                    DetailItemRow(
                        label = "From Account",
                        value = if (fromArchived) "$fromName (Archived)" else fromName
                    )
                    DetailItemRow(
                        label = "To Account",
                        value = if (toArchived) "$toName (Archived)" else toName
                    )
                } else {
                    val accName = uiState.sourceAccount?.name ?: "Unknown"
                    val isArchived = uiState.sourceAccount?.isArchived == true
                    DetailItemRow(
                        label = if (tx.type == TransactionType.INCOME) "Receiving Account" else "Account",
                        value = if (isArchived) "$accName (Archived)" else accName
                    )
                }

                // Date & Time
                DetailItemRow(
                    label = "Date & Time",
                    value = formattedDateTime
                )

                // Note
                DetailItemRow(
                    label = "Note",
                    value = if (tx.note.isNotBlank()) tx.note else "No note added"
                )

                // Recurring reference if present
                tx.recurringTransactionId?.let { recId ->
                    DetailItemRow(
                        label = "Recurring Transaction",
                        value = "Scheduled series #${recId.value.take(8)}"
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailItemRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Interactive editing form for transactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditingContent(
    uiState: TransactionDetailsUiState,
    onAmountChanged: (String) -> Unit,
    onCategorySelected: (EntityId?) -> Unit,
    onSourceAccountSelected: (EntityId?) -> Unit,
    onDestinationAccountSelected: (EntityId?) -> Unit,
    onOpenDatePicker: () -> Unit,
    onNoteChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tx = uiState.transaction ?: return
    val scrollState = rememberScrollState()

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
    }
    val formattedDate = uiState.editedTimestamp.atZone(ZoneId.systemDefault()).format(dateFormatter)

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Amount input
        OutlinedTextField(
            value = uiState.editedAmountInput,
            onValueChange = onAmountChanged,
            label = { Text("Amount") },
            prefix = { Text("${uiState.currency.symbol} ") },
            isError = uiState.amountError != null,
            supportingText = uiState.amountError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Category dropdown (for Expense & Income)
        if (tx.type != TransactionType.TRANSFER) {
            var categoryExpanded by remember { mutableStateOf(false) }
            val selectedCategory = uiState.availableCategories.find { it.id == uiState.editedCategoryId }

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "Select Category",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Uncategorized") },
                        onClick = {
                            onCategorySelected(null)
                            categoryExpanded = false
                        }
                    )
                    uiState.availableCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(if (category.isArchived) "${category.name} (Archived)" else category.name) },
                            onClick = {
                                onCategorySelected(category.id)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Account selectors
        if (tx.type == TransactionType.TRANSFER) {
            // Source Account
            var sourceExpanded by remember { mutableStateOf(false) }
            val selectedSource = uiState.availableAccounts.find { it.id == uiState.editedSourceAccountId }

            ExposedDropdownMenuBox(
                expanded = sourceExpanded,
                onExpandedChange = { sourceExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedSource?.name ?: "Select Source Account",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("From Account") },
                    isError = uiState.accountError != null,
                    supportingText = uiState.accountError?.let { { Text(it) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = sourceExpanded,
                    onDismissRequest = { sourceExpanded = false }
                ) {
                    uiState.availableAccounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(if (account.isArchived) "${account.name} (Archived)" else account.name) },
                            onClick = {
                                onSourceAccountSelected(account.id)
                                sourceExpanded = false
                            }
                        )
                    }
                }
            }

            // Destination Account
            var destExpanded by remember { mutableStateOf(false) }
            val selectedDest = uiState.availableAccounts.find { it.id == uiState.editedDestinationAccountId }

            ExposedDropdownMenuBox(
                expanded = destExpanded,
                onExpandedChange = { destExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedDest?.name ?: "Select Destination Account",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("To Account") },
                    isError = uiState.destinationAccountError != null,
                    supportingText = uiState.destinationAccountError?.let { { Text(it) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = destExpanded,
                    onDismissRequest = { destExpanded = false }
                ) {
                    uiState.availableAccounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(if (account.isArchived) "${account.name} (Archived)" else account.name) },
                            onClick = {
                                onDestinationAccountSelected(account.id)
                                destExpanded = false
                            }
                        )
                    }
                }
            }
        } else {
            // Single Account (Expense or Income)
            var accountExpanded by remember { mutableStateOf(false) }
            val selectedAccount = uiState.availableAccounts.find { it.id == uiState.editedSourceAccountId }

            ExposedDropdownMenuBox(
                expanded = accountExpanded,
                onExpandedChange = { accountExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedAccount?.name ?: "Select Account",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (tx.type == TransactionType.INCOME) "Receiving Account" else "Account") },
                    isError = uiState.accountError != null,
                    supportingText = uiState.accountError?.let { { Text(it) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = accountExpanded,
                    onDismissRequest = { accountExpanded = false }
                ) {
                    uiState.availableAccounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(if (account.isArchived) "${account.name} (Archived)" else account.name) },
                            onClick = {
                                onSourceAccountSelected(account.id)
                                accountExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Date selection
        OutlinedTextField(
            value = formattedDate,
            onValueChange = {},
            readOnly = true,
            label = { Text("Date") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDatePicker() },
            enabled = false,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outline
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Note
        OutlinedTextField(
            value = uiState.editedNote,
            onValueChange = onNoteChanged,
            label = { Text("Note") },
            placeholder = { Text("Add note (optional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            maxLines = 3
        )
    }
}

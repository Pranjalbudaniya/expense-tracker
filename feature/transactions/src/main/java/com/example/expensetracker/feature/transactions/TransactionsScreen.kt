package com.example.expensetracker.feature.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Stateful entry point for the Transactions screen.
 */
@Composable
fun TransactionsScreen(
    onNavigateUp: () -> Unit,
    onTransactionClick: (String) -> Unit = {},
    onNavigateToTrash: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val undoEvent by viewModel.undoSnackbarEvent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(undoEvent) {
        undoEvent?.let { deletedId ->
            val result = snackbarHostState.showSnackbar(
                message = "Transaction moved to trash",
                actionLabel = "Undo",
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.undoMoveToTrash(deletedId)
            } else {
                viewModel.clearUndoSnackbarEvent()
            }
        }
    }

    TransactionsScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onTransactionClick = onTransactionClick,
        onNavigateToTrash = onNavigateToTrash,
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        onSearchActiveChange = viewModel::onSearchActiveChanged,
        onFilterChange = viewModel::onFilterChanged,
        onClearFilters = viewModel::onClearFilters,
        onSortChange = viewModel::onSortChanged,
        onToggleTrashView = viewModel::onToggleTrashView,
        onMoveToTrash = viewModel::moveToTrash,
        onRestoreFromTrash = viewModel::restoreFromTrash,
        onDeletePermanently = viewModel::deletePermanently,
        onClearTrash = viewModel::clearTrash,
        modifier = modifier
    )
}

/**
 * Stateless content for the Transactions screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreenContent(
    uiState: TransactionsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onTransactionClick: (String) -> Unit = {},
    onNavigateToTrash: (() -> Unit)? = null,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onFilterChange: (TransactionFilter) -> Unit,
    onClearFilters: () -> Unit,
    onSortChange: (TransactionSort) -> Unit,
    onToggleTrashView: () -> Unit,
    onMoveToTrash: (EntityId) -> Unit,
    onRestoreFromTrash: (EntityId) -> Unit,
    onDeletePermanently: (EntityId) -> Unit,
    onClearTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showClearTrashDialog by remember { mutableStateOf(false) }
    var transactionToDeletePermanently by remember { mutableStateOf<EntityId?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = if (uiState.isTrashView) "Trash" else "Transactions",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (uiState.isTrashView) {
                                onToggleTrashView()
                            } else {
                                onNavigateUp()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (uiState.isTrashView) "Exit Trash" else "Back"
                            )
                        }
                    },
                    actions = {
                        // Toggle search bar
                        IconButton(onClick = {
                            onSearchActiveChange(!uiState.isSearchActive)
                        }) {
                            Icon(
                                imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (uiState.isSearchActive) "Close Search" else "Search"
                            )
                        }

                        // Filter button (only in active transactions view)
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Filters"
                            )
                        }

                        // Trash toggle / Clear trash action
                        if (uiState.isTrashView) {
                            if (uiState.groupedTransactions.isNotEmpty()) {
                                IconButton(onClick = { showClearTrashDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Empty Trash",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = {
                                if (onNavigateToTrash != null) {
                                    onNavigateToTrash()
                                } else {
                                    onToggleTrashView()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "View Trash"
                                )
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

                // Search field when active
                if (uiState.isSearchActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search note, category, account...") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear text")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Filter & Sort chips row
                FilterAndSortRow(
                    filter = uiState.filter,
                    sort = uiState.sort,
                    categories = uiState.availableCategories,
                    accounts = uiState.availableAccounts,
                    onOpenFilterSheet = { showFilterSheet = true },
                    onSortClick = { showSortMenu = true },
                    onClearFilters = onClearFilters
                )

                // Sort Dropdown Menu anchor
                Box(modifier = Modifier.fillMaxWidth()) {
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        TransactionSort.entries.forEach { sortOption ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(sortOption.label)
                                        if (uiState.sort == sortOption) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSortChange(sortOption)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
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

                uiState.emptyState != null -> {
                    EmptyStateContent(
                        emptyState = uiState.emptyState,
                        searchQuery = uiState.searchQuery,
                        onClearFilters = onClearFilters,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    TransactionsList(
                        groupedTransactions = uiState.groupedTransactions,
                        isTrashView = uiState.isTrashView,
                        onTransactionClick = onTransactionClick,
                        onMoveToTrash = onMoveToTrash,
                        onRestoreFromTrash = onRestoreFromTrash,
                        onDeletePermanently = { id -> transactionToDeletePermanently = id }
                    )
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            currentFilter = uiState.filter,
            categories = uiState.availableCategories,
            accounts = uiState.availableAccounts,
            onApply = { newFilter ->
                onFilterChange(newFilter)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }

    // Permanent Delete Confirmation Dialog
    transactionToDeletePermanently?.let { txId ->
        AlertDialog(
            onDismissRequest = { transactionToDeletePermanently = null },
            title = { Text("Delete Permanently") },
            text = { Text("Are you sure you want to permanently delete this transaction? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePermanently(txId)
                        transactionToDeletePermanently = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDeletePermanently = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Trash Confirmation Dialog
    if (showClearTrashDialog) {
        AlertDialog(
            onDismissRequest = { showClearTrashDialog = false },
            title = { Text("Empty Trash") },
            text = { Text("Are you sure you want to permanently remove all transactions in the trash? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearTrash()
                        showClearTrashDialog = false
                    }
                ) {
                    Text("Empty Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTrashDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Filter and sort horizontal chip strip.
 */
@Composable
private fun FilterAndSortRow(
    filter: TransactionFilter,
    sort: TransactionSort,
    categories: List<Category>,
    accounts: List<Account>,
    onOpenFilterSheet: () -> Unit,
    onSortClick: () -> Unit,
    onClearFilters: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sort Chip
        item {
            FilterChip(
                selected = true,
                onClick = onSortClick,
                label = { Text(sort.label) }
            )
        }

        // Active Type filter indicator
        if (filter.type != null) {
            item {
                FilterChip(
                    selected = true,
                    onClick = onOpenFilterSheet,
                    label = { Text("Type: ${filter.type.name.lowercase().replaceFirstChar { it.uppercase() }}") }
                )
            }
        }

        // Active Category filter indicator
        if (filter.categoryId != null) {
            val catName = categories.find { it.id == filter.categoryId }?.name ?: "Category"
            item {
                FilterChip(
                    selected = true,
                    onClick = onOpenFilterSheet,
                    label = { Text("Category: $catName") }
                )
            }
        }

        // Active Account filter indicator
        if (filter.accountId != null) {
            val accName = accounts.find { it.id == filter.accountId }?.name ?: "Account"
            item {
                FilterChip(
                    selected = true,
                    onClick = onOpenFilterSheet,
                    label = { Text("Account: $accName") }
                )
            }
        }

        // Filter button
        item {
            FilterChip(
                selected = filter.isActive,
                onClick = onOpenFilterSheet,
                label = { Text(if (filter.isActive) "Filters Active" else "Filter") }
            )
        }

        // Clear All Chip
        if (filter.isActive) {
            item {
                TextButton(onClick = onClearFilters) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear all filters",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }
    }
}

/**
 * Grouped transaction list with sticky date headers.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionsList(
    groupedTransactions: List<DateGroupedTransactions>,
    isTrashView: Boolean,
    onTransactionClick: (String) -> Unit,
    onMoveToTrash: (EntityId) -> Unit,
    onRestoreFromTrash: (EntityId) -> Unit,
    onDeletePermanently: (EntityId) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groupedTransactions.forEach { group ->
            stickyHeader(key = "header_${group.date}") {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = group.header,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            items(
                items = group.transactions,
                key = { it.transaction.id.value }
            ) { item ->
                TransactionCard(
                    item = item,
                    isTrashView = isTrashView,
                    onClick = { onTransactionClick(item.transaction.id.value) },
                    onMoveToTrash = { onMoveToTrash(item.transaction.id) },
                    onRestoreFromTrash = { onRestoreFromTrash(item.transaction.id) },
                    onDeletePermanently = { onDeletePermanently(item.transaction.id) }
                )
            }
        }
    }
}

/**
 * Card representing an individual transaction.
 */
@Composable
private fun TransactionCard(
    item: TransactionDisplayItem,
    isTrashView: Boolean,
    onClick: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestoreFromTrash: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    val tx = item.transaction
    val isTransfer = tx.type == TransactionType.TRANSFER

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    }
    val timeString = tx.timestamp.atZone(ZoneId.systemDefault()).format(timeFormatter)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTransfer) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Leading Badge / Icon
            val badgeContainerColor = when (tx.type) {
                TransactionType.EXPENSE -> MaterialTheme.colorScheme.errorContainer
                TransactionType.INCOME -> MaterialTheme.colorScheme.primaryContainer
                TransactionType.TRANSFER -> MaterialTheme.colorScheme.tertiaryContainer
            }
            val badgeContentColor = when (tx.type) {
                TransactionType.EXPENSE -> MaterialTheme.colorScheme.onErrorContainer
                TransactionType.INCOME -> MaterialTheme.colorScheme.onPrimaryContainer
                TransactionType.TRANSFER -> MaterialTheme.colorScheme.onTertiaryContainer
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(badgeContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (tx.type) {
                        TransactionType.EXPENSE -> "−"
                        TransactionType.INCOME -> "+"
                        TransactionType.TRANSFER -> "⇄"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = badgeContentColor
                )
            }

            // Central details column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title / Note / Category
                val title = if (tx.note.isNotBlank()) {
                    tx.note
                } else if (isTransfer) {
                    "Transfer"
                } else {
                    item.category?.name ?: "Uncategorized"
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle: account info & category info
                val subtitle = if (isTransfer) {
                    val from = item.sourceAccount?.name ?: "Unknown"
                    val to = item.destinationAccount?.name ?: "Unknown"
                    "$from → $to • $timeString"
                } else {
                    val catName = item.category?.name ?: "Uncategorized"
                    val accName = item.sourceAccount?.name ?: "Unknown"
                    "$catName • $accName • $timeString"
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Trailing amount column
            Column(
                horizontalAlignment = Alignment.End
            ) {
                val amountPrefix = when (tx.type) {
                    TransactionType.EXPENSE -> "- "
                    TransactionType.INCOME -> "+ "
                    TransactionType.TRANSFER -> ""
                }
                val amountColor = when (tx.type) {
                    TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                    TransactionType.INCOME -> MaterialTheme.colorScheme.primary
                    TransactionType.TRANSFER -> MaterialTheme.colorScheme.tertiary
                }

                Text(
                    text = "$amountPrefix${tx.amount.currency.symbol} ${tx.amount.amount.toPlainString()}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
            }

            // Actions: Move to trash or Restore/Delete
            if (isTrashView) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onRestoreFromTrash,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restore",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeletePermanently,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Permanently",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onMoveToTrash,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Move to Trash",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Presentation of different empty states.
 */
@Composable
private fun EmptyStateContent(
    emptyState: TransactionsEmptyState,
    searchQuery: String,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val (title, subtitle) = when (emptyState) {
            TransactionsEmptyState.NO_TRANSACTIONS ->
                "No Transactions Yet" to "Transactions you create will appear here."

            TransactionsEmptyState.NO_SEARCH_RESULTS ->
                "No Results Found" to "No transactions match '$searchQuery'."

            TransactionsEmptyState.NO_MATCHING_FILTERS ->
                "No Matching Transactions" to "Try adjusting or clearing your active filters."

            TransactionsEmptyState.EMPTY_TRASH ->
                "Trash is Empty" to "Transactions moved to trash will appear here."
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (emptyState == TransactionsEmptyState.NO_MATCHING_FILTERS) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onClearFilters) {
                Text("Clear Filters")
            }
        }
    }
}

/**
 * Filter Bottom Sheet allowing configuration of Type, Category, and Account filters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    currentFilter: TransactionFilter,
    categories: List<Category>,
    accounts: List<Account>,
    onApply: (TransactionFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember { mutableStateOf(currentFilter.type) }
    var selectedCategoryId by remember { mutableStateOf(currentFilter.categoryId) }
    var selectedAccountId by remember { mutableStateOf(currentFilter.accountId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    selectedType = null
                    selectedCategoryId = null
                    selectedAccountId = null
                }) {
                    Text("Reset")
                }
            }

            // Transaction Type filter
            Text(
                text = "Transaction Type",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedType == null,
                        onClick = { selectedType = null },
                        label = { Text("All") }
                    )
                }
                items(TransactionType.entries) { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = if (selectedType == type) null else type },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            // Category filter
            if (categories.isNotEmpty()) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("All Categories") }
                        )
                    }
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = {
                                selectedCategoryId =
                                    if (selectedCategoryId == category.id) null else category.id
                            },
                            label = { Text(category.name) }
                        )
                    }
                }
            }

            // Account filter
            if (accounts.isNotEmpty()) {
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedAccountId == null,
                            onClick = { selectedAccountId = null },
                            label = { Text("All Accounts") }
                        )
                    }
                    items(accounts) { account ->
                        FilterChip(
                            selected = selectedAccountId == account.id,
                            onClick = {
                                selectedAccountId =
                                    if (selectedAccountId == account.id) null else account.id
                            },
                            label = { Text(account.name) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onApply(
                            currentFilter.copy(
                                type = selectedType,
                                categoryId = selectedCategoryId,
                                accountId = selectedAccountId
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

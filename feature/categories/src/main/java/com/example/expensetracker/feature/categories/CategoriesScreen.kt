package com.example.expensetracker.feature.categories

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.category.CategoryType

/**
 * Stateful entry point for Category Management.
 */
@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(enabled = uiState.isFormOpen) {
        viewModel.closeForm()
    }

    if (uiState.isFormOpen && uiState.formState != null) {
        CategoryFormScreen(
            formState = uiState.formState!!,
            onNameChange = viewModel::onFormNameChange,
            onTypeChange = viewModel::onFormTypeChange,
            onIconChange = viewModel::onFormIconChange,
            onColorChange = viewModel::onFormColorChange,
            onArchivedChange = viewModel::onFormArchivedChange,
            onSave = viewModel::saveCategory,
            onDismiss = viewModel::closeForm,
            modifier = modifier
        )
    } else {
        CategoriesListContent(
            uiState = uiState,
            onNavigateBack = onNavigateBack,
            onSelectTab = viewModel::selectTab,
            onAddCategory = { viewModel.openCreateForm(uiState.selectedTab) },
            onEditCategory = viewModel::openEditForm,
            onMoveUp = viewModel::moveCategoryUp,
            onMoveDown = viewModel::moveCategoryDown,
            onRequestArchive = viewModel::requestArchiveCategory,
            onRequestUnarchive = viewModel::requestUnarchiveCategory,
            onRequestDelete = viewModel::requestDeleteCategory,
            onToggleShowArchived = viewModel::toggleShowArchived,
            onClearError = viewModel::clearError,
            modifier = modifier
        )
    }

    // Dialogs
    uiState.categoryToArchive?.let { cat ->
        AlertDialog(
            onDismissRequest = viewModel::dismissArchiveDialog,
            title = { Text("Archive Category") },
            text = {
                Text(
                    "Archive \"${cat.name}\"? It will be hidden when adding new transactions, but historical records and budgets will remain intact."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmArchiveCategory) {
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

    uiState.categoryToUnarchive?.let { cat ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUnarchiveDialog,
            title = { Text("Restore Category") },
            text = {
                Text("Restore \"${cat.name}\" to active categories?")
            },
            confirmButton = {
                Button(onClick = viewModel::confirmUnarchiveCategory) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissUnarchiveDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    uiState.categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = { Text("Delete Category") },
            text = {
                Text("Permanently delete \"${cat.name}\"? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDeleteCategory,
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
 * Main categories list layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesListContent(
    uiState: CategoriesUiState,
    onNavigateBack: () -> Unit,
    onSelectTab: (CategoryType) -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onMoveUp: (com.example.expensetracker.core.model.common.EntityId) -> Unit,
    onMoveDown: (com.example.expensetracker.core.model.common.EntityId) -> Unit,
    onRequestArchive: (Category) -> Unit,
    onRequestUnarchive: (Category) -> Unit,
    onRequestDelete: (Category) -> Unit,
    onToggleShowArchived: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Categories",
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
                onClick = onAddCategory,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Category"
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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
                // Tab Row for Expense vs Income
                TabRow(
                    selectedTabIndex = if (uiState.selectedTab == CategoryType.EXPENSE) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = uiState.selectedTab == CategoryType.EXPENSE,
                        onClick = { onSelectTab(CategoryType.EXPENSE) },
                        text = { Text("Expense (${uiState.expenseCategories.size})") }
                    )
                    Tab(
                        selected = uiState.selectedTab == CategoryType.INCOME,
                        onClick = { onSelectTab(CategoryType.INCOME) },
                        text = { Text("Income (${uiState.incomeCategories.size})") }
                    )
                }

                val currentList = if (uiState.selectedTab == CategoryType.EXPENSE) {
                    uiState.expenseCategories
                } else {
                    uiState.incomeCategories
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentList.isEmpty()) {
                        item {
                            EmptyCategoriesState(
                                tab = uiState.selectedTab,
                                onAddCategory = onAddCategory,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp)
                            )
                        }
                    } else {
                        items(
                            items = currentList,
                            key = { it.category.id.value }
                        ) { itemUi ->
                            CategoryRowCard(
                                itemUi = itemUi,
                                onEdit = { onEditCategory(itemUi.category) },
                                onMoveUp = { onMoveUp(itemUi.category.id) },
                                onMoveDown = { onMoveDown(itemUi.category.id) },
                                onArchive = { onRequestArchive(itemUi.category) }
                            )
                        }
                    }

                    // Archived Categories Accordion / Section
                    if (uiState.archivedCategories.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleShowArchived() },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Archived Categories (${uiState.archivedCategories.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                items = uiState.archivedCategories,
                                key = { "archived_${it.id.value}" }
                            ) { archivedCat ->
                                ArchivedCategoryRowCard(
                                    category = archivedCat,
                                    onRestore = { onRequestUnarchive(archivedCat) },
                                    onDelete = { onRequestDelete(archivedCat) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single card row for an active category.
 */
@Composable
fun CategoryRowCard(
    itemUi: CategoryItemUi,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = itemUi.category
    val color = CategoryVisualCatalog.getColor(category.colorKey)
    val icon = CategoryVisualCatalog.getIcon(category.iconKey)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with color swatch background
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and Badges
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (category.type == CategoryType.BOTH) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "BOTH",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (category.isDefault) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "DEFAULT",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Reorder Up/Down arrows
            IconButton(
                onClick = onMoveUp,
                enabled = !itemUi.isFirst,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move Up",
                    tint = if (!itemUi.isFirst) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant
                )
            }

            IconButton(
                onClick = onMoveDown,
                enabled = !itemUi.isLast,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move Down",
                    tint = if (!itemUi.isLast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant
                )
            }

            // Edit button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Category",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Archive button
            IconButton(
                onClick = onArchive,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Archive Category",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Row card for an archived category with unarchive/restore and delete options.
 */
@Composable
fun ArchivedCategoryRowCard(
    category: Category,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = CategoryVisualCatalog.getColor(category.colorKey)
    val icon = CategoryVisualCatalog.getIcon(category.iconKey)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "ARCHIVED",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (category.isDefault) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "DEFAULT",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Restore / Unarchive Button
            IconButton(
                onClick = onRestore,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restore Category",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Permanent Delete (only available if not default)
            if (!category.isDefault) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Category",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Empty state when no categories exist in the selected tab.
 */
@Composable
fun EmptyCategoriesState(
    tab: CategoryType,
    onAddCategory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No ${tab.name.lowercase().replaceFirstChar { it.uppercase() }} Categories",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create custom categories to organize your ${tab.name.lowercase()} transactions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAddCategory) {
            Text("Create Category")
        }
    }
}

package com.example.expensetracker.feature.export

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportToUri(contentResolver, uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.analyzeCsvUri(contentResolver, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Backup & CSV Transfer",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Export Card
            ExportSection(
                uiState = uiState,
                onFilterSelected = viewModel::setExportFilter,
                onExportClick = {
                    val defaultFileName = "transactions_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.csv"
                    exportLauncher.launch(defaultFileName)
                },
                onDismissMessage = viewModel::dismissExportMessage
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Import Card
            ImportSection(
                uiState = uiState,
                onSelectFileClick = {
                    importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                },
                onModeSelected = viewModel::setImportMode,
                onToggleDetails = viewModel::toggleShowInvalidRowsDetails,
                onExecuteClick = viewModel::requestExecuteImport,
                onCancelClick = viewModel::clearImportAnalysis,
                onDismissMessage = viewModel::dismissImportMessage
            )
        }
    }

    if (uiState.showReplaceConfirmDialog) {
        val count = uiState.importAnalysis?.validTransactions?.size ?: 0
        AlertDialog(
            onDismissRequest = viewModel::dismissReplaceConfirmDialog,
            title = {
                Text("Replace All Transactions?")
            },
            text = {
                Text(
                    "This action will permanently delete all existing transactions and insert $count transactions from the CSV file.\n\n" +
                            "Accounts, categories, and user preferences will NOT be deleted.\n\n" +
                            "This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmAndExecuteImport,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Replace All Transactions")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissReplaceConfirmDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ExportSection(
    uiState: ExportUiState,
    onFilterSelected: (ExportFilter) -> Unit,
    onExportClick: () -> Unit,
    onDismissMessage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "Export Transactions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Export transaction history to standard RFC 4180 CSV format.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Scope:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.exportFilter == ExportFilter.ALL,
                    onClick = { onFilterSelected(ExportFilter.ALL) },
                    label = { Text("All Records") }
                )
                FilterChip(
                    selected = uiState.exportFilter == ExportFilter.ACTIVE_ONLY,
                    onClick = { onFilterSelected(ExportFilter.ACTIVE_ONLY) },
                    label = { Text("Active Only") }
                )
                FilterChip(
                    selected = uiState.exportFilter == ExportFilter.TRASH_ONLY,
                    onClick = { onFilterSelected(ExportFilter.TRASH_ONLY) },
                    label = { Text("Trash Only") }
                )
            }

            Button(
                onClick = onExportClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isExporting
            ) {
                if (uiState.isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exporting...")
                } else {
                    Text("Export to CSV")
                }
            }

            // Success feedback
            if (uiState.exportSuccessMessage != null) {
                StatusBanner(
                    message = uiState.exportSuccessMessage,
                    isError = false,
                    onDismiss = onDismissMessage
                )
            }

            // Error feedback
            if (uiState.exportError != null) {
                StatusBanner(
                    message = uiState.exportError,
                    isError = true,
                    onDismiss = onDismissMessage
                )
            }
        }
    }
}

@Composable
private fun ImportSection(
    uiState: ExportUiState,
    onSelectFileClick: () -> Unit,
    onModeSelected: (ImportMode) -> Unit,
    onToggleDetails: () -> Unit,
    onExecuteClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDismissMessage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "Import Transactions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Select a valid CSV file to inspect and import transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onSelectFileClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isAnalyzingImport && !uiState.isExecutingImport
            ) {
                if (uiState.isAnalyzingImport) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyzing CSV...")
                } else {
                    Text("Select CSV File")
                }
            }

            val analysis = uiState.importAnalysis
            if (analysis != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Import Preview",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total rows processed:", style = MaterialTheme.typography.bodySmall)
                            Text("${analysis.totalRows}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Valid transactions:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${analysis.validTransactions.size}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(" • New transactions:", style = MaterialTheme.typography.bodySmall)
                            Text("${analysis.newTransactionsCount}", style = MaterialTheme.typography.bodySmall)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(" • Updating existing IDs:", style = MaterialTheme.typography.bodySmall)
                            Text("${analysis.updateTransactionsCount}", style = MaterialTheme.typography.bodySmall)
                        }

                        if (analysis.invalidRows.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Invalid / Skipped rows:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${analysis.invalidRows.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            TextButton(
                                onClick = onToggleDetails,
                                modifier = Modifier.align(Alignment.Start)
                            ) {
                                Text(
                                    if (uiState.showInvalidRowsDetails) "Hide error details" else "View ${analysis.invalidRows.size} error(s)"
                                )
                            }

                            AnimatedVisibility(visible = uiState.showInvalidRowsDetails) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (invalidRow in analysis.invalidRows.take(15)) {
                                        Text(
                                            text = "Line ${invalidRow.lineNumber}: ${invalidRow.reason}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    if (analysis.invalidRows.size > 15) {
                                        Text(
                                            text = "...and ${analysis.invalidRows.size - 15} more",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Import Mode Selection
                        Text(
                            text = "Import Strategy:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Column(modifier = Modifier.selectableGroup()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = uiState.importMode == ImportMode.MERGE,
                                        onClick = { onModeSelected(ImportMode.MERGE) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.importMode == ImportMode.MERGE,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Merge Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        "Adds new transactions and updates existing IDs. Preserves all other data.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = uiState.importMode == ImportMode.REPLACE,
                                        onClick = { onModeSelected(ImportMode.REPLACE) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.importMode == ImportMode.REPLACE,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Replace Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        "Wipes current transactions and imports only the CSV rows. Accounts & categories are kept.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (uiState.importMode == ImportMode.REPLACE) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "⚠️ Warning: Replace mode requires confirmation and will erase all existing transactions in the app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onExecuteClick,
                                modifier = Modifier.weight(1f),
                                enabled = analysis.validTransactions.isNotEmpty() && !uiState.isExecutingImport
                            ) {
                                if (uiState.isExecutingImport) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Importing...")
                                } else {
                                    Text("Execute Import (${analysis.validTransactions.size})")
                                }
                            }

                            OutlinedButton(
                                onClick = onCancelClick,
                                enabled = !uiState.isExecutingImport
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            // Success feedback
            if (uiState.importSuccessMessage != null) {
                StatusBanner(
                    message = uiState.importSuccessMessage,
                    isError = false,
                    onDismiss = onDismissMessage
                )
            }

            // Error feedback
            if (uiState.importError != null) {
                StatusBanner(
                    message = uiState.importError,
                    isError = true,
                    onDismiss = onDismissMessage
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isError) Icons.Filled.Warning else Icons.Filled.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

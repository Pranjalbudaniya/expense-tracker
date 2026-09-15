package com.example.expensetracker.feature.recurring

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy")

/**
 * Screen layout for creating or editing a recurring transaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringFormScreen(
    formState: RecurringFormState,
    accounts: List<Account>,
    categories: List<Category>,
    currencies: List<Currency>,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onSourceAccountChange: (EntityId) -> Unit,
    onDestinationAccountChange: (EntityId?) -> Unit,
    onCategoryChange: (EntityId?) -> Unit,
    onNoteChange: (String) -> Unit,
    onFrequencyChange: (RecurrenceFrequency) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onNextOccurrenceChange: (LocalDate) -> Unit,
    onHasEndDateChange: (Boolean) -> Unit,
    onEndDateChange: (LocalDate?) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showNextOccPicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var destMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var currencyMenuExpanded by remember { mutableStateOf(false) }

    val activeAccounts = remember(accounts, formState.sourceAccountId, formState.destinationAccountId) {
        accounts.filter { !it.isArchived || it.id == formState.sourceAccountId || it.id == formState.destinationAccountId }
    }
    val relevantCategories = remember(categories, formState.type, formState.categoryId) {
        categories.filter {
            (!it.isArchived || it.id == formState.categoryId) &&
            (it.type.name == formState.type.name || it.type.name == "BOTH")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (formState.isEditing) "Edit Recurring" else "New Recurring",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSave) {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Type Tabs: Expense / Income / Transfer
            TabRow(
                selectedTabIndex = formState.type.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                TransactionType.entries.forEach { type ->
                    Tab(
                        selected = formState.type == type,
                        onClick = { onTypeChange(type) },
                        text = {
                            Text(
                                text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontWeight = if (formState.type == type) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Amount Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Currency dropdown
                ExposedDropdownMenuBox(
                    expanded = currencyMenuExpanded,
                    onExpandedChange = { currencyMenuExpanded = it },
                    modifier = Modifier.width(110.dp)
                ) {
                    OutlinedTextField(
                        value = "${formState.currency.symbol} ${formState.currency.code}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Currency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyMenuExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = currencyMenuExpanded,
                        onDismissRequest = { currencyMenuExpanded = false }
                    ) {
                        currencies.forEach { currency ->
                            DropdownMenuItem(
                                text = { Text("${currency.symbol} (${currency.code})") },
                                onClick = {
                                    onCurrencyChange(currency)
                                    currencyMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Amount text field
                OutlinedTextField(
                    value = formState.amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Amount") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = formState.amountError != null,
                    supportingText = formState.amountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Frequency Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Recurrence Frequency",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        RecurrenceFrequency.DAILY to "Daily",
                        RecurrenceFrequency.WEEKLY to "Weekly",
                        RecurrenceFrequency.MONTHLY to "Monthly",
                        RecurrenceFrequency.YEARLY to "Yearly"
                    ).forEach { (freq, label) ->
                        FilterChip(
                            selected = formState.frequency == freq,
                            onClick = { onFrequencyChange(freq) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Source Account Dropdown
            val selectedSource = accounts.firstOrNull { it.id == formState.sourceAccountId }
            ExposedDropdownMenuBox(
                expanded = sourceMenuExpanded,
                onExpandedChange = { sourceMenuExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedSource?.name ?: "Select Source Account",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (formState.type == TransactionType.TRANSFER) "From Account" else "Account") },
                    isError = formState.sourceAccountError != null,
                    supportingText = formState.sourceAccountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = sourceMenuExpanded,
                    onDismissRequest = { sourceMenuExpanded = false }
                ) {
                    activeAccounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text("${account.name} (${account.type.name})") },
                            onClick = {
                                onSourceAccountChange(account.id)
                                sourceMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Destination Account Dropdown (only for Transfer)
            if (formState.type == TransactionType.TRANSFER) {
                val selectedDest = accounts.firstOrNull { it.id == formState.destinationAccountId }
                ExposedDropdownMenuBox(
                    expanded = destMenuExpanded,
                    onExpandedChange = { destMenuExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedDest?.name ?: "Select Destination Account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("To Account") },
                        isError = formState.destinationAccountError != null,
                        supportingText = formState.destinationAccountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = destMenuExpanded,
                        onDismissRequest = { destMenuExpanded = false }
                    ) {
                        activeAccounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (${account.type.name})") },
                                onClick = {
                                    onDestinationAccountChange(account.id)
                                    destMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Category Dropdown (for Expense or Income)
            if (formState.type != TransactionType.TRANSFER) {
                val selectedCat = categories.firstOrNull { it.id == formState.categoryId }
                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCat?.name ?: "None (Optional)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                onCategoryChange(null)
                                categoryMenuExpanded = false
                            }
                        )
                        relevantCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    onCategoryChange(category.id)
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Start Date Picker Row
            OutlinedTextField(
                value = formState.startDate.format(DATE_FORMATTER),
                onValueChange = {},
                readOnly = true,
                label = { Text("Start Date") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Pick start date",
                        modifier = Modifier.clickable { showStartDatePicker = true }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showStartDatePicker = true }
            )

            // Next Occurrence Picker Row
            OutlinedTextField(
                value = formState.nextOccurrence.format(DATE_FORMATTER),
                onValueChange = {},
                readOnly = true,
                label = { Text("Next Occurrence") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Pick next occurrence",
                        modifier = Modifier.clickable { showNextOccPicker = true }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showNextOccPicker = true }
            )

            // End Date Option
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "End Date",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Optional end limit for recurrence",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = formState.hasEndDate,
                    onCheckedChange = onHasEndDateChange
                )
            }

            if (formState.hasEndDate && formState.endDate != null) {
                OutlinedTextField(
                    value = formState.endDate.format(DATE_FORMATTER),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("End Date") },
                    isError = formState.dateError != null,
                    supportingText = formState.dateError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Pick end date",
                            modifier = Modifier.clickable { showEndDatePicker = true }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEndDatePicker = true }
                )
            }

            // Note Input
            OutlinedTextField(
                value = formState.note,
                onValueChange = onNoteChange,
                label = { Text("Note (Optional)") },
                placeholder = { Text("e.g., Netflix subscription, Apartment rent") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            // Enabled Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Enable Recurrence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Active recurring transactions generate scheduled entries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = formState.isEnabled,
                    onCheckedChange = onEnabledChange
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (formState.isEditing) "Save Changes" else "Create Recurring Transaction",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Date Picker Dialogs
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formState.startDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            onStartDateChange(selected)
                        }
                        showStartDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showNextOccPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formState.nextOccurrence.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showNextOccPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            onNextOccurrenceChange(selected)
                        }
                        showNextOccPicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showNextOccPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val currentEnd = formState.endDate ?: formState.startDate.plusMonths(6)
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentEnd.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            onEndDateChange(selected)
                        }
                        showEndDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

package com.example.expensetracker.feature.backup

import com.example.expensetracker.core.database.entity.AccountEntity
import com.example.expensetracker.core.database.entity.BudgetEntity
import com.example.expensetracker.core.database.entity.CategoryEntity
import com.example.expensetracker.core.database.entity.RecurringTransactionEntity
import com.example.expensetracker.core.database.entity.TransactionEntity
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.category.CategoryType
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.transaction.TransactionType
import com.example.expensetracker.core.preferences.ThemeMode
import com.example.expensetracker.core.preferences.UserPreferences
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

const val CURRENT_BACKUP_VERSION = 1

@Serializable
data class AppBackup(
    val version: Int = CURRENT_BACKUP_VERSION,
    val exportedAtEpochMillis: Long,
    val appVersionName: String = "1.0.0",
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
    val budgets: List<BackupBudget>,
    val recurringTransactions: List<BackupRecurringTransaction>,
    val preferences: BackupPreferences? = null
)

@Serializable
data class BackupAccount(
    val id: String,
    val name: String,
    val type: String,
    val currencyCode: String,
    val initialBalanceMinor: Long,
    val currentBalanceMinor: Long,
    val isArchived: Boolean = false
)

@Serializable
data class BackupCategory(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val type: String = "EXPENSE",
    val orderIndex: Int = 0
)

@Serializable
data class BackupTransaction(
    val id: String,
    val amountMinor: Long,
    val currencyCode: String,
    val type: String,
    val sourceAccountId: String,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val timestampEpochMillis: Long,
    val note: String = "",
    val recurringTransactionId: String? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class BackupBudget(
    val id: String,
    val name: String,
    val targetAmountMinor: Long,
    val currencyCode: String,
    val categoryId: String? = null,
    val startDateIso: String,
    val endDateIso: String,
    val isEnabled: Boolean = true
)

@Serializable
data class BackupRecurringTransaction(
    val id: String,
    val amountMinor: Long,
    val currencyCode: String,
    val type: String,
    val sourceAccountId: String,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    val frequency: String,
    val nextOccurrenceIso: String,
    val startDateIso: String,
    val endDateIso: String? = null,
    val isEnabled: Boolean = true,
    val lastGeneratedOccurrenceIso: String? = null
)

@Serializable
data class BackupPreferences(
    val themeMode: String? = null,
    val isDynamicColorEnabled: Boolean? = null,
    val customAccentColor: Long? = null,
    val currencyCode: String? = null,
    val notificationsEnabled: Boolean? = null,
    val recurringNotificationsEnabled: Boolean? = null,
    val budgetAlertsEnabled: Boolean? = null,
    val budgetThresholdPercent: Int? = null
)

enum class RestoreMode {
    REPLACE,
    MERGE
}

data class BackupSummary(
    val formatVersion: Int,
    val accountsCount: Int,
    val categoriesCount: Int,
    val transactionsCount: Int,
    val activeTransactionsCount: Int,
    val trashTransactionsCount: Int,
    val budgetsCount: Int,
    val recurringCount: Int,
    val exportedAtEpochMillis: Long,
    val hasPreferences: Boolean
)

sealed interface BackupValidationResult {
    data class Valid(val backup: AppBackup, val summary: BackupSummary) : BackupValidationResult
    data class Invalid(val errors: List<String>) : BackupValidationResult
}

// --- Entity Mappers ---

fun AccountEntity.toBackup() = BackupAccount(
    id = id,
    name = name,
    type = type.name,
    currencyCode = currencyCode,
    initialBalanceMinor = initialBalanceMinor,
    currentBalanceMinor = currentBalanceMinor,
    isArchived = isArchived
)

fun BackupAccount.toEntity() = AccountEntity(
    id = id,
    name = name,
    type = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.BANK),
    currencyCode = currencyCode,
    initialBalanceMinor = initialBalanceMinor,
    currentBalanceMinor = currentBalanceMinor,
    isArchived = isArchived
)

fun CategoryEntity.toBackup() = BackupCategory(
    id = id,
    name = name,
    iconKey = iconKey,
    colorKey = colorKey,
    isDefault = isDefault,
    isArchived = isArchived,
    type = type.name,
    orderIndex = orderIndex
)

fun BackupCategory.toEntity() = CategoryEntity(
    id = id,
    name = name,
    iconKey = iconKey,
    colorKey = colorKey,
    isDefault = isDefault,
    isArchived = isArchived,
    type = runCatching { CategoryType.valueOf(type) }.getOrDefault(CategoryType.EXPENSE),
    orderIndex = orderIndex
)

fun TransactionEntity.toBackup() = BackupTransaction(
    id = id,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    type = type.name,
    sourceAccountId = sourceAccountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    timestampEpochMillis = timestamp.toEpochMilli(),
    note = note,
    recurringTransactionId = recurringTransactionId,
    isDeleted = isDeleted
)

fun BackupTransaction.toEntity() = TransactionEntity(
    id = id,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    sourceAccountId = sourceAccountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    timestamp = Instant.ofEpochMilli(timestampEpochMillis),
    note = note,
    recurringTransactionId = recurringTransactionId,
    isDeleted = isDeleted
)

fun BudgetEntity.toBackup() = BackupBudget(
    id = id,
    name = name,
    targetAmountMinor = targetAmountMinor,
    currencyCode = currencyCode,
    categoryId = categoryId,
    startDateIso = startDate.toString(),
    endDateIso = endDate.toString(),
    isEnabled = isEnabled
)

fun BackupBudget.toEntity() = BudgetEntity(
    id = id,
    name = name,
    targetAmountMinor = targetAmountMinor,
    currencyCode = currencyCode,
    categoryId = categoryId,
    startDate = LocalDate.parse(startDateIso),
    endDate = LocalDate.parse(endDateIso),
    isEnabled = isEnabled
)

fun RecurringTransactionEntity.toBackup() = BackupRecurringTransaction(
    id = id,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    type = type.name,
    sourceAccountId = sourceAccountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    note = note,
    frequency = frequency.name,
    nextOccurrenceIso = nextOccurrence.toString(),
    startDateIso = startDate.toString(),
    endDateIso = endDate?.toString(),
    isEnabled = isEnabled,
    lastGeneratedOccurrenceIso = lastGeneratedOccurrence?.toString()
)

fun BackupRecurringTransaction.toEntity() = RecurringTransactionEntity(
    id = id,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    sourceAccountId = sourceAccountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    note = note,
    frequency = runCatching { RecurrenceFrequency.valueOf(frequency) }.getOrDefault(RecurrenceFrequency.MONTHLY),
    nextOccurrence = LocalDate.parse(nextOccurrenceIso),
    startDate = LocalDate.parse(startDateIso),
    endDate = endDateIso?.let { LocalDate.parse(it) },
    isEnabled = isEnabled,
    lastGeneratedOccurrence = lastGeneratedOccurrenceIso?.let { LocalDate.parse(it) }
)

fun UserPreferences.toBackup() = BackupPreferences(
    themeMode = themeMode.name,
    isDynamicColorEnabled = isDynamicColorEnabled,
    customAccentColor = customAccentColor,
    currencyCode = currencyCode,
    notificationsEnabled = notificationsEnabled,
    recurringNotificationsEnabled = recurringNotificationsEnabled,
    budgetAlertsEnabled = budgetAlertsEnabled,
    budgetThresholdPercent = budgetThresholdPercent
)

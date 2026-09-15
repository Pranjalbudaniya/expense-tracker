package com.example.expensetracker.feature.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import javax.inject.Inject

class BackupReader @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun readAndValidate(inputStream: InputStream): BackupValidationResult = withContext(Dispatchers.IO) {
        val jsonString = try {
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { it.readText() }
        } catch (e: Exception) {
            return@withContext BackupValidationResult.Invalid(
                listOf("Failed to read backup stream: ${e.message}")
            )
        }

        parseAndValidate(jsonString)
    }

    fun parseAndValidate(jsonString: String): BackupValidationResult {
        if (jsonString.isBlank()) {
            return BackupValidationResult.Invalid(listOf("Backup file is empty"))
        }

        val backup: AppBackup = try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(
                listOf("Malformed backup file: ${e.message}")
            )
        }

        val errors = mutableListOf<String>()

        // 1. Version Check
        if (backup.version > CURRENT_BACKUP_VERSION) {
            return BackupValidationResult.Invalid(
                listOf("Backup format version ${backup.version} is not supported by this application version. Please update the app.")
            )
        }
        if (backup.version < 1) {
            errors.add("Invalid backup format version: ${backup.version}")
        }

        // 2. ID Uniqueness
        fun <T> checkDuplicateIds(items: List<T>, idSelector: (T) -> String, entityName: String) {
            val seen = mutableSetOf<String>()
            for (item in items) {
                val id = idSelector(item)
                if (id.isBlank()) {
                    errors.add("$entityName contains an entry with an empty ID")
                } else if (!seen.add(id)) {
                    errors.add("Duplicate ID found in $entityName: '$id'")
                }
            }
        }

        checkDuplicateIds(backup.accounts, { it.id }, "Accounts")
        checkDuplicateIds(backup.categories, { it.id }, "Categories")
        checkDuplicateIds(backup.transactions, { it.id }, "Transactions")
        checkDuplicateIds(backup.budgets, { it.id }, "Budgets")
        checkDuplicateIds(backup.recurringTransactions, { it.id }, "Recurring transactions")

        // 3. Currency Validation
        fun validateCurrency(code: String, context: String) {
            if (code.isBlank() || code.length != 3 || !code.all { it.isLetter() }) {
                errors.add("Invalid currency code '$code' in $context")
            }
        }

        backup.accounts.forEach { validateCurrency(it.currencyCode, "Account '${it.name}'") }
        backup.transactions.forEach { validateCurrency(it.currencyCode, "Transaction '${it.id}'") }
        backup.budgets.forEach { validateCurrency(it.currencyCode, "Budget '${it.name}'") }
        backup.recurringTransactions.forEach { validateCurrency(it.currencyCode, "Recurring Transaction '${it.id}'") }

        // 4. Monetary Value Validations
        backup.transactions.forEach {
            if (it.amountMinor < 0) {
                errors.add("Transaction '${it.id}' has a negative amount: ${it.amountMinor}")
            }
        }
        backup.budgets.forEach {
            if (it.targetAmountMinor <= 0) {
                errors.add("Budget '${it.name}' has a non-positive target amount: ${it.targetAmountMinor}")
            }
        }
        backup.recurringTransactions.forEach {
            if (it.amountMinor <= 0) {
                errors.add("Recurring Transaction '${it.id}' has a non-positive amount: ${it.amountMinor}")
            }
        }

        // 5. Date & Timestamp Validations
        backup.transactions.forEach {
            if (it.timestampEpochMillis <= 0) {
                errors.add("Transaction '${it.id}' has an invalid timestamp: ${it.timestampEpochMillis}")
            }
        }

        backup.budgets.forEach { budget ->
            val start = runCatching { LocalDate.parse(budget.startDateIso) }.getOrNull()
            val end = runCatching { LocalDate.parse(budget.endDateIso) }.getOrNull()

            if (start == null) errors.add("Budget '${budget.name}' has invalid start date: '${budget.startDateIso}'")
            if (end == null) errors.add("Budget '${budget.name}' has invalid end date: '${budget.endDateIso}'")
            if (start != null && end != null && start.isAfter(end)) {
                errors.add("Budget '${budget.name}' has start date ($start) after end date ($end)")
            }
        }

        backup.recurringTransactions.forEach { recurring ->
            val start = runCatching { LocalDate.parse(recurring.startDateIso) }.getOrNull()
            val next = runCatching { LocalDate.parse(recurring.nextOccurrenceIso) }.getOrNull()
            val end = recurring.endDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

            if (start == null) errors.add("Recurring transaction '${recurring.id}' has invalid start date: '${recurring.startDateIso}'")
            if (next == null) errors.add("Recurring transaction '${recurring.id}' has invalid next occurrence: '${recurring.nextOccurrenceIso}'")
            if (recurring.endDateIso != null && end == null) {
                errors.add("Recurring transaction '${recurring.id}' has invalid end date: '${recurring.endDateIso}'")
            }
            if (start != null && end != null && start.isAfter(end)) {
                errors.add("Recurring transaction '${recurring.id}' has start date ($start) after end date ($end)")
            }
        }

        // 6. Relational Integrity Checks
        val accountMap = backup.accounts.associateBy { it.id }
        val categoryIds = backup.categories.map { it.id }.toSet()
        val recurringIds = backup.recurringTransactions.map { it.id }.toSet()

        backup.transactions.forEach { tx ->
            val srcAcc = accountMap[tx.sourceAccountId]
            val destAcc = tx.destinationAccountId?.let { accountMap[it] }

            if (srcAcc == null) {
                errors.add("Transaction '${tx.id}' references missing source account '${tx.sourceAccountId}'")
            }
            if (tx.destinationAccountId != null && destAcc == null) {
                errors.add("Transaction '${tx.id}' references missing destination account '${tx.destinationAccountId}'")
            }
            if (tx.type.equals("TRANSFER", ignoreCase = true)) {
                if (destAcc != null && srcAcc != null && !srcAcc.currencyCode.equals(destAcc.currencyCode, ignoreCase = true)) {
                    errors.add("Transfer transaction '${tx.id}' has mismatched account currencies: source '${srcAcc.currencyCode}' vs destination '${destAcc.currencyCode}'")
                }
            }
            if (tx.categoryId != null && !categoryIds.contains(tx.categoryId)) {
                errors.add("Transaction '${tx.id}' references missing category '${tx.categoryId}'")
            }
            if (tx.recurringTransactionId != null && !recurringIds.contains(tx.recurringTransactionId)) {
                errors.add("Transaction '${tx.id}' references missing recurring definition '${tx.recurringTransactionId}'")
            }
        }

        backup.budgets.forEach { budget ->
            if (budget.categoryId != null && !categoryIds.contains(budget.categoryId)) {
                errors.add("Budget '${budget.name}' (${budget.id}) references missing category '${budget.categoryId}'")
            }
        }

        backup.recurringTransactions.forEach { rec ->
            val srcAcc = accountMap[rec.sourceAccountId]
            val destAcc = rec.destinationAccountId?.let { accountMap[it] }

            if (srcAcc == null) {
                errors.add("Recurring transaction '${rec.id}' references missing source account '${rec.sourceAccountId}'")
            }
            if (rec.destinationAccountId != null && destAcc == null) {
                errors.add("Recurring transaction '${rec.id}' references missing destination account '${rec.destinationAccountId}'")
            }
            if (rec.type.equals("TRANSFER", ignoreCase = true)) {
                if (destAcc != null && srcAcc != null && !srcAcc.currencyCode.equals(destAcc.currencyCode, ignoreCase = true)) {
                    errors.add("Recurring transfer '${rec.id}' has mismatched account currencies: source '${srcAcc.currencyCode}' vs destination '${destAcc.currencyCode}'")
                }
            }
            if (rec.categoryId != null && !categoryIds.contains(rec.categoryId)) {
                errors.add("Recurring transaction '${rec.id}' references missing category '${rec.categoryId}'")
            }
        }

        if (errors.isNotEmpty()) {
            return BackupValidationResult.Invalid(errors)
        }

        val summary = BackupSummary(
            formatVersion = backup.version,
            accountsCount = backup.accounts.size,
            categoriesCount = backup.categories.size,
            transactionsCount = backup.transactions.size,
            activeTransactionsCount = backup.transactions.count { !it.isDeleted },
            trashTransactionsCount = backup.transactions.count { it.isDeleted },
            budgetsCount = backup.budgets.size,
            recurringCount = backup.recurringTransactions.size,
            exportedAtEpochMillis = backup.exportedAtEpochMillis,
            hasPreferences = backup.preferences != null
        )

        return BackupValidationResult.Valid(backup, summary)
    }
}

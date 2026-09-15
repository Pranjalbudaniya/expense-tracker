package com.example.expensetracker.feature.backup

import com.example.expensetracker.core.database.ExpenseTrackerDatabase
import com.example.expensetracker.core.preferences.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject

class BackupWriter @Inject constructor(
    private val database: ExpenseTrackerDatabase,
    private val preferencesRepository: PreferencesRepository
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun generateBackup(): AppBackup = withContext(Dispatchers.IO) {
        val accounts = database.accountDao().getAllAccounts().first().map { it.toBackup() }
        val categories = database.categoryDao().getAllCategories().first().map { it.toBackup() }
        val transactions = database.transactionDao().getAllTransactions().first().map { it.toBackup() }
        val budgets = database.budgetDao().getAllBudgets().first().map { it.toBackup() }
        val recurring = database.recurringTransactionDao().getAllRecurring().first().map { it.toBackup() }
        val preferences = preferencesRepository.userPreferences.first().toBackup()

        AppBackup(
            version = CURRENT_BACKUP_VERSION,
            exportedAtEpochMillis = System.currentTimeMillis(),
            appVersionName = "1.0.0",
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            budgets = budgets,
            recurringTransactions = recurring,
            preferences = preferences
        )
    }

    fun serializeBackup(backup: AppBackup): String {
        return json.encodeToString(backup)
    }

    suspend fun writeBackup(outputStream: OutputStream): BackupSummary = withContext(Dispatchers.IO) {
        val backup = generateBackup()
        val jsonString = serializeBackup(backup)

        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            writer.write(jsonString)
            writer.flush()
        }

        BackupSummary(
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
    }
}

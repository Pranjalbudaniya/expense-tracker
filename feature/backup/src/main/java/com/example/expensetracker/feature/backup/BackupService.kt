package com.example.expensetracker.feature.backup

import androidx.room.withTransaction
import com.example.expensetracker.core.database.ExpenseTrackerDatabase
import com.example.expensetracker.core.preferences.PreferencesRepository
import com.example.expensetracker.core.preferences.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupService @Inject constructor(
    private val database: ExpenseTrackerDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val backupWriter: BackupWriter,
    private val backupReader: BackupReader
) {

    internal var transactionRunner: suspend (suspend () -> Unit) -> Unit = { block ->
        database.withTransaction { block() }
    }

    suspend fun exportBackup(outputStream: OutputStream): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            backupWriter.writeBackup(outputStream)
        }
    }

    suspend fun validateBackup(inputStream: InputStream): BackupValidationResult = withContext(Dispatchers.IO) {
        backupReader.readAndValidate(inputStream)
    }

    suspend fun restoreBackup(backup: AppBackup, mode: RestoreMode): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            transactionRunner {
                when (mode) {
                    RestoreMode.REPLACE -> executeReplaceRestore(backup)
                    RestoreMode.MERGE -> executeMergeRestore(backup)
                }
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

    private suspend fun executeReplaceRestore(backup: AppBackup) {
        // 1. Wipe all local data atomically
        database.transactionDao().deleteAllTransactions()
        database.budgetDao().deleteAllBudgets()
        database.recurringTransactionDao().deleteAllRecurring()
        database.categoryDao().deleteAllCategories()
        database.accountDao().deleteAllAccounts()

        // 2. Insert entities in relational dependency order
        database.accountDao().insertAll(backup.accounts.map { it.toEntity() })
        database.categoryDao().insertAll(backup.categories.map { it.toEntity() })
        database.recurringTransactionDao().insertAll(backup.recurringTransactions.map { it.toEntity() })
        database.budgetDao().insertAll(backup.budgets.map { it.toEntity() })
        database.transactionDao().insertAll(backup.transactions.map { it.toEntity() })

        // 3. Restore safe preferences
        restoreSafePreferences(backup.preferences)
    }

    private suspend fun executeMergeRestore(backup: AppBackup) {
        // Insert accounts, categories, recurring templates, and budgets (idempotently replacing or creating)
        database.accountDao().insertAll(backup.accounts.map { it.toEntity() })
        database.categoryDao().insertAll(backup.categories.map { it.toEntity() })
        database.recurringTransactionDao().insertAll(backup.recurringTransactions.map { it.toEntity() })
        database.budgetDao().insertAll(backup.budgets.map { it.toEntity() })

        // For transactions, merge and preserve soft-delete state if already in local trash
        val entities = backup.transactions.map { backupTx ->
            val existing = database.transactionDao().getById(backupTx.id)
            val entity = backupTx.toEntity()
            if (existing != null && existing.isDeleted) {
                entity.copy(isDeleted = true)
            } else {
                entity
            }
        }
        database.transactionDao().insertAll(entities)
    }

    private suspend fun restoreSafePreferences(preferences: BackupPreferences?) {
        if (preferences == null) return

        preferences.themeMode?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrNull()?.let { mode ->
                preferencesRepository.setThemeMode(mode)
            }
        }
        preferences.isDynamicColorEnabled?.let {
            preferencesRepository.setDynamicColorEnabled(it)
        }
        if (preferences.customAccentColor != null) {
            preferencesRepository.setCustomAccentColor(preferences.customAccentColor)
        }
        preferences.currencyCode?.let {
            runCatching { preferencesRepository.setCurrencyCode(it) }
        }
        preferences.notificationsEnabled?.let {
            preferencesRepository.setNotificationsEnabled(it)
        }
        preferences.recurringNotificationsEnabled?.let {
            preferencesRepository.setRecurringNotificationsEnabled(it)
        }
        preferences.budgetAlertsEnabled?.let {
            preferencesRepository.setBudgetAlertsEnabled(it)
        }
        preferences.budgetThresholdPercent?.let {
            runCatching { preferencesRepository.setBudgetThresholdPercent(it) }
        }
    }
}

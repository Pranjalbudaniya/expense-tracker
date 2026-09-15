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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate

class BackupServiceTest {

    private lateinit var fakeDatabase: TestExpenseTrackerDatabase
    private lateinit var fakePreferencesRepository: TestPreferencesRepository
    private lateinit var backupWriter: BackupWriter
    private lateinit var backupReader: BackupReader
    private lateinit var service: BackupService

    @Before
    fun setUp() {
        fakeDatabase = TestExpenseTrackerDatabase()
        fakePreferencesRepository = TestPreferencesRepository()
        backupWriter = BackupWriter(fakeDatabase, fakePreferencesRepository)
        backupReader = BackupReader()

        service = BackupService(
            database = fakeDatabase,
            preferencesRepository = fakePreferencesRepository,
            backupWriter = backupWriter,
            backupReader = backupReader
        )
        // Set direct transaction execution for testing without SQLite OpenHelper
        service.transactionRunner = { block -> block() }
    }

    @Test
    fun restoreBackup_replaceMode_replacesAllDataAndPreferences() = runTest {
        // Populate existing local data
        fakeDatabase.fakeAccountDao.insert(
            AccountEntity(id = "acc_old", name = "Old Account", type = AccountType.CASH, currencyCode = "EUR", initialBalanceMinor = 5000L, currentBalanceMinor = 5000L)
        )
        fakeDatabase.fakeCategoryDao.insert(
            CategoryEntity(id = "cat_old", name = "Old Category", iconKey = "icon_old", colorKey = "grey", type = CategoryType.EXPENSE)
        )
        fakeDatabase.fakeTransactionDao.insert(
            TransactionEntity(id = "tx_old", amountMinor = 1000L, currencyCode = "EUR", type = TransactionType.EXPENSE, sourceAccountId = "acc_old", timestamp = Instant.now())
        )

        val backupToRestore = AppBackup(
            version = 1,
            exportedAtEpochMillis = 1700000000000L,
            appVersionName = "1.0.0",
            accounts = listOf(
                BackupAccount(id = "acc_new", name = "Bank Account", type = "BANK", currencyCode = "USD", initialBalanceMinor = 100000L, currentBalanceMinor = 85000L, isArchived = false)
            ),
            categories = listOf(
                BackupCategory(id = "cat_new", name = "Dining", iconKey = "restaurant", colorKey = "amber", isDefault = false, isArchived = false, type = "EXPENSE", orderIndex = 0)
            ),
            transactions = listOf(
                BackupTransaction(
                    id = "tx_new",
                    amountMinor = 15000L, // 150.00
                    currencyCode = "USD",
                    type = "EXPENSE",
                    sourceAccountId = "acc_new",
                    destinationAccountId = null,
                    categoryId = "cat_new",
                    timestampEpochMillis = 1700000050000L,
                    note = "Dinner",
                    recurringTransactionId = null,
                    isDeleted = false
                )
            ),
            budgets = listOf(
                BackupBudget(
                    id = "bgt_new",
                    name = "Dining Budget",
                    targetAmountMinor = 50000L,
                    currencyCode = "USD",
                    categoryId = "cat_new",
                    startDateIso = "2026-09-01",
                    endDateIso = "2026-09-30",
                    isEnabled = true
                )
            ),
            recurringTransactions = listOf(
                BackupRecurringTransaction(
                    id = "rec_new",
                    amountMinor = 1200L,
                    currencyCode = "USD",
                    type = "EXPENSE",
                    sourceAccountId = "acc_new",
                    destinationAccountId = null,
                    categoryId = "cat_new",
                    note = "Cloud Storage",
                    frequency = "MONTHLY",
                    nextOccurrenceIso = "2026-10-01",
                    startDateIso = "2026-01-01",
                    endDateIso = null,
                    isEnabled = true
                )
            ),
            preferences = BackupPreferences(
                themeMode = "LIGHT",
                isDynamicColorEnabled = false,
                customAccentColor = 0xFF445566L,
                currencyCode = "USD",
                notificationsEnabled = true,
                recurringNotificationsEnabled = false,
                budgetAlertsEnabled = true,
                budgetThresholdPercent = 75
            )
        )

        val result = service.restoreBackup(backupToRestore, RestoreMode.REPLACE)
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()

        assertEquals(1, summary.accountsCount)
        assertEquals(1, summary.categoriesCount)
        assertEquals(1, summary.transactionsCount)
        assertEquals(1, summary.budgetsCount)
        assertEquals(1, summary.recurringCount)
        assertTrue(summary.hasPreferences)

        // Check local DB contains only new data
        val accounts = fakeDatabase.fakeAccountDao.getAllAccounts().first()
        assertEquals(1, accounts.size)
        assertEquals("acc_new", accounts[0].id)
        assertEquals(85000L, accounts[0].currentBalanceMinor)

        val categories = fakeDatabase.fakeCategoryDao.getAllCategories().first()
        assertEquals(1, categories.size)
        assertEquals("cat_new", categories[0].id)

        val transactions = fakeDatabase.fakeTransactionDao.getAllTransactions().first()
        assertEquals(1, transactions.size)
        assertEquals("tx_new", transactions[0].id)
        assertEquals(15000L, transactions[0].amountMinor)

        val budgets = fakeDatabase.fakeBudgetDao.getAllBudgets().first()
        assertEquals(1, budgets.size)
        assertEquals("bgt_new", budgets[0].id)

        val recurring = fakeDatabase.fakeRecurringDao.getAllRecurring().first()
        assertEquals(1, recurring.size)
        assertEquals("rec_new", recurring[0].id)

        // Check preferences were restored safely
        val prefs = fakePreferencesRepository.userPreferences.first()
        assertEquals(ThemeMode.LIGHT, prefs.themeMode)
        assertFalse(prefs.isDynamicColorEnabled)
        assertEquals(0xFF445566L, prefs.customAccentColor)
        assertEquals("USD", prefs.currencyCode)
        assertTrue(prefs.notificationsEnabled)
        assertFalse(prefs.recurringNotificationsEnabled)
        assertTrue(prefs.budgetAlertsEnabled)
        assertEquals(75, prefs.budgetThresholdPercent)
    }

    @Test
    fun restoreBackup_mergeMode_mergesDataAndPreservesLocalTrashState() = runTest {
        // Pre-existing Account & Category
        fakeDatabase.fakeAccountDao.insert(
            AccountEntity(id = "acc_existing", name = "Existing Account", type = AccountType.BANK, currencyCode = "INR", initialBalanceMinor = 10000L, currentBalanceMinor = 10000L)
        )
        fakeDatabase.fakeCategoryDao.insert(
            CategoryEntity(id = "cat_groceries", name = "Groceries", iconKey = "cart", colorKey = "green", type = CategoryType.EXPENSE)
        )

        // Pre-existing transaction that is TRASHED locally
        fakeDatabase.fakeTransactionDao.insert(
            TransactionEntity(
                id = "tx_trashed_locally",
                amountMinor = 2000L,
                currencyCode = "INR",
                type = TransactionType.EXPENSE,
                sourceAccountId = "acc_existing",
                categoryId = "cat_groceries",
                timestamp = Instant.now(),
                isDeleted = true // In trash!
            )
        )

        // Pre-existing transaction that is NOT in the backup
        fakeDatabase.fakeTransactionDao.insert(
            TransactionEntity(
                id = "tx_unrelated_local",
                amountMinor = 3000L,
                currencyCode = "INR",
                type = TransactionType.EXPENSE,
                sourceAccountId = "acc_existing",
                categoryId = "cat_groceries",
                timestamp = Instant.now(),
                isDeleted = false
            )
        )

        // Backup contains:
        // 1. New account
        // 2. The same tx_trashed_locally, but backup had isDeleted = false
        // 3. Brand new transaction
        val backupToRestore = AppBackup(
            version = 1,
            exportedAtEpochMillis = 1700000000000L,
            appVersionName = "1.0.0",
            accounts = listOf(
                BackupAccount(id = "acc_new", name = "Secondary Wallet", type = "WALLET", currencyCode = "INR", initialBalanceMinor = 5000L, currentBalanceMinor = 5000L, isArchived = false)
            ),
            categories = emptyList(),
            transactions = listOf(
                BackupTransaction(
                    id = "tx_trashed_locally",
                    amountMinor = 2500L, // updated amount
                    currencyCode = "INR",
                    type = "EXPENSE",
                    sourceAccountId = "acc_existing",
                    destinationAccountId = null,
                    categoryId = "cat_groceries",
                    timestampEpochMillis = 1700000050000L,
                    note = "Updated grocery note",
                    recurringTransactionId = null,
                    isDeleted = false // Backup claims active, but locally was trashed!
                ),
                BackupTransaction(
                    id = "tx_brand_new",
                    amountMinor = 9999L,
                    currencyCode = "INR",
                    type = "EXPENSE",
                    sourceAccountId = "acc_new",
                    destinationAccountId = null,
                    categoryId = null,
                    timestampEpochMillis = 1700000060000L,
                    note = "Brand new",
                    recurringTransactionId = null,
                    isDeleted = false
                )
            ),
            budgets = emptyList(),
            recurringTransactions = emptyList(),
            preferences = null
        )

        val result = service.restoreBackup(backupToRestore, RestoreMode.MERGE)
        assertTrue(result.isSuccess)

        // Accounts: existing was kept, new was merged in
        val accounts = fakeDatabase.fakeAccountDao.getAllAccounts().first()
        assertEquals(2, accounts.size)
        assertTrue(accounts.any { it.id == "acc_existing" })
        assertTrue(accounts.any { it.id == "acc_new" })

        // Transactions: all 3 should exist
        val allTx = fakeDatabase.fakeTransactionDao.getAllTransactions().first()
        assertEquals(3, allTx.size)
        assertTrue(allTx.any { it.id == "tx_unrelated_local" })
        assertTrue(allTx.any { it.id == "tx_brand_new" })

        // Crucial test: tx_trashed_locally must maintain isDeleted = true!
        val mergedTrashedTx = fakeDatabase.fakeTransactionDao.getById("tx_trashed_locally")
        assertNotNull(mergedTrashedTx)
        assertTrue("Local trash state must be preserved across merge", mergedTrashedTx!!.isDeleted)
        assertEquals(2500L, mergedTrashedTx.amountMinor)
        assertEquals("Updated grocery note", mergedTrashedTx.note)
    }

    @Test
    fun restoreBackup_validationFailureDoesNotTouchLocalState() = runTest {
        // Pre-populate database
        fakeDatabase.fakeAccountDao.insert(
            AccountEntity(id = "initial", name = "Initial", type = AccountType.CASH, currencyCode = "USD", initialBalanceMinor = 0, currentBalanceMinor = 0)
        )

        val badStream = ByteArrayInputStream("bad json".toByteArray(Charsets.UTF_8))
        val result = service.validateBackup(badStream)

        assertTrue(result is BackupValidationResult.Invalid)
        // Local state was not touched because validation failed
        assertEquals(1, fakeDatabase.fakeAccountDao.getAllAccounts().first().size)
    }

    @Test
    fun restoreBackup_idempotentExecution() = runTest {
        val backup = AppBackup(
            version = 1,
            exportedAtEpochMillis = 1700000000000L,
            appVersionName = "1.0.0",
            accounts = listOf(
                BackupAccount(id = "acc_idem", name = "Checking", type = "BANK", currencyCode = "EUR", initialBalanceMinor = 1000L, currentBalanceMinor = 1000L, isArchived = false)
            ),
            categories = listOf(
                BackupCategory(id = "cat_idem", name = "General", iconKey = "tag", colorKey = "blue", isDefault = true, isArchived = false, type = "EXPENSE", orderIndex = 0)
            ),
            transactions = listOf(
                BackupTransaction(
                    id = "tx_idem",
                    amountMinor = 500L,
                    currencyCode = "EUR",
                    type = "EXPENSE",
                    sourceAccountId = "acc_idem",
                    destinationAccountId = null,
                    categoryId = "cat_idem",
                    timestampEpochMillis = 1700000000000L,
                    note = "Payment",
                    recurringTransactionId = null,
                    isDeleted = false
                )
            ),
            budgets = emptyList(),
            recurringTransactions = emptyList(),
            preferences = null
        )

        // Run restore twice in replace mode
        service.restoreBackup(backup, RestoreMode.REPLACE)
        service.restoreBackup(backup, RestoreMode.REPLACE)

        assertEquals(1, fakeDatabase.fakeAccountDao.getAllAccounts().first().size)
        assertEquals(1, fakeDatabase.fakeCategoryDao.getAllCategories().first().size)
        assertEquals(1, fakeDatabase.fakeTransactionDao.getAllTransactions().first().size)

        // Run merge mode twice
        service.restoreBackup(backup, RestoreMode.MERGE)
        service.restoreBackup(backup, RestoreMode.MERGE)

        assertEquals(1, fakeDatabase.fakeAccountDao.getAllAccounts().first().size)
        assertEquals(1, fakeDatabase.fakeCategoryDao.getAllCategories().first().size)
        assertEquals(1, fakeDatabase.fakeTransactionDao.getAllTransactions().first().size)
    }
}

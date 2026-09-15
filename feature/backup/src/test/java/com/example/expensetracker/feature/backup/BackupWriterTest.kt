package com.example.expensetracker.feature.backup

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.expensetracker.core.database.ExpenseTrackerDatabase
import com.example.expensetracker.core.database.dao.AccountDao
import com.example.expensetracker.core.database.dao.BudgetDao
import com.example.expensetracker.core.database.dao.CategoryDao
import com.example.expensetracker.core.database.dao.RecurringTransactionDao
import com.example.expensetracker.core.database.dao.TransactionDao
import com.example.expensetracker.core.database.entity.AccountEntity
import com.example.expensetracker.core.database.entity.BudgetEntity
import com.example.expensetracker.core.database.entity.CategoryEntity
import com.example.expensetracker.core.database.entity.RecurringTransactionEntity
import com.example.expensetracker.core.database.entity.TransactionEntity
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.category.CategoryType
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.transaction.TransactionType
import com.example.expensetracker.core.preferences.PreferencesRepository
import com.example.expensetracker.core.preferences.ThemeMode
import com.example.expensetracker.core.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

class BackupWriterTest {

    private lateinit var fakeDatabase: TestExpenseTrackerDatabase
    private lateinit var fakePreferencesRepository: TestPreferencesRepository
    private lateinit var writer: BackupWriter

    @Before
    fun setUp() {
        fakeDatabase = TestExpenseTrackerDatabase()
        fakePreferencesRepository = TestPreferencesRepository()
        writer = BackupWriter(fakeDatabase, fakePreferencesRepository)
    }

    @Test
    fun generateBackup_serializesCompleteDatasetWithExactMoney() = runTest {
        // Setup Accounts in multiple currencies
        val accountInr = AccountEntity(
            id = "acc_inr",
            name = "Bank Account",
            type = AccountType.BANK,
            currencyCode = "INR",
            initialBalanceMinor = 1000000L, // 10,000.00
            currentBalanceMinor = 950000L
        )
        val accountUsd = AccountEntity(
            id = "acc_usd",
            name = "USD Travel Wallet",
            type = AccountType.WALLET,
            currencyCode = "USD",
            initialBalanceMinor = 50000L, // 500.00
            currentBalanceMinor = 45000L
        )
        fakeDatabase.fakeAccountDao.accounts.addAll(listOf(accountInr, accountUsd))

        // Setup Category
        val categoryGroceries = CategoryEntity(
            id = "cat_groceries",
            name = "Groceries",
            iconKey = "shopping_cart",
            colorKey = "green",
            type = CategoryType.EXPENSE,
            isDefault = true,
            orderIndex = 1
        )
        fakeDatabase.fakeCategoryDao.categories.add(categoryGroceries)

        // Setup Recurring Rule
        val recurringRent = RecurringTransactionEntity(
            id = "rec_rent",
            amountMinor = 2500000L, // 25,000.00
            currencyCode = "INR",
            type = TransactionType.EXPENSE,
            sourceAccountId = "acc_inr",
            categoryId = "cat_groceries",
            note = "Monthly Rent",
            frequency = RecurrenceFrequency.MONTHLY,
            nextOccurrence = LocalDate.parse("2026-10-01"),
            startDate = LocalDate.parse("2026-01-01")
        )
        fakeDatabase.fakeRecurringDao.recurring.add(recurringRent)

        // Setup Budget
        val budgetFood = BudgetEntity(
            id = "bgt_food",
            name = "Food Budget",
            targetAmountMinor = 1500000L,
            currencyCode = "INR",
            categoryId = "cat_groceries",
            startDate = LocalDate.parse("2026-09-01"),
            endDate = LocalDate.parse("2026-09-30")
        )
        fakeDatabase.fakeBudgetDao.budgets.add(budgetFood)

        // Setup Active and Trash Transactions
        val activeTx = TransactionEntity(
            id = "tx_active",
            amountMinor = 125050L, // 1,250.50
            currencyCode = "INR",
            type = TransactionType.EXPENSE,
            sourceAccountId = "acc_inr",
            categoryId = "cat_groceries",
            timestamp = Instant.parse("2026-09-15T10:00:00Z"),
            note = "Supermarket Grocery",
            isDeleted = false
        )
        val trashTx = TransactionEntity(
            id = "tx_trash",
            amountMinor = 3500L,
            currencyCode = "USD",
            type = TransactionType.EXPENSE,
            sourceAccountId = "acc_usd",
            categoryId = "cat_groceries",
            timestamp = Instant.parse("2026-09-14T08:30:00Z"),
            note = "Mistake purchase moved to trash",
            isDeleted = true
        )
        fakeDatabase.fakeTransactionDao.transactions.addAll(listOf(activeTx, trashTx))

        // Setup Preferences
        fakePreferencesRepository.currentPreferences = UserPreferences(
            themeMode = ThemeMode.DARK,
            isDynamicColorEnabled = true,
            customAccentColor = 0xFF123456L,
            currencyCode = "INR",
            notificationsEnabled = true,
            recurringNotificationsEnabled = true,
            budgetAlertsEnabled = true,
            budgetThresholdPercent = 90
        )

        // Export to OutputStream
        val outputStream = ByteArrayOutputStream()
        val summary = writer.writeBackup(outputStream)

        // Verify summary
        assertEquals(CURRENT_BACKUP_VERSION, summary.formatVersion)
        assertEquals(2, summary.accountsCount)
        assertEquals(1, summary.categoriesCount)
        assertEquals(2, summary.transactionsCount)
        assertEquals(1, summary.activeTransactionsCount)
        assertEquals(1, summary.trashTransactionsCount)
        assertEquals(1, summary.budgetsCount)
        assertEquals(1, summary.recurringCount)
        assertTrue(summary.hasPreferences)

        // Verify JSON content
        val jsonOutput = outputStream.toString("UTF-8")
        assertTrue(jsonOutput.contains("\"version\": 1"))
        assertTrue(jsonOutput.contains("\"amountMinor\": 125050"))
        assertTrue(jsonOutput.contains("\"amountMinor\": 3500"))
        assertTrue(jsonOutput.contains("\"isDeleted\": true"))
        assertTrue(jsonOutput.contains("\"isDeleted\": false"))
        assertTrue(jsonOutput.contains("\"currencyCode\": \"INR\""))
        assertTrue(jsonOutput.contains("\"currencyCode\": \"USD\""))
        assertTrue(jsonOutput.contains("\"themeMode\": \"DARK\""))
        assertTrue(jsonOutput.contains("\"budgetThresholdPercent\": 90"))
    }
}

// --- Fakes for Testing ---

class TestExpenseTrackerDatabase : ExpenseTrackerDatabase() {
    val fakeAccountDao = TestAccountDao()
    val fakeCategoryDao = TestCategoryDao()
    val fakeTransactionDao = TestTransactionDao()
    val fakeBudgetDao = TestBudgetDao()
    val fakeRecurringDao = TestRecurringTransactionDao()

    override fun accountDao(): AccountDao = fakeAccountDao
    override fun categoryDao(): CategoryDao = fakeCategoryDao
    override fun transactionDao(): TransactionDao = fakeTransactionDao
    override fun budgetDao(): BudgetDao = fakeBudgetDao
    override fun recurringTransactionDao(): RecurringTransactionDao = fakeRecurringDao

    override fun clearAllTables() {}
    override fun createInvalidationTracker(): InvalidationTracker {
        return InvalidationTracker(this, "accounts", "categories", "transactions", "budgets", "recurring_transactions")
    }
    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
        throw UnsupportedOperationException("In-memory test fake does not use SQLiteOpenHelper")
    }
}

class TestAccountDao : AccountDao {
    val accounts = mutableListOf<AccountEntity>()

    override suspend fun insert(account: AccountEntity) {
        accounts.removeAll { it.id == account.id }
        accounts.add(account)
    }
    override suspend fun insertAll(accounts: List<AccountEntity>) {
        accounts.forEach { insert(it) }
    }
    override suspend fun update(account: AccountEntity) { insert(account) }
    override suspend fun delete(account: AccountEntity) { accounts.removeAll { it.id == account.id } }
    override suspend fun getById(id: String): AccountEntity? = accounts.firstOrNull { it.id == id }
    override fun getActiveAccounts(): Flow<List<AccountEntity>> = flowOf(accounts.filter { !it.isArchived })
    override fun getAllAccounts(): Flow<List<AccountEntity>> = flowOf(accounts.toList())
    override suspend fun deleteAllAccounts() { accounts.clear() }
}

class TestCategoryDao : CategoryDao {
    val categories = mutableListOf<CategoryEntity>()

    override suspend fun insert(category: CategoryEntity) {
        categories.removeAll { it.id == category.id }
        categories.add(category)
    }
    override suspend fun insertAll(categories: List<CategoryEntity>) {
        categories.forEach { insert(it) }
    }
    override suspend fun update(category: CategoryEntity) { insert(category) }
    override suspend fun delete(category: CategoryEntity) { categories.removeAll { it.id == category.id } }
    override suspend fun getById(id: String): CategoryEntity? = categories.firstOrNull { it.id == id }
    override fun getActiveCategories(): Flow<List<CategoryEntity>> = flowOf(categories.filter { !it.isArchived })
    override fun getAllCategories(): Flow<List<CategoryEntity>> = flowOf(categories.toList())
    override suspend fun deleteAllCategories() { categories.clear() }
}

class TestTransactionDao : TransactionDao {
    val transactions = mutableListOf<TransactionEntity>()

    override suspend fun insert(transaction: TransactionEntity) {
        transactions.removeAll { it.id == transaction.id }
        transactions.add(transaction)
    }
    override suspend fun insertAll(transactions: List<TransactionEntity>) {
        transactions.forEach { insert(it) }
    }
    override suspend fun update(transaction: TransactionEntity) { insert(transaction) }
    override suspend fun deletePermanently(transaction: TransactionEntity) {
        transactions.removeAll { it.id == transaction.id }
    }
    override suspend fun deletePermanentlyById(id: String) {
        transactions.removeAll { it.id == id }
    }
    override suspend fun getById(id: String): TransactionEntity? = transactions.firstOrNull { it.id == id }
    override fun getByIdFlow(id: String): Flow<TransactionEntity?> = flowOf(transactions.firstOrNull { it.id == id })
    override fun getActiveTransactions(): Flow<List<TransactionEntity>> = flowOf(transactions.filter { !it.isDeleted })
    override fun getDeletedTransactions(): Flow<List<TransactionEntity>> = flowOf(transactions.filter { it.isDeleted })
    override suspend fun moveToTrash(id: String) {
        val tx = getById(id)
        if (tx != null) insert(tx.copy(isDeleted = true))
    }
    override suspend fun restoreFromTrash(id: String) {
        val tx = getById(id)
        if (tx != null) insert(tx.copy(isDeleted = false))
    }
    override fun getAllTransactions(): Flow<List<TransactionEntity>> = flowOf(transactions.toList())
    override suspend fun deleteAllTransactions() { transactions.clear() }
    override suspend fun clearTrash() { transactions.removeAll { it.isDeleted } }
}

class TestBudgetDao : BudgetDao {
    val budgets = mutableListOf<BudgetEntity>()

    override suspend fun insert(budget: BudgetEntity) {
        budgets.removeAll { it.id == budget.id }
        budgets.add(budget)
    }
    override suspend fun insertAll(budgets: List<BudgetEntity>) {
        budgets.forEach { insert(it) }
    }
    override suspend fun update(budget: BudgetEntity) { insert(budget) }
    override suspend fun delete(budget: BudgetEntity) { budgets.removeAll { it.id == budget.id } }
    override suspend fun getById(id: String): BudgetEntity? = budgets.firstOrNull { it.id == id }
    override fun getAllBudgets(): Flow<List<BudgetEntity>> = flowOf(budgets.toList())
    override fun getActiveBudgets(): Flow<List<BudgetEntity>> = flowOf(budgets.filter { it.isEnabled })
    override suspend fun deleteAllBudgets() { budgets.clear() }
}

class TestRecurringTransactionDao : RecurringTransactionDao {
    val recurring = mutableListOf<RecurringTransactionEntity>()

    override suspend fun insert(recurring: RecurringTransactionEntity) {
        this.recurring.removeAll { it.id == recurring.id }
        this.recurring.add(recurring)
    }
    override suspend fun insertAll(recurring: List<RecurringTransactionEntity>) {
        recurring.forEach { insert(it) }
    }
    override suspend fun update(recurring: RecurringTransactionEntity) { insert(recurring) }
    override suspend fun delete(recurring: RecurringTransactionEntity) {
        this.recurring.removeAll { it.id == recurring.id }
    }
    override suspend fun getById(id: String): RecurringTransactionEntity? = recurring.firstOrNull { it.id == id }
    override fun getAllRecurring(): Flow<List<RecurringTransactionEntity>> = flowOf(recurring.toList())
    override fun getActiveRecurring(): Flow<List<RecurringTransactionEntity>> = flowOf(recurring.filter { it.isEnabled })
    override suspend fun deleteAllRecurring() { recurring.clear() }
}

class TestPreferencesRepository : PreferencesRepository {
    var currentPreferences = UserPreferences()
        set(value) {
            field = value
            _preferencesFlow.value = value
        }

    private val _preferencesFlow = MutableStateFlow(currentPreferences)
    override val userPreferences: Flow<UserPreferences> = _preferencesFlow.asStateFlow()

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        currentPreferences = currentPreferences.copy(themeMode = themeMode)
    }
    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        currentPreferences = currentPreferences.copy(isDynamicColorEnabled = enabled)
    }
    override suspend fun setCustomAccentColor(color: Long?) {
        currentPreferences = currentPreferences.copy(customAccentColor = color)
    }
    override suspend fun clearCustomAccentColor() {
        currentPreferences = currentPreferences.copy(customAccentColor = null)
    }
    override suspend fun setCurrencyCode(currencyCode: String) {
        currentPreferences = currentPreferences.copy(currencyCode = currencyCode)
    }
    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        currentPreferences = currentPreferences.copy(notificationsEnabled = enabled)
    }
    override suspend fun setRecurringNotificationsEnabled(enabled: Boolean) {
        currentPreferences = currentPreferences.copy(recurringNotificationsEnabled = enabled)
    }
    override suspend fun setBudgetAlertsEnabled(enabled: Boolean) {
        currentPreferences = currentPreferences.copy(budgetAlertsEnabled = enabled)
    }
    override suspend fun setBudgetThresholdPercent(percent: Int) {
        currentPreferences = currentPreferences.copy(budgetThresholdPercent = percent)
    }
}

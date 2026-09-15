package com.example.expensetracker.feature.home

import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.BudgetRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.budget.Budget
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.category.CategoryType
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import com.example.expensetracker.core.preferences.PreferencesRepository
import com.example.expensetracker.core.preferences.ThemeMode
import com.example.expensetracker.core.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val useCases = HomeUseCases()

    private val inr = Currency.INR
    private val usd = Currency.USD
    private val accountId1 = EntityId("acc-1")
    private val accountId2 = EntityId("acc-2")

    private val catFood = Category(
        id = EntityId("cat-food"),
        name = "Food",
        iconKey = "restaurant",
        colorKey = "category_orange",
        type = CategoryType.EXPENSE
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- HomeUseCases Unit Tests ---

    @Test
    fun noTransactions_producesZeroTotals() {
        val income = useCases.calculateTotalIncome(emptyList(), inr)
        val expense = useCases.calculateTotalExpenses(emptyList(), inr)
        val balance = useCases.calculateTotalBalance(emptyList(), emptyList(), inr)

        assertTrue(income.isZero)
        assertTrue(expense.isZero)
        assertTrue(balance.isZero)
        assertEquals(BigDecimal.ZERO, income.amount)
        assertEquals(BigDecimal.ZERO, expense.amount)
        assertEquals(BigDecimal.ZERO, balance.amount)
    }

    @Test
    fun incomeCalculation_sumsOnlyActiveIncome() {
        val transactions = listOf(
            createTransaction(id = "1", type = TransactionType.INCOME, amount = BigDecimal("1500.50")),
            createTransaction(id = "2", type = TransactionType.INCOME, amount = BigDecimal("500.00")),
            createTransaction(id = "3", type = TransactionType.EXPENSE, amount = BigDecimal("200.00"))
        )

        val totalIncome = useCases.calculateTotalIncome(transactions, inr)
        assertEquals(BigDecimal("2000.50"), totalIncome.amount)
        assertEquals(inr, totalIncome.currency)
    }

    @Test
    fun expenseCalculation_sumsOnlyActiveExpenses() {
        val transactions = listOf(
            createTransaction(id = "1", type = TransactionType.EXPENSE, amount = BigDecimal("300.00")),
            createTransaction(id = "2", type = TransactionType.EXPENSE, amount = BigDecimal("150.25")),
            createTransaction(id = "3", type = TransactionType.INCOME, amount = BigDecimal("1000.00"))
        )

        val totalExpenses = useCases.calculateTotalExpenses(transactions, inr)
        assertEquals(BigDecimal("450.25"), totalExpenses.amount)
        assertEquals(inr, totalExpenses.currency)
    }

    @Test
    fun transfersExcludedFromIncomeAndExpenses() {
        val transactions = listOf(
            createTransaction(id = "1", type = TransactionType.INCOME, amount = BigDecimal("1000.00")),
            createTransaction(id = "2", type = TransactionType.EXPENSE, amount = BigDecimal("400.00")),
            createTransaction(
                id = "3",
                type = TransactionType.TRANSFER,
                amount = BigDecimal("500.00"),
                destinationAccountId = accountId2
            )
        )

        val income = useCases.calculateTotalIncome(transactions, inr)
        val expense = useCases.calculateTotalExpenses(transactions, inr)

        assertEquals(BigDecimal("1000.00"), income.amount)
        assertEquals(BigDecimal("400.00"), expense.amount)
    }

    @Test
    fun negativeBalance_calculatedCorrectly_fromTransactions() {
        // Expenses exceed income
        val transactions = listOf(
            createTransaction(id = "1", type = TransactionType.INCOME, amount = BigDecimal("200.00")),
            createTransaction(id = "2", type = TransactionType.EXPENSE, amount = BigDecimal("500.00"))
        )

        val balance = useCases.calculateTotalBalance(emptyList(), transactions, inr)
        assertEquals(BigDecimal("-300.00"), balance.amount)
        assertTrue(balance.isNegative)
    }

    @Test
    fun negativeBalance_calculatedCorrectly_withNegativeAccountInitialBalance() {
        val accounts = listOf(
            createAccount(id = accountId1, initialBalance = BigDecimal("-1000.00"))
        )
        val transactions = listOf(
            createTransaction(id = "1", type = TransactionType.INCOME, amount = BigDecimal("300.00"), sourceAccountId = accountId1)
        )

        val balance = useCases.calculateTotalBalance(accounts, transactions, inr)
        assertEquals(BigDecimal("-700.00"), balance.amount)
        assertTrue(balance.isNegative)
    }

    @Test
    fun deletedTransactionsExcluded_fromAllCalculations() {
        val transactions = listOf(
            createTransaction(id = "1", type = TransactionType.INCOME, amount = BigDecimal("1000.00"), isDeleted = true),
            createTransaction(id = "2", type = TransactionType.INCOME, amount = BigDecimal("500.00"), isDeleted = false),
            createTransaction(id = "3", type = TransactionType.EXPENSE, amount = BigDecimal("800.00"), isDeleted = true),
            createTransaction(id = "4", type = TransactionType.EXPENSE, amount = BigDecimal("200.00"), isDeleted = false),
            createTransaction(id = "5", type = TransactionType.TRANSFER, amount = BigDecimal("300.00"), isDeleted = true)
        )

        val income = useCases.calculateTotalIncome(transactions, inr)
        val expense = useCases.calculateTotalExpenses(transactions, inr)
        val balance = useCases.calculateTotalBalance(emptyList(), transactions, inr)

        assertEquals(BigDecimal("500.00"), income.amount)
        assertEquals(BigDecimal("200.00"), expense.amount)
        assertEquals(BigDecimal("300.00"), balance.amount)
    }

    @Test
    fun transferBetweenAccounts_preservesTotalBalance() {
        val account1 = createAccount(id = accountId1, initialBalance = BigDecimal("1000.00"))
        val account2 = createAccount(id = accountId2, initialBalance = BigDecimal("500.00"))
        val transferTx = createTransaction(
            id = "tx-transfer",
            type = TransactionType.TRANSFER,
            amount = BigDecimal("300.00"),
            sourceAccountId = accountId1,
            destinationAccountId = accountId2
        )

        val balanceBefore = useCases.calculateTotalBalance(listOf(account1, account2), emptyList(), inr)
        val balanceAfter = useCases.calculateTotalBalance(listOf(account1, account2), listOf(transferTx), inr)

        assertEquals(BigDecimal("1500.00"), balanceBefore.amount)
        assertEquals(BigDecimal("1500.00"), balanceAfter.amount)
    }

    @Test
    fun multiCurrency_balancesCalculatedSeparately_withoutFakeConversion() {
        val inrAccount = createAccount(id = accountId1, initialBalance = BigDecimal("10000.00"), currency = inr)
        val usdAccount = createAccount(id = accountId2, initialBalance = BigDecimal("500.00"), currency = usd)

        val inrTx = createTransaction(id = "tx1", type = TransactionType.EXPENSE, amount = BigDecimal("1000.00"), currency = inr)
        val usdTx = createTransaction(id = "tx2", type = TransactionType.EXPENSE, amount = BigDecimal("50.00"), currency = usd, sourceAccountId = accountId2)

        val balances = useCases.calculateCurrencyBalances(
            accounts = listOf(inrAccount, usdAccount),
            transactions = listOf(inrTx, usdTx),
            preferredCurrency = inr
        )

        assertEquals(2, balances.size)
        // INR preferred is first
        assertEquals(inr, balances[0].currency)
        assertEquals(BigDecimal("9000.00"), balances[0].totalBalance.amount)
        assertEquals(BigDecimal("1000.00"), balances[0].totalExpenses.amount)

        // USD is second
        assertEquals(usd, balances[1].currency)
        assertEquals(BigDecimal("450.00"), balances[1].totalBalance.amount)
        assertEquals(BigDecimal("50.00"), balances[1].totalExpenses.amount)
    }

    @Test
    fun recentTransactions_sortedDescending_excludesDeleted_limitsToRequestedCount() {
        val now = Instant.now()
        val txs = listOf(
            createTransaction(id = "tx1", type = TransactionType.EXPENSE, amount = BigDecimal("10.00"), timestamp = now.minusSeconds(300)),
            createTransaction(id = "tx2", type = TransactionType.INCOME, amount = BigDecimal("100.00"), timestamp = now.minusSeconds(100)),
            createTransaction(id = "tx3_deleted", type = TransactionType.EXPENSE, amount = BigDecimal("50.00"), timestamp = now, isDeleted = true),
            createTransaction(id = "tx4", type = TransactionType.EXPENSE, amount = BigDecimal("20.00"), timestamp = now.minusSeconds(200)),
            createTransaction(id = "tx5", type = TransactionType.EXPENSE, amount = BigDecimal("30.00"), timestamp = now.minusSeconds(400)),
            createTransaction(id = "tx6", type = TransactionType.EXPENSE, amount = BigDecimal("40.00"), timestamp = now.minusSeconds(500)),
            createTransaction(id = "tx7", type = TransactionType.EXPENSE, amount = BigDecimal("60.00"), timestamp = now.minusSeconds(600))
        )

        val accountsMap = mapOf(accountId1 to createAccount(id = accountId1, initialBalance = BigDecimal.ZERO))
        val categoriesMap = mapOf(catFood.id to catFood)

        val recent = useCases.getRecentTransactions(txs, accountsMap, categoriesMap, limit = 4)

        assertEquals(4, recent.size)
        assertEquals(EntityId("tx2"), recent[0].id)
        assertEquals(EntityId("tx4"), recent[1].id)
        assertEquals(EntityId("tx1"), recent[2].id)
        assertEquals(EntityId("tx5"), recent[3].id)
        assertFalse(recent.any { it.id == EntityId("tx3_deleted") })
    }

    // --- HomeViewModel State & Loading Unit Tests ---

    @Test
    fun homeViewModel_emitsSuccessState_withCalculatedData() = runTest {
        val fakeTxRepo = FakeTransactionRepository(
            listOf(
                createTransaction(id = "1", type = TransactionType.INCOME, amount = BigDecimal("2000.00")),
                createTransaction(id = "2", type = TransactionType.EXPENSE, amount = BigDecimal("750.00"))
            )
        )
        val fakeAccountRepo = FakeAccountRepository(
            listOf(createAccount(id = accountId1, initialBalance = BigDecimal("1000.00")))
        )
        val fakeBudgetRepo = FakeBudgetRepository(emptyList())
        val fakeCategoryRepo = FakeCategoryRepository(listOf(catFood))
        val fakeRecurringRepo = FakeRecurringTransactionRepository(emptyList())
        val fakePreferencesRepo = FakePreferencesRepository(UserPreferences(currencyCode = "INR"))

        val viewModel = HomeViewModel(
            transactionRepository = fakeTxRepo,
            accountRepository = fakeAccountRepo,
            budgetRepository = fakeBudgetRepo,
            categoryRepository = fakeCategoryRepo,
            recurringTransactionRepository = fakeRecurringRepo,
            preferencesRepository = fakePreferencesRepo,
            homeUseCases = useCases
        )

        val states = mutableListOf<HomeUiState>()
        val job = launch(StandardTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states.add(it) }
        }

        advanceUntilIdle()

        val latestState = states.last()
        assertFalse(latestState.isLoading)
        assertEquals(BigDecimal("2250.00"), latestState.totalBalance.amount)
        assertEquals(BigDecimal("2000.00"), latestState.totalIncome.amount)
        assertEquals(BigDecimal("750.00"), latestState.totalExpenses.amount)
        assertFalse(latestState.hasBudget)
        assertEquals(null, latestState.errorMessage)
        assertEquals(2, latestState.recentTransactions.size)

        job.cancel()
    }

    @Test
    fun homeViewModel_emitsHasBudgetTrue_andCalculatesSnapshot_whenBudgetsExist() = runTest {
        val now = LocalDate.now()
        val fakeTxRepo = FakeTransactionRepository(
            listOf(
                createTransaction(
                    id = "tx-food",
                    type = TransactionType.EXPENSE,
                    amount = BigDecimal("1200.00"),
                    categoryId = catFood.id
                )
            )
        )
        val fakeAccountRepo = FakeAccountRepository(emptyList())
        val fakeBudgetRepo = FakeBudgetRepository(
            listOf(
                Budget(
                    id = EntityId("b-1"),
                    name = "Monthly Food",
                    targetAmount = Money.of(5000, inr),
                    categoryId = catFood.id,
                    startDate = now.withDayOfMonth(1),
                    endDate = now.withDayOfMonth(now.lengthOfMonth())
                )
            )
        )
        val fakeCategoryRepo = FakeCategoryRepository(listOf(catFood))
        val fakeRecurringRepo = FakeRecurringTransactionRepository(emptyList())
        val fakePreferencesRepo = FakePreferencesRepository(UserPreferences(currencyCode = "INR"))

        val viewModel = HomeViewModel(
            transactionRepository = fakeTxRepo,
            accountRepository = fakeAccountRepo,
            budgetRepository = fakeBudgetRepo,
            categoryRepository = fakeCategoryRepo,
            recurringTransactionRepository = fakeRecurringRepo,
            preferencesRepository = fakePreferencesRepo,
            homeUseCases = useCases
        )

        val states = mutableListOf<HomeUiState>()
        val job = launch(StandardTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states.add(it) }
        }

        advanceUntilIdle()

        val latestState = states.last()
        assertFalse(latestState.isLoading)
        assertTrue(latestState.hasBudget)
        assertEquals(1, latestState.budgetSnapshots.size)
        assertEquals("Monthly Food", latestState.budgetSnapshots[0].name)
        assertEquals(BigDecimal("1200.00"), latestState.budgetSnapshots[0].spentAmount.amount)
        assertEquals(BigDecimal("3800.00"), latestState.budgetSnapshots[0].remainingAmount.amount)
        assertFalse(latestState.budgetSnapshots[0].isOverspent)

        job.cancel()
    }

    @Test
    fun homeViewModel_emitsErrorState_whenRepositoryThrows() = runTest {
        val failingTxRepo = object : TransactionRepository by FakeTransactionRepository(emptyList()) {
            override fun getActiveTransactions(): Flow<List<Transaction>> = flow {
                throw RuntimeException("Database error")
            }
        }
        val fakeAccountRepo = FakeAccountRepository(emptyList())
        val fakeBudgetRepo = FakeBudgetRepository(emptyList())
        val fakeCategoryRepo = FakeCategoryRepository(emptyList())
        val fakeRecurringRepo = FakeRecurringTransactionRepository(emptyList())
        val fakePreferencesRepo = FakePreferencesRepository()

        val viewModel = HomeViewModel(
            transactionRepository = failingTxRepo,
            accountRepository = fakeAccountRepo,
            budgetRepository = fakeBudgetRepo,
            categoryRepository = fakeCategoryRepo,
            recurringTransactionRepository = fakeRecurringRepo,
            preferencesRepository = fakePreferencesRepo,
            homeUseCases = useCases
        )

        val states = mutableListOf<HomeUiState>()
        val job = launch(StandardTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states.add(it) }
        }

        advanceUntilIdle()

        val latestState = states.last()
        assertFalse(latestState.isLoading)
        assertNotNull(latestState.errorMessage)
        assertEquals("Database error", latestState.errorMessage)

        job.cancel()
    }

    // --- Helpers ---

    private fun createTransaction(
        id: String,
        type: TransactionType,
        amount: BigDecimal,
        currency: Currency = inr,
        sourceAccountId: EntityId = accountId1,
        destinationAccountId: EntityId? = null,
        categoryId: EntityId? = null,
        timestamp: Instant = Instant.now(),
        isDeleted: Boolean = false
    ): Transaction = Transaction(
        id = EntityId(id),
        amount = Money(amount, currency),
        type = type,
        sourceAccountId = sourceAccountId,
        destinationAccountId = destinationAccountId,
        categoryId = categoryId,
        timestamp = timestamp,
        note = "Test transaction",
        isDeleted = isDeleted
    )

    private fun createAccount(
        id: EntityId,
        initialBalance: BigDecimal,
        currency: Currency = inr
    ): Account = Account(
        id = id,
        name = "Test Account",
        type = AccountType.BANK,
        currency = currency,
        initialBalance = Money(initialBalance, currency),
        currentBalance = Money(initialBalance, currency),
        isArchived = false
    )
}

private class FakeTransactionRepository(
    transactions: List<Transaction>
) : TransactionRepository {
    private val flow = MutableStateFlow(transactions)

    override fun getActiveTransactions(): Flow<List<Transaction>> = flow
    override fun getDeletedTransactions(): Flow<List<Transaction>> = flowOf(emptyList())
    override fun getTransaction(id: EntityId): Flow<Transaction?> = flowOf(null)
    override suspend fun getTransactionById(id: EntityId): Transaction? = null
    override suspend fun insertTransaction(transaction: Transaction) {}
    override suspend fun updateTransaction(transaction: Transaction) {}
    override suspend fun moveToTrash(id: EntityId) {}
    override suspend fun restoreFromTrash(id: EntityId) {}
    override suspend fun deletePermanently(id: EntityId) {}
    override suspend fun clearTrash() {}
}

private class FakeAccountRepository(
    accounts: List<Account>
) : AccountRepository {
    private val flow = MutableStateFlow(accounts)

    override fun getActiveAccounts(): Flow<List<Account>> = flow
    override fun getAllAccounts(): Flow<List<Account>> = flow
    override fun getAccount(id: EntityId): Flow<Account?> = flowOf(null)
    override suspend fun getAccountById(id: EntityId): Account? = null
    override suspend fun insertAccount(account: Account) {}
    override suspend fun updateAccount(account: Account) {}
    override suspend fun archiveAccount(id: EntityId) {}
    override suspend fun deleteAccount(account: Account) {}
}

private class FakeBudgetRepository(
    budgets: List<Budget>
) : BudgetRepository {
    private val flow = MutableStateFlow(budgets)

    override fun getAllBudgets(): Flow<List<Budget>> = flow
    override fun getActiveBudgets(): Flow<List<Budget>> = flow
    override suspend fun getBudgetById(id: EntityId): Budget? = null
    override suspend fun insertBudget(budget: Budget) {}
    override suspend fun updateBudget(budget: Budget) {}
    override suspend fun deleteBudget(budget: Budget) {}
}

private class FakeCategoryRepository(
    categories: List<Category>
) : CategoryRepository {
    private val flow = MutableStateFlow(categories)

    override fun getActiveCategories(): Flow<List<Category>> = flow
    override fun getAllCategories(): Flow<List<Category>> = flow
    override fun getCategory(id: EntityId): Flow<Category?> = flowOf(null)
    override suspend fun getCategoryById(id: EntityId): Category? = null
    override suspend fun insertCategory(category: Category) {}
    override suspend fun updateCategory(category: Category) {}
    override suspend fun updateCategoryOrder(categories: List<Category>) {}
    override suspend fun archiveCategory(id: EntityId) {}
    override suspend fun deleteCategory(category: Category) {}
}

private class FakePreferencesRepository(
    initialPreferences: UserPreferences = UserPreferences()
) : PreferencesRepository {
    override val userPreferences = MutableStateFlow(initialPreferences)

    override suspend fun setThemeMode(themeMode: ThemeMode) {}
    override suspend fun setDynamicColorEnabled(enabled: Boolean) {}
    override suspend fun setCustomAccentColor(color: Long?) {}
    override suspend fun clearCustomAccentColor() {}
    override suspend fun setCurrencyCode(currencyCode: String) {}
}

private class FakeRecurringTransactionRepository(
    recurring: List<com.example.expensetracker.core.model.recurring.RecurringTransaction> = emptyList()
) : com.example.expensetracker.core.data.repository.RecurringTransactionRepository {
    private val flow = MutableStateFlow(recurring)

    override fun getAllRecurringTransactions(): Flow<List<com.example.expensetracker.core.model.recurring.RecurringTransaction>> = flow
    override fun getActiveRecurringTransactions(): Flow<List<com.example.expensetracker.core.model.recurring.RecurringTransaction>> = flow
    override suspend fun getRecurringTransactionById(id: EntityId): com.example.expensetracker.core.model.recurring.RecurringTransaction? = null
    override suspend fun insertRecurringTransaction(recurringTransaction: com.example.expensetracker.core.model.recurring.RecurringTransaction) {}
    override suspend fun updateRecurringTransaction(recurringTransaction: com.example.expensetracker.core.model.recurring.RecurringTransaction) {}
    override suspend fun setEnabled(id: EntityId, isEnabled: Boolean) {}
    override suspend fun deleteRecurringTransaction(recurringTransaction: com.example.expensetracker.core.model.recurring.RecurringTransaction) {}
}


package com.example.expensetracker.feature.statistics

import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.account.AccountType
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeTransactionRepository: FakeTransactionRepository
    private lateinit var fakeCategoryRepository: FakeCategoryRepository
    private lateinit var fakeAccountRepository: FakeAccountRepository
    private lateinit var fakePreferencesRepository: FakePreferencesRepository
    private lateinit var viewModel: StatisticsViewModel

    private val checkingAccount = Account(
        id = EntityId("acc_checking"),
        name = "Checking",
        type = AccountType.BANK,
        currency = Currency.USD,
        initialBalance = Money(BigDecimal("1000.00"), Currency.USD),
        currentBalance = Money(BigDecimal("1000.00"), Currency.USD)
    )

    private val foodCategory = Category(
        id = EntityId("cat_food"),
        name = "Groceries",
        iconKey = "shopping_cart",
        colorKey = "category_orange",
        type = CategoryType.EXPENSE
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeTransactionRepository = FakeTransactionRepository()
        fakeCategoryRepository = FakeCategoryRepository(listOf(foodCategory))
        fakeAccountRepository = FakeAccountRepository(listOf(checkingAccount))
        fakePreferencesRepository = FakePreferencesRepository(
            UserPreferences(currencyCode = "USD")
        )

        viewModel = StatisticsViewModel(
            transactionRepository = fakeTransactionRepository,
            categoryRepository = fakeCategoryRepository,
            accountRepository = fakeAccountRepository,
            preferencesRepository = fakePreferencesRepository,
            useCases = StatisticsUseCases()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsDataSuccessfully() = runTest {
        val now = Instant.now()
        val tx1 = Transaction(
            id = EntityId("tx1"),
            amount = Money(BigDecimal("150.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = checkingAccount.id,
            categoryId = foodCategory.id,
            timestamp = now
        )
        val tx2 = Transaction(
            id = EntityId("tx2"),
            amount = Money(BigDecimal("500.00"), Currency.USD),
            type = TransactionType.INCOME,
            sourceAccountId = checkingAccount.id,
            timestamp = now
        )
        fakeTransactionRepository.transactions.value = listOf(tx1, tx2)

        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(Currency.USD, state.selectedCurrency)
        assertEquals(BigDecimal("500.00"), state.totalIncome.amount)
        assertEquals(BigDecimal("150.00"), state.totalExpense.amount)
        assertEquals(BigDecimal("350.00"), state.netChange.amount)
        assertEquals(2, state.transactionCount)
        assertFalse(state.hasExcludedCurrencies)

        job.cancel()
    }

    @Test
    fun selectDateRangePreset_updatesPresetFilter() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.selectDateRangePreset(DateRangePreset.THIS_YEAR)
        advanceUntilIdle()

        assertEquals(DateRangePreset.THIS_YEAR, viewModel.uiState.value.selectedDateRange.preset)

        job.cancel()
    }

    @Test
    fun selectDateRangePreset_custom_opensDatePicker() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCustomDatePickerOpen)

        viewModel.selectDateRangePreset(DateRangePreset.CUSTOM)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCustomDatePickerOpen)

        viewModel.dismissCustomDatePicker()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCustomDatePickerOpen)

        job.cancel()
    }

    @Test
    fun setCustomDateRange_setsDatesAndClosesDialog() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 6, 30)
        viewModel.setCustomDateRange(start, end)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DateRangePreset.CUSTOM, state.selectedDateRange.preset)
        assertEquals(start, state.selectedDateRange.startDate)
        assertEquals(end, state.selectedDateRange.endDate)
        assertFalse(state.isCustomDatePickerOpen)

        job.cancel()
    }

    @Test
    fun selectCurrency_filtersTransactionsByNewCurrency() = runTest {
        val now = Instant.now()
        val usdTx = Transaction(
            id = EntityId("tx_usd"),
            amount = Money(BigDecimal("100.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = checkingAccount.id,
            timestamp = now
        )
        val inrTx = Transaction(
            id = EntityId("tx_inr"),
            amount = Money(BigDecimal("5000.00"), Currency.INR),
            type = TransactionType.EXPENSE,
            sourceAccountId = checkingAccount.id,
            timestamp = now
        )
        fakeTransactionRepository.transactions.value = listOf(usdTx, inrTx)

        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(Currency.USD, state.selectedCurrency)
        assertEquals(BigDecimal("100.00"), state.totalExpense.amount)
        assertTrue(state.hasExcludedCurrencies)
        assertEquals(1, state.excludedCurrenciesCount)

        // Switch to INR
        viewModel.selectCurrency(Currency.INR)
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(Currency.INR, state.selectedCurrency)
        assertEquals(BigDecimal("5000.00"), state.totalExpense.amount)
        assertTrue(state.hasExcludedCurrencies)
        assertEquals(1, state.excludedCurrenciesCount)

        job.cancel()
    }

    @Test
    fun toggleCurrencyMenu_updatesMenuOpenState() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCurrencyMenuOpen)

        viewModel.toggleCurrencyMenu(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCurrencyMenuOpen)

        viewModel.toggleCurrencyMenu(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCurrencyMenuOpen)

        job.cancel()
    }
}

// --- Test doubles ---

private class FakeTransactionRepository : TransactionRepository {
    val transactions = MutableStateFlow<List<Transaction>>(emptyList())

    override fun getActiveTransactions(): Flow<List<Transaction>> = transactions
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

private class FakeCategoryRepository(
    initialCategories: List<Category> = emptyList()
) : CategoryRepository {
    private val categories = MutableStateFlow(initialCategories)

    override fun getActiveCategories(): Flow<List<Category>> = categories
    override fun getAllCategories(): Flow<List<Category>> = categories
    override fun getCategory(id: EntityId): Flow<Category?> = flowOf(null)
    override suspend fun getCategoryById(id: EntityId): Category? = null
    override suspend fun insertCategory(category: Category) {}
    override suspend fun updateCategory(category: Category) {}
    override suspend fun updateCategoryOrder(categories: List<Category>) {}
    override suspend fun archiveCategory(id: EntityId) {}
    override suspend fun deleteCategory(category: Category) {}
}

private class FakeAccountRepository(
    initialAccounts: List<Account> = emptyList()
) : AccountRepository {
    private val accounts = MutableStateFlow(initialAccounts)

    override fun getActiveAccounts(): Flow<List<Account>> = accounts
    override fun getAllAccounts(): Flow<List<Account>> = accounts
    override fun getAccount(id: EntityId): Flow<Account?> = flowOf(null)
    override suspend fun getAccountById(id: EntityId): Account? = null
    override suspend fun insertAccount(account: Account) {}
    override suspend fun updateAccount(account: Account) {}
    override suspend fun archiveAccount(id: EntityId) {}
    override suspend fun deleteAccount(account: Account) {}
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

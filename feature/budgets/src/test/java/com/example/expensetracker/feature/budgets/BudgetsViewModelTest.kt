package com.example.expensetracker.feature.budgets

import com.example.expensetracker.core.common.time.DateTimeProvider
import com.example.expensetracker.core.data.repository.BudgetRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.budget.Budget
import com.example.expensetracker.core.model.category.Category
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testZoneId = ZoneOffset.UTC

    private lateinit var budgetRepository: FakeBudgetRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var preferencesRepository: FakePreferencesRepository
    private lateinit var dateTimeProvider: TestDateTimeProvider
    private lateinit var budgetUseCases: BudgetUseCases
    private lateinit var viewModel: BudgetsViewModel

    private val inr = Currency.INR
    private val usd = Currency.USD
    private val foodCategory = Category(
        id = EntityId("cat-food"),
        name = "Food",
        iconKey = "food",
        colorKey = "color_food"
    )
    private val billsCategory = Category(
        id = EntityId("cat-bills"),
        name = "Bills",
        iconKey = "bills",
        colorKey = "color_bills"
    )

    private val now = LocalDate.of(2026, 9, 15)
    private val startOfMonth = LocalDate.of(2026, 9, 1)
    private val endOfMonth = LocalDate.of(2026, 9, 30)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        budgetRepository = FakeBudgetRepository()
        transactionRepository = FakeTransactionRepository()
        categoryRepository = FakeCategoryRepository(listOf(foodCategory, billsCategory))
        preferencesRepository = FakePreferencesRepository()
        dateTimeProvider = TestDateTimeProvider(now, testZoneId)
        budgetUseCases = BudgetUseCases()

        viewModel = BudgetsViewModel(
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            preferencesRepository = preferencesRepository,
            dateTimeProvider = dateTimeProvider,
            budgetUseCases = budgetUseCases
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createExpense(
        id: String,
        amount: String,
        currency: Currency = inr,
        categoryId: EntityId? = foodCategory.id,
        date: LocalDate = now,
        isDeleted: Boolean = false
    ): Transaction {
        return Transaction(
            id = EntityId(id),
            amount = Money(BigDecimal(amount), currency),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc-1"),
            categoryId = categoryId,
            timestamp = date.atStartOfDay(testZoneId).toInstant(),
            isDeleted = isDeleted
        )
    }

    @Test
    fun noBudgets_emitsEmptyListAndNotLoading() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.budgets.isEmpty())
        assertFalse(state.isFormOpen)

        collectJob.cancel()
    }

    @Test
    fun overallBudget_calculatesExpensesCorrectly() = runTest {
        val overallBudget = Budget(
            id = EntityId("budget-1"),
            name = "September Budget",
            targetAmount = Money(BigDecimal("10000.00"), inr),
            categoryId = null,
            startDate = startOfMonth,
            endDate = endOfMonth,
            isEnabled = true
        )
        budgetRepository.budgetsFlow.value = listOf(overallBudget)

        // Multiple expenses in different categories
        transactionRepository.transactionsFlow.value = listOf(
            createExpense("tx-1", "3000.00", categoryId = foodCategory.id),
            createExpense("tx-2", "2500.00", categoryId = billsCategory.id)
        )

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.budgets.size)

        val item = state.budgets[0]
        assertEquals(BigDecimal("5500.00"), item.spent.amount)
        assertEquals(BigDecimal("4500.00"), item.remaining.amount)
        assertEquals(55, item.progressPercentage)
        assertFalse(item.isOverspent)
        assertNull(item.categoryName)

        collectJob.cancel()
    }

    @Test
    fun categoryBudget_countsOnlyMatchingCategoryExpenses() = runTest {
        val foodBudget = Budget(
            id = EntityId("budget-food"),
            name = "Food Budget",
            targetAmount = Money(BigDecimal("5000.00"), inr),
            categoryId = foodCategory.id,
            startDate = startOfMonth,
            endDate = endOfMonth,
            isEnabled = true
        )
        budgetRepository.budgetsFlow.value = listOf(foodBudget)

        transactionRepository.transactionsFlow.value = listOf(
            createExpense("tx-1", "2000.00", categoryId = foodCategory.id),
            createExpense("tx-2", "1500.00", categoryId = billsCategory.id) // different category
        )

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val item = state.budgets[0]
        assertEquals(BigDecimal("2000.00"), item.spent.amount)
        assertEquals(BigDecimal("3000.00"), item.remaining.amount)
        assertEquals("Food", item.categoryName)

        collectJob.cancel()
    }

    @Test
    fun transfersAndIncome_areExcludedFromSpending() = runTest {
        val budget = Budget(
            id = EntityId("budget-1"),
            name = "All Expenses",
            targetAmount = Money(BigDecimal("5000.00"), inr),
            categoryId = null,
            startDate = startOfMonth,
            endDate = endOfMonth,
            isEnabled = true
        )
        budgetRepository.budgetsFlow.value = listOf(budget)

        transactionRepository.transactionsFlow.value = listOf(
            createExpense("tx-1", "1000.00"),
            Transaction(
                id = EntityId("tx-income"),
                amount = Money(BigDecimal("50000.00"), inr),
                type = TransactionType.INCOME,
                sourceAccountId = EntityId("acc-1"),
                timestamp = now.atStartOfDay(testZoneId).toInstant()
            ),
            Transaction(
                id = EntityId("tx-transfer"),
                amount = Money(BigDecimal("5000.00"), inr),
                type = TransactionType.TRANSFER,
                sourceAccountId = EntityId("acc-1"),
                destinationAccountId = EntityId("acc-2"),
                timestamp = now.atStartOfDay(testZoneId).toInstant()
            )
        )

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val item = viewModel.uiState.value.budgets[0]
        assertEquals(BigDecimal("1000.00"), item.spent.amount)
        assertEquals(BigDecimal("4000.00"), item.remaining.amount)

        collectJob.cancel()
    }

    @Test
    fun deletedTransactions_areExcludedFromSpending() = runTest {
        val budget = Budget(
            id = EntityId("budget-1"),
            name = "Active Only",
            targetAmount = Money(BigDecimal("5000.00"), inr),
            categoryId = null,
            startDate = startOfMonth,
            endDate = endOfMonth,
            isEnabled = true
        )
        budgetRepository.budgetsFlow.value = listOf(budget)

        transactionRepository.transactionsFlow.value = listOf(
            createExpense("tx-active", "1000.00", isDeleted = false),
            createExpense("tx-deleted", "2000.00", isDeleted = true)
        )

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val item = viewModel.uiState.value.budgets[0]
        assertEquals(BigDecimal("1000.00"), item.spent.amount)

        collectJob.cancel()
    }

    @Test
    fun dateBoundaries_includeExactStartAndEnd_excludeOutside() = runTest {
        val budget = Budget(
            id = EntityId("budget-1"),
            name = "Sept Interval",
            targetAmount = Money(BigDecimal("10000.00"), inr),
            categoryId = null,
            startDate = startOfMonth,
            endDate = endOfMonth,
            isEnabled = true
        )
        budgetRepository.budgetsFlow.value = listOf(budget)

        transactionRepository.transactionsFlow.value = listOf(
            createExpense("tx-start", "1000.00", date = startOfMonth), // exact start date
            createExpense("tx-end", "2000.00", date = endOfMonth), // exact end date
            createExpense("tx-before", "500.00", date = startOfMonth.minusDays(1)), // outside before
            createExpense("tx-after", "700.00", date = endOfMonth.plusDays(1)) // outside after
        )

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val item = viewModel.uiState.value.budgets[0]
        assertEquals(BigDecimal("3000.00"), item.spent.amount)

        collectJob.cancel()
    }

    @Test
    fun differentCurrency_isExcludedFromSpending() = runTest {
        val inrBudget = Budget(
            id = EntityId("budget-inr"),
            name = "INR Budget",
            targetAmount = Money(BigDecimal("10000.00"), inr),
            categoryId = null,
            startDate = startOfMonth,
            endDate = endOfMonth,
            isEnabled = true
        )
        budgetRepository.budgetsFlow.value = listOf(inrBudget)

        transactionRepository.transactionsFlow.value = listOf(
            createExpense("tx-inr", "1000.00", currency = inr),
            createExpense("tx-usd", "50.00", currency = usd) // USD transaction
        )

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val item = viewModel.uiState.value.budgets[0]
        assertEquals(BigDecimal("1000.00"), item.spent.amount)
        assertEquals(1, item.excludedDifferentCurrencyCount)

        collectJob.cancel()
    }

    @Test
    fun overspending_calculatesNegativeRemainingAndOver100Percent() = runTest {
        val budget = Budget(
            id = EntityId("budget-1"),
            name = "Small Cap",
            targetAmount = Money(BigDecimal("5000.00"), inr),
            categoryId = null,
            startDate = startOfMonth,
            endDate = endOfMonth,
            isEnabled = true
        )
        budgetRepository.budgetsFlow.value = listOf(budget)

        transactionRepository.transactionsFlow.value = listOf(
            createExpense("tx-1", "5500.00")
        )

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val item = viewModel.uiState.value.budgets[0]
        assertEquals(BigDecimal("5500.00"), item.spent.amount)
        assertEquals(BigDecimal("-500.00"), item.remaining.amount)
        assertTrue(item.isOverspent)
        assertEquals(110, item.progressPercentage)
        assertEquals(1.0f, item.progressClampedFraction, 0.01f)

        collectJob.cancel()
    }

    @Test
    fun multipleOverlappingBudgets_calculateCorrectly() = runTest {
        val overallBudget = Budget(
            id = EntityId("budget-overall"),
            name = "Overall",
            targetAmount = Money(BigDecimal("10000.00"), inr),
            categoryId = null,
            startDate = startOfMonth,
            endDate = endOfMonth,
            isEnabled = true
        )
        val foodBudget = Budget(
            id = EntityId("budget-food"),
            name = "Food",
            targetAmount = Money(BigDecimal("4000.00"), inr),
            categoryId = foodCategory.id,
            startDate = startOfMonth,
            endDate = endOfMonth,
            isEnabled = true
        )
        budgetRepository.budgetsFlow.value = listOf(overallBudget, foodBudget)

        // A food transaction contributes to BOTH overall and food budget
        transactionRepository.transactionsFlow.value = listOf(
            createExpense("tx-food", "2000.00", categoryId = foodCategory.id),
            createExpense("tx-bills", "3000.00", categoryId = billsCategory.id)
        )

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val overallItem = state.budgets.find { it.budget.id == overallBudget.id }!!
        val foodItem = state.budgets.find { it.budget.id == foodBudget.id }!!

        assertEquals(BigDecimal("5000.00"), overallItem.spent.amount)
        assertEquals(BigDecimal("2000.00"), foodItem.spent.amount)

        collectJob.cancel()
    }

    @Test
    fun disabledBudget_reflectsDisabledState() = runTest {
        val disabledBudget = Budget(
            id = EntityId("budget-disabled"),
            name = "Paused",
            targetAmount = Money(BigDecimal("5000.00"), inr),
            categoryId = null,
            startDate = startOfMonth,
            endDate = endOfMonth,
            isEnabled = false
        )
        budgetRepository.budgetsFlow.value = listOf(disabledBudget)

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val item = viewModel.uiState.value.budgets[0]
        assertFalse(item.budget.isEnabled)

        viewModel.toggleBudgetEnabled(disabledBudget)
        advanceUntilIdle()

        assertTrue(budgetRepository.budgetsFlow.value[0].isEnabled)

        collectJob.cancel()
    }

    @Test
    fun missingOrArchivedCategory_handlesGracefullyWithoutCrash() = runTest {
        val orphanedBudget = Budget(
            id = EntityId("budget-orphaned"),
            name = "Old Category Budget",
            targetAmount = Money(BigDecimal("5000.00"), inr),
            categoryId = EntityId("non-existent-category-id"),
            startDate = startOfMonth,
            endDate = endOfMonth,
            isEnabled = true
        )
        budgetRepository.budgetsFlow.value = listOf(orphanedBudget)

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val item = viewModel.uiState.value.budgets[0]
        assertNull(item.categoryName) // Clean fallback, no crash

        collectJob.cancel()
    }

    @Test
    fun createEditDelete_budgetOperations() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // 1. Open create form
        viewModel.openCreateForm()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFormOpen)
        assertNotNull(viewModel.uiState.value.formState)

        // 2. Set form values and save
        viewModel.onFormNameChange("My New Budget")
        viewModel.onFormAmountChange("5000")
        viewModel.onFormCurrencyChange(inr)
        viewModel.saveBudget()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFormOpen)
        assertEquals(1, budgetRepository.budgetsFlow.value.size)
        val created = budgetRepository.budgetsFlow.value[0]
        assertEquals("My New Budget", created.name)
        assertEquals(BigDecimal("5000"), created.targetAmount.amount)

        // 3. Edit budget
        viewModel.openEditForm(created)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFormOpen)
        assertTrue(viewModel.uiState.value.formState!!.isEditing)

        viewModel.onFormNameChange("Updated Budget")
        viewModel.saveBudget()
        advanceUntilIdle()

        assertEquals("Updated Budget", budgetRepository.budgetsFlow.value[0].name)

        // 4. Delete budget
        viewModel.showDeleteConfirmation(created)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.deleteConfirmationBudget)

        viewModel.confirmDeleteBudget()
        advanceUntilIdle()

        assertTrue(budgetRepository.budgetsFlow.value.isEmpty())
        assertNull(viewModel.uiState.value.deleteConfirmationBudget)

        collectJob.cancel()
    }
}

// --- Fakes for Testing ---

class FakeBudgetRepository : BudgetRepository {
    val budgetsFlow = MutableStateFlow<List<Budget>>(emptyList())

    override fun getAllBudgets(): Flow<List<Budget>> = budgetsFlow.asStateFlow()
    override fun getActiveBudgets(): Flow<List<Budget>> = budgetsFlow.asStateFlow()

    override suspend fun getBudgetById(id: EntityId): Budget? =
        budgetsFlow.value.find { it.id == id }

    override suspend fun insertBudget(budget: Budget) {
        budgetsFlow.update { it + budget }
    }

    override suspend fun updateBudget(budget: Budget) {
        budgetsFlow.update { list -> list.map { if (it.id == budget.id) budget else it } }
    }

    override suspend fun deleteBudget(budget: Budget) {
        budgetsFlow.update { list -> list.filter { it.id != budget.id } }
    }
}

class FakeTransactionRepository : TransactionRepository {
    val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())

    override fun getActiveTransactions(): Flow<List<Transaction>> = transactionsFlow.asStateFlow()
    override fun getDeletedTransactions(): Flow<List<Transaction>> = MutableStateFlow(emptyList())
    override fun getTransaction(id: EntityId): Flow<Transaction?> = MutableStateFlow(null)
    override suspend fun getTransactionById(id: EntityId): Transaction? = null
    override suspend fun insertTransaction(transaction: Transaction) {}
    override suspend fun updateTransaction(transaction: Transaction) {}
    override suspend fun moveToTrash(id: EntityId) {}
    override suspend fun restoreFromTrash(id: EntityId) {}
    override suspend fun deletePermanently(id: EntityId) {}
    override suspend fun clearTrash() {}
}

class FakeCategoryRepository(
    initialCategories: List<Category> = emptyList()
) : CategoryRepository {
    val categoriesFlow = MutableStateFlow(initialCategories)

    override fun getActiveCategories(): Flow<List<Category>> = categoriesFlow.asStateFlow()
    override fun getAllCategories(): Flow<List<Category>> = categoriesFlow.asStateFlow()
    override fun getCategory(id: EntityId): Flow<Category?> = MutableStateFlow(null)
    override suspend fun getCategoryById(id: EntityId): Category? = categoriesFlow.value.find { it.id == id }
    override suspend fun insertCategory(category: Category) {}
    override suspend fun updateCategory(category: Category) {}
    override suspend fun archiveCategory(id: EntityId) {}
    override suspend fun deleteCategory(category: Category) {}
}

private class FakePreferencesRepository : PreferencesRepository {
    val preferencesFlow = MutableStateFlow(UserPreferences.DEFAULT)

    override val userPreferences: Flow<UserPreferences> = preferencesFlow.asStateFlow()
    override suspend fun setThemeMode(themeMode: ThemeMode) {}
    override suspend fun setDynamicColorEnabled(enabled: Boolean) {}
    override suspend fun setCustomAccentColor(color: Long?) {}
    override suspend fun clearCustomAccentColor() {}
    override suspend fun setCurrencyCode(currencyCode: String) {}
}

private class TestDateTimeProvider(
    private val todayDate: LocalDate,
    private val zoneId: ZoneId
) : DateTimeProvider {
    override fun nowInstant(): Instant = todayDate.atStartOfDay(zoneId).toInstant()
    override fun today(zoneId: ZoneId): LocalDate = todayDate
    override fun nowLocalDateTime(zoneId: ZoneId): LocalDateTime = todayDate.atStartOfDay()
    override fun currentZoneId(): ZoneId = zoneId
}

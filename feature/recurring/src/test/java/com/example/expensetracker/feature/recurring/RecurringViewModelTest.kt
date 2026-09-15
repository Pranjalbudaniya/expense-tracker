package com.example.expensetracker.feature.recurring

import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.RecurringTransactionRepository
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.category.CategoryType
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.recurring.RecurringTransaction
import com.example.expensetracker.core.model.transaction.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RecurringViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRecurringRepo: FakeRecurringRepository
    private lateinit var fakeAccountRepo: FakeAccountRepository
    private lateinit var fakeCategoryRepo: FakeCategoryRepository
    private lateinit var viewModel: RecurringViewModel

    private val accountBank = Account(
        id = EntityId("acc-bank"),
        name = "Bank Account",
        type = AccountType.BANK,
        currency = Currency.USD,
        initialBalance = Money(BigDecimal("2000.00"), Currency.USD),
        currentBalance = Money(BigDecimal("2000.00"), Currency.USD)
    )

    private val accountSavings = Account(
        id = EntityId("acc-savings"),
        name = "Savings",
        type = AccountType.BANK,
        currency = Currency.USD,
        initialBalance = Money(BigDecimal("5000.00"), Currency.USD),
        currentBalance = Money(BigDecimal("5000.00"), Currency.USD)
    )

    private val accountEur = Account(
        id = EntityId("acc-eur"),
        name = "EUR Account",
        type = AccountType.BANK,
        currency = Currency.EUR,
        initialBalance = Money(BigDecimal("1000.00"), Currency.EUR),
        currentBalance = Money(BigDecimal("1000.00"), Currency.EUR)
    )

    private val catRent = Category(
        id = EntityId("cat-rent"),
        name = "Rent",
        iconKey = "home",
        colorKey = "category_blue",
        type = CategoryType.EXPENSE
    )

    private val recurringRent = RecurringTransaction(
        id = EntityId("rec-rent"),
        amount = Money(BigDecimal("1200.00"), Currency.USD),
        type = TransactionType.EXPENSE,
        sourceAccountId = accountBank.id,
        categoryId = catRent.id,
        note = "Apartment rent",
        frequency = RecurrenceFrequency.MONTHLY,
        startDate = LocalDate.of(2026, 1, 1),
        nextOccurrence = LocalDate.of(2026, 10, 1),
        isEnabled = true
    )

    private val recurringTransfer = RecurringTransaction(
        id = EntityId("rec-transfer"),
        amount = Money(BigDecimal("300.00"), Currency.USD),
        type = TransactionType.TRANSFER,
        sourceAccountId = accountBank.id,
        destinationAccountId = accountSavings.id,
        note = "Monthly savings",
        frequency = RecurrenceFrequency.MONTHLY,
        startDate = LocalDate.of(2026, 1, 1),
        nextOccurrence = LocalDate.of(2026, 10, 1),
        isEnabled = false // Paused
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeRecurringRepo = FakeRecurringRepository(listOf(recurringRent, recurringTransfer))
        fakeAccountRepo = FakeAccountRepository(listOf(accountBank, accountSavings, accountEur))
        fakeCategoryRepo = FakeCategoryRepository(listOf(catRent))

        viewModel = RecurringViewModel(
            recurringRepository = fakeRecurringRepo,
            accountRepository = fakeAccountRepo,
            categoryRepository = fakeCategoryRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Validation Tests ---

    @Test
    fun validate_amountZeroOrNegative_fails() {
        val resultZero = RecurringUseCases.validate(
            amount = BigDecimal.ZERO,
            currency = Currency.USD,
            type = TransactionType.EXPENSE,
            sourceAccountId = accountBank.id,
            destinationAccountId = null,
            startDate = LocalDate.now(),
            nextOccurrence = LocalDate.now(),
            endDate = null
        )
        assertFalse(resultZero.isValid)
        assertNotNull(resultZero.amountError)

        val resultNull = RecurringUseCases.validate(
            amount = null,
            currency = Currency.USD,
            type = TransactionType.EXPENSE,
            sourceAccountId = accountBank.id,
            destinationAccountId = null,
            startDate = LocalDate.now(),
            nextOccurrence = LocalDate.now(),
            endDate = null
        )
        assertFalse(resultNull.isValid)
    }

    @Test
    fun validate_transferSameAccount_fails() {
        val result = RecurringUseCases.validate(
            amount = BigDecimal("100.00"),
            currency = Currency.USD,
            type = TransactionType.TRANSFER,
            sourceAccountId = accountBank.id,
            destinationAccountId = accountBank.id, // Same account
            startDate = LocalDate.now(),
            nextOccurrence = LocalDate.now(),
            endDate = null
        )
        assertFalse(result.isValid)
        assertTrue(result.destinationAccountError?.contains("different") == true)
    }

    @Test
    fun validate_endDateBeforeStartDate_fails() {
        val start = LocalDate.of(2026, 5, 1)
        val end = LocalDate.of(2026, 4, 1) // Before start
        val result = RecurringUseCases.validate(
            amount = BigDecimal("100.00"),
            currency = Currency.USD,
            type = TransactionType.EXPENSE,
            sourceAccountId = accountBank.id,
            destinationAccountId = null,
            startDate = start,
            nextOccurrence = start,
            endDate = end
        )
        assertFalse(result.isValid)
        assertTrue(result.dateError?.contains("End date cannot be before start date") == true)
    }

    @Test
    fun validate_validInputs_passes() {
        val start = LocalDate.of(2026, 5, 1)
        val result = RecurringUseCases.validate(
            amount = BigDecimal("100.00"),
            currency = Currency.USD,
            type = TransactionType.EXPENSE,
            sourceAccountId = accountBank.id,
            destinationAccountId = null,
            startDate = start,
            nextOccurrence = start,
            endDate = null
        )
        assertTrue(result.isValid)
    }

    // --- ViewModel State & Operations Tests ---

    @Test
    fun initialUiState_loadsAndGroupsActiveAndPausedItems() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.activeItems.size)
        assertEquals("Apartment rent", state.activeItems[0].recurring.note)
        assertEquals("Bank Account", state.activeItems[0].sourceAccountName)
        assertEquals("Rent", state.activeItems[0].categoryName)

        assertEquals(1, state.pausedItems.size)
        assertEquals("Monthly savings", state.pausedItems[0].recurring.note)
        assertEquals("Bank Account", state.pausedItems[0].sourceAccountName)
        assertEquals("Savings", state.pausedItems[0].destinationAccountName)

        job.cancel()
    }

    @Test
    fun openCreateForm_initializesWithDefaults() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.openCreateForm()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isFormOpen)
        assertNotNull(state.formState)
        assertEquals(TransactionType.EXPENSE, state.formState?.type)
        assertEquals(accountBank.id, state.formState?.sourceAccountId)
        assertEquals(RecurrenceFrequency.MONTHLY, state.formState?.frequency)
        assertFalse(state.formState!!.isEditing)

        job.cancel()
    }

    @Test
    fun openEditForm_populatesExistingData() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.openEditForm(recurringRent)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isFormOpen)
        val form = state.formState!!
        assertTrue(form.isEditing)
        assertEquals(recurringRent.id, form.id)
        assertEquals("1200.00", form.amountText)
        assertEquals("Apartment rent", form.note)

        job.cancel()
    }

    @Test
    fun saveRecurring_createsNewRecurringTransaction() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.openCreateForm()
        viewModel.onFormAmountChange("15.99")
        viewModel.onFormNoteChange("Spotify Subscription")
        viewModel.onFormFrequencyChange(RecurrenceFrequency.MONTHLY)
        viewModel.onFormCategoryChange(catRent.id)

        viewModel.saveRecurring()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFormOpen)
        val created = fakeRecurringRepo.recurringMap.values.firstOrNull { it.note == "Spotify Subscription" }
        assertNotNull(created)
        assertEquals(BigDecimal("15.99"), created?.amount?.amount)
        assertEquals(RecurrenceFrequency.MONTHLY, created?.frequency)

        job.cancel()
    }

    @Test
    fun saveRecurring_updatesExistingRecurringTransaction() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.openEditForm(recurringRent)
        viewModel.onFormAmountChange("1350.00")
        viewModel.onFormNoteChange("Updated Rent")

        viewModel.saveRecurring()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFormOpen)
        val updated = fakeRecurringRepo.getRecurringTransactionById(recurringRent.id)
        assertNotNull(updated)
        assertEquals(BigDecimal("1350.00"), updated?.amount?.amount)
        assertEquals("Updated Rent", updated?.note)

        job.cancel()
    }

    @Test
    fun toggleEnabled_updatesRepositoryStatus() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.toggleEnabled(recurringRent) // was true -> becomes false
        advanceUntilIdle()

        val updated = fakeRecurringRepo.getRecurringTransactionById(recurringRent.id)
        assertFalse(updated!!.isEnabled)

        job.cancel()
    }

    @Test
    fun deleteRecurring_deletesFromRepository() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.requestDelete(recurringRent)
        advanceUntilIdle()
        assertEquals(recurringRent, viewModel.uiState.value.recurringToDelete)

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.recurringToDelete)
        assertNull(fakeRecurringRepo.getRecurringTransactionById(recurringRent.id))

        job.cancel()
    }

    @Test
    fun recurringTransfer_differentCurrencies_failsValidation() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.openCreateForm()
        advanceUntilIdle()

        viewModel.onFormTypeChange(TransactionType.TRANSFER)
        viewModel.onFormAmountChange("50.00")
        viewModel.onFormSourceAccountChange(accountBank.id) // USD
        viewModel.onFormDestinationAccountChange(accountEur.id) // EUR
        advanceUntilIdle()

        val formState = viewModel.uiState.value.formState
        assertNotNull(formState)
        assertEquals("Source and destination accounts must have the same currency", formState!!.destinationAccountError)

        viewModel.saveRecurring()
        advanceUntilIdle()

        // Form remains open, rule was not inserted
        assertTrue(viewModel.uiState.value.isFormOpen)
        assertEquals(2, fakeRecurringRepo.recurringMap.size)

        // Selecting same currency account clears error
        viewModel.onFormDestinationAccountChange(accountSavings.id) // USD
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.formState?.destinationAccountError)

        job.cancel()
    }
}

// --- Test Fakes ---

private class FakeRecurringRepository(
    initial: List<RecurringTransaction> = emptyList()
) : RecurringTransactionRepository {
    val recurringMap = mutableMapOf<EntityId, RecurringTransaction>()
    private val flow = MutableStateFlow<List<RecurringTransaction>>(emptyList())

    init {
        for (item in initial) recurringMap[item.id] = item
        updateFlow()
    }

    private fun updateFlow() {
        flow.value = recurringMap.values.toList()
    }

    override fun getAllRecurringTransactions(): Flow<List<RecurringTransaction>> = flow

    override fun getActiveRecurringTransactions(): Flow<List<RecurringTransaction>> {
        return flow.map { list -> list.filter { it.isEnabled } }
    }

    override suspend fun getRecurringTransactionById(id: EntityId): RecurringTransaction? {
        return recurringMap[id]
    }

    override suspend fun insertRecurringTransaction(recurringTransaction: RecurringTransaction) {
        recurringMap[recurringTransaction.id] = recurringTransaction
        updateFlow()
    }

    override suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransaction) {
        recurringMap[recurringTransaction.id] = recurringTransaction
        updateFlow()
    }

    override suspend fun setEnabled(id: EntityId, isEnabled: Boolean) {
        recurringMap[id]?.let {
            recurringMap[id] = it.copy(isEnabled = isEnabled)
            updateFlow()
        }
    }

    override suspend fun deleteRecurringTransaction(recurringTransaction: RecurringTransaction) {
        recurringMap.remove(recurringTransaction.id)
        updateFlow()
    }
}

private class FakeAccountRepository(
    private val accounts: List<Account>
) : AccountRepository {
    override fun getActiveAccounts(): Flow<List<Account>> = flowOf(accounts.filter { !it.isArchived })
    override fun getAllAccounts(): Flow<List<Account>> = flowOf(accounts)
    override fun getAccount(id: EntityId): Flow<Account?> = flowOf(accounts.firstOrNull { it.id == id })
    override suspend fun getAccountById(id: EntityId): Account? = accounts.firstOrNull { it.id == id }
    override suspend fun insertAccount(account: Account) {}
    override suspend fun updateAccount(account: Account) {}
    override suspend fun archiveAccount(id: EntityId) {}
    override suspend fun deleteAccount(account: Account) {}
}

private class FakeCategoryRepository(
    private val categories: List<Category>
) : CategoryRepository {
    override fun getActiveCategories(): Flow<List<Category>> = flowOf(categories.filter { !it.isArchived })
    override fun getAllCategories(): Flow<List<Category>> = flowOf(categories)
    override fun getCategory(id: EntityId): Flow<Category?> = flowOf(categories.firstOrNull { it.id == id })
    override suspend fun getCategoryById(id: EntityId): Category? = categories.firstOrNull { it.id == id }
    override suspend fun insertCategory(category: Category) {}
    override suspend fun updateCategory(category: Category) {}
    override suspend fun archiveCategory(id: EntityId) {}
    override suspend fun deleteCategory(category: Category) {}
}

package com.example.expensetracker.feature.addtransaction

import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val accountId1 = EntityId("acc-1")
    private val accountId2 = EntityId("acc-2")
    private val bankAccountId = EntityId("acc-bank")
    private val categoryId1 = EntityId("cat-1")

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var useCases: AddTransactionUseCases
    private lateinit var viewModel: AddTransactionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        accountRepository = FakeAccountRepository(
            listOf(
                Account(
                    id = accountId1,
                    name = "Cash",
                    type = AccountType.CASH,
                    currency = Currency.INR,
                    initialBalance = Money.zero(Currency.INR),
                    currentBalance = Money.zero(Currency.INR)
                ),
                Account(
                    id = accountId2,
                    name = "Credit Card",
                    type = AccountType.CREDIT_CARD,
                    currency = Currency.INR,
                    initialBalance = Money.zero(Currency.INR),
                    currentBalance = Money.zero(Currency.INR)
                ),
                Account(
                    id = bankAccountId,
                    name = "Bank Account",
                    type = AccountType.BANK,
                    currency = Currency.INR,
                    initialBalance = Money.zero(Currency.INR),
                    currentBalance = Money.zero(Currency.INR)
                )
            )
        )

        categoryRepository = FakeCategoryRepository(
            listOf(
                Category(
                    id = categoryId1,
                    name = "Food",
                    iconKey = "restaurant",
                    colorKey = "category_orange",
                    isDefault = true
                )
            )
        )

        transactionRepository = FakeTransactionRepository()
        useCases = AddTransactionUseCases(accountRepository, categoryRepository, transactionRepository)
        viewModel = AddTransactionViewModel(useCases)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emptyAmount_rejected() = runTest {
        advanceUntilIdle()

        viewModel.onEvent(AddTransactionEvent.AmountChanged(""))
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.amountError)
        assertEquals("Amount is required", state.amountError)
        assertFalse(state.isSaved)
        assertTrue(transactionRepository.insertedTransactions.isEmpty())
    }

    @Test
    fun zeroAmount_rejected() = runTest {
        advanceUntilIdle()

        viewModel.onEvent(AddTransactionEvent.AmountChanged("0"))
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.amountError)
        assertEquals("Amount must be greater than zero", state.amountError)
        assertFalse(state.isSaved)
        assertTrue(transactionRepository.insertedTransactions.isEmpty())
    }

    @Test
    fun invalidAmount_rejected() = runTest {
        advanceUntilIdle()

        viewModel.onEvent(AddTransactionEvent.AmountChanged("invalid_number"))
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.amountError)
        assertEquals("Invalid amount format", state.amountError)
        assertFalse(state.isSaved)
        assertTrue(transactionRepository.insertedTransactions.isEmpty())
    }

    @Test
    fun invalidAmount_excessiveDecimals_rejected() = runTest {
        advanceUntilIdle()

        // Currency INR supports up to 2 decimal places
        viewModel.onEvent(AddTransactionEvent.AmountChanged("10.555"))
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.amountError)
        assertEquals("Amount cannot exceed 2 decimal places", state.amountError)
        assertFalse(state.isSaved)
        assertTrue(transactionRepository.insertedTransactions.isEmpty())
    }

    @Test
    fun validExpense_saved() = runTest {
        advanceUntilIdle()

        viewModel.onEvent(AddTransactionEvent.TransactionTypeChanged(TransactionType.EXPENSE))
        viewModel.onEvent(AddTransactionEvent.AmountChanged("150.75"))
        viewModel.onEvent(AddTransactionEvent.SourceAccountSelected(accountId1))
        viewModel.onEvent(AddTransactionEvent.CategorySelected(categoryId1))
        viewModel.onEvent(AddTransactionEvent.NoteChanged("  Dinner at cafe  "))
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertNull(state.amountError)
        assertNull(state.sourceAccountError)

        assertEquals(1, transactionRepository.insertedTransactions.size)
        val saved = transactionRepository.insertedTransactions.first()
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(BigDecimal("150.75"), saved.amount.amount)
        assertEquals(Currency.INR, saved.amount.currency)
        assertEquals(accountId1, saved.sourceAccountId)
        assertNull(saved.destinationAccountId)
        assertEquals(categoryId1, saved.categoryId)
        assertEquals("Dinner at cafe", saved.note)
    }

    @Test
    fun validIncome_saved() = runTest {
        advanceUntilIdle()

        viewModel.onEvent(AddTransactionEvent.TransactionTypeChanged(TransactionType.INCOME))
        viewModel.onEvent(AddTransactionEvent.AmountChanged("50000.00"))
        viewModel.onEvent(AddTransactionEvent.DestinationAccountSelected(accountId1))
        viewModel.onEvent(AddTransactionEvent.CategorySelected(categoryId1))
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)

        assertEquals(1, transactionRepository.insertedTransactions.size)
        val saved = transactionRepository.insertedTransactions.first()
        assertEquals(TransactionType.INCOME, saved.type)
        assertEquals(BigDecimal("50000.00"), saved.amount.amount)
        assertEquals(accountId1, saved.sourceAccountId)
        assertEquals(categoryId1, saved.categoryId)
    }

    @Test
    fun bankAccounts_filteredOutFromPaymentOptions() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.availableAccounts.size)
        assertTrue(state.availableAccounts.any { it.id == accountId1 })
        assertTrue(state.availableAccounts.any { it.id == accountId2 })
        assertFalse(state.availableAccounts.any { it.id == bankAccountId })
    }

    @Test
    fun transferType_fallbackToExpense() = runTest {
        advanceUntilIdle()
        viewModel.onEvent(AddTransactionEvent.TransactionTypeChanged(TransactionType.TRANSFER))
        val state = viewModel.uiState.value
        assertEquals(TransactionType.EXPENSE, state.transactionType)
        assertFalse(state.isTransfer)
    }

    @Test
    fun quickAmountAdded_incrementsAmountCorrectly() = runTest {
        advanceUntilIdle()
        viewModel.onEvent(AddTransactionEvent.AmountChanged("40.00"))
        viewModel.onEvent(AddTransactionEvent.QuickAmountAdded(10.0))
        assertEquals("50.00", viewModel.uiState.value.amountInput)
    }

    @Test
    fun roundUpAmount_roundsUpCorrectly() = runTest {
        advanceUntilIdle()
        viewModel.onEvent(AddTransactionEvent.AmountChanged("48.50"))
        viewModel.onEvent(AddTransactionEvent.RoundUpAmount)
        assertEquals("49.00", viewModel.uiState.value.amountInput)
    }

    @Test
    fun missingRequiredAccount_rejected() = runTest {
        val emptyAccountRepo = FakeAccountRepository(emptyList())
        val customUseCases = AddTransactionUseCases(emptyAccountRepo, categoryRepository, transactionRepository)
        val customVm = AddTransactionViewModel(customUseCases)
        advanceUntilIdle()

        customVm.onEvent(AddTransactionEvent.AmountChanged("100.00"))
        customVm.onEvent(AddTransactionEvent.SaveClicked)
        advanceUntilIdle()

        val state = customVm.uiState.value
        assertNotNull(state.sourceAccountError)
        assertEquals("Please select an account", state.sourceAccountError)
        assertFalse(state.isSaved)
        assertTrue(transactionRepository.insertedTransactions.isEmpty())
    }

    @Test
    fun saveFailure_producesErrorState() = runTest {
        transactionRepository.shouldFail = true
        advanceUntilIdle()

        viewModel.onEvent(AddTransactionEvent.AmountChanged("100.00"))
        viewModel.onEvent(AddTransactionEvent.SourceAccountSelected(accountId1))
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaved)
        assertFalse(state.isSaving)
        assertNotNull(state.generalError)
        assertEquals("Database write failure", state.generalError)
    }

    @Test
    fun successfulSave_producesSuccessState() = runTest {
        advanceUntilIdle()

        viewModel.onEvent(AddTransactionEvent.AmountChanged("50.00"))
        viewModel.onEvent(AddTransactionEvent.SourceAccountSelected(accountId1))
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertFalse(state.isSaving)
        assertNull(state.generalError)
    }

    @Test
    fun repeatedSave_cannotCreateDuplicateSubmission() = runTest {
        advanceUntilIdle()

        viewModel.onEvent(AddTransactionEvent.AmountChanged("300.00"))
        viewModel.onEvent(AddTransactionEvent.SourceAccountSelected(accountId1))

        // Trigger save multiple times rapidly
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        advanceUntilIdle()

        // Even after completion, another click does nothing
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        advanceUntilIdle()

        assertEquals(1, transactionRepository.insertedTransactions.size)
    }

    @Test
    fun merchantAndNote_combinedProperlyOnSave() = runTest {
        advanceUntilIdle()
        viewModel.onEvent(AddTransactionEvent.AmountChanged("48.50"))
        viewModel.onEvent(AddTransactionEvent.SourceAccountSelected(accountId1))
        viewModel.onEvent(AddTransactionEvent.CategorySelected(categoryId1))
        viewModel.onEvent(AddTransactionEvent.NoteChanged("Dinner with team"))
        viewModel.onEvent(AddTransactionEvent.MerchantChanged("Sweetgreen, Downtown Market"))
        viewModel.onEvent(AddTransactionEvent.SaveClicked)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        val saved = transactionRepository.insertedTransactions.first()
        assertEquals("Dinner with team (Sweetgreen, Downtown Market)", saved.note)
    }

    @Test
    fun resetForm_clearsInputs() = runTest {
        advanceUntilIdle()
        viewModel.onEvent(AddTransactionEvent.AmountChanged("100.00"))
        viewModel.onEvent(AddTransactionEvent.NoteChanged("Test note"))
        viewModel.onEvent(AddTransactionEvent.MerchantChanged("Test merchant"))
        viewModel.onEvent(AddTransactionEvent.ResetForm)

        val state = viewModel.uiState.value
        assertEquals("", state.amountInput)
        assertEquals("", state.note)
        assertEquals("", state.merchant)
    }
}

// --- Fakes for Testing ---

private class FakeAccountRepository(
    accounts: List<Account>
) : AccountRepository {
    private val flow = MutableStateFlow(accounts)

    override fun getActiveAccounts(): Flow<List<Account>> = flow
    override fun getAllAccounts(): Flow<List<Account>> = flow
    override fun getAccount(id: EntityId): Flow<Account?> = flowOf(null)
    override suspend fun getAccountById(id: EntityId): Account? = flow.value.firstOrNull { it.id == id }
    override suspend fun insertAccount(account: Account) {}
    override suspend fun updateAccount(account: Account) {}
    override suspend fun archiveAccount(id: EntityId) {}
    override suspend fun deleteAccount(account: Account) {}
}

private class FakeCategoryRepository(
    categories: List<Category>
) : CategoryRepository {
    private val flow = MutableStateFlow(categories)

    override fun getActiveCategories(): Flow<List<Category>> = flow
    override fun getAllCategories(): Flow<List<Category>> = flow
    override fun getCategory(id: EntityId): Flow<Category?> = flowOf(null)
    override suspend fun getCategoryById(id: EntityId): Category? = flow.value.firstOrNull { it.id == id }
    override suspend fun insertCategory(category: Category) {}
    override suspend fun updateCategory(category: Category) {}
    override suspend fun archiveCategory(id: EntityId) {}
    override suspend fun deleteCategory(category: Category) {}
}

private class FakeTransactionRepository : TransactionRepository {
    val insertedTransactions = mutableListOf<Transaction>()
    var shouldFail = false

    override fun getActiveTransactions(): Flow<List<Transaction>> = flowOf(insertedTransactions)
    override fun getDeletedTransactions(): Flow<List<Transaction>> = flowOf(emptyList())
    override fun getTransaction(id: EntityId): Flow<Transaction?> = flowOf(null)
    override suspend fun getTransactionById(id: EntityId): Transaction? = null
    override suspend fun insertTransaction(transaction: Transaction) {
        if (shouldFail) {
            throw RuntimeException("Database write failure")
        }
        insertedTransactions.add(transaction)
    }
    override suspend fun updateTransaction(transaction: Transaction) {}
    override suspend fun moveToTrash(id: EntityId) {}
    override suspend fun restoreFromTrash(id: EntityId) {}
    override suspend fun deletePermanently(id: EntityId) {}
    override suspend fun clearTrash() {}
}

package com.example.expensetracker.feature.transactions

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val accountId1 = EntityId("acc-1")
    private val accountId2 = EntityId("acc-2")
    private val archivedAccountId = EntityId("acc-archived")

    private val categoryId1 = EntityId("cat-1")
    private val categoryId2 = EntityId("cat-2")
    private val archivedCategoryId = EntityId("cat-archived")

    private val sampleCategory1 = Category(
        id = categoryId1,
        name = "Groceries",
        iconKey = "shopping_cart",
        colorKey = "green",
        isDefault = true
    )
    private val sampleCategory2 = Category(
        id = categoryId2,
        name = "Utilities",
        iconKey = "bolt",
        colorKey = "blue",
        isDefault = true
    )
    private val archivedCategory = Category(
        id = archivedCategoryId,
        name = "Old Subscription",
        iconKey = "archive",
        colorKey = "gray",
        isArchived = true
    )

    private val sampleAccount1 = Account(
        id = accountId1,
        name = "Checking",
        type = AccountType.BANK,
        currency = Currency.INR,
        initialBalance = Money(BigDecimal("1000.00"), Currency.INR),
        currentBalance = Money(BigDecimal("1000.00"), Currency.INR)
    )
    private val sampleAccount2 = Account(
        id = accountId2,
        name = "Savings",
        type = AccountType.BANK,
        currency = Currency.INR,
        initialBalance = Money(BigDecimal("2000.00"), Currency.INR),
        currentBalance = Money(BigDecimal("2000.00"), Currency.INR)
    )
    private val archivedAccount = Account(
        id = archivedAccountId,
        name = "Old Credit Card",
        type = AccountType.CREDIT_CARD,
        currency = Currency.INR,
        initialBalance = Money.zero(Currency.INR),
        currentBalance = Money.zero(Currency.INR),
        isArchived = true
    )

    private val usdAccountId = EntityId("acc-usd")
    private val sampleAccountUsd = Account(
        id = usdAccountId,
        name = "USD Account",
        type = AccountType.BANK,
        currency = Currency.USD,
        initialBalance = Money.zero(Currency.USD),
        currentBalance = Money.zero(Currency.USD)
    )

    private val now = Instant.now()

    private val sampleExpense = Transaction(
        id = EntityId("tx-expense"),
        type = TransactionType.EXPENSE,
        amount = Money(BigDecimal("120.50"), Currency.INR),
        categoryId = categoryId1,
        sourceAccountId = accountId1,
        destinationAccountId = null,
        timestamp = now,
        note = "Supermarket run",
        isDeleted = false
    )

    private val sampleTransfer = Transaction(
        id = EntityId("tx-transfer"),
        type = TransactionType.TRANSFER,
        amount = Money(BigDecimal("300.00"), Currency.INR),
        categoryId = null,
        sourceAccountId = accountId1,
        destinationAccountId = accountId2,
        timestamp = now,
        note = "Transfer to savings",
        isDeleted = false
    )

    private val sampleArchivedTx = Transaction(
        id = EntityId("tx-archived"),
        type = TransactionType.EXPENSE,
        amount = Money(BigDecimal("45.00"), Currency.INR),
        categoryId = archivedCategoryId,
        sourceAccountId = archivedAccountId,
        destinationAccountId = null,
        timestamp = now,
        note = "Legacy charge",
        isDeleted = false
    )

    private lateinit var fakeTransactionRepo: FakeDetailsTransactionRepository
    private lateinit var fakeCategoryRepo: FakeDetailsCategoryRepository
    private lateinit var fakeAccountRepo: FakeDetailsAccountRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeTransactionRepo = FakeDetailsTransactionRepository(
            listOf(sampleExpense, sampleTransfer, sampleArchivedTx)
        )
        fakeCategoryRepo = FakeDetailsCategoryRepository(
            listOf(sampleCategory1, sampleCategory2, archivedCategory)
        )
        fakeAccountRepo = FakeDetailsAccountRepository(
            listOf(sampleAccount1, sampleAccount2, archivedAccount, sampleAccountUsd)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(transactionId: String): TransactionDetailsViewModel {
        return TransactionDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to transactionId)),
            transactionRepository = fakeTransactionRepo,
            categoryRepository = fakeCategoryRepo,
            accountRepository = fakeAccountRepo
        )
    }

    @Test
    fun loadExistingTransaction_populatesDetailsAndResolvedEntities() = runTest {
        val viewModel = createViewModel("tx-expense")
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.transactionNotFound)
        assertFalse(state.isEditing)
        assertNotNull(state.transaction)
        assertEquals("tx-expense", state.transaction?.id?.value)
        assertEquals("Groceries", state.category?.name)
        assertEquals("Checking", state.sourceAccount?.name)
        assertNull(state.destinationAccount)
        assertEquals("120.50", state.editedAmountInput)
        assertEquals("Supermarket run", state.editedNote)

        collectJob.cancel()
    }

    @Test
    fun missingTransaction_producesNotFoundState() = runTest {
        val viewModel = createViewModel("non-existent-id")
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.transactionNotFound)
        assertNull(state.transaction)

        collectJob.cancel()
    }

    @Test
    fun blankTransactionId_producesNotFoundState() = runTest {
        val viewModel = createViewModel("")
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.transactionNotFound)

        collectJob.cancel()
    }

    @Test
    fun archivedRelatedData_resolvesGracefullyWithoutError() = runTest {
        val viewModel = createViewModel("tx-archived")
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.transaction)
        assertEquals("Old Subscription", state.category?.name)
        assertTrue(state.category?.isArchived == true)
        assertEquals("Old Credit Card", state.sourceAccount?.name)
        assertTrue(state.sourceAccount?.isArchived == true)

        collectJob.cancel()
    }

    @Test
    fun validEdit_updatesTransactionSuccessfully() = runTest {
        val viewModel = createViewModel("tx-expense")
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.startEditing()
        assertTrue(viewModel.uiState.value.isEditing)

        viewModel.onAmountChanged("150.75")
        viewModel.onNoteChanged("Updated grocery list")
        viewModel.onCategorySelected(categoryId2)
        viewModel.saveChanges()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertFalse(state.isSaving)
        assertFalse(state.isEditing)

        val updated = fakeTransactionRepo.transactions.first { it.id.value == "tx-expense" }
        assertEquals(BigDecimal("150.75"), updated.amount.amount)
        assertEquals("Updated grocery list", updated.note)
        assertEquals(categoryId2, updated.categoryId)

        collectJob.cancel()
    }

    @Test
    fun invalidAmount_blocksSaveAndDisplaysError() = runTest {
        val viewModel = createViewModel("tx-expense")
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.startEditing()

        // 1. Empty amount
        viewModel.onAmountChanged("")
        viewModel.saveChanges()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.amountError)
        assertEquals("Amount is required", viewModel.uiState.value.amountError)
        assertFalse(viewModel.uiState.value.isSaved)

        // 2. Non-numeric
        viewModel.onAmountChanged("abc")
        viewModel.saveChanges()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.amountError)
        assertEquals("Invalid amount format", viewModel.uiState.value.amountError)

        // 3. Negative or zero amount
        viewModel.onAmountChanged("-10.00")
        viewModel.saveChanges()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.amountError)
        assertEquals("Amount must be greater than zero", viewModel.uiState.value.amountError)

        collectJob.cancel()
    }

    @Test
    fun transfer_sameSourceAndDestination_blocksSave() = runTest {
        val viewModel = createViewModel("tx-transfer")
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.startEditing()

        // Source is accountId1, user changes destination to accountId1
        viewModel.onDestinationAccountSelected(accountId1)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.destinationAccountError)
        assertEquals("Source and destination accounts must differ", viewModel.uiState.value.destinationAccountError)

        viewModel.saveChanges()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)

        collectJob.cancel()
    }

    @Test
    fun updateFailure_producesGeneralErrorMessage() = runTest {
        fakeTransactionRepo.shouldFail = true
        val viewModel = createViewModel("tx-expense")
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.startEditing()
        viewModel.onAmountChanged("200.00")
        viewModel.saveChanges()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaved)
        assertFalse(state.isSaving)
        assertNotNull(state.generalError)
        assertEquals("Simulated update failure", state.generalError)

        collectJob.cancel()
    }

    @Test
    fun moveToTrash_andRestore_updatesReactiveState() = runTest {
        val viewModel = createViewModel("tx-expense")
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleted)

        viewModel.moveToTrash()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDeleted)
        assertTrue(fakeTransactionRepo.transactions.first { it.id.value == "tx-expense" }.isDeleted)

        viewModel.restoreFromTrash()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleted)
        assertFalse(fakeTransactionRepo.transactions.first { it.id.value == "tx-expense" }.isDeleted)

        collectJob.cancel()
    }

    @Test
    fun permanentDelete_removesTransactionAndSignalsNavigation() = runTest {
        val viewModel = createViewModel("tx-expense")
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPermanentlyDeleted)

        viewModel.deletePermanently()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPermanentlyDeleted)
        assertNull(fakeTransactionRepo.transactions.firstOrNull { it.id.value == "tx-expense" })

        collectJob.cancel()
    }

    @Test
    fun unsavedChanges_triggersDiscardConfirmation() = runTest {
        val viewModel = createViewModel("tx-expense")
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.startEditing()
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        // Cancel with NO changes directly exits editing
        viewModel.cancelEditing()
        assertFalse(viewModel.uiState.value.isEditing)
        assertFalse(viewModel.uiState.value.showDiscardDialog)

        // Start editing and make a change
        viewModel.startEditing()
        viewModel.onNoteChanged("Different note")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        // Attempting to cancel shows discard dialog
        viewModel.cancelEditing()
        assertTrue(viewModel.uiState.value.showDiscardDialog)
        assertTrue(viewModel.uiState.value.isEditing)

        // Dismissing keeps editing
        viewModel.dismissDiscardDialog()
        assertFalse(viewModel.uiState.value.showDiscardDialog)
        assertTrue(viewModel.uiState.value.isEditing)

        // Confirming discard resets fields and exits editing
        viewModel.confirmDiscard()
        assertFalse(viewModel.uiState.value.isEditing)
        assertFalse(viewModel.uiState.value.showDiscardDialog)
        assertEquals("Supermarket run", viewModel.uiState.value.editedNote)

        collectJob.cancel()
    }

    @Test
    fun transfer_differentCurrencies_failsValidation() = runTest {
        val viewModel = createViewModel("tx-transfer")
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.startEditing()
        viewModel.onDestinationAccountSelected(usdAccountId)

        val state = viewModel.uiState.value
        assertEquals("Source and destination accounts must have the same currency", state.destinationAccountError)

        viewModel.saveChanges()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertTrue(viewModel.uiState.value.isEditing)

        // Switching back to matching INR account clears error
        viewModel.onDestinationAccountSelected(accountId2)
        assertNull(viewModel.uiState.value.destinationAccountError)

        collectJob.cancel()
    }
}

// --- Test Fakes ---

private class FakeDetailsTransactionRepository(
    initialTransactions: List<Transaction>
) : TransactionRepository {

    val transactions = initialTransactions.toMutableList()
    private val flow = MutableStateFlow(transactions.toList())
    var shouldFail = false

    private fun notifyFlow() {
        flow.value = transactions.toList()
    }

    override fun getActiveTransactions(): Flow<List<Transaction>> =
        flowOf(transactions.filterNot { it.isDeleted })

    override fun getDeletedTransactions(): Flow<List<Transaction>> =
        flowOf(transactions.filter { it.isDeleted })

    override fun getTransaction(id: EntityId): Flow<Transaction?> =
        flow.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getTransactionById(id: EntityId): Transaction? =
        transactions.firstOrNull { it.id == id }

    override suspend fun insertTransaction(transaction: Transaction) {
        transactions.add(transaction)
        notifyFlow()
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        if (shouldFail) {
            throw RuntimeException("Simulated update failure")
        }
        val idx = transactions.indexOfFirst { it.id == transaction.id }
        if (idx != -1) {
            transactions[idx] = transaction
            notifyFlow()
        }
    }

    override suspend fun moveToTrash(id: EntityId) {
        val idx = transactions.indexOfFirst { it.id == id }
        if (idx != -1) {
            transactions[idx] = transactions[idx].copy(isDeleted = true)
            notifyFlow()
        }
    }

    override suspend fun restoreFromTrash(id: EntityId) {
        val idx = transactions.indexOfFirst { it.id == id }
        if (idx != -1) {
            transactions[idx] = transactions[idx].copy(isDeleted = false)
            notifyFlow()
        }
    }

    override suspend fun deletePermanently(id: EntityId) {
        transactions.removeAll { it.id == id }
        notifyFlow()
    }

    override suspend fun clearTrash() {
        transactions.removeAll { it.isDeleted }
        notifyFlow()
    }
}

private class FakeDetailsCategoryRepository(
    categories: List<Category>
) : CategoryRepository {
    private val flow = MutableStateFlow(categories)

    override fun getActiveCategories(): Flow<List<Category>> =
        flowOf(flow.value.filterNot { it.isArchived })

    override fun getAllCategories(): Flow<List<Category>> = flow

    override fun getCategory(id: EntityId): Flow<Category?> =
        flowOf(flow.value.firstOrNull { it.id == id })

    override suspend fun getCategoryById(id: EntityId): Category? =
        flow.value.firstOrNull { it.id == id }

    override suspend fun insertCategory(category: Category) {}
    override suspend fun updateCategory(category: Category) {}
    override suspend fun archiveCategory(id: EntityId) {}
    override suspend fun deleteCategory(category: Category) {}
}

private class FakeDetailsAccountRepository(
    accounts: List<Account>
) : AccountRepository {
    private val flow = MutableStateFlow(accounts)

    override fun getActiveAccounts(): Flow<List<Account>> =
        flowOf(flow.value.filterNot { it.isArchived })

    override fun getAllAccounts(): Flow<List<Account>> = flow

    override fun getAccount(id: EntityId): Flow<Account?> =
        flowOf(flow.value.firstOrNull { it.id == id })

    override suspend fun getAccountById(id: EntityId): Account? =
        flow.value.firstOrNull { it.id == id }

    override suspend fun insertAccount(account: Account) {}
    override suspend fun updateAccount(account: Account) {}
    override suspend fun archiveAccount(id: EntityId) {}
    override suspend fun deleteAccount(account: Account) {}
}

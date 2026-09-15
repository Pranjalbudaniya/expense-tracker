package com.example.expensetracker.feature.accounts

import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.account.AccountType
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val useCases = AccountUseCases()

    private val inr = Currency.INR
    private val usd = Currency.USD

    private val accountId1 = EntityId("acc-1")
    private val accountId2 = EntityId("acc-2")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- AccountUseCases Unit Tests ---

    @Test
    fun calculateBalance_incomeAddsToBalance() {
        val account = createAccount(id = accountId1, initialBalance = BigDecimal("1000.00"))
        val tx = createTransaction(
            id = "tx-1",
            type = TransactionType.INCOME,
            amount = BigDecimal("500.00"),
            sourceAccountId = accountId1
        )

        val balance = useCases.calculateAccountBalance(account, listOf(tx))
        assertEquals(BigDecimal("1500.00"), balance.amount)
        assertEquals(inr, balance.currency)
    }

    @Test
    fun calculateBalance_expenseDeductsFromBalance() {
        val account = createAccount(id = accountId1, initialBalance = BigDecimal("1000.00"))
        val tx = createTransaction(
            id = "tx-1",
            type = TransactionType.EXPENSE,
            amount = BigDecimal("400.00"),
            sourceAccountId = accountId1
        )

        val balance = useCases.calculateAccountBalance(account, listOf(tx))
        assertEquals(BigDecimal("600.00"), balance.amount)
    }

    @Test
    fun calculateBalance_transferOutDeducts_transferInAdds() {
        val account1 = createAccount(id = accountId1, initialBalance = BigDecimal("1000.00"))
        val account2 = createAccount(id = accountId2, initialBalance = BigDecimal("200.00"))

        val transferTx = createTransaction(
            id = "tx-transfer",
            type = TransactionType.TRANSFER,
            amount = BigDecimal("300.00"),
            sourceAccountId = accountId1,
            destinationAccountId = accountId2
        )

        val balance1 = useCases.calculateAccountBalance(account1, listOf(transferTx))
        val balance2 = useCases.calculateAccountBalance(account2, listOf(transferTx))

        assertEquals(BigDecimal("700.00"), balance1.amount)
        assertEquals(BigDecimal("500.00"), balance2.amount)
    }

    @Test
    fun calculateBalance_deletedTransactionsAreIgnored() {
        val account = createAccount(id = accountId1, initialBalance = BigDecimal("1000.00"))
        val activeTx = createTransaction(
            id = "tx-1",
            type = TransactionType.INCOME,
            amount = BigDecimal("500.00"),
            sourceAccountId = accountId1,
            isDeleted = false
        )
        val deletedTx = createTransaction(
            id = "tx-2",
            type = TransactionType.EXPENSE,
            amount = BigDecimal("800.00"),
            sourceAccountId = accountId1,
            isDeleted = true
        )

        val balance = useCases.calculateAccountBalance(account, listOf(activeTx, deletedTx))
        assertEquals(BigDecimal("1500.00"), balance.amount)
    }

    @Test
    fun calculateBalance_differentCurrencyTransactionsAreIgnored() {
        val inrAccount = createAccount(id = accountId1, initialBalance = BigDecimal("1000.00"), currency = inr)
        val usdTx = Transaction(
            id = EntityId("tx-usd"),
            amount = Money(BigDecimal("100.00"), usd),
            type = TransactionType.INCOME,
            sourceAccountId = accountId1,
            timestamp = Instant.now()
        )
        val inrTx = createTransaction(
            id = "tx-inr",
            type = TransactionType.INCOME,
            amount = BigDecimal("200.00"),
            sourceAccountId = accountId1
        )

        val balance = useCases.calculateAccountBalance(inrAccount, listOf(usdTx, inrTx))
        assertEquals(BigDecimal("1200.00"), balance.amount)
        assertEquals(inr, balance.currency)
    }

    @Test
    fun calculateBalance_creditCardNegativeInitialBalance_calculatedAccurately() {
        // Outstanding debt of 5000, repaid 2000, spent 1000 more
        val ccAccount = createAccount(
            id = accountId1,
            initialBalance = BigDecimal("-5000.00"),
            type = AccountType.CREDIT_CARD
        )
        val repayment = createTransaction(
            id = "repay",
            type = TransactionType.INCOME,
            amount = BigDecimal("2000.00"),
            sourceAccountId = accountId1
        )
        val expense = createTransaction(
            id = "expense",
            type = TransactionType.EXPENSE,
            amount = BigDecimal("1000.00"),
            sourceAccountId = accountId1
        )

        val balance = useCases.calculateAccountBalance(ccAccount, listOf(repayment, expense))
        assertEquals(BigDecimal("-4000.00"), balance.amount)
        assertTrue(balance.isNegative)
    }

    @Test
    fun validateInitialBalance_acceptsNegativeZeroAndPositive() {
        val resZero = useCases.parseAndValidateInitialBalance("0", inr)
        assertTrue(resZero is BalanceValidationResult.Success)
        assertEquals(BigDecimal.ZERO, (resZero as BalanceValidationResult.Success).amount)

        val resPos = useCases.parseAndValidateInitialBalance("1250.75", inr)
        assertTrue(resPos is BalanceValidationResult.Success)
        assertEquals(BigDecimal("1250.75"), (resPos as BalanceValidationResult.Success).amount)

        val resNeg = useCases.parseAndValidateInitialBalance("-350.00", inr)
        assertTrue(resNeg is BalanceValidationResult.Success)
        assertEquals(BigDecimal("-350.00"), (resNeg as BalanceValidationResult.Success).amount)
    }

    @Test
    fun validateInitialBalance_rejectsInvalidFormats() {
        val resEmpty = useCases.parseAndValidateInitialBalance("  ", inr)
        assertTrue(resEmpty is BalanceValidationResult.Error)

        val resAlpha = useCases.parseAndValidateInitialBalance("abc", inr)
        assertTrue(resAlpha is BalanceValidationResult.Error)

        val resExtraScale = useCases.parseAndValidateInitialBalance("10.999", inr)
        assertTrue(resExtraScale is BalanceValidationResult.Error)
    }

    @Test
    fun validateAndCreateAccount_rejectsBlankName() {
        val result = useCases.validateAndCreateAccount(
            id = accountId1,
            name = "   ",
            type = AccountType.BANK,
            currency = inr,
            initialBalanceInput = "100.00"
        )
        assertTrue(result is AccountValidationResult.Failure)
        assertNotNull((result as AccountValidationResult.Failure).nameError)
    }

    // --- AccountsViewModel Unit Tests ---

    @Test
    fun accountsViewModel_emitsActiveAndArchivedAccounts() = runTest {
        val activeAccount = createAccount(id = accountId1, initialBalance = BigDecimal("1000.00"), isArchived = false)
        val archivedAccount = createAccount(id = accountId2, initialBalance = BigDecimal("500.00"), isArchived = true)

        val fakeAccountRepo = FakeAccountRepository(listOf(activeAccount, archivedAccount))
        val fakeTxRepo = FakeTransactionRepository(
            listOf(
                createTransaction(id = "1", type = TransactionType.INCOME, amount = BigDecimal("200.00"), sourceAccountId = accountId1)
            )
        )

        val viewModel = AccountsViewModel(
            accountRepository = fakeAccountRepo,
            transactionRepository = fakeTxRepo,
            accountUseCases = useCases
        )

        val states = mutableListOf<AccountsUiState>()
        val job = launch(StandardTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states.add(it) }
        }

        advanceUntilIdle()

        val state = states.last()
        assertFalse(state.isLoading)
        assertEquals(1, state.activeAccounts.size)
        assertEquals(1, state.archivedAccounts.size)
        assertEquals(BigDecimal("1200.00"), state.activeAccounts.first().calculatedBalance.amount)
        assertEquals(BigDecimal("500.00"), state.archivedAccounts.first().calculatedBalance.amount)

        job.cancel()
    }

    @Test
    fun accountsViewModel_openCreateForm_initializesWithDefaultCurrency() = runTest {
        val fakeAccountRepo = FakeAccountRepository(emptyList())
        val fakeTxRepo = FakeTransactionRepository(emptyList())

        val viewModel = AccountsViewModel(
            accountRepository = fakeAccountRepo,
            transactionRepository = fakeTxRepo,
            accountUseCases = useCases
        )

        val states = mutableListOf<AccountsUiState>()
        val job = launch(StandardTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states.add(it) }
        }

        advanceUntilIdle()

        viewModel.openCreateForm()
        advanceUntilIdle()

        val state = states.last()
        assertTrue(state.isFormOpen)
        assertNotNull(state.formState)
        assertEquals("INR", state.formState?.currency?.code)
        assertNull(state.formState?.accountId)

        job.cancel()
    }

    @Test
    fun accountsViewModel_saveAccount_createsNewAccountInRepository() = runTest {
        val fakeAccountRepo = FakeAccountRepository(emptyList())
        val fakeTxRepo = FakeTransactionRepository(emptyList())

        val viewModel = AccountsViewModel(
            accountRepository = fakeAccountRepo,
            transactionRepository = fakeTxRepo,
            accountUseCases = useCases
        )

        val states = mutableListOf<AccountsUiState>()
        val job = launch(StandardTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states.add(it) }
        }

        advanceUntilIdle()

        viewModel.openCreateForm()
        advanceUntilIdle()

        viewModel.onFormNameChange("Emergency Cash")
        viewModel.onFormTypeChange(AccountType.CASH)
        viewModel.onFormInitialBalanceChange("500.00")
        advanceUntilIdle()

        viewModel.saveAccount()
        advanceUntilIdle()

        val state = states.last()
        assertFalse(state.isFormOpen)
        assertNull(state.formState)
        assertEquals(1, state.activeAccounts.size)
        assertEquals("Emergency Cash", state.activeAccounts.first().account.name)
        assertEquals(AccountType.CASH, state.activeAccounts.first().account.type)
        assertEquals(BigDecimal("500.00"), state.activeAccounts.first().calculatedBalance.amount)

        job.cancel()
    }

    @Test
    fun accountsViewModel_archiveAccount_updatesArchiveStatus() = runTest {
        val account = createAccount(id = accountId1, initialBalance = BigDecimal("1000.00"), isArchived = false)
        val fakeAccountRepo = FakeAccountRepository(listOf(account))
        val fakeTxRepo = FakeTransactionRepository(emptyList())

        val viewModel = AccountsViewModel(
            accountRepository = fakeAccountRepo,
            transactionRepository = fakeTxRepo,
            accountUseCases = useCases
        )

        val states = mutableListOf<AccountsUiState>()
        val job = launch(StandardTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states.add(it) }
        }

        advanceUntilIdle()

        viewModel.requestArchiveAccount(account)
        advanceUntilIdle()
        assertNotNull(states.last().archiveConfirmationAccount)

        viewModel.confirmArchiveAccount()
        advanceUntilIdle()

        val latestState = states.last()
        assertNull(latestState.archiveConfirmationAccount)
        assertEquals(0, latestState.activeAccounts.size)
        assertEquals(1, latestState.archivedAccounts.size)
        assertTrue(latestState.archivedAccounts.first().isArchived)

        job.cancel()
    }

    // --- Helpers ---

    private fun createAccount(
        id: EntityId,
        initialBalance: BigDecimal,
        type: AccountType = AccountType.BANK,
        currency: Currency = inr,
        isArchived: Boolean = false
    ): Account = Account(
        id = id,
        name = "Account ${id.value}",
        type = type,
        currency = currency,
        initialBalance = Money(initialBalance, currency),
        currentBalance = Money(initialBalance, currency),
        isArchived = isArchived
    )

    private fun createTransaction(
        id: String,
        type: TransactionType,
        amount: BigDecimal,
        sourceAccountId: EntityId = accountId1,
        destinationAccountId: EntityId? = null,
        isDeleted: Boolean = false
    ): Transaction = Transaction(
        id = EntityId(id),
        amount = Money(amount, inr),
        type = type,
        sourceAccountId = sourceAccountId,
        destinationAccountId = destinationAccountId,
        categoryId = null,
        timestamp = Instant.now(),
        note = "Test transaction",
        isDeleted = isDeleted
    )
}

// --- Test Doubles ---

private class FakeAccountRepository(
    initialAccounts: List<Account> = emptyList()
) : AccountRepository {
    private val accountsFlow = MutableStateFlow(initialAccounts)

    override fun getActiveAccounts(): Flow<List<Account>> =
        MutableStateFlow(accountsFlow.value.filter { !it.isArchived })

    override fun getAllAccounts(): Flow<List<Account>> = accountsFlow

    override fun getAccount(id: EntityId): Flow<Account?> =
        flowOf(accountsFlow.value.find { it.id == id })

    override suspend fun getAccountById(id: EntityId): Account? =
        accountsFlow.value.find { it.id == id }

    override suspend fun insertAccount(account: Account) {
        accountsFlow.value = accountsFlow.value + account
    }

    override suspend fun updateAccount(account: Account) {
        accountsFlow.value = accountsFlow.value.map {
            if (it.id == account.id) account else it
        }
    }

    override suspend fun archiveAccount(id: EntityId) {
        accountsFlow.value = accountsFlow.value.map {
            if (it.id == id) it.copy(isArchived = true) else it
        }
    }

    override suspend fun deleteAccount(account: Account) {
        accountsFlow.value = accountsFlow.value.filter { it.id != account.id }
    }
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

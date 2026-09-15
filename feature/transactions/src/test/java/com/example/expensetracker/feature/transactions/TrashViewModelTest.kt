package com.example.expensetracker.feature.transactions

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class TrashViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeTransactionRepository: FakeTransactionRepository
    private lateinit var fakeCategoryRepository: FakeCategoryRepository
    private lateinit var fakeAccountRepository: FakeAccountRepository
    private lateinit var viewModel: TrashViewModel

    private val testCategory = Category(
        id = EntityId("cat_food"),
        name = "Food",
        iconKey = "restaurant",
        colorKey = "orange",
        type = CategoryType.EXPENSE
    )

    private val testAccount1 = Account(
        id = EntityId("acc_cash"),
        name = "Cash",
        type = AccountType.CASH,
        currency = Currency.USD,
        initialBalance = Money.of(BigDecimal("100"), Currency.USD),
        currentBalance = Money.of(BigDecimal("100"), Currency.USD)
    )

    private val testAccount2 = Account(
        id = EntityId("acc_bank"),
        name = "Bank",
        type = AccountType.BANK,
        currency = Currency.USD,
        initialBalance = Money.of(BigDecimal("1000"), Currency.USD),
        currentBalance = Money.of(BigDecimal("1000"), Currency.USD)
    )

    private var collectJob: kotlinx.coroutines.Job? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeTransactionRepository = FakeTransactionRepository()
        fakeCategoryRepository = FakeCategoryRepository(listOf(testCategory))
        fakeAccountRepository = FakeAccountRepository(listOf(testAccount1, testAccount2))

        viewModel = TrashViewModel(
            transactionRepository = fakeTransactionRepository,
            categoryRepository = fakeCategoryRepository,
            accountRepository = fakeAccountRepository
        )

        collectJob = kotlinx.coroutines.CoroutineScope(testDispatcher).launch {
            viewModel.uiState.collect {}
        }
    }

    @After
    fun tearDown() {
        collectJob?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_initiallyLoadsDeletedTransactionsWithDateGrouping() = runTest(testDispatcher) {
        val deletedTx1 = Transaction(
            id = EntityId("tx_1"),
            amount = Money.of(BigDecimal("50.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_cash"),
            categoryId = EntityId("cat_food"),
            timestamp = Instant.parse("2026-09-15T12:00:00Z"),
            isDeleted = true
        )
        val deletedTx2 = Transaction(
            id = EntityId("tx_2"),
            amount = Money.of(BigDecimal("100.00"), Currency.USD),
            type = TransactionType.TRANSFER,
            sourceAccountId = EntityId("acc_bank"),
            destinationAccountId = EntityId("acc_cash"),
            timestamp = Instant.parse("2026-09-14T10:00:00Z"),
            isDeleted = true
        )
        fakeTransactionRepository.deletedTransactions.value = listOf(deletedTx1, deletedTx2)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.totalCount)
        assertEquals(2, state.groupedTransactions.size)

        val firstItem = state.groupedTransactions[0].transactions[0]
        assertEquals(EntityId("tx_1"), firstItem.transaction.id)
        assertEquals("Food", firstItem.category?.name)
        assertEquals("Cash", firstItem.sourceAccount?.name)

        val secondItem = state.groupedTransactions[1].transactions[0]
        assertEquals(EntityId("tx_2"), secondItem.transaction.id)
        assertEquals("Bank", secondItem.sourceAccount?.name)
        assertEquals("Cash", secondItem.destinationAccount?.name)
    }

    @Test
    fun restoreTransaction_callsRepositoryAndSetsUserMessage() = runTest(testDispatcher) {
        val tx = Transaction(
            id = EntityId("tx_restore"),
            amount = Money.of(BigDecimal("15.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_cash"),
            timestamp = Instant.parse("2026-09-15T08:00:00Z"),
            isDeleted = true
        )
        fakeTransactionRepository.deletedTransactions.value = listOf(tx)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.restoreTransaction(EntityId("tx_restore"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Transaction restored", viewModel.uiState.value.userMessage)
        assertTrue(fakeTransactionRepository.restoredIds.contains(EntityId("tx_restore")))
    }

    @Test
    fun permanentDeleteFlow_showsConfirmationThenDeletes() = runTest(testDispatcher) {
        val id = EntityId("tx_perm")
        viewModel.showDeletePermanentlyConfirmation(id)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(id, viewModel.uiState.value.transactionToDeletePermanently)

        viewModel.confirmDeletePermanently()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.transactionToDeletePermanently)
        assertEquals("Transaction permanently deleted", viewModel.uiState.value.userMessage)
        assertTrue(fakeTransactionRepository.permanentlyDeletedIds.contains(id))
    }

    @Test
    fun dismissDeletePermanentlyConfirmation_clearsTargetId() = runTest(testDispatcher) {
        val id = EntityId("tx_dismiss")
        viewModel.showDeletePermanentlyConfirmation(id)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(id, viewModel.uiState.value.transactionToDeletePermanently)

        viewModel.dismissDeletePermanentlyConfirmation()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.transactionToDeletePermanently)
    }

    @Test
    fun emptyTrashFlow_showsConfirmationThenClearsTrash() = runTest(testDispatcher) {
        viewModel.showEmptyTrashConfirmation()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEmptyTrashDialog)

        viewModel.confirmEmptyTrash()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showEmptyTrashDialog)
        assertEquals("Trash emptied", viewModel.uiState.value.userMessage)
        assertTrue(fakeTransactionRepository.trashCleared)
    }

    @Test
    fun missingForeignEntities_handledGracefullyWithoutCrashing() = runTest(testDispatcher) {
        val txWithMissingEntities = Transaction(
            id = EntityId("tx_orphan"),
            amount = Money.of(BigDecimal("20.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_nonexistent"),
            categoryId = EntityId("cat_nonexistent"),
            timestamp = Instant.parse("2026-09-15T00:00:00Z"),
            isDeleted = true
        )
        fakeTransactionRepository.deletedTransactions.value = listOf(txWithMissingEntities)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.totalCount)
        val item = state.groupedTransactions[0].transactions[0]
        assertNull(item.category)
        assertNull(item.sourceAccount)
    }

    @Test
    fun repeatedOperations_areSafeAndDoNotCrash() = runTest(testDispatcher) {
        val id = EntityId("tx_repeated")
        viewModel.restoreTransaction(id)
        viewModel.restoreTransaction(id)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Transaction restored", viewModel.uiState.value.userMessage)

        viewModel.showDeletePermanentlyConfirmation(id)
        viewModel.confirmDeletePermanently()
        viewModel.confirmDeletePermanently() // should do nothing since target is already null
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Transaction permanently deleted", viewModel.uiState.value.userMessage)
    }

    // --- Fake Test Repositories ---

    private class FakeTransactionRepository : TransactionRepository {
        val deletedTransactions = MutableStateFlow<List<Transaction>>(emptyList())
        val restoredIds = mutableListOf<EntityId>()
        val permanentlyDeletedIds = mutableListOf<EntityId>()
        var trashCleared = false

        override fun getActiveTransactions(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun getDeletedTransactions(): Flow<List<Transaction>> = deletedTransactions.asStateFlow()

        override fun getTransaction(id: EntityId): Flow<Transaction?> = flowOf(null)

        override suspend fun getTransactionById(id: EntityId): Transaction? = null

        override suspend fun insertTransaction(transaction: Transaction) {}

        override suspend fun updateTransaction(transaction: Transaction) {}

        override suspend fun moveToTrash(id: EntityId) {}

        override suspend fun restoreFromTrash(id: EntityId) {
            restoredIds.add(id)
            deletedTransactions.value = deletedTransactions.value.filter { it.id != id }
        }

        override suspend fun deletePermanently(id: EntityId) {
            permanentlyDeletedIds.add(id)
            deletedTransactions.value = deletedTransactions.value.filter { it.id != id }
        }

        override suspend fun clearTrash() {
            trashCleared = true
            deletedTransactions.value = emptyList()
        }
    }

    private class FakeCategoryRepository(
        private val initialCategories: List<Category> = emptyList()
    ) : CategoryRepository {
        override fun getActiveCategories(): Flow<List<Category>> = flowOf(initialCategories.filter { !it.isArchived })
        override fun getAllCategories(): Flow<List<Category>> = flowOf(initialCategories)
        override fun getCategory(id: EntityId): Flow<Category?> = flowOf(initialCategories.find { it.id == id })
        override suspend fun getCategoryById(id: EntityId): Category? = initialCategories.find { it.id == id }
        override suspend fun insertCategory(category: Category) {}
        override suspend fun updateCategory(category: Category) {}
        override suspend fun archiveCategory(id: EntityId) {}
        override suspend fun deleteCategory(category: Category) {}
    }

    private class FakeAccountRepository(
        private val initialAccounts: List<Account> = emptyList()
    ) : AccountRepository {
        override fun getActiveAccounts(): Flow<List<Account>> = flowOf(initialAccounts.filter { !it.isArchived })
        override fun getAllAccounts(): Flow<List<Account>> = flowOf(initialAccounts)
        override fun getAccount(id: EntityId): Flow<Account?> = flowOf(initialAccounts.find { it.id == id })
        override suspend fun getAccountById(id: EntityId): Account? = initialAccounts.find { it.id == id }
        override suspend fun insertAccount(account: Account) {}
        override suspend fun updateAccount(account: Account) {}
        override suspend fun archiveAccount(id: EntityId) {}
        override suspend fun deleteAccount(account: Account) {}
    }
}

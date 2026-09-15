package com.example.expensetracker.feature.transactions

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
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val accountId1 = EntityId("acc-1")
    private val accountId2 = EntityId("acc-2")
    private val archivedAccountId = EntityId("acc-archived")

    private val categoryId1 = EntityId("cat-1")
    private val categoryId2 = EntityId("cat-2")
    private val archivedCategoryId = EntityId("cat-archived")

    private val now = Instant.now()
    private val yesterday = now.minus(1, ChronoUnit.DAYS)
    private val threeDaysAgo = now.minus(3, ChronoUnit.DAYS)

    private val sampleCategory1 = Category(
        id = categoryId1,
        name = "Food",
        iconKey = "restaurant",
        colorKey = "orange",
        isDefault = true
    )
    private val sampleCategory2 = Category(
        id = categoryId2,
        name = "Salary",
        iconKey = "work",
        colorKey = "green",
        isDefault = true
    )
    private val archivedCategory = Category(
        id = archivedCategoryId,
        name = "Old Category",
        iconKey = "archive",
        colorKey = "gray",
        isArchived = true
    )

    private val sampleAccount1 = Account(
        id = accountId1,
        name = "Bank Account",
        type = AccountType.BANK,
        currency = Currency.INR,
        initialBalance = Money(BigDecimal("1000.00"), Currency.INR),
        currentBalance = Money(BigDecimal("1000.00"), Currency.INR)
    )
    private val sampleAccount2 = Account(
        id = accountId2,
        name = "Cash",
        type = AccountType.CASH,
        currency = Currency.INR,
        initialBalance = Money(BigDecimal("500.00"), Currency.INR),
        currentBalance = Money(BigDecimal("500.00"), Currency.INR)
    )
    private val archivedAccount = Account(
        id = archivedAccountId,
        name = "Old Card",
        type = AccountType.CREDIT_CARD,
        currency = Currency.INR,
        initialBalance = Money.zero(Currency.INR),
        currentBalance = Money.zero(Currency.INR),
        isArchived = true
    )

    private val txExpense = Transaction(
        id = EntityId("tx-1"),
        type = TransactionType.EXPENSE,
        amount = Money(BigDecimal("150.00"), Currency.INR),
        categoryId = categoryId1,
        sourceAccountId = accountId1,
        destinationAccountId = null,
        timestamp = now,
        note = "Weekly Grocery"
    )

    private val txIncome = Transaction(
        id = EntityId("tx-2"),
        type = TransactionType.INCOME,
        amount = Money(BigDecimal("5000.00"), Currency.INR),
        categoryId = categoryId2,
        sourceAccountId = accountId1,
        destinationAccountId = null,
        timestamp = yesterday,
        note = "Monthly Paycheck"
    )

    private val txTransfer = Transaction(
        id = EntityId("tx-3"),
        type = TransactionType.TRANSFER,
        amount = Money(BigDecimal("200.00"), Currency.INR),
        categoryId = null,
        sourceAccountId = accountId1,
        destinationAccountId = accountId2,
        timestamp = threeDaysAgo,
        note = "ATM Withdrawal"
    )

    private lateinit var fakeTransactionRepo: FakeTransactionRepository
    private lateinit var fakeCategoryRepo: FakeCategoryRepository
    private lateinit var fakeAccountRepo: FakeAccountRepository
    private lateinit var useCases: TransactionsUseCases
    private lateinit var viewModel: TransactionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeTransactionRepo = FakeTransactionRepository(
            active = listOf(txExpense, txIncome, txTransfer),
            deleted = emptyList()
        )
        fakeCategoryRepo = FakeCategoryRepository(
            listOf(sampleCategory1, sampleCategory2, archivedCategory)
        )
        fakeAccountRepo = FakeAccountRepository(
            listOf(sampleAccount1, sampleAccount2, archivedAccount)
        )

        useCases = TransactionsUseCases(fakeTransactionRepo, fakeCategoryRepo, fakeAccountRepo)
        viewModel = TransactionsViewModel(useCases)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_loadsActiveTransactionsGroupedByDate() = runTest {
        // Collect state
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isTrashView)
        assertEquals(3, state.totalCount)
        assertNull(state.emptyState)
        assertEquals(3, state.groupedTransactions.size)

        collectJob.cancel()
    }

    @Test
    fun searchByNote_filtersMatchingTransactionsCaseInsensitive() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("GROCERY")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.totalCount)
        assertEquals("Weekly Grocery", state.groupedTransactions.first().transactions.first().transaction.note)

        collectJob.cancel()
    }

    @Test
    fun searchByCategoryName_matchesTransactions() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("food")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.totalCount)
        assertEquals("Food", state.groupedTransactions.first().transactions.first().category?.name)

        collectJob.cancel()
    }

    @Test
    fun searchByAccountName_matchesSourceOrDestinationAccount() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Cash is only on the transfer tx as destination
        viewModel.onSearchQueryChanged("Cash")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.totalCount)
        assertEquals(TransactionType.TRANSFER, state.groupedTransactions.first().transactions.first().transaction.type)

        collectJob.cancel()
    }

    @Test
    fun filterByType_filtersCorrectly() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onFilterChanged(TransactionFilter(type = TransactionType.EXPENSE))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.totalCount)
        assertEquals(TransactionType.EXPENSE, state.groupedTransactions.first().transactions.first().transaction.type)

        collectJob.cancel()
    }

    @Test
    fun combinedFilters_filtersSimultaneously() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Expense + Food + Bank Account
        viewModel.onFilterChanged(
            TransactionFilter(
                type = TransactionType.EXPENSE,
                categoryId = categoryId1,
                accountId = accountId1
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.totalCount)
        assertEquals(txExpense.id, state.groupedTransactions.first().transactions.first().transaction.id)

        // Combining filter with non-matching criteria yields empty
        viewModel.onFilterChanged(
            TransactionFilter(
                type = TransactionType.EXPENSE,
                categoryId = categoryId2 // Salary (income category)
            )
        )
        advanceUntilIdle()

        val emptyFilteredState = viewModel.uiState.value
        assertEquals(0, emptyFilteredState.totalCount)
        assertEquals(TransactionsEmptyState.NO_MATCHING_FILTERS, emptyFilteredState.emptyState)

        collectJob.cancel()
    }

    @Test
    fun clearFilters_restoresFullList() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onFilterChanged(TransactionFilter(type = TransactionType.EXPENSE))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.totalCount)

        viewModel.onClearFilters()
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.totalCount)
        assertFalse(viewModel.uiState.value.filter.isActive)

        collectJob.cancel()
    }

    @Test
    fun sorting_ordersCorrectly() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // 1. Highest amount first (Salary 5000 > ATM 200 > Grocery 150)
        viewModel.onSortChanged(TransactionSort.HIGHEST_AMOUNT)
        advanceUntilIdle()
        val highestFirst = viewModel.uiState.value.groupedTransactions.flatMap { it.transactions }
        assertEquals(BigDecimal("5000.00"), highestFirst[0].transaction.amount.amount)
        assertEquals(BigDecimal("200.00"), highestFirst[1].transaction.amount.amount)
        assertEquals(BigDecimal("150.00"), highestFirst[2].transaction.amount.amount)

        // 2. Lowest amount first (Grocery 150 < ATM 200 < Salary 5000)
        viewModel.onSortChanged(TransactionSort.LOWEST_AMOUNT)
        advanceUntilIdle()
        val lowestFirst = viewModel.uiState.value.groupedTransactions.flatMap { it.transactions }
        assertEquals(BigDecimal("150.00"), lowestFirst[0].transaction.amount.amount)
        assertEquals(BigDecimal("200.00"), lowestFirst[1].transaction.amount.amount)
        assertEquals(BigDecimal("5000.00"), lowestFirst[2].transaction.amount.amount)

        // 3. Oldest first (threeDaysAgo < yesterday < now)
        viewModel.onSortChanged(TransactionSort.OLDEST_FIRST)
        advanceUntilIdle()
        val oldestFirst = viewModel.uiState.value.groupedTransactions.flatMap { it.transactions }
        assertEquals(txTransfer.id, oldestFirst[0].transaction.id)
        assertEquals(txIncome.id, oldestFirst[1].transaction.id)
        assertEquals(txExpense.id, oldestFirst[2].transaction.id)

        // 4. Newest first (now > yesterday > threeDaysAgo)
        viewModel.onSortChanged(TransactionSort.NEWEST_FIRST)
        advanceUntilIdle()
        val newestFirst = viewModel.uiState.value.groupedTransactions.flatMap { it.transactions }
        assertEquals(txExpense.id, newestFirst[0].transaction.id)
        assertEquals(txIncome.id, newestFirst[1].transaction.id)
        assertEquals(txTransfer.id, newestFirst[2].transaction.id)

        collectJob.cancel()
    }

    @Test
    fun dateGrouping_formatsTodayAndYesterdayProperly() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onSortChanged(TransactionSort.NEWEST_FIRST)
        advanceUntilIdle()

        val groups = viewModel.uiState.value.groupedTransactions
        assertEquals(3, groups.size)
        assertEquals("Today", groups[0].header)
        assertEquals("Yesterday", groups[1].header)
        // Group 2 is older than yesterday, formatted localized date
        assertFalse(groups[2].header == "Today" || groups[2].header == "Yesterday")

        collectJob.cancel()
    }

    @Test
    fun transfersDisplayedCorrectly_withSourceAndDestination() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val transferItem = viewModel.uiState.value.groupedTransactions
            .flatMap { it.transactions }
            .first { it.transaction.type == TransactionType.TRANSFER }

        assertNull(transferItem.category)
        assertNotNull(transferItem.sourceAccount)
        assertEquals("Bank Account", transferItem.sourceAccount?.name)
        assertNotNull(transferItem.destinationAccount)
        assertEquals("Cash", transferItem.destinationAccount?.name)

        collectJob.cancel()
    }

    @Test
    fun trashOperations_moveToTrashAndRestoreAndPermanentDelete() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.totalCount)

        // 1. Move expense to trash
        viewModel.moveToTrash(txExpense.id)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.totalCount)

        // 2. Switch to Trash view
        viewModel.onToggleTrashView()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isTrashView)
        assertEquals(1, viewModel.uiState.value.totalCount)
        assertEquals(txExpense.id, viewModel.uiState.value.groupedTransactions.first().transactions.first().transaction.id)

        // 3. Restore from trash
        viewModel.restoreFromTrash(txExpense.id)
        advanceUntilIdle()

        // Trash view is now empty
        assertEquals(0, viewModel.uiState.value.totalCount)
        assertEquals(TransactionsEmptyState.EMPTY_TRASH, viewModel.uiState.value.emptyState)

        // 4. Back to active view
        viewModel.onToggleTrashView()
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.totalCount)

        // 5. Move to trash again and permanently delete
        viewModel.moveToTrash(txIncome.id)
        viewModel.onToggleTrashView()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.totalCount)
        viewModel.deletePermanently(txIncome.id)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.totalCount)
        assertEquals(TransactionsEmptyState.EMPTY_TRASH, viewModel.uiState.value.emptyState)

        collectJob.cancel()
    }

    @Test
    fun clearTrash_permanentlyDeletesAllItemsInTrash() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.moveToTrash(txExpense.id)
        viewModel.moveToTrash(txIncome.id)
        viewModel.onToggleTrashView()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.totalCount)

        viewModel.clearTrash()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.totalCount)
        assertEquals(TransactionsEmptyState.EMPTY_TRASH, viewModel.uiState.value.emptyState)

        collectJob.cancel()
    }

    @Test
    fun emptyStates_correctlyIdentified() = runTest {
        // Setup empty repository
        val emptyRepo = FakeTransactionRepository(emptyList(), emptyList())
        val emptyVm = TransactionsViewModel(TransactionsUseCases(emptyRepo, fakeCategoryRepo, fakeAccountRepo))

        val collectJob = launch { emptyVm.uiState.collect {} }
        advanceUntilIdle()

        // 1. No transactions at all
        assertEquals(TransactionsEmptyState.NO_TRANSACTIONS, emptyVm.uiState.value.emptyState)

        // 2. Empty Trash
        emptyVm.onToggleTrashView()
        advanceUntilIdle()
        assertEquals(TransactionsEmptyState.EMPTY_TRASH, emptyVm.uiState.value.emptyState)

        // Switch back and test with existing repo for search empty state
        val collectJob2 = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // 3. Search with no matches
        viewModel.onSearchQueryChanged("non-existent-query-12345")
        advanceUntilIdle()
        assertEquals(TransactionsEmptyState.NO_SEARCH_RESULTS, viewModel.uiState.value.emptyState)

        collectJob.cancel()
        collectJob2.cancel()
    }

    @Test
    fun archivedAndMissingEntities_handledGracefullyWithoutCrashing() = runTest {
        val txWithArchived = Transaction(
            id = EntityId("tx-archived"),
            type = TransactionType.EXPENSE,
            amount = Money(BigDecimal("99.00"), Currency.INR),
            categoryId = archivedCategoryId,
            sourceAccountId = archivedAccountId,
            destinationAccountId = null,
            timestamp = now,
            note = "Archived refs"
        )

        val txWithMissing = Transaction(
            id = EntityId("tx-missing"),
            type = TransactionType.EXPENSE,
            amount = Money(BigDecimal("12.00"), Currency.INR),
            categoryId = EntityId("non-existent-cat"),
            sourceAccountId = EntityId("non-existent-acc"),
            destinationAccountId = null,
            timestamp = now,
            note = "Missing refs"
        )

        val repo = FakeTransactionRepository(
            active = listOf(txWithArchived, txWithMissing),
            deleted = emptyList()
        )
        val vm = TransactionsViewModel(TransactionsUseCases(repo, fakeCategoryRepo, fakeAccountRepo))

        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.totalCount)

        val allItems = state.groupedTransactions.flatMap { it.transactions }
        val archivedItem = allItems.first { it.transaction.id == txWithArchived.id }
        val missingItem = allItems.first { it.transaction.id == txWithMissing.id }

        // Archived entities resolve fine because getAllCategories() / getAllAccounts() includes archived
        assertEquals("Old Category", archivedItem.category?.name)
        assertEquals("Old Card", archivedItem.sourceAccount?.name)

        // Missing entities resolve gracefully to null without throwing any exceptions
        assertNull(missingItem.category)
        assertNull(missingItem.sourceAccount)

        collectJob.cancel()
    }
}

// --- Test Fakes ---

private class FakeTransactionRepository(
    active: List<Transaction>,
    deleted: List<Transaction>
) : TransactionRepository {

    private val activeFlow = MutableStateFlow(active)
    private val deletedFlow = MutableStateFlow(deleted)

    override fun getActiveTransactions(): Flow<List<Transaction>> = activeFlow

    override fun getDeletedTransactions(): Flow<List<Transaction>> = deletedFlow

    override fun getTransaction(id: EntityId): Flow<Transaction?> = flowOf(
        (activeFlow.value + deletedFlow.value).firstOrNull { it.id == id }
    )

    override suspend fun getTransactionById(id: EntityId): Transaction? =
        (activeFlow.value + deletedFlow.value).firstOrNull { it.id == id }

    override suspend fun insertTransaction(transaction: Transaction) {
        activeFlow.update { it + transaction }
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        activeFlow.update { list -> list.map { if (it.id == transaction.id) transaction else it } }
    }

    override suspend fun moveToTrash(id: EntityId) {
        val item = activeFlow.value.firstOrNull { it.id == id } ?: return
        val updated = item.copy(isDeleted = true)
        activeFlow.update { it.filterNot { tx -> tx.id == id } }
        deletedFlow.update { it + updated }
    }

    override suspend fun restoreFromTrash(id: EntityId) {
        val item = deletedFlow.value.firstOrNull { it.id == id } ?: return
        val updated = item.copy(isDeleted = false)
        deletedFlow.update { it.filterNot { tx -> tx.id == id } }
        activeFlow.update { it + updated }
    }

    override suspend fun deletePermanently(id: EntityId) {
        deletedFlow.update { it.filterNot { tx -> tx.id == id } }
        activeFlow.update { it.filterNot { tx -> tx.id == id } }
    }

    override suspend fun clearTrash() {
        deletedFlow.value = emptyList()
    }
}

private class FakeCategoryRepository(
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

private class FakeAccountRepository(
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

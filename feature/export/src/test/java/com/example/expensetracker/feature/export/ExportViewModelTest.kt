package com.example.expensetracker.feature.export

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeTransactionRepository: FakeTransactionRepository
    private lateinit var fakeAccountRepository: FakeAccountRepository
    private lateinit var fakeCategoryRepository: FakeCategoryRepository
    private lateinit var exportService: TransactionExportService
    private lateinit var viewModel: ExportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeTransactionRepository = FakeTransactionRepository()
        fakeAccountRepository = FakeAccountRepository()
        fakeCategoryRepository = FakeCategoryRepository()

        // Populate standard accounts and categories
        fakeAccountRepository.accounts.value = listOf(
            Account(
                id = EntityId("acc_cash"),
                name = "Cash",
                type = AccountType.CASH,
                currency = Currency.USD,
                initialBalance = Money.of(BigDecimal("100"), Currency.USD),
                currentBalance = Money.of(BigDecimal("100"), Currency.USD)
            ),
            Account(
                id = EntityId("acc_bank"),
                name = "Bank",
                type = AccountType.BANK,
                currency = Currency.USD,
                initialBalance = Money.of(BigDecimal("1000"), Currency.USD),
                currentBalance = Money.of(BigDecimal("1000"), Currency.USD)
            ),
            Account(
                id = EntityId("acc_inr"),
                name = "INR Account",
                type = AccountType.BANK,
                currency = Currency.INR,
                initialBalance = Money.of(BigDecimal("5000"), Currency.INR),
                currentBalance = Money.of(BigDecimal("5000"), Currency.INR)
            )
        )

        fakeCategoryRepository.categories.value = listOf(
            Category(
                id = EntityId("cat_groceries"),
                name = "Groceries",
                iconKey = "shopping_cart",
                colorKey = "green",
                type = CategoryType.EXPENSE
            )
        )

        exportService = TransactionExportService(
            transactionRepository = fakeTransactionRepository,
            accountRepository = fakeAccountRepository,
            categoryRepository = fakeCategoryRepository
        )

        viewModel = ExportViewModel(exportService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setExportFilter_updatesState() {
        viewModel.setExportFilter(ExportFilter.ACTIVE_ONLY)
        assertEquals(ExportFilter.ACTIVE_ONLY, viewModel.uiState.value.exportFilter)

        viewModel.setExportFilter(ExportFilter.TRASH_ONLY)
        assertEquals(ExportFilter.TRASH_ONLY, viewModel.uiState.value.exportFilter)
    }

    @Test
    fun exportToStream_writesTransactionsAndSetsSuccessMessage() = runTest(testDispatcher) {
        val tx = Transaction(
            id = EntityId("tx_1"),
            amount = Money.of(BigDecimal("25.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_cash"),
            categoryId = EntityId("cat_groceries"),
            timestamp = Instant.parse("2026-09-15T09:00:00Z"),
            note = "Market"
        )
        fakeTransactionRepository.transactions.value = listOf(tx)

        val outputStream = ByteArrayOutputStream()
        viewModel.exportToStream(outputStream)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExporting)
        assertNotNull(state.exportSuccessMessage)
        assertTrue(state.exportSuccessMessage!!.contains("1 transactions"))

        val writtenCsv = outputStream.toString(StandardCharsets.UTF_8.name())
        assertTrue(writtenCsv.contains("tx_1"))
        assertTrue(writtenCsv.contains("25.00"))
    }

    @Test
    fun analyzeCsvStream_validCsv_detectsNewAndUpdatedTransactions() = runTest(testDispatcher) {
        // Pre-existing transaction
        val existingTx = Transaction(
            id = EntityId("tx_existing"),
            amount = Money.of(BigDecimal("10.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_cash"),
            timestamp = Instant.parse("2026-09-15T00:00:00Z")
        )
        fakeTransactionRepository.transactions.value = listOf(existingTx)

        val csv = """
            version,id,type,amount,currency,source_account_id,destination_account_id,category_id,timestamp,note,recurring_id,is_deleted
            1,tx_existing,EXPENSE,15.00,USD,acc_cash,,cat_groceries,2026-09-15T00:00:00Z,Updated note,,false
            1,tx_brand_new,EXPENSE,50.00,USD,acc_bank,,cat_groceries,2026-09-15T01:00:00Z,New note,,false
        """.trimIndent()

        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        viewModel.analyzeCsvStream(inputStream)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAnalyzingImport)
        val analysis = state.importAnalysis
        assertNotNull(analysis)
        assertEquals(2, analysis!!.totalRows)
        assertEquals(2, analysis.validTransactions.size)
        assertEquals(1, analysis.newTransactionsCount)
        assertEquals(1, analysis.updateTransactionsCount)
        assertEquals(0, analysis.invalidRows.size)
    }

    @Test
    fun analyzeCsvStream_missingForeignKeys_flagsInvalidRows() = runTest(testDispatcher) {
        val csv = """
            version,id,type,amount,currency,source_account_id,destination_account_id,category_id,timestamp,note,recurring_id,is_deleted
            1,tx_unknown_acc,EXPENSE,10.00,USD,acc_non_existent,,,2026-09-15T00:00:00Z,,,false
            1,tx_unknown_cat,EXPENSE,20.00,USD,acc_cash,,cat_non_existent,2026-09-15T00:00:00Z,,,false
            1,tx_valid,EXPENSE,30.00,USD,acc_cash,,cat_groceries,2026-09-15T00:00:00Z,,,false
        """.trimIndent()

        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        viewModel.analyzeCsvStream(inputStream)
        testDispatcher.scheduler.advanceUntilIdle()

        val analysis = viewModel.uiState.value.importAnalysis
        assertNotNull(analysis)
        assertEquals(3, analysis!!.totalRows)
        assertEquals(1, analysis.validTransactions.size)
        assertEquals(2, analysis.invalidRows.size)

        assertEquals("tx_valid", analysis.validTransactions[0].id.value)
        assertTrue(analysis.invalidRows.any { it.reason.contains("Source account 'acc_non_existent' does not exist") })
        assertTrue(analysis.invalidRows.any { it.reason.contains("Category 'cat_non_existent' does not exist") })
    }

    @Test
    fun executeImport_mergeMode_insertsWithoutDeletingExisting() = runTest(testDispatcher) {
        val existingTx = Transaction(
            id = EntityId("tx_keep"),
            amount = Money.of(BigDecimal("10.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_cash"),
            timestamp = Instant.parse("2026-09-15T00:00:00Z")
        )
        fakeTransactionRepository.transactions.value = listOf(existingTx)

        val csv = """
            version,id,type,amount,currency,source_account_id,destination_account_id,category_id,timestamp,note,recurring_id,is_deleted
            1,tx_new,EXPENSE,30.00,USD,acc_cash,,cat_groceries,2026-09-15T00:00:00Z,,,false
        """.trimIndent()

        viewModel.analyzeCsvStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setImportMode(ImportMode.MERGE)
        viewModel.requestExecuteImport()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExecutingImport)
        assertNotNull(state.importSuccessMessage)
        assertTrue(state.importSuccessMessage!!.contains("Merged 1 transactions"))

        // Repository should contain both tx_keep and tx_new
        val repoTxs = fakeTransactionRepository.transactions.value
        assertEquals(2, repoTxs.size)
        assertTrue(repoTxs.any { it.id.value == "tx_keep" })
        assertTrue(repoTxs.any { it.id.value == "tx_new" })
    }

    @Test
    fun executeImport_replaceMode_showsDialogThenReplacesExisting() = runTest(testDispatcher) {
        val existingTx = Transaction(
            id = EntityId("tx_to_delete"),
            amount = Money.of(BigDecimal("10.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_cash"),
            timestamp = Instant.parse("2026-09-15T00:00:00Z")
        )
        fakeTransactionRepository.transactions.value = listOf(existingTx)

        val csv = """
            version,id,type,amount,currency,source_account_id,destination_account_id,category_id,timestamp,note,recurring_id,is_deleted
            1,tx_replacement,EXPENSE,75.00,USD,acc_cash,,cat_groceries,2026-09-15T00:00:00Z,,,false
        """.trimIndent()

        viewModel.analyzeCsvStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setImportMode(ImportMode.REPLACE)
        viewModel.requestExecuteImport()

        // Must show confirmation dialog first
        assertTrue(viewModel.uiState.value.showReplaceConfirmDialog)

        // Confirm
        viewModel.confirmAndExecuteImport()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showReplaceConfirmDialog)
        assertFalse(state.isExecutingImport)
        assertNotNull(state.importSuccessMessage)
        assertTrue(state.importSuccessMessage!!.contains("Replaced 1 old records with 1 new transactions"))

        // Old transaction wiped, only replacement exists
        val repoTxs = fakeTransactionRepository.transactions.value
        assertEquals(1, repoTxs.size)
        assertEquals("tx_replacement", repoTxs[0].id.value)
    }

    @Test
    fun clearImportAnalysis_resetsImportState() = runTest(testDispatcher) {
        val csv = "version,id,type,amount,currency,source_account_id,destination_account_id,category_id,timestamp,note,recurring_id,is_deleted\r\n" +
                "1,tx_1,EXPENSE,10.00,USD,acc_cash,,,2026-09-15T00:00:00Z,,,false"
        viewModel.analyzeCsvStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.importAnalysis)
        viewModel.clearImportAnalysis()

        assertNull(viewModel.uiState.value.importAnalysis)
        assertNull(viewModel.uiState.value.importError)
    }

    @Test
    fun analyzeCsv_transferDifferentCurrencies_failsValidation() = runTest(testDispatcher) {
        val csv = """
            version,id,type,amount,currency,source_account_id,destination_account_id,category_id,timestamp,note,recurring_id,is_deleted
            1,tx_cross,TRANSFER,50.00,USD,acc_bank,acc_inr,,2026-09-15T00:00:00Z,,,false
        """.trimIndent()

        viewModel.analyzeCsvStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        testDispatcher.scheduler.advanceUntilIdle()

        val analysis = viewModel.uiState.value.importAnalysis
        assertNotNull(analysis)
        assertEquals(0, analysis!!.validTransactions.size)
        assertEquals(1, analysis.invalidRows.size)
        assertTrue(analysis.invalidRows[0].reason.contains("Transfer between differing currencies is not supported"))
    }

    @Test
    fun executeImport_mergeMode_preservesLocalTrashState() = runTest(testDispatcher) {
        val trashedTx = Transaction(
            id = EntityId("tx_trashed"),
            amount = Money.of(BigDecimal("20.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_cash"),
            timestamp = Instant.parse("2026-09-15T00:00:00Z"),
            isDeleted = true
        )
        fakeTransactionRepository.transactions.value = listOf(trashedTx)

        // Incoming CSV has is_deleted=false for tx_trashed
        val csv = """
            version,id,type,amount,currency,source_account_id,destination_account_id,category_id,timestamp,note,recurring_id,is_deleted
            1,tx_trashed,EXPENSE,20.00,USD,acc_cash,,cat_groceries,2026-09-15T00:00:00Z,,,false
        """.trimIndent()

        viewModel.analyzeCsvStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setImportMode(ImportMode.MERGE)
        viewModel.requestExecuteImport()
        testDispatcher.scheduler.advanceUntilIdle()

        val repoTxs = fakeTransactionRepository.transactions.value
        assertEquals(1, repoTxs.size)
        assertTrue("Local trash state must be preserved during merge", repoTxs[0].isDeleted)
    }

    // --- Fake Test Repositories ---

    private class FakeTransactionRepository : TransactionRepository {
        val transactions = MutableStateFlow<List<Transaction>>(emptyList())

        override fun getAllTransactions(): Flow<List<Transaction>> = transactions.asStateFlow()

        override fun getActiveTransactions(): Flow<List<Transaction>> =
            MutableStateFlow(transactions.value.filter { !it.isDeleted })

        override fun getDeletedTransactions(): Flow<List<Transaction>> =
            MutableStateFlow(transactions.value.filter { it.isDeleted })

        override fun getTransaction(id: EntityId): Flow<Transaction?> =
            MutableStateFlow(transactions.value.find { it.id == id })

        override suspend fun getTransactionById(id: EntityId): Transaction? =
            transactions.value.find { it.id == id }

        override suspend fun insertTransaction(transaction: Transaction) {
            transactions.value = transactions.value.filter { it.id != transaction.id } + transaction
        }

        override suspend fun insertTransactions(newTransactions: List<Transaction>) {
            val newIds = newTransactions.map { it.id }.toSet()
            transactions.value = transactions.value.filter { !newIds.contains(it.id) } + newTransactions
        }

        override suspend fun updateTransaction(transaction: Transaction) {
            insertTransaction(transaction)
        }

        override suspend fun deleteAllTransactions() {
            transactions.value = emptyList()
        }

        override suspend fun moveToTrash(id: EntityId) {
            transactions.value = transactions.value.map {
                if (it.id == id) it.copy(isDeleted = true) else it
            }
        }

        override suspend fun restoreFromTrash(id: EntityId) {
            transactions.value = transactions.value.map {
                if (it.id == id) it.copy(isDeleted = false) else it
            }
        }

        override suspend fun deletePermanently(id: EntityId) {
            transactions.value = transactions.value.filter { it.id != id }
        }

        override suspend fun clearTrash() {
            transactions.value = transactions.value.filter { !it.isDeleted }
        }
    }

    private class FakeAccountRepository : AccountRepository {
        val accounts = MutableStateFlow<List<Account>>(emptyList())

        override fun getAllAccounts(): Flow<List<Account>> = accounts.asStateFlow()

        override fun getActiveAccounts(): Flow<List<Account>> =
            MutableStateFlow(accounts.value.filter { !it.isArchived })

        override fun getAccount(id: EntityId): Flow<Account?> =
            MutableStateFlow(accounts.value.find { it.id == id })

        override suspend fun getAccountById(id: EntityId): Account? =
            accounts.value.find { it.id == id }

        override suspend fun insertAccount(account: Account) {
            accounts.value = accounts.value.filter { it.id != account.id } + account
        }

        override suspend fun updateAccount(account: Account) {
            insertAccount(account)
        }

        override suspend fun archiveAccount(id: EntityId) {
            accounts.value = accounts.value.map {
                if (it.id == id) it.copy(isArchived = true) else it
            }
        }

        override suspend fun deleteAccount(account: Account) {
            accounts.value = accounts.value.filter { it.id != account.id }
        }
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categories = MutableStateFlow<List<Category>>(emptyList())

        override fun getAllCategories(): Flow<List<Category>> = categories.asStateFlow()

        override fun getActiveCategories(): Flow<List<Category>> =
            MutableStateFlow(categories.value.filter { !it.isArchived })

        override fun getCategory(id: EntityId): Flow<Category?> =
            MutableStateFlow(categories.value.find { it.id == id })

        override suspend fun getCategoryById(id: EntityId): Category? =
            categories.value.find { it.id == id }

        override suspend fun insertCategory(category: Category) {
            categories.value = categories.value.filter { it.id != category.id } + category
        }

        override suspend fun updateCategory(category: Category) {
            insertCategory(category)
        }

        override suspend fun updateCategoryOrder(newCategories: List<Category>) {
            categories.value = newCategories
        }

        override suspend fun archiveCategory(id: EntityId) {
            categories.value = categories.value.map {
                if (it.id == id) it.copy(isArchived = true) else it
            }
        }

        override suspend fun deleteCategory(category: Category) {
            categories.value = categories.value.filter { it.id != category.id }
        }
    }
}

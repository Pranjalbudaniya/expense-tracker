package com.example.expensetracker.worker

import androidx.work.ListenableWorker
import com.example.expensetracker.core.data.recurring.RecurringTransactionProcessor
import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.RecurringTransactionRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.category.CategoryType
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.recurring.RecurringTransaction
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class RecurringTransactionWorkerTest {

    private lateinit var recurringRepo: FakeRecurringTransactionRepository
    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var categoryRepo: FakeCategoryRepository
    private lateinit var processor: RecurringTransactionProcessor

    private val defaultZone = ZoneId.of("UTC")

    private val accountCash = Account(
        id = EntityId("acc-cash"),
        name = "Cash Wallet",
        type = AccountType.CASH,
        currency = Currency.USD,
        initialBalance = Money(BigDecimal("1000.00"), Currency.USD),
        currentBalance = Money(BigDecimal("1000.00"), Currency.USD),
        isArchived = false
    )

    private val accountBank = Account(
        id = EntityId("acc-bank"),
        name = "Checking Account",
        type = AccountType.BANK,
        currency = Currency.USD,
        initialBalance = Money(BigDecimal("5000.00"), Currency.USD),
        currentBalance = Money(BigDecimal("5000.00"), Currency.USD),
        isArchived = false
    )

    private val catRent = Category(
        id = EntityId("cat-rent"),
        name = "Rent",
        iconKey = "home",
        colorKey = "category_blue",
        type = CategoryType.EXPENSE,
        isArchived = false
    )

    @Before
    fun setUp() {
        recurringRepo = FakeRecurringTransactionRepository()
        transactionRepo = FakeTransactionRepository()
        accountRepo = FakeAccountRepository()
        categoryRepo = FakeCategoryRepository()

        accountRepo.saveAccount(accountCash)
        accountRepo.saveAccount(accountBank)
        categoryRepo.saveCategory(catRent)

        processor = RecurringTransactionProcessor(
            recurringRepository = recurringRepo,
            transactionRepository = transactionRepo,
            accountRepository = accountRepo,
            categoryRepository = categoryRepo
        )
    }

    @Test
    fun dueRecurringTransaction_isCreated() = runTest {
        val recurring = RecurringTransaction(
            id = EntityId("rec-rent"),
            amount = Money(BigDecimal("1200.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = accountBank.id,
            categoryId = catRent.id,
            frequency = RecurrenceFrequency.MONTHLY,
            nextOccurrence = LocalDate.of(2026, 5, 1),
            startDate = LocalDate.of(2026, 5, 1)
        )
        recurringRepo.insertRecurringTransaction(recurring)

        val result = processor.processDueOccurrences(
            asOfDate = LocalDate.of(2026, 5, 1),
            zoneId = defaultZone
        )

        assertEquals(1, result.occurrencesCreated)
        assertEquals(0, result.skippedDuplicates)
        assertTrue(result.isSuccess)

        val expectedTxId = EntityId("rec_tx_rec-rent_2026-05-01")
        val createdTx = transactionRepo.getTransactionById(expectedTxId)
        assertNotNull(createdTx)
        assertEquals(Money(BigDecimal("1200.00"), Currency.USD), createdTx!!.amount)
        assertEquals(TransactionType.EXPENSE, createdTx.type)
        assertEquals(accountBank.id, createdTx.sourceAccountId)
        assertEquals(catRent.id, createdTx.categoryId)

        val updatedRecurring = recurringRepo.getRecurringTransactionById(recurring.id)
        assertNotNull(updatedRecurring)
        assertEquals(LocalDate.of(2026, 5, 1), updatedRecurring!!.lastGeneratedOccurrence)
        assertEquals(LocalDate.of(2026, 6, 1), updatedRecurring.nextOccurrence)
    }

    @Test
    fun futureTransaction_isNotCreated() = runTest {
        val recurring = RecurringTransaction(
            id = EntityId("rec-future"),
            amount = Money(BigDecimal("50.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = accountCash.id,
            frequency = RecurrenceFrequency.DAILY,
            nextOccurrence = LocalDate.of(2026, 5, 10),
            startDate = LocalDate.of(2026, 5, 10)
        )
        recurringRepo.insertRecurringTransaction(recurring)

        val result = processor.processDueOccurrences(
            asOfDate = LocalDate.of(2026, 5, 5),
            zoneId = defaultZone
        )

        assertEquals(0, result.occurrencesCreated)
        assertEquals(1, result.skippedDisabledOrNotDue)
        assertTrue(result.isSuccess)
        assertEquals(0, transactionRepo.getAllInserted().size)
    }

    @Test
    fun sameOccurrence_cannotBeDuplicated() = runTest {
        val recurring = RecurringTransaction(
            id = EntityId("rec-dupe-test"),
            amount = Money(BigDecimal("100.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = accountCash.id,
            frequency = RecurrenceFrequency.WEEKLY,
            nextOccurrence = LocalDate.of(2026, 5, 1),
            startDate = LocalDate.of(2026, 5, 1)
        )
        recurringRepo.insertRecurringTransaction(recurring)

        // First execution creates occurrence for 2026-05-01
        val firstResult = processor.processDueOccurrences(
            asOfDate = LocalDate.of(2026, 5, 1),
            zoneId = defaultZone
        )
        assertEquals(1, firstResult.occurrencesCreated)

        // Reset recurring nextOccurrence to simulate an abnormal restart / replay
        val updated = recurringRepo.getRecurringTransactionById(recurring.id)!!
        recurringRepo.updateRecurringTransaction(updated.copy(nextOccurrence = LocalDate.of(2026, 5, 1), lastGeneratedOccurrence = null))

        // Second execution attempts to process the exact same occurrence date
        val secondResult = processor.processDueOccurrences(
            asOfDate = LocalDate.of(2026, 5, 1),
            zoneId = defaultZone
        )

        assertEquals(0, secondResult.occurrencesCreated)
        assertEquals(1, secondResult.skippedDuplicates)
        assertEquals(1, transactionRepo.getAllInserted().size)
    }

    @Test
    fun multipleMissedOccurrences_behaveCorrectly() = runTest {
        // Monthly transaction starts Jan 1. App is not opened until April 10.
        val recurring = RecurringTransaction(
            id = EntityId("rec-missed"),
            amount = Money(BigDecimal("25.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = accountCash.id,
            frequency = RecurrenceFrequency.MONTHLY,
            nextOccurrence = LocalDate.of(2026, 1, 1),
            startDate = LocalDate.of(2026, 1, 1)
        )
        recurringRepo.insertRecurringTransaction(recurring)

        val result = processor.processDueOccurrences(
            asOfDate = LocalDate.of(2026, 4, 10),
            zoneId = defaultZone
        )

        // Should create occurrences for Jan 1, Feb 1, Mar 1, Apr 1 (4 total).
        assertEquals(4, result.occurrencesCreated)
        assertEquals(0, result.skippedDuplicates)

        val inserted = transactionRepo.getAllInserted()
        assertEquals(4, inserted.size)

        assertNotNull(transactionRepo.getTransactionById(EntityId("rec_tx_rec-missed_2026-01-01")))
        assertNotNull(transactionRepo.getTransactionById(EntityId("rec_tx_rec-missed_2026-02-01")))
        assertNotNull(transactionRepo.getTransactionById(EntityId("rec_tx_rec-missed_2026-03-01")))
        assertNotNull(transactionRepo.getTransactionById(EntityId("rec_tx_rec-missed_2026-04-01")))
        assertNull(transactionRepo.getTransactionById(EntityId("rec_tx_rec-missed_2026-05-01")))

        val updated = recurringRepo.getRecurringTransactionById(recurring.id)!!
        assertEquals(LocalDate.of(2026, 4, 1), updated.lastGeneratedOccurrence)
        assertEquals(LocalDate.of(2026, 5, 1), updated.nextOccurrence)
    }

    @Test
    fun endDate_works_andCompletesRecurrence() = runTest {
        val recurring = RecurringTransaction(
            id = EntityId("rec-end-date"),
            amount = Money(BigDecimal("80.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = accountCash.id,
            frequency = RecurrenceFrequency.MONTHLY,
            nextOccurrence = LocalDate.of(2026, 1, 1),
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 2, 28)
        )
        recurringRepo.insertRecurringTransaction(recurring)

        val result = processor.processDueOccurrences(
            asOfDate = LocalDate.of(2026, 4, 1),
            zoneId = defaultZone
        )

        // Should generate Jan 1 and Feb 1, but NOT Mar 1 because Mar 1 > endDate
        assertEquals(2, result.occurrencesCreated)
        assertEquals(1, result.completedRecurrences)

        val updated = recurringRepo.getRecurringTransactionById(recurring.id)!!
        assertFalse("Recurrence must be completed and disabled", updated.isEnabled)
        assertEquals(LocalDate.of(2026, 2, 1), updated.lastGeneratedOccurrence)
    }

    @Test
    fun exactEndDateOccurrence_works() = runTest {
        val recurring = RecurringTransaction(
            id = EntityId("rec-exact-end"),
            amount = Money(BigDecimal("500.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = accountBank.id,
            frequency = RecurrenceFrequency.MONTHLY,
            nextOccurrence = LocalDate.of(2026, 3, 31),
            startDate = LocalDate.of(2026, 3, 31),
            endDate = LocalDate.of(2026, 3, 31)
        )
        recurringRepo.insertRecurringTransaction(recurring)

        val result = processor.processDueOccurrences(
            asOfDate = LocalDate.of(2026, 3, 31),
            zoneId = defaultZone
        )

        assertEquals(1, result.occurrencesCreated)
        assertEquals(1, result.completedRecurrences)

        assertNotNull(transactionRepo.getTransactionById(EntityId("rec_tx_rec-exact-end_2026-03-31")))
        val updated = recurringRepo.getRecurringTransactionById(recurring.id)!!
        assertFalse("Recurrence must be disabled after exact end date occurrence", updated.isEnabled)
    }

    @Test
    fun disabledRecurrence_isIgnored() = runTest {
        val recurring = RecurringTransaction(
            id = EntityId("rec-disabled"),
            amount = Money(BigDecimal("100.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = accountCash.id,
            frequency = RecurrenceFrequency.DAILY,
            nextOccurrence = LocalDate.of(2026, 5, 1),
            startDate = LocalDate.of(2026, 5, 1),
            isEnabled = false
        )
        recurringRepo.insertRecurringTransaction(recurring)

        val result = processor.processDueOccurrences(
            asOfDate = LocalDate.of(2026, 5, 5),
            zoneId = defaultZone
        )

        assertEquals(0, result.occurrencesCreated)
        assertEquals(1, result.skippedDisabledOrNotDue)
        assertEquals(0, transactionRepo.getAllInserted().size)
    }

    @Test
    fun transfer_remainsTransfer() = runTest {
        val recurring = RecurringTransaction(
            id = EntityId("rec-transfer"),
            amount = Money(BigDecimal("300.00"), Currency.USD),
            type = TransactionType.TRANSFER,
            sourceAccountId = accountBank.id,
            destinationAccountId = accountCash.id,
            frequency = RecurrenceFrequency.MONTHLY,
            nextOccurrence = LocalDate.of(2026, 5, 1),
            startDate = LocalDate.of(2026, 5, 1)
        )
        recurringRepo.insertRecurringTransaction(recurring)

        val result = processor.processDueOccurrences(
            asOfDate = LocalDate.of(2026, 5, 1),
            zoneId = defaultZone
        )

        assertEquals(1, result.occurrencesCreated)
        val tx = transactionRepo.getTransactionById(EntityId("rec_tx_rec-transfer_2026-05-01"))
        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER, tx!!.type)
        assertEquals(accountBank.id, tx.sourceAccountId)
        assertEquals(accountCash.id, tx.destinationAccountId)
        assertNull(tx.categoryId)
    }

    @Test
    fun archivedReferences_failSafely() = runTest {
        val archivedAccount = Account(
            id = EntityId("acc-archived"),
            name = "Old Account",
            type = AccountType.BANK,
            currency = Currency.USD,
            initialBalance = Money(BigDecimal.ZERO, Currency.USD),
            currentBalance = Money(BigDecimal.ZERO, Currency.USD),
            isArchived = true
        )
        accountRepo.saveAccount(archivedAccount)

        val recurring = RecurringTransaction(
            id = EntityId("rec-archived-account"),
            amount = Money(BigDecimal("50.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = archivedAccount.id,
            frequency = RecurrenceFrequency.DAILY,
            nextOccurrence = LocalDate.of(2026, 5, 1),
            startDate = LocalDate.of(2026, 5, 1)
        )
        recurringRepo.insertRecurringTransaction(recurring)

        val result = processor.processDueOccurrences(
            asOfDate = LocalDate.of(2026, 5, 1),
            zoneId = defaultZone
        )

        assertEquals(0, result.occurrencesCreated)
        assertEquals(1, result.failures.size)
        assertFalse(result.failures[0].isTransient)
        assertTrue(result.failures[0].reason.contains("archived", ignoreCase = true))

        // Account must not be resurrected
        val accCheck = accountRepo.getAccountById(archivedAccount.id)
        assertTrue(accCheck!!.isArchived)
    }

    @Test
    fun transientFailure_causesRetry() = runTest {
        val recurring = RecurringTransaction(
            id = EntityId("rec-transient"),
            amount = Money(BigDecimal("10.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = accountCash.id,
            frequency = RecurrenceFrequency.DAILY,
            nextOccurrence = LocalDate.of(2026, 5, 1),
            startDate = LocalDate.of(2026, 5, 1)
        )
        recurringRepo.insertRecurringTransaction(recurring)

        transactionRepo.shouldThrowTransientError = true

        val workerResult = RecurringTransactionWorker.executeRecurringWork(
            processor = processor,
            asOfDate = LocalDate.of(2026, 5, 1),
            zoneId = defaultZone
        )
        assertEquals(ListenableWorker.Result.retry(), workerResult)
    }

    @Test
    fun permanentInvalidData_doesNotCauseEndlessRetries() = runTest {
        val recurring = RecurringTransaction(
            id = EntityId("rec-missing-acc"),
            amount = Money(BigDecimal("10.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("non-existent-acc"),
            frequency = RecurrenceFrequency.DAILY,
            nextOccurrence = LocalDate.of(2026, 5, 1),
            startDate = LocalDate.of(2026, 5, 1)
        )
        recurringRepo.insertRecurringTransaction(recurring)

        val workerResult = RecurringTransactionWorker.executeRecurringWork(
            processor = processor,
            asOfDate = LocalDate.of(2026, 5, 1),
            zoneId = defaultZone
        )
        // Should succeed rather than causing endless retries for permanent errors
        assertEquals(ListenableWorker.Result.success(), workerResult)
    }

    @Test
    fun worker_canSafelyRunMoreThanOnce() = runTest {
        val recurring = RecurringTransaction(
            id = EntityId("rec-run-twice"),
            amount = Money(BigDecimal("40.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = accountCash.id,
            frequency = RecurrenceFrequency.WEEKLY,
            nextOccurrence = LocalDate.of(2026, 5, 1),
            startDate = LocalDate.of(2026, 5, 1)
        )
        recurringRepo.insertRecurringTransaction(recurring)

        val firstWorkerResult = RecurringTransactionWorker.executeRecurringWork(
            processor = processor,
            asOfDate = LocalDate.of(2026, 5, 1),
            zoneId = defaultZone
        )
        assertEquals(ListenableWorker.Result.success(), firstWorkerResult)
        assertEquals(1, transactionRepo.getAllInserted().size)

        val secondWorkerResult = RecurringTransactionWorker.executeRecurringWork(
            processor = processor,
            asOfDate = LocalDate.of(2026, 5, 1),
            zoneId = defaultZone
        )
        assertEquals(ListenableWorker.Result.success(), secondWorkerResult)
        assertEquals(1, transactionRepo.getAllInserted().size)
    }
}

// -------------------------------------------------------------------------
// Test Fakes
// -------------------------------------------------------------------------

private class FakeRecurringTransactionRepository : RecurringTransactionRepository {
    private val items = mutableMapOf<EntityId, RecurringTransaction>()
    private val flow = MutableStateFlow<List<RecurringTransaction>>(emptyList())

    override fun getAllRecurringTransactions(): Flow<List<RecurringTransaction>> = flow

    override fun getActiveRecurringTransactions(): Flow<List<RecurringTransaction>> = flow

    override suspend fun getRecurringTransactionById(id: EntityId): RecurringTransaction? = items[id]

    override suspend fun insertRecurringTransaction(recurringTransaction: RecurringTransaction) {
        items[recurringTransaction.id] = recurringTransaction
        flow.value = items.values.toList()
    }

    override suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransaction) {
        items[recurringTransaction.id] = recurringTransaction
        flow.value = items.values.toList()
    }

    override suspend fun setEnabled(id: EntityId, isEnabled: Boolean) {
        items[id]?.let {
            items[id] = it.copy(isEnabled = isEnabled)
            flow.value = items.values.toList()
        }
    }

    override suspend fun deleteRecurringTransaction(recurringTransaction: RecurringTransaction) {
        items.remove(recurringTransaction.id)
        flow.value = items.values.toList()
    }
}

private class FakeTransactionRepository : TransactionRepository {
    private val transactions = mutableMapOf<EntityId, Transaction>()
    private val flow = MutableStateFlow<List<Transaction>>(emptyList())
    var shouldThrowTransientError: Boolean = false

    fun getAllInserted(): List<Transaction> = transactions.values.toList()

    override fun getActiveTransactions(): Flow<List<Transaction>> = flow

    override fun getDeletedTransactions(): Flow<List<Transaction>> = flowOf(emptyList())

    override fun getTransaction(id: EntityId): Flow<Transaction?> = flowOf(transactions[id])

    override suspend fun getTransactionById(id: EntityId): Transaction? = transactions[id]

    override suspend fun insertTransaction(transaction: Transaction) {
        if (shouldThrowTransientError) {
            throw IOException("Simulated transient IO failure")
        }
        transactions[transaction.id] = transaction
        flow.value = transactions.values.toList()
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactions[transaction.id] = transaction
        flow.value = transactions.values.toList()
    }

    override suspend fun moveToTrash(id: EntityId) {
        transactions[id]?.let { transactions[id] = it.copy(isDeleted = true) }
    }

    override suspend fun restoreFromTrash(id: EntityId) {
        transactions[id]?.let { transactions[id] = it.copy(isDeleted = false) }
    }

    override suspend fun deletePermanently(id: EntityId) {
        transactions.remove(id)
    }

    override suspend fun clearTrash() {
        transactions.entries.removeIf { it.value.isDeleted }
    }
}

private class FakeAccountRepository : AccountRepository {
    private val accounts = mutableMapOf<EntityId, Account>()

    fun saveAccount(account: Account) {
        accounts[account.id] = account
    }

    override fun getActiveAccounts(): Flow<List<Account>> = flowOf(accounts.values.filter { !it.isArchived })

    override fun getAllAccounts(): Flow<List<Account>> = flowOf(accounts.values.toList())

    override fun getAccount(id: EntityId): Flow<Account?> = flowOf(accounts[id])

    override suspend fun getAccountById(id: EntityId): Account? = accounts[id]

    override suspend fun insertAccount(account: Account) {
        accounts[account.id] = account
    }

    override suspend fun updateAccount(account: Account) {
        accounts[account.id] = account
    }

    override suspend fun archiveAccount(id: EntityId) {
        accounts[id]?.let { accounts[id] = it.copy(isArchived = true) }
    }

    override suspend fun deleteAccount(account: Account) {
        accounts.remove(account.id)
    }
}

private class FakeCategoryRepository : CategoryRepository {
    private val categories = mutableMapOf<EntityId, Category>()

    fun saveCategory(category: Category) {
        categories[category.id] = category
    }

    override fun getActiveCategories(): Flow<List<Category>> = flowOf(categories.values.filter { !it.isArchived })

    override fun getAllCategories(): Flow<List<Category>> = flowOf(categories.values.toList())

    override fun getCategory(id: EntityId): Flow<Category?> = flowOf(categories[id])

    override suspend fun getCategoryById(id: EntityId): Category? = categories[id]

    override suspend fun insertCategory(category: Category) {
        categories[category.id] = category
    }

    override suspend fun updateCategory(category: Category) {
        categories[category.id] = category
    }

    override suspend fun archiveCategory(id: EntityId) {
        categories[id]?.let { categories[id] = it.copy(isArchived = true) }
    }

    override suspend fun deleteCategory(category: Category) {
        categories.remove(category.id)
    }

    override suspend fun updateCategoryOrder(categories: List<Category>) {}
}

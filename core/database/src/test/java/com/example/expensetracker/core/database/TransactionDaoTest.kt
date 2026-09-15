package com.example.expensetracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.core.database.converter.DatabaseConverters
import com.example.expensetracker.core.database.dao.AccountDao
import com.example.expensetracker.core.database.dao.CategoryDao
import com.example.expensetracker.core.database.dao.TransactionDao
import com.example.expensetracker.core.database.entity.AccountEntity
import com.example.expensetracker.core.database.entity.CategoryEntity
import com.example.expensetracker.core.database.entity.TransactionEntity
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = database.transactionDao()
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testInsertAndReadTransaction() = runBlocking {
        val account = AccountEntity(
            id = "acc-1",
            name = "Main Bank",
            type = AccountType.BANK,
            currencyCode = "USD",
            initialBalanceMinor = 100000L,
            currentBalanceMinor = 100000L
        )
        accountDao.insert(account)

        val money = Money(BigDecimal("49.99"), Currency.USD)
        val transaction = TransactionEntity(
            id = "tx-1",
            amountMinor = DatabaseConverters.toMinorUnits(money),
            currencyCode = money.currency.code,
            type = TransactionType.EXPENSE,
            sourceAccountId = "acc-1",
            timestamp = Instant.ofEpochMilli(1700000000000L),
            note = "Groceries"
        )
        transactionDao.insert(transaction)

        val read = transactionDao.getById("tx-1")
        assertNotNull(read)
        assertEquals("tx-1", read!!.id)
        assertEquals(4999L, read.amountMinor)
        assertEquals("USD", read.currencyCode)
        assertEquals(TransactionType.EXPENSE, read.type)
        assertEquals("Groceries", read.note)

        val restoredMoney = DatabaseConverters.toMoney(read.amountMinor, read.currencyCode)
        assertEquals(BigDecimal("49.99"), restoredMoney.amount)
        assertEquals("USD", restoredMoney.currency.code)
    }

    @Test
    fun testTrashAndRestoreBehavior() = runBlocking {
        val transaction = TransactionEntity(
            id = "tx-trash",
            amountMinor = 1500L,
            currencyCode = "USD",
            type = TransactionType.EXPENSE,
            sourceAccountId = "acc-1",
            timestamp = Instant.now(),
            isDeleted = false
        )
        transactionDao.insert(transaction)

        val activeBefore = transactionDao.getActiveTransactions().first()
        assertTrue(activeBefore.any { it.id == "tx-trash" })

        val deletedBefore = transactionDao.getDeletedTransactions().first()
        assertTrue(deletedBefore.none { it.id == "tx-trash" })

        // Move to trash
        transactionDao.moveToTrash("tx-trash")

        val activeAfterTrash = transactionDao.getActiveTransactions().first()
        assertTrue(activeAfterTrash.none { it.id == "tx-trash" })

        val deletedAfterTrash = transactionDao.getDeletedTransactions().first()
        assertTrue(deletedAfterTrash.any { it.id == "tx-trash" })

        // Restore from trash
        transactionDao.restoreFromTrash("tx-trash")

        val activeAfterRestore = transactionDao.getActiveTransactions().first()
        assertTrue(activeAfterRestore.any { it.id == "tx-trash" })

        val deletedAfterRestore = transactionDao.getDeletedTransactions().first()
        assertTrue(deletedAfterRestore.none { it.id == "tx-trash" })
    }

    @Test
    fun testMoneyPrecisionStorageRepresentation() = runBlocking {
        // High-precision 3-decimal currency: KWD 123.456
        val kwdMoney = Money(BigDecimal("123.456"), Currency.fromCode("KWD"))
        val kwdTx = TransactionEntity(
            id = "tx-kwd",
            amountMinor = DatabaseConverters.toMinorUnits(kwdMoney),
            currencyCode = "KWD",
            type = TransactionType.INCOME,
            sourceAccountId = "acc-kwd",
            timestamp = Instant.now()
        )
        transactionDao.insert(kwdTx)

        val readKwd = transactionDao.getById("tx-kwd")
        assertNotNull(readKwd)
        assertEquals(123456L, readKwd!!.amountMinor)
        val restoredKwd = DatabaseConverters.toMoney(readKwd.amountMinor, readKwd.currencyCode)
        assertEquals(BigDecimal("123.456"), restoredKwd.amount)

        // 0-decimal currency: JPY 10000
        val jpyMoney = Money(BigDecimal("10000"), Currency.JPY)
        val jpyTx = TransactionEntity(
            id = "tx-jpy",
            amountMinor = DatabaseConverters.toMinorUnits(jpyMoney),
            currencyCode = "JPY",
            type = TransactionType.EXPENSE,
            sourceAccountId = "acc-jpy",
            timestamp = Instant.now()
        )
        transactionDao.insert(jpyTx)

        val readJpy = transactionDao.getById("tx-jpy")
        assertNotNull(readJpy)
        assertEquals(10000L, readJpy!!.amountMinor)
        val restoredJpy = DatabaseConverters.toMoney(readJpy.amountMinor, readJpy.currencyCode)
        assertEquals(BigDecimal("10000"), restoredJpy.amount)
    }

    @Test
    fun testOptionalCategoryOnTransaction() = runBlocking {
        val category = CategoryEntity(
            id = "cat-food",
            name = "Food",
            iconKey = "restaurant",
            colorKey = "amber"
        )
        categoryDao.insert(category)

        // Transaction WITH category
        val txWithCat = TransactionEntity(
            id = "tx-cat",
            amountMinor = 2500L,
            currencyCode = "USD",
            type = TransactionType.EXPENSE,
            sourceAccountId = "acc-1",
            categoryId = "cat-food",
            timestamp = Instant.now()
        )
        transactionDao.insert(txWithCat)

        // Transaction WITHOUT category (e.g. transfer or uncategorized expense)
        val txWithoutCat = TransactionEntity(
            id = "tx-nocat",
            amountMinor = 5000L,
            currencyCode = "USD",
            type = TransactionType.TRANSFER,
            sourceAccountId = "acc-1",
            destinationAccountId = "acc-2",
            categoryId = null,
            timestamp = Instant.now()
        )
        transactionDao.insert(txWithoutCat)

        val readWithCat = transactionDao.getById("tx-cat")
        assertEquals("cat-food", readWithCat?.categoryId)

        val readWithoutCat = transactionDao.getById("tx-nocat")
        assertNull(readWithoutCat?.categoryId)
        assertEquals("acc-2", readWithoutCat?.destinationAccountId)
        assertEquals(TransactionType.TRANSFER, readWithoutCat?.type)
    }
}

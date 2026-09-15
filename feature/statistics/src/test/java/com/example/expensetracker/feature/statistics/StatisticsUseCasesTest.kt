package com.example.expensetracker.feature.statistics

import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.category.CategoryType
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class StatisticsUseCasesTest {

    private val useCases = StatisticsUseCases()
    private val zoneId = ZoneId.of("UTC")

    private val foodCategory = Category(
        id = EntityId("cat_food"),
        name = "Food & Dining",
        iconKey = "restaurant",
        colorKey = "category_orange",
        type = CategoryType.EXPENSE
    )

    private val salaryCategory = Category(
        id = EntityId("cat_salary"),
        name = "Salary",
        iconKey = "payments",
        colorKey = "category_green",
        type = CategoryType.INCOME
    )

    private val bankAccount = Account(
        id = EntityId("acc_bank"),
        name = "Checking Account",
        type = AccountType.BANK,
        currency = Currency.USD,
        initialBalance = Money(BigDecimal("1000.00"), Currency.USD),
        currentBalance = Money(BigDecimal("1500.00"), Currency.USD)
    )

    @Test
    fun calculateTotals_sumsIncomeAndExpenses_strictlyExcludesTransfers() {
        val txs = listOf(
            Transaction(
                id = EntityId("tx1"),
                amount = Money(BigDecimal("500.00"), Currency.USD),
                type = TransactionType.INCOME,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 10).atStartOfDay(zoneId).toInstant()
            ),
            Transaction(
                id = EntityId("tx2"),
                amount = Money(BigDecimal("120.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 11).atStartOfDay(zoneId).toInstant()
            ),
            Transaction(
                id = EntityId("tx3"),
                amount = Money(BigDecimal("80.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 12).atStartOfDay(zoneId).toInstant()
            ),
            // Transfer: $10,000 between accounts - MUST NOT appear in income or expense totals
            Transaction(
                id = EntityId("tx4"),
                amount = Money(BigDecimal("10000.00"), Currency.USD),
                type = TransactionType.TRANSFER,
                sourceAccountId = bankAccount.id,
                destinationAccountId = EntityId("acc_savings"),
                timestamp = LocalDate.of(2026, 9, 12).atStartOfDay(zoneId).toInstant()
            )
        )

        val (totalIncome, totalExpense, netChange) = useCases.calculateTotals(txs, Currency.USD)

        assertEquals(BigDecimal("500.00"), totalIncome.amount)
        assertEquals(BigDecimal("200.00"), totalExpense.amount)
        assertEquals(BigDecimal("300.00"), netChange.amount)
    }

    @Test
    fun calculateTotals_ignoresTransactionsInDifferentCurrencies() {
        val txs = listOf(
            Transaction(
                id = EntityId("tx1"),
                amount = Money(BigDecimal("100.00"), Currency.USD),
                type = TransactionType.INCOME,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 10).atStartOfDay(zoneId).toInstant()
            ),
            Transaction(
                id = EntityId("tx2"),
                amount = Money(BigDecimal("50.00"), Currency.EUR),
                type = TransactionType.INCOME,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 10).atStartOfDay(zoneId).toInstant()
            )
        )

        val (totalIncome, _, _) = useCases.calculateTotals(txs, Currency.USD)
        assertEquals(BigDecimal("100.00"), totalIncome.amount)
    }

    @Test
    fun filterByDateRange_strictlyExcludesSoftDeletedTransactions() {
        val txs = listOf(
            Transaction(
                id = EntityId("active_tx"),
                amount = Money(BigDecimal("50.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 10).atStartOfDay(zoneId).toInstant(),
                isDeleted = false
            ),
            Transaction(
                id = EntityId("deleted_tx"),
                amount = Money(BigDecimal("100.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 10).atStartOfDay(zoneId).toInstant(),
                isDeleted = true
            )
        )

        val filter = DateRangeFilter(
            preset = DateRangePreset.CUSTOM,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 30)
        )

        val filtered = useCases.filterByDateRange(txs, filter, zoneId)

        assertEquals(1, filtered.size)
        assertEquals(EntityId("active_tx"), filtered.first().id)
    }

    @Test
    fun filterByDateRange_inclusiveBoundaries() {
        val startInstant = LocalDate.of(2026, 9, 1).atStartOfDay(zoneId).toInstant()
        val endInstant = LocalDate.of(2026, 9, 30).atTime(23, 59, 59).atZone(zoneId).toInstant()
        val outsideInstant = LocalDate.of(2026, 10, 1).atStartOfDay(zoneId).toInstant()

        val txs = listOf(
            Transaction(
                id = EntityId("start_bound"),
                amount = Money(BigDecimal("10.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = startInstant
            ),
            Transaction(
                id = EntityId("end_bound"),
                amount = Money(BigDecimal("20.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = endInstant
            ),
            Transaction(
                id = EntityId("outside"),
                amount = Money(BigDecimal("30.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = outsideInstant
            )
        )

        val filter = DateRangeFilter(
            preset = DateRangePreset.CUSTOM,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 30)
        )

        val result = useCases.filterByDateRange(txs, filter, zoneId)

        assertEquals(2, result.size)
        assertTrue(result.any { it.id == EntityId("start_bound") })
        assertTrue(result.any { it.id == EntityId("end_bound") })
        assertFalse(result.any { it.id == EntityId("outside") })
    }

    @Test
    fun filterByCurrency_returnsOnlyMatchingCurrency() {
        val txs = listOf(
            Transaction(
                id = EntityId("usd_tx"),
                amount = Money(BigDecimal("10.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 1).atStartOfDay(zoneId).toInstant()
            ),
            Transaction(
                id = EntityId("eur_tx"),
                amount = Money(BigDecimal("20.00"), Currency.EUR),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 1).atStartOfDay(zoneId).toInstant()
            )
        )

        val filtered = useCases.filterByCurrency(txs, Currency.USD)
        assertEquals(1, filtered.size)
        assertEquals(EntityId("usd_tx"), filtered.first().id)
    }

    @Test
    fun getAvailableCurrencies_sortsByTransactionCount() {
        val txs = listOf(
            Transaction(
                id = EntityId("tx1"),
                amount = Money(BigDecimal("10.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 1).atStartOfDay(zoneId).toInstant()
            ),
            Transaction(
                id = EntityId("tx2"),
                amount = Money(BigDecimal("20.00"), Currency.EUR),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 1).atStartOfDay(zoneId).toInstant()
            ),
            Transaction(
                id = EntityId("tx3"),
                amount = Money(BigDecimal("30.00"), Currency.EUR),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 1).atStartOfDay(zoneId).toInstant()
            )
        )

        val currencies = useCases.getAvailableCurrencies(txs)
        assertEquals(2, currencies.size)
        assertEquals(Currency.EUR, currencies[0].currency)
        assertEquals(2, currencies[0].transactionCount)
        assertEquals(Currency.USD, currencies[1].currency)
        assertEquals(1, currencies[1].transactionCount)
    }

    @Test
    fun calculateSpendingByCategory_computesCorrectPercentagesAndHandlesMissingCategory() {
        val txs = listOf(
            Transaction(
                id = EntityId("tx1"),
                amount = Money(BigDecimal("75.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                categoryId = foodCategory.id,
                timestamp = LocalDate.of(2026, 9, 1).atStartOfDay(zoneId).toInstant()
            ),
            Transaction(
                id = EntityId("tx2"),
                amount = Money(BigDecimal("25.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                categoryId = null, // Missing / uncategorized
                timestamp = LocalDate.of(2026, 9, 2).atStartOfDay(zoneId).toInstant()
            ),
            // Income transaction must NOT be counted in category spending breakdown
            Transaction(
                id = EntityId("tx3"),
                amount = Money(BigDecimal("1000.00"), Currency.USD),
                type = TransactionType.INCOME,
                sourceAccountId = bankAccount.id,
                categoryId = salaryCategory.id,
                timestamp = LocalDate.of(2026, 9, 3).atStartOfDay(zoneId).toInstant()
            )
        )

        val categories = mapOf(foodCategory.id to foodCategory, salaryCategory.id to salaryCategory)
        val breakdown = useCases.calculateSpendingByCategory(txs, categories, Currency.USD)

        assertEquals(2, breakdown.size)
        assertEquals("Food & Dining", breakdown[0].categoryName)
        assertEquals(BigDecimal("75.00"), breakdown[0].amount.amount)
        assertEquals(75.0f, breakdown[0].percentage)

        assertEquals("Uncategorized", breakdown[1].categoryName)
        assertEquals(BigDecimal("25.00"), breakdown[1].amount.amount)
        assertEquals(25.0f, breakdown[1].percentage)
    }

    @Test
    fun calculateTimeSeries_fillsZeroGapsForContiguousDays() {
        val filter = DateRangeFilter(
            preset = DateRangePreset.CUSTOM,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 3)
        )

        val txs = listOf(
            Transaction(
                id = EntityId("tx1"),
                amount = Money(BigDecimal("50.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 1).atStartOfDay(zoneId).toInstant()
            ),
            Transaction(
                id = EntityId("tx2"),
                amount = Money(BigDecimal("30.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 3).atStartOfDay(zoneId).toInstant()
            )
        )

        val series = useCases.calculateTimeSeries(txs, filter, Currency.USD, zoneId)

        assertEquals(3, series.size)
        // Day 1: $50
        assertEquals(LocalDate.of(2026, 9, 1), series[0].date)
        assertEquals(BigDecimal("50.00"), series[0].totalExpense.amount)
        // Day 2: $0 (Gap filled!)
        assertEquals(LocalDate.of(2026, 9, 2), series[1].date)
        assertEquals(BigDecimal.ZERO, series[1].totalExpense.amount)
        // Day 3: $30
        assertEquals(LocalDate.of(2026, 9, 3), series[2].date)
        assertEquals(BigDecimal("30.00"), series[2].totalExpense.amount)
    }

    @Test
    fun calculateLargestExpense_picksHighestExpense_ignoresLargerIncomeOrTransfers() {
        val txs = listOf(
            Transaction(
                id = EntityId("exp1"),
                amount = Money(BigDecimal("80.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                categoryId = foodCategory.id,
                note = "Family dinner",
                timestamp = LocalDate.of(2026, 9, 5).atStartOfDay(zoneId).toInstant()
            ),
            Transaction(
                id = EntityId("exp2"),
                amount = Money(BigDecimal("250.00"), Currency.USD),
                type = TransactionType.EXPENSE,
                sourceAccountId = bankAccount.id,
                categoryId = foodCategory.id,
                note = "Grocery haul",
                timestamp = LocalDate.of(2026, 9, 6).atStartOfDay(zoneId).toInstant()
            ),
            // Much larger income: $2000 - should NOT be chosen
            Transaction(
                id = EntityId("inc1"),
                amount = Money(BigDecimal("2000.00"), Currency.USD),
                type = TransactionType.INCOME,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 1).atStartOfDay(zoneId).toInstant()
            ),
            // Much larger transfer: $5000 - should NOT be chosen
            Transaction(
                id = EntityId("trf1"),
                amount = Money(BigDecimal("5000.00"), Currency.USD),
                type = TransactionType.TRANSFER,
                sourceAccountId = bankAccount.id,
                destinationAccountId = EntityId("acc2"),
                timestamp = LocalDate.of(2026, 9, 2).atStartOfDay(zoneId).toInstant()
            )
        )

        val largest = useCases.calculateLargestExpense(
            transactions = txs,
            accounts = mapOf(bankAccount.id to bankAccount),
            categories = mapOf(foodCategory.id to foodCategory),
            currency = Currency.USD,
            zoneId = zoneId
        )

        assertNotNull(largest)
        assertEquals(EntityId("exp2"), largest?.id)
        assertEquals(BigDecimal("250.00"), largest?.amount?.amount)
        assertEquals("Grocery haul", largest?.note)
        assertEquals("Checking Account", largest?.accountName)
        assertEquals("Food & Dining", largest?.categoryName)
    }

    @Test
    fun calculateLargestExpense_returnsNullWhenNoExpenses() {
        val txs = listOf(
            Transaction(
                id = EntityId("inc1"),
                amount = Money(BigDecimal("1000.00"), Currency.USD),
                type = TransactionType.INCOME,
                sourceAccountId = bankAccount.id,
                timestamp = LocalDate.of(2026, 9, 1).atStartOfDay(zoneId).toInstant()
            )
        )

        val largest = useCases.calculateLargestExpense(
            transactions = txs,
            accounts = mapOf(bankAccount.id to bankAccount),
            categories = emptyMap(),
            currency = Currency.USD,
            zoneId = zoneId
        )

        assertNull(largest)
    }

    @Test
    fun emptyTransactions_returnsCleanZeroState() {
        val (income, expense, net) = useCases.calculateTotals(emptyList(), Currency.USD)
        assertEquals(BigDecimal.ZERO, income.amount)
        assertEquals(BigDecimal.ZERO, expense.amount)
        assertEquals(BigDecimal.ZERO, net.amount)

        val breakdown = useCases.calculateSpendingByCategory(emptyList(), emptyMap(), Currency.USD)
        assertTrue(breakdown.isEmpty())

        val largest = useCases.calculateLargestExpense(emptyList(), emptyMap(), emptyMap(), Currency.USD)
        assertNull(largest)

        val currencies = useCases.getAvailableCurrencies(emptyList())
        assertTrue(currencies.isEmpty())
    }
}

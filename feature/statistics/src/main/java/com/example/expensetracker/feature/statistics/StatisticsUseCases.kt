package com.example.expensetracker.feature.statistics

import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure, testable analytics use cases for spending calculations,
 * category breakdowns, time-series plotting, and multi-currency filtering.
 */
@Singleton
class StatisticsUseCases @Inject constructor() {

    /**
     * Filters transactions that match [dateRange], strictly excluding soft-deleted items.
     */
    fun filterByDateRange(
        transactions: List<Transaction>,
        dateRange: DateRangeFilter,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<Transaction> {
        return transactions.filter { tx ->
            !tx.isDeleted && dateRange.contains(tx.timestamp, zoneId)
        }
    }

    /**
     * Filters transactions matching [currency].
     */
    fun filterByCurrency(
        transactions: List<Transaction>,
        currency: Currency
    ): List<Transaction> {
        return transactions.filter { it.amount.currency == currency }
    }

    /**
     * Finds all available currencies across the active transactions and counts matching items.
     */
    fun getAvailableCurrencies(transactions: List<Transaction>): List<CurrencyOption> {
        return transactions
            .filter { !it.isDeleted }
            .groupBy { it.amount.currency }
            .map { (currency, txs) -> CurrencyOption(currency, txs.size) }
            .sortedByDescending { it.transactionCount }
    }

    /**
     * Calculates total income, total expenses, and net change for transactions in [currency].
     * Transfers and deleted transactions are strictly excluded from totals.
     */
    fun calculateTotals(
        transactions: List<Transaction>,
        currency: Currency
    ): Triple<Money, Money, Money> {
        var incomeSum = BigDecimal.ZERO
        var expenseSum = BigDecimal.ZERO

        for (tx in transactions) {
            if (tx.isDeleted || tx.amount.currency != currency) continue
            when (tx.type) {
                TransactionType.INCOME -> incomeSum = incomeSum.add(tx.amount.amount)
                TransactionType.EXPENSE -> expenseSum = expenseSum.add(tx.amount.amount)
                TransactionType.TRANSFER -> {
                    // Transfers NEVER impact income or expense totals
                }
            }
        }

        val totalIncome = Money(incomeSum, currency)
        val totalExpense = Money(expenseSum, currency)
        val netChange = Money(incomeSum.subtract(expenseSum), currency)

        return Triple(totalIncome, totalExpense, netChange)
    }

    /**
     * Computes the spending breakdown per category, sorted by total amount descending.
     * Missing or archived categories are gracefully resolved to descriptive fallbacks.
     */
    fun calculateSpendingByCategory(
        transactions: List<Transaction>,
        categories: Map<EntityId, Category>,
        currency: Currency
    ): List<CategorySpending> {
        val expenseTxs = transactions.filter {
            !it.isDeleted && it.type == TransactionType.EXPENSE && it.amount.currency == currency
        }

        val totalExpense = expenseTxs.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.amount) }

        val grouped = expenseTxs.groupBy { it.categoryId }

        return grouped.map { (catId, txs) ->
            val catAmount = txs.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.amount) }
            val category = catId?.let { categories[it] }

            val categoryName = when {
                category != null && category.isArchived -> "${category.name} (Archived)"
                category != null -> category.name
                else -> "Uncategorized"
            }

            val iconKey = category?.iconKey ?: "category"
            val colorKey = category?.colorKey ?: "category_gray"

            val percentage = if (totalExpense > BigDecimal.ZERO) {
                catAmount.divide(totalExpense, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .toFloat()
            } else {
                0f
            }

            CategorySpending(
                categoryId = catId,
                categoryName = categoryName,
                iconKey = iconKey,
                colorKey = colorKey,
                amount = Money(catAmount, currency),
                percentage = percentage,
                transactionCount = txs.size
            )
        }.sortedByDescending { it.amount.amount }
    }

    /**
     * Generates a contiguous time-series sequence of spending and income points across [dateRange].
     * Days without transactions are filled with zeros so the chart has unbroken intervals.
     */
    fun calculateTimeSeries(
        transactions: List<Transaction>,
        dateRange: DateRangeFilter,
        currency: Currency,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<TimeSeriesPoint> {
        val matchingTxs = transactions.filter {
            !it.isDeleted && it.amount.currency == currency && dateRange.contains(it.timestamp, zoneId)
        }

        val daysBetween = ChronoUnit.DAYS.between(dateRange.startDate, dateRange.endDate).toInt()
        val isDaily = daysBetween in 0..31

        return if (isDaily) {
            // Group by day
            val txsByDay = matchingTxs.groupBy { it.timestamp.atZone(zoneId).toLocalDate() }
            val points = mutableListOf<TimeSeriesPoint>()
            val formatter = DateTimeFormatter.ofPattern("d MMM")

            var cursor = dateRange.startDate
            while (!cursor.isAfter(dateRange.endDate)) {
                val dayTxs = txsByDay[cursor] ?: emptyList()
                val expense = dayTxs.filter { it.type == TransactionType.EXPENSE }
                    .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.amount) }
                val income = dayTxs.filter { it.type == TransactionType.INCOME }
                    .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.amount) }

                points.add(
                    TimeSeriesPoint(
                        label = cursor.format(formatter),
                        date = cursor,
                        totalExpense = Money(expense, currency),
                        totalIncome = Money(income, currency)
                    )
                )
                cursor = cursor.plusDays(1)
            }
            points
        } else {
            // Group by weekly or monthly bucket
            val isMonthly = daysBetween > 90
            val formatter = if (isMonthly) DateTimeFormatter.ofPattern("MMM yyyy") else DateTimeFormatter.ofPattern("d MMM")

            val stepDays = if (isMonthly) 30L else 7L
            val points = mutableListOf<TimeSeriesPoint>()

            var cursor = dateRange.startDate
            while (!cursor.isAfter(dateRange.endDate)) {
                val bucketEnd = minOf(cursor.plusDays(stepDays - 1), dateRange.endDate)

                val bucketTxs = matchingTxs.filter { tx ->
                    val txDate = tx.timestamp.atZone(zoneId).toLocalDate()
                    !txDate.isBefore(cursor) && !txDate.isAfter(bucketEnd)
                }

                val expense = bucketTxs.filter { it.type == TransactionType.EXPENSE }
                    .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.amount) }
                val income = bucketTxs.filter { it.type == TransactionType.INCOME }
                    .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.amount) }

                points.add(
                    TimeSeriesPoint(
                        label = cursor.format(formatter),
                        date = cursor,
                        totalExpense = Money(expense, currency),
                        totalIncome = Money(income, currency)
                    )
                )
                cursor = bucketEnd.plusDays(1)
            }
            points
        }
    }

    /**
     * Identifies the single largest expense transaction within the filtered criteria.
     */
    fun calculateLargestExpense(
        transactions: List<Transaction>,
        accounts: Map<EntityId, Account>,
        categories: Map<EntityId, Category>,
        currency: Currency,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): LargestTransaction? {
        val maxExpense = transactions.filter {
            !it.isDeleted && it.type == TransactionType.EXPENSE && it.amount.currency == currency
        }.maxByOrNull { it.amount.amount } ?: return null

        val category = maxExpense.categoryId?.let { categories[it] }
        val account = accounts[maxExpense.sourceAccountId]
        val txDate = maxExpense.timestamp.atZone(zoneId).toLocalDate()

        return LargestTransaction(
            id = maxExpense.id,
            amount = maxExpense.amount,
            type = maxExpense.type,
            categoryName = category?.name ?: "Uncategorized",
            categoryColorKey = category?.colorKey ?: "category_gray",
            accountName = account?.name ?: "Unknown Account",
            date = txDate,
            note = maxExpense.note
        )
    }
}

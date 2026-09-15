package com.example.expensetracker.feature.home

import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.budget.Budget
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Pure calculation and domain aggregation use cases for the Home dashboard.
 */
class HomeUseCases @Inject constructor() {

    /**
     * Calculates total income from non-deleted transactions.
     * Transfers are strictly excluded.
     */
    fun calculateTotalIncome(
        transactions: List<Transaction>,
        currency: Currency
    ): Money {
        val total = transactions
            .asSequence()
            .filter { !it.isDeleted && it.type == TransactionType.INCOME && it.amount.currency == currency }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount.amount }
        return Money(total, currency)
    }

    /**
     * Calculates total expenses from non-deleted transactions.
     * Transfers are strictly excluded.
     */
    fun calculateTotalExpenses(
        transactions: List<Transaction>,
        currency: Currency
    ): Money {
        val total = transactions
            .asSequence()
            .filter { !it.isDeleted && it.type == TransactionType.EXPENSE && it.amount.currency == currency }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount.amount }
        return Money(total, currency)
    }

    /**
     * Calculates total balance across accounts and active transactions.
     *
     * Correctly accounts for:
     * - Income (+ to source account)
     * - Expenses (- from source account)
     * - Transfers (- from source account, + to destination account if tracked)
     * - Negative account balances
     * - No transactions (falls back to initial balances or zero)
     * - Deleted/trash transactions (strictly excluded)
     * - Multi-currency isolation (only accounts and transactions matching the requested currency)
     */
    fun calculateTotalBalance(
        accounts: List<Account> = emptyList(),
        transactions: List<Transaction> = emptyList(),
        currency: Currency
    ): Money {
        val matchingAccounts = accounts.filter { it.currency == currency }
        val activeTransactions = transactions.filter { !it.isDeleted && it.amount.currency == currency }

        if (matchingAccounts.isEmpty()) {
            val income = activeTransactions
                .filter { it.type == TransactionType.INCOME }
                .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount.amount }
            val expense = activeTransactions
                .filter { it.type == TransactionType.EXPENSE }
                .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount.amount }
            return Money(income - expense, currency)
        }

        val accountBalances = matchingAccounts.associate { it.id to it.initialBalance.amount }.toMutableMap()
        for (tx in activeTransactions) {
            when (tx.type) {
                TransactionType.INCOME -> {
                    accountBalances[tx.sourceAccountId]?.let { current ->
                        accountBalances[tx.sourceAccountId] = current + tx.amount.amount
                    }
                }
                TransactionType.EXPENSE -> {
                    accountBalances[tx.sourceAccountId]?.let { current ->
                        accountBalances[tx.sourceAccountId] = current - tx.amount.amount
                    }
                }
                TransactionType.TRANSFER -> {
                    accountBalances[tx.sourceAccountId]?.let { current ->
                        accountBalances[tx.sourceAccountId] = current - tx.amount.amount
                    }
                    tx.destinationAccountId?.let { destId ->
                        accountBalances[destId]?.let { current ->
                            accountBalances[destId] = current + tx.amount.amount
                        }
                    }
                }
            }
        }
        val totalAmount = accountBalances.values.fold(BigDecimal.ZERO) { acc, bal -> acc + bal }
        return Money(totalAmount, currency)
    }

    /**
     * Discovers all currencies in use and calculates per-currency balance summaries.
     * Guarantees that [preferredCurrency] is the first item in the list.
     */
    fun calculateCurrencyBalances(
        accounts: List<Account>,
        transactions: List<Transaction>,
        preferredCurrency: Currency
    ): List<CurrencyBalanceItem> {
        val currencies = mutableSetOf<Currency>()
        currencies.add(preferredCurrency)

        for (acc in accounts) {
            currencies.add(acc.currency)
        }
        for (tx in transactions) {
            if (!tx.isDeleted) {
                currencies.add(tx.amount.currency)
            }
        }

        val items = currencies.map { curr ->
            CurrencyBalanceItem(
                currency = curr,
                totalBalance = calculateTotalBalance(accounts, transactions, curr),
                totalIncome = calculateTotalIncome(transactions, curr),
                totalExpenses = calculateTotalExpenses(transactions, curr)
            )
        }

        // Put preferred currency first, then sort remaining alphabetically
        return items.sortedWith(
            compareBy<CurrencyBalanceItem> { it.currency != preferredCurrency }
                .thenBy { it.currency.code }
        )
    }

    /**
     * Retrieves up to [limit] most recent active transactions with resolved category and account details.
     */
    fun getRecentTransactions(
        transactions: List<Transaction>,
        accounts: Map<EntityId, Account>,
        categories: Map<EntityId, Category>,
        limit: Int = 5,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<HomeRecentTransactionItem> {
        val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

        return transactions
            .asSequence()
            .filter { !it.isDeleted }
            .sortedByDescending { it.timestamp }
            .take(limit)
            .map { tx ->
                val category = tx.categoryId?.let { categories[it] }
                val account = accounts[tx.sourceAccountId]
                val localDate = tx.timestamp.atZone(zoneId).toLocalDate()

                HomeRecentTransactionItem(
                    id = tx.id,
                    amount = tx.amount,
                    type = tx.type,
                    categoryName = when {
                        tx.type == TransactionType.TRANSFER -> "Transfer"
                        category != null -> category.name
                        else -> "Uncategorized"
                    },
                    categoryColorKey = when {
                        tx.type == TransactionType.TRANSFER -> "category_blue"
                        category != null -> category.colorKey
                        else -> "category_gray"
                    },
                    accountName = account?.name ?: "Account",
                    note = tx.note,
                    dateFormatted = localDate.format(dateFormatter)
                )
            }
            .toList()
    }

    /**
     * Calculates compact snapshots for active budgets.
     */
    fun calculateBudgetSnapshots(
        budgets: List<Budget>,
        transactions: List<Transaction>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<HomeBudgetSnapshotItem> {
        val activeBudgets = budgets.filter { it.isEnabled }
        if (activeBudgets.isEmpty()) return emptyList()

        return activeBudgets.map { budget ->
            val currency = budget.targetAmount.currency
            val spent = transactions
                .asSequence()
                .filter { tx ->
                    !tx.isDeleted &&
                        tx.type == TransactionType.EXPENSE &&
                        tx.amount.currency == currency &&
                        (budget.categoryId == null || tx.categoryId == budget.categoryId)
                }
                .filter { tx ->
                    val txDate = tx.timestamp.atZone(zoneId).toLocalDate()
                    !txDate.isBefore(budget.startDate) && !txDate.isAfter(budget.endDate)
                }
                .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount.amount }

            val target = budget.targetAmount.amount
            val progress = if (target > BigDecimal.ZERO) {
                spent.divide(target, 4, RoundingMode.HALF_UP).toFloat()
            } else 0f

            val isOverspent = spent > target
            val remaining = (target - spent).max(BigDecimal.ZERO)

            HomeBudgetSnapshotItem(
                id = budget.id,
                name = budget.name,
                targetAmount = budget.targetAmount,
                spentAmount = Money(spent, currency),
                progressPercentage = progress,
                isOverspent = isOverspent,
                remainingAmount = Money(remaining, currency)
            )
        }.take(3)
    }

    /**
     * Calculates the current month's spending snapshot including top categories.
     */
    fun calculateSpendingSnapshot(
        transactions: List<Transaction>,
        categories: Map<EntityId, Category>,
        currency: Currency,
        currentMonth: YearMonth = YearMonth.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): HomeSpendingSnapshot {
        val startDate = currentMonth.atDay(1)
        val endDate = currentMonth.atEndOfMonth()
        val monthLabel = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

        val monthTxs = transactions.filter { tx ->
            if (tx.isDeleted || tx.amount.currency != currency) return@filter false
            val txDate = tx.timestamp.atZone(zoneId).toLocalDate()
            !txDate.isBefore(startDate) && !txDate.isAfter(endDate)
        }

        val monthIncome = monthTxs
            .filter { it.type == TransactionType.INCOME }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount.amount }

        val monthExpense = monthTxs
            .filter { it.type == TransactionType.EXPENSE }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount.amount }

        val netChange = monthIncome - monthExpense

        // Top categories
        val expenseTxs = monthTxs.filter { it.type == TransactionType.EXPENSE }
        val topCategories = if (monthExpense > BigDecimal.ZERO) {
            expenseTxs
                .groupBy { it.categoryId }
                .map { (catId, txList) ->
                    val catTotal = txList.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount.amount }
                    val catObj = catId?.let { categories[it] }
                    val percentage = catTotal
                        .multiply(BigDecimal(100))
                        .divide(monthExpense, 1, RoundingMode.HALF_UP)
                        .toFloat()

                    HomeTopCategorySpending(
                        categoryId = catId,
                        name = catObj?.name ?: "Uncategorized",
                        colorKey = catObj?.colorKey ?: "category_gray",
                        amount = Money(catTotal, currency),
                        percentage = percentage
                    )
                }
                .sortedByDescending { it.amount.amount }
                .take(3)
        } else {
            emptyList()
        }

        return HomeSpendingSnapshot(
            monthLabel = monthLabel,
            totalExpense = Money(monthExpense, currency),
            totalIncome = Money(monthIncome, currency),
            netChange = Money(netChange, currency),
            topCategories = topCategories
        )
    }
}

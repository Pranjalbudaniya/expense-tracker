package com.example.expensetracker.feature.home

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.LocalDate

/**
 * Balance, income, and expense summary for a specific currency.
 */
data class CurrencyBalanceItem(
    val currency: Currency,
    val totalBalance: Money,
    val totalIncome: Money,
    val totalExpenses: Money
)

/**
 * Lightweight transaction representation for recent transactions on the Home dashboard.
 */
data class HomeRecentTransactionItem(
    val id: EntityId,
    val amount: Money,
    val type: TransactionType,
    val categoryName: String,
    val categoryColorKey: String,
    val accountName: String,
    val note: String,
    val dateFormatted: String
)

/**
 * Snapshot of an active budget item for the Home dashboard.
 */
data class HomeBudgetSnapshotItem(
    val id: EntityId,
    val name: String,
    val targetAmount: Money,
    val spentAmount: Money,
    val progressPercentage: Float,
    val isOverspent: Boolean,
    val remainingAmount: Money
)

/**
 * Top category spending item within the current month.
 */
data class HomeTopCategorySpending(
    val categoryId: EntityId?,
    val name: String,
    val colorKey: String,
    val amount: Money,
    val percentage: Float
)

/**
 * Spending snapshot for the current month.
 */
data class HomeSpendingSnapshot(
    val monthLabel: String,
    val totalExpense: Money,
    val totalIncome: Money,
    val netChange: Money,
    val topCategories: List<HomeTopCategorySpending>
)

/**
 * Snapshot of an active recurring transaction for the Home dashboard.
 */
data class HomeRecurringItem(
    val id: EntityId,
    val amount: Money,
    val type: TransactionType,
    val frequency: RecurrenceFrequency,
    val frequencyLabel: String,
    val nextOccurrence: LocalDate,
    val nextOccurrenceFormatted: String,
    val dueStatusText: String,
    val isDue: Boolean,
    val categoryName: String,
    val categoryColorKey: String,
    val categoryIconKey: String,
    val note: String
)

/**
 * Immutable UI state for the Home screen dashboard.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val primaryCurrency: Currency = Currency.INR,
    val primaryBalance: CurrencyBalanceItem = CurrencyBalanceItem(
        currency = Currency.INR,
        totalBalance = Money.zero(Currency.INR),
        totalIncome = Money.zero(Currency.INR),
        totalExpenses = Money.zero(Currency.INR)
    ),
    val additionalCurrencyBalances: List<CurrencyBalanceItem> = emptyList(),
    val budgetSnapshots: List<HomeBudgetSnapshotItem> = emptyList(),
    val hasBudgets: Boolean = false,
    val recentTransactions: List<HomeRecentTransactionItem> = emptyList(),
    val recurringTransactions: List<HomeRecurringItem> = emptyList(),
    val spendingSnapshot: HomeSpendingSnapshot = HomeSpendingSnapshot(
        monthLabel = "",
        totalExpense = Money.zero(Currency.INR),
        totalIncome = Money.zero(Currency.INR),
        netChange = Money.zero(Currency.INR),
        topCategories = emptyList()
    ),
    val errorMessage: String? = null
) {
    // Backward compatibility delegates
    val totalBalance: Money get() = primaryBalance.totalBalance
    val totalIncome: Money get() = primaryBalance.totalIncome
    val totalExpenses: Money get() = primaryBalance.totalExpenses
    val hasBudget: Boolean get() = hasBudgets
}

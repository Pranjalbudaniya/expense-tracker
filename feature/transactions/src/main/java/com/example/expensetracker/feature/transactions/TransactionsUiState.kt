package com.example.expensetracker.feature.transactions

import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.transaction.Transaction
import java.time.LocalDate

/**
 * Resolved transaction item ready for UI presentation.
 */
data class TransactionDisplayItem(
    val transaction: Transaction,
    val category: Category?,
    val sourceAccount: Account?,
    val destinationAccount: Account?
)

/**
 * A group of transactions occurring on the same calendar date.
 */
data class DateGroupedTransactions(
    val header: String,
    val date: LocalDate,
    val transactions: List<TransactionDisplayItem>
)

/**
 * Empty state reasons for the transactions screen.
 */
enum class TransactionsEmptyState {
    NO_TRANSACTIONS,
    NO_SEARCH_RESULTS,
    NO_MATCHING_FILTERS,
    EMPTY_TRASH
}

/**
 * Immutable UI state for the Transactions screen.
 */
data class TransactionsUiState(
    val searchQuery: String = "",
    val filter: TransactionFilter = TransactionFilter.EMPTY,
    val sort: TransactionSort = TransactionSort.DEFAULT,
    val isTrashView: Boolean = false,
    val isSearchActive: Boolean = false,
    val groupedTransactions: List<DateGroupedTransactions> = emptyList(),
    val totalCount: Int = 0,
    val availableCategories: List<Category> = emptyList(),
    val availableAccounts: List<Account> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val emptyState: TransactionsEmptyState? = null
)

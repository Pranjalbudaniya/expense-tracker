package com.example.expensetracker.feature.transactions

/**
 * Sort options for transaction history.
 */
enum class TransactionSort(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    HIGHEST_AMOUNT("Highest Amount"),
    LOWEST_AMOUNT("Lowest Amount");

    companion object {
        val DEFAULT = NEWEST_FIRST
    }
}

package com.example.expensetracker.feature.transactions

import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.transaction.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Use cases providing search, filtering, sorting, date grouping, and trash management for transactions.
 */
class TransactionsUseCases @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) {

    fun getActiveTransactions(): Flow<List<Transaction>> =
        transactionRepository.getActiveTransactions()

    fun getDeletedTransactions(): Flow<List<Transaction>> =
        transactionRepository.getDeletedTransactions()

    fun getAllCategories(): Flow<List<Category>> =
        categoryRepository.getAllCategories()

    fun getAllAccounts(): Flow<List<Account>> =
        accountRepository.getAllAccounts()

    suspend fun moveToTrash(id: EntityId) {
        transactionRepository.moveToTrash(id)
    }

    suspend fun restoreFromTrash(id: EntityId) {
        transactionRepository.restoreFromTrash(id)
    }

    suspend fun deletePermanently(id: EntityId) {
        transactionRepository.deletePermanently(id)
    }

    suspend fun clearTrash() {
        transactionRepository.clearTrash()
    }

    /**
     * Filters and sorts the given list of transactions according to search query, filter criteria, and sort order.
     */
    fun filterAndSort(
        transactions: List<Transaction>,
        searchQuery: String,
        filter: TransactionFilter,
        sort: TransactionSort,
        categoryMap: Map<EntityId, Category>,
        accountMap: Map<EntityId, Account>
    ): List<TransactionDisplayItem> {
        val query = searchQuery.trim().lowercase()

        return transactions
            .asSequence()
            // 1. Search Query: case-insensitive match on note, category name, and account name(s)
            .filter { tx ->
                if (query.isEmpty()) return@filter true

                val noteMatch = tx.note.lowercase().contains(query)
                val categoryMatch = tx.categoryId?.let { categoryMap[it]?.name?.lowercase()?.contains(query) } ?: false
                val sourceAccountMatch = accountMap[tx.sourceAccountId]?.name?.lowercase()?.contains(query) ?: false
                val destAccountMatch = tx.destinationAccountId?.let { accountMap[it]?.name?.lowercase()?.contains(query) } ?: false

                noteMatch || categoryMatch || sourceAccountMatch || destAccountMatch
            }
            // 2. Filters
            .filter { tx ->
                if (filter.type != null && tx.type != filter.type) return@filter false
                if (filter.categoryId != null && tx.categoryId != filter.categoryId) return@filter false
                if (filter.accountId != null && tx.sourceAccountId != filter.accountId && tx.destinationAccountId != filter.accountId) return@filter false
                if (filter.startDate != null && tx.timestamp < filter.startDate) return@filter false
                if (filter.endDate != null && tx.timestamp > filter.endDate) return@filter false
                true
            }
            // 3. Sorting (stable with timestamp tie-breaker)
            .sortedWith { a, b ->
                when (sort) {
                    TransactionSort.NEWEST_FIRST -> {
                        val cmp = b.timestamp.compareTo(a.timestamp)
                        if (cmp != 0) cmp else b.id.value.compareTo(a.id.value)
                    }
                    TransactionSort.OLDEST_FIRST -> {
                        val cmp = a.timestamp.compareTo(b.timestamp)
                        if (cmp != 0) cmp else a.id.value.compareTo(b.id.value)
                    }
                    TransactionSort.HIGHEST_AMOUNT -> {
                        val cmp = b.amount.amount.compareTo(a.amount.amount)
                        if (cmp != 0) cmp else b.timestamp.compareTo(a.timestamp)
                    }
                    TransactionSort.LOWEST_AMOUNT -> {
                        val cmp = a.amount.amount.compareTo(b.amount.amount)
                        if (cmp != 0) cmp else b.timestamp.compareTo(a.timestamp)
                    }
                }
            }
            // 4. Map to UI display items (safely resolving categories and accounts, even if archived or missing)
            .map { tx ->
                TransactionDisplayItem(
                    transaction = tx,
                    category = tx.categoryId?.let { categoryMap[it] },
                    sourceAccount = accountMap[tx.sourceAccountId],
                    destinationAccount = tx.destinationAccountId?.let { accountMap[it] }
                )
            }
            .toList()
    }

    /**
     * Groups transactions by calendar date preserving sort order.
     */
    fun groupByDate(items: List<TransactionDisplayItem>): List<DateGroupedTransactions> {
        val grouped = linkedMapOf<LocalDate, MutableList<TransactionDisplayItem>>()
        val zone = ZoneId.systemDefault()

        for (item in items) {
            val date = item.transaction.timestamp.atZone(zone).toLocalDate()
            grouped.getOrPut(date) { mutableListOf() }.add(item)
        }

        return grouped.map { (date, transactions) ->
            DateGroupedTransactions(
                header = formatDateHeader(date),
                date = date,
                transactions = transactions
            )
        }
    }

    fun formatDateHeader(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
                date.format(formatter)
            }
        }
    }
}

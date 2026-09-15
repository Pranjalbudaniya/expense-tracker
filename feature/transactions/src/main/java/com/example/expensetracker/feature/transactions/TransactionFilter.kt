package com.example.expensetracker.feature.transactions

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.Instant

/**
 * Filter criteria for transactions.
 *
 * @property type Optional transaction type filter.
 * @property categoryId Optional category ID filter.
 * @property accountId Optional account ID filter (matches source or destination account).
 * @property startDate Optional start timestamp boundary (inclusive).
 * @property endDate Optional end timestamp boundary (inclusive).
 */
data class TransactionFilter(
    val type: TransactionType? = null,
    val categoryId: EntityId? = null,
    val accountId: EntityId? = null,
    val startDate: Instant? = null,
    val endDate: Instant? = null
) {
    val isActive: Boolean
        get() = type != null || categoryId != null || accountId != null || startDate != null || endDate != null

    companion object {
        val EMPTY = TransactionFilter()
    }
}

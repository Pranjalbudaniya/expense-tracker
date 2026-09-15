package com.example.expensetracker.feature.transactions

import com.example.expensetracker.core.model.common.EntityId

/**
 * Immutable UI state for the dedicated Trash screen.
 */
data class TrashUiState(
    val groupedTransactions: List<DateGroupedTransactions> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val userMessage: String? = null,
    val showEmptyTrashDialog: Boolean = false,
    val transactionToDeletePermanently: EntityId? = null
)

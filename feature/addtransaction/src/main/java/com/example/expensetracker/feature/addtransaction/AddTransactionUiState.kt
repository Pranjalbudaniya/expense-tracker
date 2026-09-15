package com.example.expensetracker.feature.addtransaction

import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.Instant

/**
 * Immutable UI state for the Add Transaction screen matching the Stitch design.
 */
data class AddTransactionUiState(
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val selectedSourceAccountId: EntityId? = null,
    val selectedDestinationAccountId: EntityId? = null,
    val selectedCategoryId: EntityId? = null,
    val timestamp: Instant = Instant.now(),
    val note: String = "",
    val merchant: String = "",
    val isRecurring: Boolean = false,
    val budgetHint: String? = null,
    val availableAccounts: List<Account> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val selectedCurrency: Currency = Currency.INR,
    val amountError: String? = null,
    val sourceAccountError: String? = null,
    val destinationAccountError: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val generalError: String? = null
) {
    val isExpense: Boolean
        get() = transactionType == TransactionType.EXPENSE

    val isIncome: Boolean
        get() = transactionType == TransactionType.INCOME

    val isTransfer: Boolean
        get() = false

    val hasAccounts: Boolean
        get() = availableAccounts.isNotEmpty()
}

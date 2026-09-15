package com.example.expensetracker.feature.transactions

import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.Instant

/**
 * Immutable UI state for the Transaction Details and Editing screen.
 */
data class TransactionDetailsUiState(
    val transaction: Transaction? = null,
    val category: Category? = null,
    val sourceAccount: Account? = null,
    val destinationAccount: Account? = null,
    val availableCategories: List<Category> = emptyList(),
    val availableAccounts: List<Account> = emptyList(),
    val isEditing: Boolean = false,
    val editedAmountInput: String = "",
    val editedCategoryId: EntityId? = null,
    val editedSourceAccountId: EntityId? = null,
    val editedDestinationAccountId: EntityId? = null,
    val editedTimestamp: Instant = Instant.now(),
    val editedNote: String = "",
    val amountError: String? = null,
    val accountError: String? = null,
    val destinationAccountError: String? = null,
    val generalError: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isPermanentlyDeleted: Boolean = false,
    val transactionNotFound: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val showDiscardDialog: Boolean = false
) {
    val isDeleted: Boolean
        get() = transaction?.isDeleted == true

    val isTransfer: Boolean
        get() = transaction?.type == TransactionType.TRANSFER

    val isExpense: Boolean
        get() = transaction?.type == TransactionType.EXPENSE

    val isIncome: Boolean
        get() = transaction?.type == TransactionType.INCOME

    val currency: Currency
        get() = availableAccounts.find { it.id == editedSourceAccountId }?.currency
            ?: transaction?.amount?.currency
            ?: Currency.INR

    val hasUnsavedChanges: Boolean
        get() {
            val tx = transaction ?: return false
            if (!isEditing) return false

            val currentAmountStr = tx.amount.amount.toPlainString()
            val amountChanged = editedAmountInput.trim() != currentAmountStr
            val categoryChanged = editedCategoryId != tx.categoryId
            val sourceChanged = editedSourceAccountId != tx.sourceAccountId
            val destChanged = editedDestinationAccountId != tx.destinationAccountId
            val timestampChanged = editedTimestamp != tx.timestamp
            val noteChanged = editedNote.trim() != tx.note.trim()

            return amountChanged || categoryChanged || sourceChanged || destChanged || timestampChanged || noteChanged
        }
}

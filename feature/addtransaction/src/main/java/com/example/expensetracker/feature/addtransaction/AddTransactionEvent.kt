package com.example.expensetracker.feature.addtransaction

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.Instant

/**
 * UI events triggered from the Add Transaction screen.
 */
sealed interface AddTransactionEvent {
    data class TransactionTypeChanged(val type: TransactionType) : AddTransactionEvent
    data class AmountChanged(val amount: String) : AddTransactionEvent
    data class QuickAmountAdded(val delta: Double) : AddTransactionEvent
    data object RoundUpAmount : AddTransactionEvent
    data class SourceAccountSelected(val accountId: EntityId) : AddTransactionEvent
    data class DestinationAccountSelected(val accountId: EntityId) : AddTransactionEvent
    data class CategorySelected(val categoryId: EntityId?) : AddTransactionEvent
    data class TimestampChanged(val timestamp: Instant) : AddTransactionEvent
    data class NoteChanged(val note: String) : AddTransactionEvent
    data class MerchantChanged(val merchant: String) : AddTransactionEvent
    data class RecurringToggled(val isRecurring: Boolean) : AddTransactionEvent
    data object ResetForm : AddTransactionEvent
    data object SaveClicked : AddTransactionEvent
    data object SaveAndAddAnotherClicked : AddTransactionEvent
}

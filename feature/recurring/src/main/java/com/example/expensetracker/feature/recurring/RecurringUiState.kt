package com.example.expensetracker.feature.recurring

import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.recurring.RecurringTransaction
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.LocalDate

/**
 * State of the recurring transaction create/edit form.
 */
data class RecurringFormState(
    val id: EntityId? = null,
    val amountText: String = "",
    val amountError: String? = null,
    val currency: Currency = Currency.INR,
    val type: TransactionType = TransactionType.EXPENSE,
    val sourceAccountId: EntityId? = null,
    val sourceAccountError: String? = null,
    val destinationAccountId: EntityId? = null,
    val destinationAccountError: String? = null,
    val categoryId: EntityId? = null,
    val note: String = "",
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val startDate: LocalDate = LocalDate.now(),
    val nextOccurrence: LocalDate = LocalDate.now(),
    val hasEndDate: Boolean = false,
    val endDate: LocalDate? = null,
    val dateError: String? = null,
    val isEnabled: Boolean = true
) {
    val isEditing: Boolean get() = id != null
}

/**
 * Presentation wrapper for a single recurring transaction card with joined entity metadata.
 */
data class RecurringItemUiState(
    val recurring: RecurringTransaction,
    val sourceAccountName: String,
    val destinationAccountName: String? = null,
    val categoryName: String? = null,
    val categoryIconKey: String? = null,
    val categoryColorKey: String? = null,
    val isSourceArchived: Boolean = false,
    val isDestinationArchived: Boolean = false,
    val isCategoryArchived: Boolean = false,
    val isDue: Boolean = false,
    val isCompleted: Boolean = false
)

/**
 * Root UI state for the Recurring Transactions feature.
 */
data class RecurringUiState(
    val isLoading: Boolean = false,
    val activeItems: List<RecurringItemUiState> = emptyList(),
    val pausedItems: List<RecurringItemUiState> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val selectedTab: Int = 0, // 0 = Active, 1 = Paused
    val isFormOpen: Boolean = false,
    val formState: RecurringFormState? = null,
    val recurringToDelete: RecurringTransaction? = null,
    val errorMessage: String? = null
)

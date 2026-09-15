package com.example.expensetracker.feature.accounts

import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money

/**
 * Presentation item representing an account with its calculated live balance.
 *
 * @property account The underlying account entity.
 * @property calculatedBalance Live balance computed from opening balance and all active transactions.
 * @property isArchived Whether this account is archived.
 */
data class AccountItemUiState(
    val account: Account,
    val calculatedBalance: Money,
    val isArchived: Boolean = account.isArchived
)

/**
 * Form state for creating or editing an account.
 *
 * @property accountId Unique identifier if editing an existing account; null for new.
 * @property name User-entered account name.
 * @property type Account type (CASH, BANK, UPI, CREDIT_CARD, WALLET, OTHER).
 * @property currency Account currency.
 * @property initialBalanceInput Text input for opening balance (can be zero, positive, or negative).
 * @property isArchived Whether the account is archived.
 * @property nameError Validation error for name field.
 * @property initialBalanceError Validation error for initial balance field.
 * @property isSaving Whether save operation is in progress.
 */
data class AccountFormState(
    val accountId: EntityId? = null,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val currency: Currency = Currency.INR,
    val initialBalanceInput: String = "0",
    val isArchived: Boolean = false,
    val nameError: String? = null,
    val initialBalanceError: String? = null,
    val isSaving: Boolean = false
) {
    val isEditing: Boolean get() = accountId != null
}

/**
 * UI State for the Accounts feature.
 *
 * @property activeAccounts List of active accounts with calculated balances.
 * @property archivedAccounts List of archived accounts with calculated balances.
 * @property showArchived Whether the archived accounts section is expanded/visible.
 * @property isFormOpen Whether the create/edit form screen is displayed.
 * @property formState Active form editing state (or null if form is closed).
 * @property isLoading Whether data is loading.
 * @property archiveConfirmationAccount Account pending archive confirmation (or null).
 * @property unarchiveConfirmationAccount Account pending unarchive confirmation (or null).
 * @property deleteConfirmationAccount Account pending deletion confirmation (or null).
 * @property errorMessage Transient error message.
 */
data class AccountsUiState(
    val activeAccounts: List<AccountItemUiState> = emptyList(),
    val archivedAccounts: List<AccountItemUiState> = emptyList(),
    val showArchived: Boolean = false,
    val isFormOpen: Boolean = false,
    val formState: AccountFormState? = null,
    val availableCurrencies: List<Currency> = emptyList(),
    val availableTypes: List<AccountType> = AccountType.values().toList(),
    val isLoading: Boolean = false,
    val archiveConfirmationAccount: Account? = null,
    val unarchiveConfirmationAccount: Account? = null,
    val deleteConfirmationAccount: Account? = null,
    val errorMessage: String? = null
)

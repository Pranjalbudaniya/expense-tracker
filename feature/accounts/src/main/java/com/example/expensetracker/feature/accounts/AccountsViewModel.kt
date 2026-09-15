package com.example.expensetracker.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Local UI state for transient dialogs and form interactions.
 */
private data class LocalAccountsState(
    val formState: AccountFormState? = null,
    val showArchived: Boolean = false,
    val archiveConfirmationAccount: Account? = null,
    val unarchiveConfirmationAccount: Account? = null,
    val deleteConfirmationAccount: Account? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel managing Accounts list, live balances, creation, editing, and archiving.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val accountUseCases: AccountUseCases
) : ViewModel() {

    private val localState = MutableStateFlow(LocalAccountsState())

    val supportedCurrencies = listOf(
        Currency.INR,
        Currency.USD,
        Currency.EUR,
        Currency.GBP,
        Currency.JPY,
        Currency(code = "CAD", symbol = "CA$", displayName = "Canadian Dollar"),
        Currency(code = "AUD", symbol = "A$", displayName = "Australian Dollar"),
        Currency(code = "CHF", symbol = "CHF", displayName = "Swiss Franc"),
        Currency(code = "CNY", symbol = "CN¥", displayName = "Chinese Yuan"),
        Currency(code = "SGD", symbol = "S$", displayName = "Singapore Dollar")
    )

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.getAllAccounts(),
        transactionRepository.getActiveTransactions(),
        localState
    ) { accounts, transactions, local ->
        val activeItems = mutableListOf<AccountItemUiState>()
        val archivedItems = mutableListOf<AccountItemUiState>()

        for (account in accounts) {
            val liveBalance = accountUseCases.calculateAccountBalance(account, transactions)
            val item = AccountItemUiState(
                account = account,
                calculatedBalance = liveBalance,
                isArchived = account.isArchived
            )
            if (account.isArchived) {
                archivedItems.add(item)
            } else {
                activeItems.add(item)
            }
        }

        AccountsUiState(
            activeAccounts = activeItems,
            archivedAccounts = archivedItems,
            showArchived = local.showArchived,
            isFormOpen = local.formState != null,
            formState = local.formState,
            availableCurrencies = supportedCurrencies,
            availableTypes = AccountType.values().filter { it != AccountType.BANK },
            isLoading = false,
            archiveConfirmationAccount = local.archiveConfirmationAccount,
            unarchiveConfirmationAccount = local.unarchiveConfirmationAccount,
            deleteConfirmationAccount = local.deleteConfirmationAccount,
            errorMessage = local.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountsUiState(isLoading = true)
    )

    /**
     * Opens the account form for creating a new account.
     */
    fun openCreateForm() {
        val initialCurrency = uiState.value.activeAccounts.firstOrNull()?.account?.currency ?: Currency.INR
        localState.update {
            it.copy(
                formState = AccountFormState(
                    accountId = null,
                    name = "",
                    type = AccountType.CASH,
                    currency = initialCurrency,
                    initialBalanceInput = "0",
                    isArchived = false
                )
            )
        }
    }

    /**
     * Opens the account form for editing an existing account.
     */
    fun openEditForm(account: Account) {
        localState.update {
            it.copy(
                formState = AccountFormState(
                    accountId = account.id,
                    name = account.name,
                    type = account.type,
                    currency = account.currency,
                    initialBalanceInput = account.initialBalance.amount.toPlainString(),
                    isArchived = account.isArchived
                )
            )
        }
    }

    /**
     * Closes the account form without saving.
     */
    fun closeForm() {
        localState.update { it.copy(formState = null) }
    }

    fun onFormNameChange(name: String) {
        localState.update { state ->
            state.copy(formState = state.formState?.copy(name = name, nameError = null))
        }
    }

    fun onFormTypeChange(type: AccountType) {
        localState.update { state ->
            state.copy(formState = state.formState?.copy(type = type))
        }
    }

    fun onFormCurrencyChange(currency: Currency) {
        localState.update { state ->
            state.copy(formState = state.formState?.copy(currency = currency, initialBalanceError = null))
        }
    }

    fun onFormInitialBalanceChange(input: String) {
        localState.update { state ->
            state.copy(formState = state.formState?.copy(initialBalanceInput = input, initialBalanceError = null))
        }
    }

    fun onFormArchivedChange(isArchived: Boolean) {
        localState.update { state ->
            state.copy(formState = state.formState?.copy(isArchived = isArchived))
        }
    }

    /**
     * Validates and persists the account to the repository.
     */
    fun saveAccount() {
        val form = localState.value.formState ?: return
        val id = form.accountId ?: EntityId(UUID.randomUUID().toString())

        val validation = accountUseCases.validateAndCreateAccount(
            id = id,
            name = form.name,
            type = form.type,
            currency = form.currency,
            initialBalanceInput = form.initialBalanceInput,
            isArchived = form.isArchived
        )

        when (validation) {
            is AccountValidationResult.Success -> {
                localState.update { state ->
                    state.copy(formState = state.formState?.copy(isSaving = true))
                }
                viewModelScope.launch {
                    try {
                        if (form.isEditing) {
                            accountRepository.updateAccount(validation.account)
                        } else {
                            accountRepository.insertAccount(validation.account)
                        }
                        localState.update { it.copy(formState = null) }
                    } catch (e: Exception) {
                        localState.update { state ->
                            state.copy(
                                formState = state.formState?.copy(isSaving = false),
                                errorMessage = e.localizedMessage ?: "Failed to save account"
                            )
                        }
                    }
                }
            }
            is AccountValidationResult.Failure -> {
                localState.update { state ->
                    state.copy(
                        formState = state.formState?.copy(
                            nameError = validation.nameError,
                            initialBalanceError = validation.initialBalanceError
                        )
                    )
                }
            }
        }
    }

    /**
     * Archive flow
     */
    fun requestArchiveAccount(account: Account) {
        localState.update { it.copy(archiveConfirmationAccount = account) }
    }

    fun dismissArchiveDialog() {
        localState.update { it.copy(archiveConfirmationAccount = null) }
    }

    fun confirmArchiveAccount() {
        val account = localState.value.archiveConfirmationAccount ?: return
        viewModelScope.launch {
            try {
                accountRepository.archiveAccount(account.id)
                localState.update { it.copy(archiveConfirmationAccount = null) }
            } catch (e: Exception) {
                localState.update {
                    it.copy(
                        archiveConfirmationAccount = null,
                        errorMessage = e.localizedMessage ?: "Failed to archive account"
                    )
                }
            }
        }
    }

    /**
     * Unarchive flow
     */
    fun requestUnarchiveAccount(account: Account) {
        localState.update { it.copy(unarchiveConfirmationAccount = account) }
    }

    fun dismissUnarchiveDialog() {
        localState.update { it.copy(unarchiveConfirmationAccount = null) }
    }

    fun confirmUnarchiveAccount() {
        val account = localState.value.unarchiveConfirmationAccount ?: return
        viewModelScope.launch {
            try {
                accountRepository.updateAccount(account.copy(isArchived = false))
                localState.update { it.copy(unarchiveConfirmationAccount = null) }
            } catch (e: Exception) {
                localState.update {
                    it.copy(
                        unarchiveConfirmationAccount = null,
                        errorMessage = e.localizedMessage ?: "Failed to unarchive account"
                    )
                }
            }
        }
    }

    /**
     * Delete flow
     */
    fun requestDeleteAccount(account: Account) {
        localState.update { it.copy(deleteConfirmationAccount = account) }
    }

    fun dismissDeleteDialog() {
        localState.update { it.copy(deleteConfirmationAccount = null) }
    }

    fun confirmDeleteAccount() {
        val account = localState.value.deleteConfirmationAccount ?: return
        viewModelScope.launch {
            try {
                accountRepository.deleteAccount(account)
                localState.update { it.copy(deleteConfirmationAccount = null) }
            } catch (e: Exception) {
                localState.update {
                    it.copy(
                        deleteConfirmationAccount = null,
                        errorMessage = e.localizedMessage ?: "Failed to delete account"
                    )
                }
            }
        }
    }

    fun toggleShowArchived() {
        localState.update { it.copy(showArchived = !it.showArchived) }
    }

    fun clearErrorMessage() {
        localState.update { it.copy(errorMessage = null) }
    }
}

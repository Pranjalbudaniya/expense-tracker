package com.example.expensetracker.feature.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.TransactionType
import com.example.expensetracker.core.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel managing UI state and business interactions for adding transactions.
 * Excludes bank accounts from payment options and restricts types to Expense and Income.
 */
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val useCases: AddTransactionUseCases,
    private val preferencesRepository: PreferencesRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val preferredCurrency = try {
                preferencesRepository?.userPreferences?.first()?.currencyCode?.let { Currency.fromCode(it) } ?: Currency.USD
            } catch (e: Exception) {
                Currency.USD
            }

            combine(
                useCases.loadAccounts(),
                useCases.loadCategories()
            ) { accounts, categories ->
                // Remove bank accounts from payment options per user requirement
                val paymentOptions = accounts.filter { it.type != AccountType.BANK }
                Pair(paymentOptions, categories)
            }.catch { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        generalError = throwable.localizedMessage ?: "Failed to load accounts and categories"
                    )
                }
            }.collect { (accounts, categories) ->
                _uiState.update { current ->
                    val defaultSource = current.selectedSourceAccountId ?: accounts.firstOrNull()?.id
                    val defaultDest = current.selectedDestinationAccountId ?: accounts.firstOrNull()?.id
                    val currency = accounts.firstOrNull()?.currency ?: preferredCurrency

                    // Find dining/food category as default if available to match Stitch design
                    val defaultCategory = current.selectedCategoryId
                        ?: categories.firstOrNull { it.name.contains("Dining", ignoreCase = true) || it.name.contains("Food", ignoreCase = true) }?.id
                        ?: categories.firstOrNull()?.id

                    current.copy(
                        availableAccounts = accounts,
                        availableCategories = categories,
                        selectedCurrency = currency,
                        selectedSourceAccountId = defaultSource,
                        selectedDestinationAccountId = defaultDest,
                        selectedCategoryId = defaultCategory,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onEvent(event: AddTransactionEvent) {
        when (event) {
            is AddTransactionEvent.TransactionTypeChanged -> {
                _uiState.update { current ->
                    // Transfer feature removed: fallback to EXPENSE if TRANSFER requested
                    val newType = if (event.type == TransactionType.TRANSFER) TransactionType.EXPENSE else event.type
                    val updatedDest = if (newType == TransactionType.INCOME) {
                        current.selectedSourceAccountId ?: current.availableAccounts.firstOrNull()?.id
                    } else {
                        current.selectedDestinationAccountId
                    }

                    current.copy(
                        transactionType = newType,
                        selectedDestinationAccountId = updatedDest,
                        amountError = null,
                        sourceAccountError = null,
                        destinationAccountError = null,
                        generalError = null
                    )
                }
            }
            is AddTransactionEvent.AmountChanged -> {
                _uiState.update { it.copy(amountInput = event.amount, amountError = null) }
            }
            is AddTransactionEvent.QuickAmountAdded -> {
                _uiState.update { current ->
                    val currentVal = current.amountInput.toDoubleOrNull() ?: 0.0
                    val newVal = currentVal + event.delta
                    current.copy(
                        amountInput = String.format(Locale.US, "%.2f", newVal),
                        amountError = null
                    )
                }
            }
            is AddTransactionEvent.RoundUpAmount -> {
                _uiState.update { current ->
                    val currentVal = current.amountInput.toDoubleOrNull() ?: 0.0
                    val rounded = Math.ceil(currentVal)
                    val finalVal = if (rounded == currentVal) currentVal + 1.0 else rounded
                    current.copy(
                        amountInput = String.format(Locale.US, "%.2f", finalVal),
                        amountError = null
                    )
                }
            }
            is AddTransactionEvent.SourceAccountSelected -> {
                _uiState.update { current ->
                    val selectedAcc = current.availableAccounts.find { it.id == event.accountId }
                    current.copy(
                        selectedSourceAccountId = event.accountId,
                        selectedCurrency = if (!current.isIncome) selectedAcc?.currency ?: current.selectedCurrency else current.selectedCurrency,
                        sourceAccountError = null
                    )
                }
            }
            is AddTransactionEvent.DestinationAccountSelected -> {
                _uiState.update { current ->
                    val destAcc = current.availableAccounts.find { it.id == event.accountId }
                    current.copy(
                        selectedDestinationAccountId = event.accountId,
                        selectedCurrency = if (current.isIncome) destAcc?.currency ?: current.selectedCurrency else current.selectedCurrency,
                        destinationAccountError = null
                    )
                }
            }
            is AddTransactionEvent.CategorySelected -> {
                _uiState.update { it.copy(selectedCategoryId = event.categoryId) }
            }
            is AddTransactionEvent.TimestampChanged -> {
                _uiState.update { it.copy(timestamp = event.timestamp) }
            }
            is AddTransactionEvent.NoteChanged -> {
                _uiState.update { it.copy(note = event.note) }
            }
            is AddTransactionEvent.MerchantChanged -> {
                _uiState.update { it.copy(merchant = event.merchant) }
            }
            is AddTransactionEvent.RecurringToggled -> {
                _uiState.update { it.copy(isRecurring = event.isRecurring) }
            }
            is AddTransactionEvent.ResetForm -> {
                _uiState.update { current ->
                    current.copy(
                        amountInput = "",
                        note = "",
                        merchant = "",
                        isRecurring = false,
                        amountError = null,
                        sourceAccountError = null,
                        destinationAccountError = null,
                        generalError = null
                    )
                }
            }
            is AddTransactionEvent.SaveClicked -> {
                saveTransaction(resetAfterSave = false)
            }
            is AddTransactionEvent.SaveAndAddAnotherClicked -> {
                saveTransaction(resetAfterSave = true)
            }
        }
    }

    private fun saveTransaction(resetAfterSave: Boolean = false) {
        val currentState = _uiState.value
        if (currentState.isSaving || currentState.isSaved) return

        val validation = useCases.validateForm(
            type = currentState.transactionType,
            amountInput = currentState.amountInput,
            sourceAccountId = currentState.selectedSourceAccountId,
            destinationAccountId = currentState.selectedDestinationAccountId,
            currency = currentState.selectedCurrency
        )

        if (!validation.isValid) {
            _uiState.update {
                it.copy(
                    amountError = validation.amountError,
                    sourceAccountError = validation.sourceAccountError,
                    destinationAccountError = validation.destinationAccountError
                )
            }
            return
        }

        val parsedAmount = validation.parsedAmount ?: return
        val finalNote = when {
            currentState.merchant.isNotBlank() && currentState.note.isNotBlank() ->
                "${currentState.note} (${currentState.merchant})"
            currentState.merchant.isNotBlank() -> currentState.merchant
            else -> currentState.note
        }

        val transaction = useCases.createTransaction(
            type = currentState.transactionType,
            amount = Money(parsedAmount, currentState.selectedCurrency),
            sourceAccountId = currentState.selectedSourceAccountId,
            destinationAccountId = currentState.selectedDestinationAccountId,
            categoryId = currentState.selectedCategoryId,
            timestamp = currentState.timestamp,
            note = finalNote
        )

        _uiState.update { it.copy(isSaving = true, generalError = null) }

        viewModelScope.launch {
            try {
                useCases.saveTransaction(transaction)
                if (resetAfterSave) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            amountInput = "",
                            note = "",
                            merchant = "",
                            generalError = null
                        )
                    }
                } else {
                    _uiState.update { it.copy(isSaving = false, isSaved = true) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        generalError = e.localizedMessage ?: "Failed to save transaction"
                    )
                }
            }
        }
    }
}

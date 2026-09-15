package com.example.expensetracker.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.money.MonetaryValidationResult
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class TransactionDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    val transactionId: EntityId = EntityId(
        savedStateHandle.get<String>("transactionId") ?: ""
    )

    private val _uiState = MutableStateFlow(TransactionDetailsUiState())
    val uiState: StateFlow<TransactionDetailsUiState> = _uiState.asStateFlow()

    init {
        loadTransactionDetails()
    }

    private fun loadTransactionDetails() {
        if (transactionId.value.isBlank()) {
            _uiState.update { it.copy(isLoading = false, transactionNotFound = true) }
            return
        }

        viewModelScope.launch {
            combine(
                transactionRepository.getTransaction(transactionId),
                categoryRepository.getAllCategories(),
                accountRepository.getAllAccounts()
            ) { tx: Transaction?, categories: List<Category>, accounts: List<Account> ->
                Triple(tx, categories, accounts)
            }.catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        generalError = error.localizedMessage ?: "Failed to load transaction details"
                    )
                }
            }.collect { (tx, categories, accounts) ->
                if (tx == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            transactionNotFound = true,
                            transaction = null
                        )
                    }
                    return@collect
                }

                val categoryMap = categories.associateBy { it.id }
                val accountMap = accounts.associateBy { it.id }

                val resolvedCategory = tx.categoryId?.let { categoryMap[it] }
                val resolvedSource = accountMap[tx.sourceAccountId]
                val resolvedDest = tx.destinationAccountId?.let { accountMap[it] }

                _uiState.update { current ->
                    // Only update editable fields if not currently user-editing to avoid overwriting typed input
                    val shouldInitEditing = !current.isEditing || current.transaction == null
                    current.copy(
                        transaction = tx,
                        category = resolvedCategory,
                        sourceAccount = resolvedSource,
                        destinationAccount = resolvedDest,
                        availableCategories = categories,
                        availableAccounts = accounts,
                        isLoading = false,
                        transactionNotFound = false,
                        editedAmountInput = if (shouldInitEditing) tx.amount.amount.toPlainString() else current.editedAmountInput,
                        editedCategoryId = if (shouldInitEditing) tx.categoryId else current.editedCategoryId,
                        editedSourceAccountId = if (shouldInitEditing) tx.sourceAccountId else current.editedSourceAccountId,
                        editedDestinationAccountId = if (shouldInitEditing) tx.destinationAccountId else current.editedDestinationAccountId,
                        editedTimestamp = if (shouldInitEditing) tx.timestamp else current.editedTimestamp,
                        editedNote = if (shouldInitEditing) tx.note else current.editedNote
                    )
                }
            }
        }
    }

    fun startEditing() {
        val tx = _uiState.value.transaction ?: return
        _uiState.update {
            it.copy(
                isEditing = true,
                editedAmountInput = tx.amount.amount.toPlainString(),
                editedCategoryId = tx.categoryId,
                editedSourceAccountId = tx.sourceAccountId,
                editedDestinationAccountId = tx.destinationAccountId,
                editedTimestamp = tx.timestamp,
                editedNote = tx.note,
                amountError = null,
                accountError = null,
                destinationAccountError = null,
                generalError = null
            )
        }
    }

    fun cancelEditing() {
        if (_uiState.value.hasUnsavedChanges) {
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            resetEditableFields()
        }
    }

    fun confirmDiscard() {
        resetEditableFields()
    }

    fun dismissDiscardDialog() {
        _uiState.update { it.copy(showDiscardDialog = false) }
    }

    private fun resetEditableFields() {
        val tx = _uiState.value.transaction
        _uiState.update {
            it.copy(
                isEditing = false,
                showDiscardDialog = false,
                editedAmountInput = tx?.amount?.amount?.toPlainString() ?: "",
                editedCategoryId = tx?.categoryId,
                editedSourceAccountId = tx?.sourceAccountId,
                editedDestinationAccountId = tx?.destinationAccountId,
                editedTimestamp = tx?.timestamp ?: Instant.now(),
                editedNote = tx?.note ?: "",
                amountError = null,
                accountError = null,
                destinationAccountError = null
            )
        }
    }

    fun onAmountChanged(amount: String) {
        _uiState.update { it.copy(editedAmountInput = amount, amountError = null) }
    }

    fun onCategorySelected(categoryId: EntityId?) {
        _uiState.update { it.copy(editedCategoryId = categoryId) }
    }

    fun onSourceAccountSelected(accountId: EntityId?) {
        _uiState.update { current ->
            val srcAcc = current.availableAccounts.find { it.id == accountId }
            val destAcc = current.availableAccounts.find { it.id == current.editedDestinationAccountId }
            val destError = if (current.isTransfer && accountId != null && accountId == current.editedDestinationAccountId) {
                "Source and destination accounts must differ"
            } else if (current.isTransfer && srcAcc != null && destAcc != null && !srcAcc.currency.code.equals(destAcc.currency.code, ignoreCase = true)) {
                "Source and destination accounts must have the same currency"
            } else {
                null
            }
            current.copy(
                editedSourceAccountId = accountId,
                accountError = null,
                destinationAccountError = destError
            )
        }
    }

    fun onDestinationAccountSelected(accountId: EntityId?) {
        _uiState.update { current ->
            val srcAcc = current.availableAccounts.find { it.id == current.editedSourceAccountId }
            val destAcc = current.availableAccounts.find { it.id == accountId }
            val destError = if (current.isTransfer && accountId != null && accountId == current.editedSourceAccountId) {
                "Source and destination accounts must differ"
            } else if (current.isTransfer && srcAcc != null && destAcc != null && !srcAcc.currency.code.equals(destAcc.currency.code, ignoreCase = true)) {
                "Source and destination accounts must have the same currency"
            } else {
                null
            }
            current.copy(
                editedDestinationAccountId = accountId,
                destinationAccountError = destError
            )
        }
    }

    fun onTimestampChanged(timestamp: Instant) {
        _uiState.update { it.copy(editedTimestamp = timestamp) }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(editedNote = note) }
    }

    fun saveChanges() {
        val current = _uiState.value
        val tx = current.transaction ?: return
        if (current.isSaving) return

        // 1. Validate Amount
        val amountResult = Money.parseAndValidateAmount(current.editedAmountInput, current.currency)
        val amountError = when (amountResult) {
            is MonetaryValidationResult.Error -> amountResult.message
            is MonetaryValidationResult.Success -> null
        }
        val parsedAmount = (amountResult as? MonetaryValidationResult.Success)?.amount

        // 2. Validate Accounts
        var accountError: String? = null
        var destError: String? = null

        when (tx.type) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                if (current.editedSourceAccountId == null) {
                    accountError = "Please select an account"
                }
            }
            TransactionType.TRANSFER -> {
                if (current.editedSourceAccountId == null) {
                    accountError = "Please select a source account"
                }
                if (current.editedDestinationAccountId == null) {
                    destError = "Please select a destination account"
                } else if (current.editedSourceAccountId != null && current.editedSourceAccountId == current.editedDestinationAccountId) {
                    destError = "Source and destination accounts must differ"
                } else if (current.editedSourceAccountId != null) {
                    val srcAcc = current.availableAccounts.find { it.id == current.editedSourceAccountId }
                    val destAcc = current.availableAccounts.find { it.id == current.editedDestinationAccountId }
                    if (srcAcc != null && destAcc != null && !srcAcc.currency.code.equals(destAcc.currency.code, ignoreCase = true)) {
                        destError = "Source and destination accounts must have the same currency"
                    }
                }
            }
        }

        if (amountError != null || accountError != null || destError != null || parsedAmount == null) {
            _uiState.update {
                it.copy(
                    amountError = amountError,
                    accountError = accountError,
                    destinationAccountError = destError
                )
            }
            return
        }

        val updatedTransaction = tx.copy(
            amount = Money(parsedAmount, current.currency),
            sourceAccountId = current.editedSourceAccountId!!,
            destinationAccountId = if (tx.type == TransactionType.TRANSFER) current.editedDestinationAccountId else null,
            categoryId = if (tx.type == TransactionType.TRANSFER) null else current.editedCategoryId,
            timestamp = current.editedTimestamp,
            note = current.editedNote.trim()
        )

        _uiState.update { it.copy(isSaving = true, generalError = null) }

        viewModelScope.launch {
            try {
                transactionRepository.updateTransaction(updatedTransaction)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSaved = true,
                        isEditing = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        generalError = e.localizedMessage ?: "Failed to update transaction"
                    )
                }
            }
        }
    }

    fun moveToTrash() {
        val tx = _uiState.value.transaction ?: return
        viewModelScope.launch {
            try {
                transactionRepository.moveToTrash(tx.id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(generalError = e.localizedMessage ?: "Failed to move transaction to trash")
                }
            }
        }
    }

    fun restoreFromTrash() {
        val tx = _uiState.value.transaction ?: return
        viewModelScope.launch {
            try {
                transactionRepository.restoreFromTrash(tx.id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(generalError = e.localizedMessage ?: "Failed to restore transaction")
                }
            }
        }
    }

    fun deletePermanently() {
        val tx = _uiState.value.transaction ?: return
        viewModelScope.launch {
            try {
                transactionRepository.deletePermanently(tx.id)
                _uiState.update {
                    it.copy(
                        isPermanentlyDeleted = true,
                        showDeleteConfirmation = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showDeleteConfirmation = false,
                        generalError = e.localizedMessage ?: "Failed to permanently delete transaction"
                    )
                }
            }
        }
    }

    fun setShowDeleteConfirmation(show: Boolean) {
        _uiState.update { it.copy(showDeleteConfirmation = show) }
    }

    fun clearGeneralError() {
        _uiState.update { it.copy(generalError = null) }
    }
}

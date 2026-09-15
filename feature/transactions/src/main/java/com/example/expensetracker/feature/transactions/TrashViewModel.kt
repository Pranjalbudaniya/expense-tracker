package com.example.expensetracker.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.transaction.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _showEmptyTrashDialog = MutableStateFlow(false)
    private val _transactionToDeletePermanently = MutableStateFlow<EntityId?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TrashUiState> = combine(
        transactionRepository.getDeletedTransactions(),
        categoryRepository.getAllCategories(),
        accountRepository.getAllAccounts(),
        _showEmptyTrashDialog,
        _transactionToDeletePermanently,
        _userMessage,
        _errorMessage
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val deletedTxs = args[0] as List<Transaction>
        @Suppress("UNCHECKED_CAST")
        val categories = args[1] as List<Category>
        @Suppress("UNCHECKED_CAST")
        val accounts = args[2] as List<Account>
        val showEmptyDialog = args[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val txToDelete = args[4] as EntityId?
        val userMsg = args[5] as String?
        val errorMsg = args[6] as String?
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }

        val sortedTxs = deletedTxs.sortedWith { a, b ->
            val cmp = b.timestamp.compareTo(a.timestamp)
            if (cmp != 0) cmp else b.id.value.compareTo(a.id.value)
        }

        val displayItems = sortedTxs.map { tx ->
            TransactionDisplayItem(
                transaction = tx,
                category = tx.categoryId?.let { categoryMap[it] },
                sourceAccount = accountMap[tx.sourceAccountId],
                destinationAccount = tx.destinationAccountId?.let { accountMap[it] }
            )
        }

        val grouped = groupItemsByDate(displayItems)

        TrashUiState(
            groupedTransactions = grouped,
            totalCount = deletedTxs.size,
            isLoading = false,
            errorMessage = errorMsg,
            userMessage = userMsg,
            showEmptyTrashDialog = showEmptyDialog,
            transactionToDeletePermanently = txToDelete
        )
    }.catch { throwable ->
        emit(
            TrashUiState(
                isLoading = false,
                errorMessage = throwable.message ?: "Failed to load deleted transactions"
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrashUiState(isLoading = true)
    )

    fun restoreTransaction(id: EntityId) {
        viewModelScope.launch {
            try {
                transactionRepository.restoreFromTrash(id)
                _userMessage.value = "Transaction restored"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to restore transaction"
            }
        }
    }

    fun showDeletePermanentlyConfirmation(id: EntityId) {
        _transactionToDeletePermanently.value = id
    }

    fun dismissDeletePermanentlyConfirmation() {
        _transactionToDeletePermanently.value = null
    }

    fun confirmDeletePermanently() {
        val id = _transactionToDeletePermanently.value ?: return
        _transactionToDeletePermanently.value = null
        viewModelScope.launch {
            try {
                transactionRepository.deletePermanently(id)
                _userMessage.value = "Transaction permanently deleted"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to permanently delete transaction"
            }
        }
    }

    fun showEmptyTrashConfirmation() {
        _showEmptyTrashDialog.value = true
    }

    fun dismissEmptyTrashConfirmation() {
        _showEmptyTrashDialog.value = false
    }

    fun confirmEmptyTrash() {
        _showEmptyTrashDialog.value = false
        viewModelScope.launch {
            try {
                transactionRepository.clearTrash()
                _userMessage.value = "Trash emptied"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to empty trash"
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private fun groupItemsByDate(items: List<TransactionDisplayItem>): List<DateGroupedTransactions> {
        val grouped = linkedMapOf<LocalDate, MutableList<TransactionDisplayItem>>()
        val zone = ZoneId.systemDefault()

        for (item in items) {
            val date = item.transaction.timestamp.atZone(zone).toLocalDate()
            grouped.getOrPut(date) { mutableListOf() }.add(item)
        }

        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())

        return grouped.map { (date, txs) ->
            val header = when (date) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> date.format(formatter)
            }
            DateGroupedTransactions(
                header = header,
                date = date,
                transactions = txs
            )
        }
    }
}

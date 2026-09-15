package com.example.expensetracker.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.transaction.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionsUseCases: TransactionsUseCases
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(TransactionFilter.EMPTY)
    val filter = _filter.asStateFlow()

    private val _sort = MutableStateFlow(TransactionSort.DEFAULT)
    val sort = _sort.asStateFlow()

    private val _isTrashView = MutableStateFlow(false)
    val isTrashView = _isTrashView.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive = _isSearchActive.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _undoSnackbarEvent = MutableStateFlow<EntityId?>(null)
    val undoSnackbarEvent = _undoSnackbarEvent.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawTransactionsFlow = _isTrashView.flatMapLatest { isTrash ->
        if (isTrash) {
            transactionsUseCases.getDeletedTransactions()
        } else {
            transactionsUseCases.getActiveTransactions()
        }
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        rawTransactionsFlow,
        transactionsUseCases.getAllCategories(),
        transactionsUseCases.getAllAccounts(),
        _searchQuery,
        _filter,
        _sort,
        _isTrashView,
        _isSearchActive,
        _errorMessage
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val rawTransactions = args[0] as List<Transaction>
        @Suppress("UNCHECKED_CAST")
        val categories = args[1] as List<Category>
        @Suppress("UNCHECKED_CAST")
        val accounts = args[2] as List<Account>
        val query = args[3] as String
        val filter = args[4] as TransactionFilter
        val sort = args[5] as TransactionSort
        val isTrash = args[6] as Boolean
        val searchActive = args[7] as Boolean
        val error = args[8] as String?

        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }

        val filteredItems = transactionsUseCases.filterAndSort(
            transactions = rawTransactions,
            searchQuery = query,
            filter = filter,
            sort = sort,
            categoryMap = categoryMap,
            accountMap = accountMap
        )

        val grouped = transactionsUseCases.groupByDate(filteredItems)

        val emptyState: TransactionsEmptyState? = when {
            filteredItems.isNotEmpty() -> null
            isTrash && rawTransactions.isEmpty() -> TransactionsEmptyState.EMPTY_TRASH
            !isTrash && rawTransactions.isEmpty() -> TransactionsEmptyState.NO_TRANSACTIONS
            query.isNotBlank() -> TransactionsEmptyState.NO_SEARCH_RESULTS
            filter.isActive -> TransactionsEmptyState.NO_MATCHING_FILTERS
            isTrash -> TransactionsEmptyState.EMPTY_TRASH
            else -> TransactionsEmptyState.NO_TRANSACTIONS
        }

        TransactionsUiState(
            searchQuery = query,
            filter = filter,
            sort = sort,
            isTrashView = isTrash,
            isSearchActive = searchActive,
            groupedTransactions = grouped,
            totalCount = filteredItems.size,
            availableCategories = categories,
            availableAccounts = accounts,
            isLoading = false,
            errorMessage = error,
            emptyState = emptyState
        )
    }.catch { throwable ->
        emit(
            TransactionsUiState(
                isLoading = false,
                errorMessage = throwable.message ?: "An unexpected error occurred"
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSearchActiveChanged(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }

    fun onFilterChanged(filter: TransactionFilter) {
        _filter.value = filter
    }

    fun onClearFilters() {
        _filter.value = TransactionFilter.EMPTY
    }

    fun onSortChanged(sort: TransactionSort) {
        _sort.value = sort
    }

    fun onToggleTrashView() {
        _isTrashView.value = !_isTrashView.value
    }

    fun setTrashView(enabled: Boolean) {
        _isTrashView.value = enabled
    }

    fun moveToTrash(id: EntityId) {
        viewModelScope.launch {
            try {
                transactionsUseCases.moveToTrash(id)
                _undoSnackbarEvent.value = id
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to move transaction to trash"
            }
        }
    }

    fun clearUndoSnackbarEvent() {
        _undoSnackbarEvent.value = null
    }

    fun undoMoveToTrash(id: EntityId) {
        restoreFromTrash(id)
        clearUndoSnackbarEvent()
    }

    fun restoreFromTrash(id: EntityId) {
        viewModelScope.launch {
            try {
                transactionsUseCases.restoreFromTrash(id)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to restore transaction"
            }
        }
    }

    fun deletePermanently(id: EntityId) {
        viewModelScope.launch {
            try {
                transactionsUseCases.deletePermanently(id)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to permanently delete transaction"
            }
        }
    }

    fun clearTrash() {
        viewModelScope.launch {
            try {
                transactionsUseCases.clearTrash()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to clear trash"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

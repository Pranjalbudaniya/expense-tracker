package com.example.expensetracker.core.data.repository

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.recurring.RecurringTransaction
import kotlinx.coroutines.flow.Flow

interface RecurringTransactionRepository {
    fun getAllRecurringTransactions(): Flow<List<RecurringTransaction>>
    fun getActiveRecurringTransactions(): Flow<List<RecurringTransaction>>
    suspend fun getRecurringTransactionById(id: EntityId): RecurringTransaction?
    suspend fun insertRecurringTransaction(recurringTransaction: RecurringTransaction)
    suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransaction)
    suspend fun setEnabled(id: EntityId, isEnabled: Boolean)
    suspend fun deleteRecurringTransaction(recurringTransaction: RecurringTransaction)
}

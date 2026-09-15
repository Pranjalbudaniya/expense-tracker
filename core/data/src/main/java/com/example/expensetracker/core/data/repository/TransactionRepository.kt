package com.example.expensetracker.core.data.repository

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.transaction.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getActiveTransactions(): Flow<List<Transaction>>
    fun getDeletedTransactions(): Flow<List<Transaction>>
    fun getTransaction(id: EntityId): Flow<Transaction?>
    suspend fun getTransactionById(id: EntityId): Transaction?
    suspend fun insertTransaction(transaction: Transaction)
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun moveToTrash(id: EntityId)
    suspend fun restoreFromTrash(id: EntityId)
    suspend fun deletePermanently(id: EntityId)
    fun getAllTransactions(): Flow<List<Transaction>> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun insertTransactions(transactions: List<Transaction>) {}
    suspend fun deleteAllTransactions() {}
    suspend fun clearTrash()
}

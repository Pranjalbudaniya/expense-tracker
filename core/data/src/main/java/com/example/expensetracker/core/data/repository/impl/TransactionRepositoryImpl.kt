package com.example.expensetracker.core.data.repository.impl

import com.example.expensetracker.core.data.mapper.toDomain
import com.example.expensetracker.core.data.mapper.toEntity
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.database.dao.TransactionDao
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.transaction.Transaction
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao
) : TransactionRepository {

    override fun getActiveTransactions(): Flow<List<Transaction>> {
        return transactionDao.getActiveTransactions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDeletedTransactions(): Flow<List<Transaction>> {
        return transactionDao.getDeletedTransactions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTransaction(id: EntityId): Flow<Transaction?> {
        return transactionDao.getByIdFlow(id.value).map { it?.toDomain() }
    }

    override suspend fun getTransactionById(id: EntityId): Transaction? {
        return transactionDao.getById(id.value)?.toDomain()
    }

    override suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insert(transaction.toEntity())
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.update(transaction.toEntity())
    }

    override suspend fun moveToTrash(id: EntityId) {
        transactionDao.moveToTrash(id.value)
    }

    override suspend fun restoreFromTrash(id: EntityId) {
        transactionDao.restoreFromTrash(id.value)
    }

    override suspend fun deletePermanently(id: EntityId) {
        transactionDao.deletePermanentlyById(id.value)
    }

    override fun getAllTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertTransactions(transactions: List<Transaction>) {
        transactionDao.insertAll(transactions.map { it.toEntity() })
    }

    override suspend fun deleteAllTransactions() {
        transactionDao.deleteAllTransactions()
    }

    override suspend fun clearTrash() {
        transactionDao.clearTrash()
    }
}

package com.example.expensetracker.core.data.repository.impl

import com.example.expensetracker.core.data.mapper.toDomain
import com.example.expensetracker.core.data.mapper.toEntity
import com.example.expensetracker.core.data.repository.RecurringTransactionRepository
import com.example.expensetracker.core.database.dao.RecurringTransactionDao
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.recurring.RecurringTransaction
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecurringTransactionRepositoryImpl @Inject constructor(
    private val recurringTransactionDao: RecurringTransactionDao
) : RecurringTransactionRepository {

    override fun getAllRecurringTransactions(): Flow<List<RecurringTransaction>> {
        return recurringTransactionDao.getAllRecurring().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getActiveRecurringTransactions(): Flow<List<RecurringTransaction>> {
        return recurringTransactionDao.getActiveRecurring().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRecurringTransactionById(id: EntityId): RecurringTransaction? {
        return recurringTransactionDao.getById(id.value)?.toDomain()
    }

    override suspend fun insertRecurringTransaction(recurringTransaction: RecurringTransaction) {
        recurringTransactionDao.insert(recurringTransaction.toEntity())
    }

    override suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransaction) {
        recurringTransactionDao.update(recurringTransaction.toEntity())
    }

    override suspend fun setEnabled(id: EntityId, isEnabled: Boolean) {
        val existing = recurringTransactionDao.getById(id.value) ?: return
        recurringTransactionDao.update(existing.copy(isEnabled = isEnabled))
    }

    override suspend fun deleteRecurringTransaction(recurringTransaction: RecurringTransaction) {
        recurringTransactionDao.delete(recurringTransaction.toEntity())
    }
}

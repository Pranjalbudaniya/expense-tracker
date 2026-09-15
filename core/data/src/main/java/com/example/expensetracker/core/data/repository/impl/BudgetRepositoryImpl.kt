package com.example.expensetracker.core.data.repository.impl

import com.example.expensetracker.core.data.mapper.toDomain
import com.example.expensetracker.core.data.mapper.toEntity
import com.example.expensetracker.core.data.repository.BudgetRepository
import com.example.expensetracker.core.database.dao.BudgetDao
import com.example.expensetracker.core.model.budget.Budget
import com.example.expensetracker.core.model.common.EntityId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao
) : BudgetRepository {

    override fun getAllBudgets(): Flow<List<Budget>> {
        return budgetDao.getAllBudgets().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getActiveBudgets(): Flow<List<Budget>> {
        return budgetDao.getActiveBudgets().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getBudgetById(id: EntityId): Budget? {
        return budgetDao.getById(id.value)?.toDomain()
    }

    override suspend fun insertBudget(budget: Budget) {
        budgetDao.insert(budget.toEntity())
    }

    override suspend fun updateBudget(budget: Budget) {
        budgetDao.update(budget.toEntity())
    }

    override suspend fun deleteBudget(budget: Budget) {
        budgetDao.delete(budget.toEntity())
    }
}

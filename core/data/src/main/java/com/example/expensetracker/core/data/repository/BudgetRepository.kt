package com.example.expensetracker.core.data.repository

import com.example.expensetracker.core.model.budget.Budget
import com.example.expensetracker.core.model.common.EntityId
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getAllBudgets(): Flow<List<Budget>>
    fun getActiveBudgets(): Flow<List<Budget>>
    suspend fun getBudgetById(id: EntityId): Budget?
    suspend fun insertBudget(budget: Budget)
    suspend fun updateBudget(budget: Budget)
    suspend fun deleteBudget(budget: Budget)
}

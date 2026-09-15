package com.example.expensetracker.core.data.repository

import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getActiveCategories(): Flow<List<Category>>
    fun getAllCategories(): Flow<List<Category>>
    fun getCategory(id: EntityId): Flow<Category?>
    suspend fun getCategoryById(id: EntityId): Category?
    suspend fun insertCategory(category: Category)
    suspend fun updateCategory(category: Category)
    suspend fun updateCategoryOrder(categories: List<Category>) {}
    suspend fun archiveCategory(id: EntityId)
    suspend fun deleteCategory(category: Category)
}

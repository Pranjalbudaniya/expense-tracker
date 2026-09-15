package com.example.expensetracker.core.data.repository.impl

import com.example.expensetracker.core.data.defaults.DefaultDataInitializer
import com.example.expensetracker.core.data.mapper.toDomain
import com.example.expensetracker.core.data.mapper.toEntity
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.database.dao.CategoryDao
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val defaultDataInitializer: Provider<DefaultDataInitializer>? = null
) : CategoryRepository {

    init {
        defaultDataInitializer?.let { provider ->
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    provider.get().initialize()
                }
            }
        }
    }

    override fun getActiveCategories(): Flow<List<Category>> {
        return categoryDao.getActiveCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getCategory(id: EntityId): Flow<Category?> {
        return categoryDao.getAllCategories().map { list ->
            list.firstOrNull { it.id == id.value }?.toDomain()
        }
    }

    override suspend fun getCategoryById(id: EntityId): Category? {
        return categoryDao.getById(id.value)?.toDomain()
    }

    override suspend fun insertCategory(category: Category) {
        categoryDao.insert(category.toEntity())
    }

    override suspend fun updateCategory(category: Category) {
        categoryDao.update(category.toEntity())
    }

    override suspend fun updateCategoryOrder(categories: List<Category>) {
        categoryDao.insertAll(categories.map { it.toEntity() })
    }

    override suspend fun archiveCategory(id: EntityId) {
        val existing = categoryDao.getById(id.value) ?: return
        categoryDao.update(existing.copy(isArchived = true))
    }

    override suspend fun deleteCategory(category: Category) {
        categoryDao.delete(category.toEntity())
    }
}

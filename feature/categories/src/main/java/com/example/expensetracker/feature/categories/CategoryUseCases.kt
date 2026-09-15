package com.example.expensetracker.feature.categories

import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.category.CategoryType
import com.example.expensetracker.core.model.common.EntityId

/**
 * Result of validating category input.
 */
data class CategoryValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

/**
 * Domain use-cases and pure business logic for category management.
 */
object CategoryUseCases {

    /**
     * Validates a category name:
     * 1. Must not be blank.
     * 2. Must not be a duplicate (case-insensitive) of an existing active or matching category
     *    within the same category type scope (EXPENSE vs INCOME vs BOTH).
     */
    fun validateName(
        name: String,
        currentCategoryId: EntityId?,
        existingCategories: List<Category>,
        type: CategoryType
    ): CategoryValidationResult {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return CategoryValidationResult(
                isValid = false,
                errorMessage = "Category name cannot be empty"
            )
        }

        val isDuplicate = existingCategories.any { other ->
            // Skip self if editing
            if (currentCategoryId != null && other.id == currentCategoryId) {
                return@any false
            }

            // Same name (case-insensitive)
            val nameMatches = other.name.trim().equals(trimmed, ignoreCase = true)
            if (!nameMatches) return@any false

            // Type collision:
            // BOTH collides with EXPENSE, INCOME, and BOTH.
            // EXPENSE collides with EXPENSE and BOTH.
            // INCOME collides with INCOME and BOTH.
            typesConflict(type, other.type)
        }

        if (isDuplicate) {
            return CategoryValidationResult(
                isValid = false,
                errorMessage = "A category named \"$trimmed\" already exists for this type"
            )
        }

        return CategoryValidationResult(isValid = true)
    }

    /**
     * Returns true if two category types have an overlapping transaction scope.
     */
    fun typesConflict(typeA: CategoryType, typeB: CategoryType): Boolean {
        if (typeA == CategoryType.BOTH || typeB == CategoryType.BOTH) return true
        return typeA == typeB
    }

    /**
     * Reorders a list of categories by moving the item at [fromIndex] to [toIndex]
     * and recalculates sequential [Category.orderIndex] starting at 0.
     */
    fun reorder(categories: List<Category>, fromIndex: Int, toIndex: Int): List<Category> {
        if (fromIndex !in categories.indices || toIndex !in categories.indices || fromIndex == toIndex) {
            return categories
        }
        val mutable = categories.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable.mapIndexed { index, cat ->
            cat.copy(orderIndex = index)
        }
    }

    /**
     * Moves a category up by 1 position within its list.
     */
    fun moveUp(categories: List<Category>, categoryId: EntityId): List<Category> {
        val index = categories.indexOfFirst { it.id == categoryId }
        if (index <= 0) return categories
        return reorder(categories, index, index - 1)
    }

    /**
     * Moves a category down by 1 position within its list.
     */
    fun moveDown(categories: List<Category>, categoryId: EntityId): List<Category> {
        val index = categories.indexOfFirst { it.id == categoryId }
        if (index < 0 || index >= categories.lastIndex) return categories
        return reorder(categories, index, index + 1)
    }

    /**
     * Filters categories applicable for a given [CategoryType] tab.
     * Active categories matching the requested type or BOTH are returned, ordered by orderIndex.
     */
    fun filterByType(categories: List<Category>, type: CategoryType): List<Category> {
        return categories
            .filter { !it.isArchived && (it.type == type || it.type == CategoryType.BOTH) }
            .sortedWith(compareBy<Category> { it.orderIndex }.thenBy { it.name })
    }

    /**
     * Returns all archived categories sorted by name.
     */
    fun filterArchived(categories: List<Category>): List<Category> {
        return categories
            .filter { it.isArchived }
            .sortedBy { it.name.lowercase() }
    }
}

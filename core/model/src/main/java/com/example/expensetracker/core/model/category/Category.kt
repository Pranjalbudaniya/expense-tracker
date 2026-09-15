package com.example.expensetracker.core.model.category

import com.example.expensetracker.core.model.common.EntityId

data class Category(
    val id: EntityId,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val type: CategoryType = CategoryType.EXPENSE,
    val orderIndex: Int = 0
)

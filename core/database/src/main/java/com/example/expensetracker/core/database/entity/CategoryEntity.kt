package com.example.expensetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.expensetracker.core.model.category.CategoryType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val type: CategoryType = CategoryType.EXPENSE,
    val orderIndex: Int = 0
)

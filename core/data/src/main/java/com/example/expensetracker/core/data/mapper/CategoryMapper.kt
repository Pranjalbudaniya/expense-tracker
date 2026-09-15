package com.example.expensetracker.core.data.mapper

import com.example.expensetracker.core.database.entity.CategoryEntity
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId

fun CategoryEntity.toDomain(): Category {
    return Category(
        id = EntityId(id),
        name = name,
        iconKey = iconKey,
        colorKey = colorKey,
        isDefault = isDefault,
        isArchived = isArchived,
        type = type,
        orderIndex = orderIndex
    )
}

fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = id.value,
        name = name,
        iconKey = iconKey,
        colorKey = colorKey,
        isDefault = isDefault,
        isArchived = isArchived,
        type = type,
        orderIndex = orderIndex
    )
}


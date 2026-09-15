package com.example.expensetracker.core.model.budget

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Money
import java.time.LocalDate

data class Budget(
    val id: EntityId,
    val name: String,
    val targetAmount: Money,
    val categoryId: EntityId? = null,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val isEnabled: Boolean = true
)

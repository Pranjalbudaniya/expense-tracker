package com.example.expensetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val targetAmountMinor: Long,
    val currencyCode: String,
    val categoryId: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val isEnabled: Boolean = true
)

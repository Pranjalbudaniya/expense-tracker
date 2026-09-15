package com.example.expensetracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.expensetracker.core.database.converter.DatabaseConverters
import com.example.expensetracker.core.database.dao.AccountDao
import com.example.expensetracker.core.database.dao.BudgetDao
import com.example.expensetracker.core.database.dao.CategoryDao
import com.example.expensetracker.core.database.dao.RecurringTransactionDao
import com.example.expensetracker.core.database.dao.TransactionDao
import com.example.expensetracker.core.database.entity.AccountEntity
import com.example.expensetracker.core.database.entity.BudgetEntity
import com.example.expensetracker.core.database.entity.CategoryEntity
import com.example.expensetracker.core.database.entity.RecurringTransactionEntity
import com.example.expensetracker.core.database.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class ExpenseTrackerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
}

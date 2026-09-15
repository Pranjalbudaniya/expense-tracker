package com.example.expensetracker.core.di

import android.content.Context
import androidx.room.Room
import com.example.expensetracker.core.database.ExpenseTrackerDatabase
import com.example.expensetracker.core.database.dao.AccountDao
import com.example.expensetracker.core.database.dao.BudgetDao
import com.example.expensetracker.core.database.dao.CategoryDao
import com.example.expensetracker.core.database.dao.RecurringTransactionDao
import com.example.expensetracker.core.database.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE categories ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
            db.execSQL("ALTER TABLE categories ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE recurring_transactions ADD COLUMN startDate TEXT NOT NULL DEFAULT '2026-01-01'")
            db.execSQL("ALTER TABLE recurring_transactions ADD COLUMN endDate TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE recurring_transactions ADD COLUMN lastGeneratedOccurrence TEXT DEFAULT NULL")
        }
    }

    @Provides
    @Singleton
    fun provideExpenseTrackerDatabase(
        @ApplicationContext context: Context
    ): ExpenseTrackerDatabase {
        return Room.databaseBuilder(
            context,
            ExpenseTrackerDatabase::class.java,
            "expense_tracker.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTransactionDao(database: ExpenseTrackerDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    fun provideCategoryDao(database: ExpenseTrackerDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    fun provideAccountDao(database: ExpenseTrackerDatabase): AccountDao {
        return database.accountDao()
    }

    @Provides
    fun provideBudgetDao(database: ExpenseTrackerDatabase): BudgetDao {
        return database.budgetDao()
    }

    @Provides
    fun provideRecurringTransactionDao(database: ExpenseTrackerDatabase): RecurringTransactionDao {
        return database.recurringTransactionDao()
    }
}

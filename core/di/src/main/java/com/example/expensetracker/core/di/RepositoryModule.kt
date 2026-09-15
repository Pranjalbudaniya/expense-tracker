package com.example.expensetracker.core.di

import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.BudgetRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.RecurringTransactionRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.data.repository.impl.AccountRepositoryImpl
import com.example.expensetracker.core.data.repository.impl.BudgetRepositoryImpl
import com.example.expensetracker.core.data.repository.impl.CategoryRepositoryImpl
import com.example.expensetracker.core.data.repository.impl.RecurringTransactionRepositoryImpl
import com.example.expensetracker.core.data.repository.impl.TransactionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        impl: TransactionRepositoryImpl
    ): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        impl: CategoryRepositoryImpl
    ): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(
        impl: AccountRepositoryImpl
    ): AccountRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(
        impl: BudgetRepositoryImpl
    ): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindRecurringTransactionRepository(
        impl: RecurringTransactionRepositoryImpl
    ): RecurringTransactionRepository
}

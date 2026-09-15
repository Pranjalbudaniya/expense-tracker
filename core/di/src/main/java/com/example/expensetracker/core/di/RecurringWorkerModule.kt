package com.example.expensetracker.core.di

import com.example.expensetracker.core.data.recurring.RecurringTransactionProcessor
import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.RecurringTransactionRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module providing the [RecurringTransactionProcessor] instance.
 */
@Module
@InstallIn(SingletonComponent::class)
object RecurringWorkerModule {

    @Provides
    @Singleton
    fun provideRecurringTransactionProcessor(
        recurringRepository: RecurringTransactionRepository,
        transactionRepository: TransactionRepository,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository
    ): RecurringTransactionProcessor {
        return RecurringTransactionProcessor(
            recurringRepository = recurringRepository,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )
    }
}

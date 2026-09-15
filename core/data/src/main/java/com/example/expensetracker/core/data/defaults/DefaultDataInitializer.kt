package com.example.expensetracker.core.data.defaults

import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.model.money.Currency
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initializes default categories and accounts on application launch.
 *
 * Guarantees:
 * 1. Safe on first launch and repeated launches (strictly idempotent).
 * 2. Checks existing items by stable ID, not display name.
 * 3. Preserves user modifications to default items (renaming, changing colors, archiving).
 * 4. Never creates duplicate records.
 * 5. Safely completes missing defaults if only partial default data is present.
 * 6. Never recreates an archived/deleted item simply because it is absent from the active list.
 */
@Singleton
class DefaultDataInitializer @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) {

    private val mutex = Mutex()

    /**
     * Initializes default categories and accounts.
     *
     * @param currency The default currency to use for starting accounts (defaults to INR).
     */
    suspend fun initialize(currency: Currency = Currency.INR) {
        mutex.withLock {
            initializeCategories()
            initializeAccounts(currency)
        }
    }

    /**
     * Inspects and inserts missing default categories.
     */
    suspend fun initializeCategories() {
        for (category in DefaultCategories.ALL) {
            val existing = categoryRepository.getCategoryById(category.id)
            if (existing == null) {
                categoryRepository.insertCategory(category)
            }
        }
    }

    /**
     * Inspects and inserts missing default accounts.
     */
    suspend fun initializeAccounts(currency: Currency = Currency.INR) {
        val defaultAccounts = DefaultAccounts.getDefaultAccounts(currency)
        for (account in defaultAccounts) {
            val existing = accountRepository.getAccountById(account.id)
            if (existing == null) {
                accountRepository.insertAccount(account)
            }
        }
    }
}

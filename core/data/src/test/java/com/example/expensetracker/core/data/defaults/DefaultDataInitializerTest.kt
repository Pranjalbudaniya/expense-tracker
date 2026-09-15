package com.example.expensetracker.core.data.defaults

import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class DefaultDataInitializerTest {

    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var initializer: DefaultDataInitializer

    @Before
    fun setUp() {
        categoryRepository = FakeCategoryRepository()
        accountRepository = FakeAccountRepository()
        initializer = DefaultDataInitializer(categoryRepository, accountRepository)
    }

    @Test
    fun firstInitialization_createsExpectedDefaults() = runTest {
        initializer.initialize()

        // Verify all default categories were created with expected properties
        for (defaultCat in DefaultCategories.ALL) {
            val stored = categoryRepository.getCategoryById(defaultCat.id)
            assertNotNull("Category ${defaultCat.id.value} should exist", stored)
            assertEquals(defaultCat.name, stored!!.name)
            assertEquals(defaultCat.iconKey, stored.iconKey)
            assertEquals(defaultCat.colorKey, stored.colorKey)
            assertTrue("Default category must have isDefault = true", stored.isDefault)
            assertFalse("Default category must not be archived", stored.isArchived)
        }

        // Verify all default accounts were created with expected properties
        for (defaultAcc in DefaultAccounts.ALL) {
            val stored = accountRepository.getAccountById(defaultAcc.id)
            assertNotNull("Account ${defaultAcc.id.value} should exist", stored)
            assertEquals(defaultAcc.name, stored!!.name)
            assertEquals(defaultAcc.type, stored.type)
            assertEquals(Currency.INR, stored.currency)
            assertTrue(stored.initialBalance.isZero)
            assertTrue(stored.currentBalance.isZero)
            assertFalse("Default account must not be archived", stored.isArchived)
        }
    }

    @Test
    fun runningInitializationTwice_doesNotDuplicateData() = runTest {
        initializer.initialize()

        val initialCategories = categoryRepository.getAllSnapshot()
        val initialAccounts = accountRepository.getAllSnapshot()

        // Run second and third times
        initializer.initialize()
        initializer.initialize()

        val subsequentCategories = categoryRepository.getAllSnapshot()
        val subsequentAccounts = accountRepository.getAllSnapshot()

        assertEquals(initialCategories.keys, subsequentCategories.keys)
        assertEquals(initialAccounts.keys, subsequentAccounts.keys)

        for ((id, originalCategory) in initialCategories) {
            assertEquals(originalCategory, subsequentCategories[id])
        }
        for ((id, originalAccount) in initialAccounts) {
            assertEquals(originalAccount, subsequentAccounts[id])
        }
    }

    @Test
    fun existingUserChanges_areNotOverwritten() = runTest {
        initializer.initialize()

        // User modifies category name and color
        val customFoodCategory = categoryRepository.getCategoryById(DefaultCategories.ID_FOOD)!!.copy(
            name = "Groceries & Dining",
            colorKey = "custom_purple_seed"
        )
        categoryRepository.updateCategory(customFoodCategory)

        // User modifies account name and balance
        val customCashAccount = accountRepository.getAccountById(DefaultAccounts.ID_CASH)!!.copy(
            name = "My Physical Cash",
            initialBalance = Money.of(BigDecimal("5000.00"), Currency.INR),
            currentBalance = Money.of(BigDecimal("5000.00"), Currency.INR)
        )
        accountRepository.updateAccount(customCashAccount)

        // Re-run initializer
        initializer.initialize()

        // Verify user changes are preserved
        val reloadedCategory = categoryRepository.getCategoryById(DefaultCategories.ID_FOOD)!!
        assertEquals("Groceries & Dining", reloadedCategory.name)
        assertEquals("custom_purple_seed", reloadedCategory.colorKey)

        val reloadedAccount = accountRepository.getAccountById(DefaultAccounts.ID_CASH)!!
        assertEquals("My Physical Cash", reloadedAccount.name)
        assertEquals(BigDecimal("5000.00"), reloadedAccount.initialBalance.amount)
    }

    @Test
    fun partialDefaultData_isCompletedSafely() = runTest {
        // Pre-insert only Food category and Cash account, with user modifications
        val customFood = DefaultCategories.EXPENSE_CATEGORIES.first { it.id == DefaultCategories.ID_FOOD }.copy(
            name = "Food Market",
            iconKey = "custom_icon"
        )
        categoryRepository.insertCategory(customFood)

        val customCash = DefaultAccounts.ALL.first { it.id == DefaultAccounts.ID_CASH }.copy(
            name = "Wallet Cash"
        )
        accountRepository.insertAccount(customCash)

        // Run initializer
        initializer.initialize()

        // Verify pre-existing item wasn't overwritten
        val foodAfter = categoryRepository.getCategoryById(DefaultCategories.ID_FOOD)!!
        assertEquals("Food Market", foodAfter.name)
        assertEquals("custom_icon", foodAfter.iconKey)

        val cashAfter = accountRepository.getAccountById(DefaultAccounts.ID_CASH)!!
        assertEquals("Wallet Cash", cashAfter.name)

        // Verify missing categories (e.g. Transport, Bills, Salary) were populated
        assertNotNull(categoryRepository.getCategoryById(DefaultCategories.ID_TRANSPORT))
        assertNotNull(categoryRepository.getCategoryById(DefaultCategories.ID_BILLS))
        assertNotNull(categoryRepository.getCategoryById(DefaultCategories.ID_SALARY))

        // Verify missing accounts (e.g. Bank, UPI, Wallet) were populated
        assertNotNull(accountRepository.getAccountById(DefaultAccounts.ID_BANK))
        assertNotNull(accountRepository.getAccountById(DefaultAccounts.ID_UPI))
        assertNotNull(accountRepository.getAccountById(DefaultAccounts.ID_WALLET))
    }

    @Test
    fun stableIds_preventDuplicates_evenWithSameDisplayName() = runTest {
        // User creates their own custom category with name "Food" but custom ID
        val customUserCategory = Category(
            id = EntityId("custom_user_food_id"),
            name = "Food",
            iconKey = "my_icon",
            colorKey = "my_color",
            isDefault = false,
            isArchived = false
        )
        categoryRepository.insertCategory(customUserCategory)

        // User creates custom account with name "Cash" but custom ID
        val customUserAccount = Account(
            id = EntityId("custom_user_cash_id"),
            name = "Cash",
            type = com.example.expensetracker.core.model.account.AccountType.CASH,
            currency = Currency.INR,
            initialBalance = Money.zero(Currency.INR),
            currentBalance = Money.zero(Currency.INR),
            isArchived = false
        )
        accountRepository.insertAccount(customUserAccount)

        // Run initializer
        initializer.initialize()

        // User's custom items still exist intact
        val storedUserCategory = categoryRepository.getCategoryById(EntityId("custom_user_food_id"))
        assertNotNull(storedUserCategory)
        assertEquals("Food", storedUserCategory!!.name)
        assertFalse(storedUserCategory.isDefault)

        val storedUserAccount = accountRepository.getAccountById(EntityId("custom_user_cash_id"))
        assertNotNull(storedUserAccount)
        assertEquals("Cash", storedUserAccount!!.name)

        // System default items also exist with their stable IDs
        val defaultFood = categoryRepository.getCategoryById(DefaultCategories.ID_FOOD)
        assertNotNull(defaultFood)
        assertTrue(defaultFood!!.isDefault)

        val defaultCash = accountRepository.getAccountById(DefaultAccounts.ID_CASH)
        assertNotNull(defaultCash)
    }

    @Test
    fun archivedDefaultCategoryAndAccount_areNotRecreatedAsActive() = runTest {
        initializer.initialize()

        // Archive Food category and Cash account
        categoryRepository.archiveCategory(DefaultCategories.ID_FOOD)
        accountRepository.archiveAccount(DefaultAccounts.ID_CASH)

        assertTrue(categoryRepository.getCategoryById(DefaultCategories.ID_FOOD)!!.isArchived)
        assertTrue(accountRepository.getAccountById(DefaultAccounts.ID_CASH)!!.isArchived)

        // Re-run initializer
        initializer.initialize()

        // Food and Cash must still be archived, NOT resurrected as active
        val foodAfter = categoryRepository.getCategoryById(DefaultCategories.ID_FOOD)!!
        assertTrue("Archived category must remain archived", foodAfter.isArchived)

        val cashAfter = accountRepository.getAccountById(DefaultAccounts.ID_CASH)!!
        assertTrue("Archived account must remain archived", cashAfter.isArchived)
    }
}

// --- Fakes for Testing ---

private class FakeCategoryRepository : CategoryRepository {
    private val categories = mutableMapOf<EntityId, Category>()
    private val flow = MutableStateFlow<List<Category>>(emptyList())

    fun getAllSnapshot(): Map<EntityId, Category> = HashMap(categories)

    private fun updateFlow() {
        flow.value = categories.values.toList()
    }

    override fun getActiveCategories(): Flow<List<Category>> {
        return flow.map { list -> list.filter { !it.isArchived } }
    }

    override fun getAllCategories(): Flow<List<Category>> = flow

    override fun getCategory(id: EntityId): Flow<Category?> {
        return flow.map { list -> list.firstOrNull { it.id == id } }
    }

    override suspend fun getCategoryById(id: EntityId): Category? {
        return categories[id]
    }

    override suspend fun insertCategory(category: Category) {
        categories[category.id] = category
        updateFlow()
    }

    override suspend fun updateCategory(category: Category) {
        categories[category.id] = category
        updateFlow()
    }

    override suspend fun updateCategoryOrder(categories: List<Category>) {
        for (category in categories) {
            this.categories[category.id] = category
        }
        updateFlow()
    }

    override suspend fun archiveCategory(id: EntityId) {
        categories[id]?.let {
            categories[id] = it.copy(isArchived = true)
            updateFlow()
        }
    }

    override suspend fun deleteCategory(category: Category) {
        categories.remove(category.id)
        updateFlow()
    }
}

private class FakeAccountRepository : AccountRepository {
    private val accounts = mutableMapOf<EntityId, Account>()
    private val flow = MutableStateFlow<List<Account>>(emptyList())

    fun getAllSnapshot(): Map<EntityId, Account> = HashMap(accounts)

    private fun updateFlow() {
        flow.value = accounts.values.toList()
    }

    override fun getActiveAccounts(): Flow<List<Account>> {
        return flow.map { list -> list.filter { !it.isArchived } }
    }

    override fun getAllAccounts(): Flow<List<Account>> = flow

    override fun getAccount(id: EntityId): Flow<Account?> {
        return flow.map { list -> list.firstOrNull { it.id == id } }
    }

    override suspend fun getAccountById(id: EntityId): Account? {
        return accounts[id]
    }

    override suspend fun insertAccount(account: Account) {
        accounts[account.id] = account
        updateFlow()
    }

    override suspend fun updateAccount(account: Account) {
        accounts[account.id] = account
        updateFlow()
    }

    override suspend fun archiveAccount(id: EntityId) {
        accounts[id]?.let {
            accounts[id] = it.copy(isArchived = true)
            updateFlow()
        }
    }

    override suspend fun deleteAccount(account: Account) {
        accounts.remove(account.id)
        updateFlow()
    }
}

package com.example.expensetracker.feature.categories

import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.category.CategoryType
import com.example.expensetracker.core.model.common.EntityId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeCategoryRepository
    private lateinit var viewModel: CategoriesViewModel

    private val catFood = Category(
        id = EntityId("cat-food"),
        name = "Food",
        iconKey = "restaurant",
        colorKey = "category_orange",
        isDefault = true,
        isArchived = false,
        type = CategoryType.EXPENSE,
        orderIndex = 0
    )

    private val catTransport = Category(
        id = EntityId("cat-transport"),
        name = "Transport",
        iconKey = "directions_bus",
        colorKey = "category_blue",
        isDefault = true,
        isArchived = false,
        type = CategoryType.EXPENSE,
        orderIndex = 1
    )

    private val catSalary = Category(
        id = EntityId("cat-salary"),
        name = "Salary",
        iconKey = "payments",
        colorKey = "category_green",
        isDefault = true,
        isArchived = false,
        type = CategoryType.INCOME,
        orderIndex = 0
    )

    private val catCustom = Category(
        id = EntityId("cat-custom"),
        name = "Freelance",
        iconKey = "laptop",
        colorKey = "category_purple",
        isDefault = false,
        isArchived = false,
        type = CategoryType.INCOME,
        orderIndex = 1
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeCategoryRepository(
            listOf(catFood, catTransport, catSalary, catCustom)
        )
        viewModel = CategoriesViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- CategoryUseCases Tests ---

    @Test
    fun validateName_emptyOrBlankFails() {
        val result1 = CategoryUseCases.validateName("", null, emptyList(), CategoryType.EXPENSE)
        assertFalse(result1.isValid)
        assertEquals("Category name cannot be empty", result1.errorMessage)

        val result2 = CategoryUseCases.validateName("   ", null, emptyList(), CategoryType.EXPENSE)
        assertFalse(result2.isValid)
    }

    @Test
    fun validateName_duplicateSameTypeFails() {
        val existing = listOf(catFood)
        val result = CategoryUseCases.validateName("food", null, existing, CategoryType.EXPENSE)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("already exists") == true)
    }

    @Test
    fun validateName_duplicateDifferentTypeAllowed() {
        val existing = listOf(catFood) // EXPENSE
        val result = CategoryUseCases.validateName("Food", null, existing, CategoryType.INCOME)
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun validateName_duplicateWithBothTypeFails() {
        val catBoth = catFood.copy(type = CategoryType.BOTH)
        val existing = listOf(catBoth)
        val result = CategoryUseCases.validateName("food", null, existing, CategoryType.EXPENSE)
        assertFalse(result.isValid)
    }

    @Test
    fun validateName_editingSameCategoryAllowsSameName() {
        val existing = listOf(catFood, catTransport)
        val result = CategoryUseCases.validateName("Food", catFood.id, existing, CategoryType.EXPENSE)
        assertTrue(result.isValid)
    }

    @Test
    fun reorder_movesItemAndRecalculatesSequentialIndices() {
        val items = listOf(catFood, catTransport, catSalary)
        val reordered = CategoryUseCases.reorder(items, 0, 2)
        assertEquals(listOf("Transport", "Salary", "Food"), reordered.map { it.name })
        assertEquals(listOf(0, 1, 2), reordered.map { it.orderIndex })
    }

    @Test
    fun moveUpAndDown_updatesOrderCorrectly() {
        val items = listOf(catFood, catTransport)
        val movedUp = CategoryUseCases.moveUp(items, catTransport.id)
        assertEquals("Transport", movedUp[0].name)
        assertEquals(0, movedUp[0].orderIndex)
        assertEquals("Food", movedUp[1].name)
        assertEquals(1, movedUp[1].orderIndex)

        val movedDown = CategoryUseCases.moveDown(movedUp, catTransport.id)
        assertEquals("Food", movedDown[0].name)
        assertEquals("Transport", movedDown[1].name)
    }

    // --- CategoriesViewModel Tests ---

    @Test
    fun initialUiState_loadsAndGroupsCategories() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(CategoryType.EXPENSE, state.selectedTab)
        assertEquals(2, state.expenseCategories.size)
        assertEquals("Food", state.expenseCategories[0].category.name)
        assertEquals("Transport", state.expenseCategories[1].category.name)
        assertEquals(2, state.incomeCategories.size)
        assertEquals("Salary", state.incomeCategories[0].category.name)
        assertEquals("Freelance", state.incomeCategories[1].category.name)

        job.cancel()
    }

    @Test
    fun selectTab_switchesTab() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.selectTab(CategoryType.INCOME)
        advanceUntilIdle()

        assertEquals(CategoryType.INCOME, viewModel.uiState.value.selectedTab)
        job.cancel()
    }

    @Test
    fun openCreateForm_initializesWithSelectedTabDefaults() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.openCreateForm(CategoryType.EXPENSE)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isFormOpen)
        assertNotNull(state.formState)
        assertEquals(CategoryType.EXPENSE, state.formState?.type)
        assertEquals("restaurant", state.formState?.iconKey)
        assertFalse(state.formState!!.isEditing)

        job.cancel()
    }

    @Test
    fun openEditForm_populatesFromCategory() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.openEditForm(catFood)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isFormOpen)
        assertEquals("Food", state.formState?.name)
        assertEquals(catFood.id, state.formState?.id)
        assertTrue(state.formState!!.isDefault)
        assertTrue(state.formState!!.isEditing)

        job.cancel()
    }

    @Test
    fun saveCategory_createsNewCategoryWithNextOrderIndex() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.openCreateForm(CategoryType.EXPENSE)
        viewModel.onFormNameChange("Subscriptions")
        viewModel.onFormIconChange("receipt_long")
        viewModel.onFormColorChange("category_red")

        viewModel.saveCategory()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFormOpen)
        val created = fakeRepository.categories.values.firstOrNull { it.name == "Subscriptions" }
        assertNotNull(created)
        assertEquals(CategoryType.EXPENSE, created?.type)
        assertEquals("receipt_long", created?.iconKey)
        assertEquals("category_red", created?.colorKey)
        assertFalse(created!!.isDefault)
        assertEquals(2, created.orderIndex)

        job.cancel()
    }

    @Test
    fun saveCategory_updatesExistingCategory() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.openEditForm(catCustom)
        viewModel.onFormNameChange("Contract Work")
        viewModel.onFormColorChange("category_teal")

        viewModel.saveCategory()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFormOpen)
        val updated = fakeRepository.getCategoryById(catCustom.id)
        assertNotNull(updated)
        assertEquals("Contract Work", updated?.name)
        assertEquals("category_teal", updated?.colorKey)

        job.cancel()
    }

    @Test
    fun saveCategory_duplicateValidationBlocksSave() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        viewModel.openCreateForm(CategoryType.EXPENSE)
        viewModel.onFormNameChange("Food") // duplicate
        viewModel.saveCategory()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isFormOpen)
        assertNotNull(viewModel.uiState.value.formState?.nameError)

        job.cancel()
    }

    @Test
    fun reorderCategories_updatesRepositoryOrder() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        // Move Food down
        viewModel.moveCategoryDown(catFood.id)
        advanceUntilIdle()

        val food = fakeRepository.getCategoryById(catFood.id)!!
        val transport = fakeRepository.getCategoryById(catTransport.id)!!
        assertEquals(1, food.orderIndex)
        assertEquals(0, transport.orderIndex)

        job.cancel()
    }

    @Test
    fun archiveAndRestoreCategory_worksCorrectly() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        // Request archive
        viewModel.requestArchiveCategory(catFood)
        advanceUntilIdle()
        assertEquals(catFood, viewModel.uiState.value.categoryToArchive)

        viewModel.confirmArchiveCategory()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.categoryToArchive)
        assertTrue(fakeRepository.getCategoryById(catFood.id)!!.isArchived)

        // Restore
        val archivedFood = fakeRepository.getCategoryById(catFood.id)!!
        viewModel.requestUnarchiveCategory(archivedFood)
        advanceUntilIdle()
        viewModel.confirmUnarchiveCategory()
        advanceUntilIdle()

        assertFalse(fakeRepository.getCategoryById(catFood.id)!!.isArchived)

        job.cancel()
    }

    @Test
    fun deleteCategory_defaultCategoryBlocked_customAllowed() = runTest {
        val job = viewModel.uiState.launchIn(this)
        advanceUntilIdle()

        // Default category cannot be deleted
        viewModel.requestDeleteCategory(catFood)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.categoryToDelete)

        // Custom category can be deleted
        viewModel.requestDeleteCategory(catCustom)
        advanceUntilIdle()
        assertEquals(catCustom, viewModel.uiState.value.categoryToDelete)

        viewModel.confirmDeleteCategory()
        advanceUntilIdle()

        assertNull(fakeRepository.getCategoryById(catCustom.id))

        job.cancel()
    }
}

// --- Fake Category Repository ---

private class FakeCategoryRepository(
    initialCategories: List<Category> = emptyList()
) : CategoryRepository {

    val categories = mutableMapOf<EntityId, Category>()
    private val flow = MutableStateFlow<List<Category>>(emptyList())

    init {
        for (cat in initialCategories) {
            categories[cat.id] = cat
        }
        updateFlow()
    }

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

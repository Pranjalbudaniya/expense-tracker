package com.example.expensetracker.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.category.CategoryType
import com.example.expensetracker.core.model.common.EntityId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private data class LocalCategoriesState(
    val selectedTab: CategoryType = CategoryType.EXPENSE,
    val showArchived: Boolean = false,
    val formState: CategoryFormData? = null,
    val isFormOpen: Boolean = false,
    val categoryToArchive: Category? = null,
    val categoryToUnarchive: Category? = null,
    val categoryToDelete: Category? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel managing categories, form state, reordering, and archiving operations.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val localState = MutableStateFlow(LocalCategoriesState())

    val uiState: StateFlow<CategoriesUiState> = combine(
        categoryRepository.getAllCategories(),
        localState
    ) { allCategories, local ->
        val expenseList = CategoryUseCases.filterByType(allCategories, CategoryType.EXPENSE)
        val incomeList = CategoryUseCases.filterByType(allCategories, CategoryType.INCOME)
        val archivedList = CategoryUseCases.filterArchived(allCategories)

        val expenseUiItems = expenseList.mapIndexed { index, cat ->
            CategoryItemUi(
                category = cat,
                isFirst = index == 0,
                isLast = index == expenseList.lastIndex
            )
        }

        val incomeUiItems = incomeList.mapIndexed { index, cat ->
            CategoryItemUi(
                category = cat,
                isFirst = index == 0,
                isLast = index == incomeList.lastIndex
            )
        }

        CategoriesUiState(
            isLoading = false,
            selectedTab = local.selectedTab,
            expenseCategories = expenseUiItems,
            incomeCategories = incomeUiItems,
            archivedCategories = archivedList,
            showArchived = local.showArchived,
            isFormOpen = local.isFormOpen,
            formState = local.formState,
            categoryToArchive = local.categoryToArchive,
            categoryToUnarchive = local.categoryToUnarchive,
            categoryToDelete = local.categoryToDelete,
            errorMessage = local.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategoriesUiState(isLoading = true)
    )

    fun selectTab(tab: CategoryType) {
        localState.update { it.copy(selectedTab = tab) }
    }

    fun toggleShowArchived() {
        localState.update { it.copy(showArchived = !it.showArchived) }
    }

    fun openCreateForm(defaultType: CategoryType = localState.value.selectedTab) {
        val initialIcon = if (defaultType == CategoryType.INCOME) "payments" else "restaurant"
        val initialColor = if (defaultType == CategoryType.INCOME) "category_green" else "category_orange"
        val form = CategoryFormData(
            type = defaultType,
            iconKey = initialIcon,
            colorKey = initialColor
        )
        localState.update {
            it.copy(
                formState = form,
                isFormOpen = true
            )
        }
    }

    fun openEditForm(category: Category) {
        val form = CategoryFormData(
            id = category.id,
            name = category.name,
            type = category.type,
            iconKey = category.iconKey,
            colorKey = category.colorKey,
            isDefault = category.isDefault,
            isArchived = category.isArchived
        )
        localState.update {
            it.copy(
                formState = form,
                isFormOpen = true
            )
        }
    }

    fun closeForm() {
        localState.update {
            it.copy(
                isFormOpen = false,
                formState = null
            )
        }
    }

    fun onFormNameChange(name: String) {
        localState.update { state ->
            state.copy(
                formState = state.formState?.copy(
                    name = name,
                    nameError = null
                )
            )
        }
    }

    fun onFormTypeChange(type: CategoryType) {
        localState.update { state ->
            state.copy(
                formState = state.formState?.copy(
                    type = type,
                    nameError = null
                )
            )
        }
    }

    fun onFormIconChange(iconKey: String) {
        localState.update { state ->
            state.copy(
                formState = state.formState?.copy(iconKey = iconKey)
            )
        }
    }

    fun onFormColorChange(colorKey: String) {
        localState.update { state ->
            state.copy(
                formState = state.formState?.copy(colorKey = colorKey)
            )
        }
    }

    fun onFormArchivedChange(isArchived: Boolean) {
        localState.update { state ->
            state.copy(
                formState = state.formState?.copy(isArchived = isArchived)
            )
        }
    }

    fun saveCategory() {
        val form = localState.value.formState ?: return

        viewModelScope.launch {
            val existingList = categoryRepository.getAllCategories().first()

            val validation = CategoryUseCases.validateName(
                name = form.name,
                currentCategoryId = form.id,
                existingCategories = existingList,
                type = form.type
            )

            if (!validation.isValid) {
                localState.update { state ->
                    state.copy(formState = form.copy(nameError = validation.errorMessage))
                }
                return@launch
            }

            if (form.isEditing && form.id != null) {
                val existing = categoryRepository.getCategoryById(form.id)
                if (existing != null) {
                    val updated = existing.copy(
                        name = form.name.trim(),
                        type = form.type,
                        iconKey = form.iconKey,
                        colorKey = form.colorKey,
                        isArchived = form.isArchived
                    )
                    categoryRepository.updateCategory(updated)
                }
            } else {
                val relevant = existingList.filter { it.type == form.type || it.type == CategoryType.BOTH }
                val nextOrder = (relevant.maxOfOrNull { it.orderIndex } ?: -1) + 1

                val newCat = Category(
                    id = EntityId(UUID.randomUUID().toString()),
                    name = form.name.trim(),
                    iconKey = form.iconKey,
                    colorKey = form.colorKey,
                    isDefault = false,
                    isArchived = false,
                    type = form.type,
                    orderIndex = nextOrder
                )
                categoryRepository.insertCategory(newCat)
            }

            closeForm()
        }
    }

    fun moveCategoryUp(categoryId: EntityId) {
        viewModelScope.launch {
            val state = uiState.value
            val list = if (state.selectedTab == CategoryType.INCOME) {
                state.incomeCategories.map { it.category }
            } else {
                state.expenseCategories.map { it.category }
            }

            val reordered = CategoryUseCases.moveUp(list, categoryId)
            if (reordered != list) {
                categoryRepository.updateCategoryOrder(reordered)
            }
        }
    }

    fun moveCategoryDown(categoryId: EntityId) {
        viewModelScope.launch {
            val state = uiState.value
            val list = if (state.selectedTab == CategoryType.INCOME) {
                state.incomeCategories.map { it.category }
            } else {
                state.expenseCategories.map { it.category }
            }

            val reordered = CategoryUseCases.moveDown(list, categoryId)
            if (reordered != list) {
                categoryRepository.updateCategoryOrder(reordered)
            }
        }
    }

    fun requestArchiveCategory(category: Category) {
        localState.update { it.copy(categoryToArchive = category) }
    }

    fun confirmArchiveCategory() {
        val cat = localState.value.categoryToArchive ?: return
        viewModelScope.launch {
            categoryRepository.archiveCategory(cat.id)
            localState.update { it.copy(categoryToArchive = null) }
        }
    }

    fun dismissArchiveDialog() {
        localState.update { it.copy(categoryToArchive = null) }
    }

    fun requestUnarchiveCategory(category: Category) {
        localState.update { it.copy(categoryToUnarchive = category) }
    }

    fun confirmUnarchiveCategory() {
        val cat = localState.value.categoryToUnarchive ?: return
        viewModelScope.launch {
            categoryRepository.updateCategory(cat.copy(isArchived = false))
            localState.update { it.copy(categoryToUnarchive = null) }
        }
    }

    fun dismissUnarchiveDialog() {
        localState.update { it.copy(categoryToUnarchive = null) }
    }

    fun requestDeleteCategory(category: Category) {
        if (category.isDefault) {
            localState.update {
                it.copy(errorMessage = "Default categories cannot be deleted, but you can archive them.")
            }
            return
        }
        localState.update { it.copy(categoryToDelete = category) }
    }

    fun confirmDeleteCategory() {
        val cat = localState.value.categoryToDelete ?: return
        viewModelScope.launch {
            categoryRepository.deleteCategory(cat)
            localState.update { it.copy(categoryToDelete = null) }
        }
    }

    fun dismissDeleteDialog() {
        localState.update { it.copy(categoryToDelete = null) }
    }

    fun clearError() {
        localState.update { it.copy(errorMessage = null) }
    }
}

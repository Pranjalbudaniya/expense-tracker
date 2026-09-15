package com.example.expensetracker.feature.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.category.CategoryType
import com.example.expensetracker.core.model.common.EntityId

/**
 * Representation of an icon option for category selection.
 */
data class CategoryIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Representation of a theme-safe color swatch option for category selection.
 */
data class CategoryColorOption(
    val key: String,
    val label: String,
    val swatchColor: Color
)

/**
 * Curated list of category icons and color swatches.
 */
object CategoryVisualCatalog {

    val ICONS: List<CategoryIconOption> = listOf(
        CategoryIconOption("restaurant", "Dining", Icons.Default.Favorite),
        CategoryIconOption("shopping_bag", "Shopping", Icons.Default.ShoppingCart),
        CategoryIconOption("directions_bus", "Transport", Icons.Default.Place),
        CategoryIconOption("receipt_long", "Bills", Icons.Default.Notifications),
        CategoryIconOption("school", "Education", Icons.Default.Star),
        CategoryIconOption("movie", "Entertainment", Icons.Default.PlayArrow),
        CategoryIconOption("fitness_center", "Health", Icons.Default.Favorite),
        CategoryIconOption("flight", "Travel", Icons.AutoMirrored.Filled.Send),
        CategoryIconOption("person", "Personal", Icons.Default.Person),
        CategoryIconOption("payments", "Salary", Icons.Default.AccountBox),
        CategoryIconOption("account_balance_wallet", "Wallet", Icons.Default.AccountBox),
        CategoryIconOption("workspace_premium", "Awards", Icons.Default.Star),
        CategoryIconOption("redeem", "Gift", Icons.Default.ThumbUp),
        CategoryIconOption("home", "Housing", Icons.Default.Home),
        CategoryIconOption("build", "Services", Icons.Default.Build),
        CategoryIconOption("phone", "Phone", Icons.Default.Call),
        CategoryIconOption("email", "Mail", Icons.Default.Email),
        CategoryIconOption("date_range", "Events", Icons.Default.DateRange),
        CategoryIconOption("more_horiz", "Other", Icons.Default.MoreVert)
    )

    val COLORS: List<CategoryColorOption> = listOf(
        CategoryColorOption("category_orange", "Orange", Color(0xFFF97316)),
        CategoryColorOption("category_blue", "Blue", Color(0xFF3B82F6)),
        CategoryColorOption("category_purple", "Purple", Color(0xFFA855F7)),
        CategoryColorOption("category_red", "Red", Color(0xFFEF4444)),
        CategoryColorOption("category_amber", "Amber", Color(0xFFF59E0B)),
        CategoryColorOption("category_pink", "Pink", Color(0xFFEC4899)),
        CategoryColorOption("category_teal", "Teal", Color(0xFF14B8A6)),
        CategoryColorOption("category_cyan", "Cyan", Color(0xFF06B6D4)),
        CategoryColorOption("category_indigo", "Indigo", Color(0xFF6366F1)),
        CategoryColorOption("category_green", "Green", Color(0xFF22C55E)),
        CategoryColorOption("category_emerald", "Emerald", Color(0xFF10B981)),
        CategoryColorOption("category_yellow", "Yellow", Color(0xFFEAB308)),
        CategoryColorOption("category_deep_purple", "Deep Purple", Color(0xFF7C3AED)),
        CategoryColorOption("category_light_green", "Lime", Color(0xFF84CC16)),
        CategoryColorOption("category_gray", "Gray", Color(0xFF64748B))
    )

    fun getIcon(key: String): ImageVector {
        return ICONS.firstOrNull { it.key.equals(key, ignoreCase = true) }?.icon
            ?: Icons.Default.Star
    }

    fun getColor(key: String): Color {
        return COLORS.firstOrNull { it.key.equals(key, ignoreCase = true) }?.swatchColor
            ?: Color(0xFF3B82F6)
    }
}

/**
 * Form state for creating or editing a category.
 */
data class CategoryFormData(
    val id: EntityId? = null,
    val name: String = "",
    val nameError: String? = null,
    val type: CategoryType = CategoryType.EXPENSE,
    val iconKey: String = "restaurant",
    val colorKey: String = "category_orange",
    val isDefault: Boolean = false,
    val isArchived: Boolean = false
) {
    val isEditing: Boolean get() = id != null
}

/**
 * UI wrapper for a single category row, containing reordering enablement flags.
 */
data class CategoryItemUi(
    val category: Category,
    val isFirst: Boolean,
    val isLast: Boolean,
    val canReorder: Boolean = true
)

/**
 * Root UI state for the Categories feature.
 */
data class CategoriesUiState(
    val isLoading: Boolean = false,
    val selectedTab: CategoryType = CategoryType.EXPENSE,
    val expenseCategories: List<CategoryItemUi> = emptyList(),
    val incomeCategories: List<CategoryItemUi> = emptyList(),
    val archivedCategories: List<Category> = emptyList(),
    val showArchived: Boolean = false,
    val isFormOpen: Boolean = false,
    val formState: CategoryFormData? = null,
    val categoryToArchive: Category? = null,
    val categoryToUnarchive: Category? = null,
    val categoryToDelete: Category? = null,
    val errorMessage: String? = null
)

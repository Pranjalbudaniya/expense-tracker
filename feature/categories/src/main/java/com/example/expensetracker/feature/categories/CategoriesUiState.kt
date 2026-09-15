package com.example.expensetracker.feature.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
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
        CategoryIconOption("restaurant", "Dining", Icons.Default.Restaurant),
        CategoryIconOption("shopping_bag", "Shopping", Icons.Default.ShoppingBag),
        CategoryIconOption("directions_bus", "Transport", Icons.Default.DirectionsBus),
        CategoryIconOption("receipt_long", "Bills", Icons.AutoMirrored.Filled.ReceiptLong),
        CategoryIconOption("school", "Education", Icons.Default.School),
        CategoryIconOption("movie", "Entertainment", Icons.Default.Movie),
        CategoryIconOption("fitness_center", "Health", Icons.Default.FitnessCenter),
        CategoryIconOption("flight", "Travel", Icons.Default.Flight),
        CategoryIconOption("person", "Personal", Icons.Default.Person),
        CategoryIconOption("payments", "Salary", Icons.Default.Payments),
        CategoryIconOption("account_balance_wallet", "Wallet", Icons.Default.AccountBalanceWallet),
        CategoryIconOption("workspace_premium", "Awards", Icons.Default.WorkspacePremium),
        CategoryIconOption("redeem", "Gift", Icons.Default.CardGiftcard),
        CategoryIconOption("attach_money", "Other Income", Icons.Default.AttachMoney),
        CategoryIconOption("home", "Housing", Icons.Default.Home),
        CategoryIconOption("build", "Services", Icons.Default.Build),
        CategoryIconOption("phone", "Phone", Icons.Default.Phone),
        CategoryIconOption("email", "Mail", Icons.Default.Email),
        CategoryIconOption("date_range", "Events", Icons.Default.DateRange),
        CategoryIconOption("more_horiz", "Other", Icons.Default.MoreHoriz)
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

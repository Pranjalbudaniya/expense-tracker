package com.example.expensetracker.core.data.defaults

import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.category.CategoryType
import com.example.expensetracker.core.model.common.EntityId

/**
 * Sensible default categories for an everyday and student expense tracker.
 */
object DefaultCategories {

    // Expense Category IDs
    val ID_FOOD = EntityId("cat_default_food")
    val ID_TRANSPORT = EntityId("cat_default_transport")
    val ID_SHOPPING = EntityId("cat_default_shopping")
    val ID_BILLS = EntityId("cat_default_bills")
    val ID_EDUCATION = EntityId("cat_default_education")
    val ID_ENTERTAINMENT = EntityId("cat_default_entertainment")
    val ID_HEALTH = EntityId("cat_default_health")
    val ID_TRAVEL = EntityId("cat_default_travel")
    val ID_PERSONAL = EntityId("cat_default_personal")
    val ID_OTHER = EntityId("cat_default_other")

    // Income Category IDs
    val ID_SALARY = EntityId("cat_default_salary")
    val ID_POCKET_MONEY = EntityId("cat_default_pocket_money")
    val ID_SCHOLARSHIP = EntityId("cat_default_scholarship")
    val ID_GIFT = EntityId("cat_default_gift")
    val ID_OTHER_INCOME = EntityId("cat_default_other_income")

    val EXPENSE_CATEGORIES: List<Category> = listOf(
        Category(
            id = ID_FOOD,
            name = "Food",
            iconKey = "restaurant",
            colorKey = "category_orange",
            isDefault = true,
            isArchived = false,
            type = CategoryType.EXPENSE,
            orderIndex = 0
        ),
        Category(
            id = ID_TRANSPORT,
            name = "Transport",
            iconKey = "directions_bus",
            colorKey = "category_blue",
            isDefault = true,
            isArchived = false,
            type = CategoryType.EXPENSE,
            orderIndex = 1
        ),
        Category(
            id = ID_SHOPPING,
            name = "Shopping",
            iconKey = "shopping_bag",
            colorKey = "category_purple",
            isDefault = true,
            isArchived = false,
            type = CategoryType.EXPENSE,
            orderIndex = 2
        ),
        Category(
            id = ID_BILLS,
            name = "Bills",
            iconKey = "receipt_long",
            colorKey = "category_red",
            isDefault = true,
            isArchived = false,
            type = CategoryType.EXPENSE,
            orderIndex = 3
        ),
        Category(
            id = ID_EDUCATION,
            name = "Education",
            iconKey = "school",
            colorKey = "category_amber",
            isDefault = true,
            isArchived = false,
            type = CategoryType.EXPENSE,
            orderIndex = 4
        ),
        Category(
            id = ID_ENTERTAINMENT,
            name = "Entertainment",
            iconKey = "movie",
            colorKey = "category_pink",
            isDefault = true,
            isArchived = false,
            type = CategoryType.EXPENSE,
            orderIndex = 5
        ),
        Category(
            id = ID_HEALTH,
            name = "Health",
            iconKey = "fitness_center",
            colorKey = "category_teal",
            isDefault = true,
            isArchived = false,
            type = CategoryType.EXPENSE,
            orderIndex = 6
        ),
        Category(
            id = ID_TRAVEL,
            name = "Travel",
            iconKey = "flight",
            colorKey = "category_cyan",
            isDefault = true,
            isArchived = false,
            type = CategoryType.EXPENSE,
            orderIndex = 7
        ),
        Category(
            id = ID_PERSONAL,
            name = "Personal",
            iconKey = "person",
            colorKey = "category_indigo",
            isDefault = true,
            isArchived = false,
            type = CategoryType.EXPENSE,
            orderIndex = 8
        ),
        Category(
            id = ID_OTHER,
            name = "Other",
            iconKey = "more_horiz",
            colorKey = "category_gray",
            isDefault = true,
            isArchived = false,
            type = CategoryType.EXPENSE,
            orderIndex = 9
        )
    )

    val INCOME_CATEGORIES: List<Category> = listOf(
        Category(
            id = ID_SALARY,
            name = "Salary",
            iconKey = "payments",
            colorKey = "category_green",
            isDefault = true,
            isArchived = false,
            type = CategoryType.INCOME,
            orderIndex = 0
        ),
        Category(
            id = ID_POCKET_MONEY,
            name = "Pocket Money",
            iconKey = "account_balance_wallet",
            colorKey = "category_emerald",
            isDefault = true,
            isArchived = false,
            type = CategoryType.INCOME,
            orderIndex = 1
        ),
        Category(
            id = ID_SCHOLARSHIP,
            name = "Scholarship",
            iconKey = "workspace_premium",
            colorKey = "category_yellow",
            isDefault = true,
            isArchived = false,
            type = CategoryType.INCOME,
            orderIndex = 2
        ),
        Category(
            id = ID_GIFT,
            name = "Gift",
            iconKey = "redeem",
            colorKey = "category_deep_purple",
            isDefault = true,
            isArchived = false,
            type = CategoryType.INCOME,
            orderIndex = 3
        ),
        Category(
            id = ID_OTHER_INCOME,
            name = "Other Income",
            iconKey = "attach_money",
            colorKey = "category_light_green",
            isDefault = true,
            isArchived = false,
            type = CategoryType.INCOME,
            orderIndex = 4
        )
    )

    val ALL: List<Category> = EXPENSE_CATEGORIES + INCOME_CATEGORIES
}

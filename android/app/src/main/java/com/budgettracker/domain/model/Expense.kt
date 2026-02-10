package com.budgettracker.domain.model

data class Expense(
    val id: String,
    val userId: String,
    val amount: Double,
    val category: Category,
    val note: String?,
    val expenseDate: String,
    val createdAt: String,
    val updatedAt: String,
)

enum class Category {
    FOOD, ENTERTAINMENT, TRAVEL, BILLS, SHOPPING, OTHER;

    companion object {
        fun fromString(value: String): Category =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }

    fun displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }
}

data class ExpenseSummary(
    val total: Double,
    val count: Int,
    val byCategory: List<CategorySummary>,
)

data class CategorySummary(
    val category: String,
    val total: Double,
    val count: Int,
)

data class DailyStatus(
    val submitted: Boolean,
    val date: String,
)

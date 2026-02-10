package com.budgettracker.data.remote.dto

import com.budgettracker.data.local.ExpenseEntity
import com.budgettracker.domain.model.*
import com.google.gson.annotations.SerializedName

data class ExpenseResponse(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val amount: String,
    val category: String,
    val note: String?,
    @SerializedName("expense_date") val expenseDate: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
) {
    fun toDomain() = Expense(
        id = id, userId = userId,
        amount = amount.toDoubleOrNull() ?: 0.0,
        category = Category.fromString(category),
        note = note, expenseDate = expenseDate,
        createdAt = createdAt, updatedAt = updatedAt,
    )

    fun toEntity() = ExpenseEntity(
        id = id, userId = userId,
        amount = amount.toDoubleOrNull() ?: 0.0,
        category = category, note = note,
        expenseDate = expenseDate,
        createdAt = createdAt, updatedAt = updatedAt,
        synced = true,
    )
}

data class CreateExpenseRequest(
    val amount: Double,
    val category: String,
    val note: String?,
    @SerializedName("expense_date") val expenseDate: String,
)

data class ExpenseSummaryResponse(
    val total: String,
    val count: Int,
    @SerializedName("by_category") val byCategory: List<CategorySummaryResponse>,
) {
    fun toDomain() = ExpenseSummary(
        total = total.toDoubleOrNull() ?: 0.0,
        count = count,
        byCategory = byCategory.map { it.toDomain() },
    )
}

data class CategorySummaryResponse(
    val category: String,
    val total: String,
    val count: Int,
) {
    fun toDomain() = CategorySummary(category, total.toDoubleOrNull() ?: 0.0, count)
}

data class DailyStatusResponse(
    val submitted: Boolean,
    val date: String,
) {
    fun toDomain() = DailyStatus(submitted, date)
}

data class SubmitDayRequest(val date: String?)

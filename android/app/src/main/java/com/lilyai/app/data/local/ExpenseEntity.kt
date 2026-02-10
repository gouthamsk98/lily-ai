package com.lilyai.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val amount: Double,
    val category: String,
    val note: String?,
    val expenseDate: String,
    val createdAt: String,
    val updatedAt: String,
    val synced: Boolean = true,
)

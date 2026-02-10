package com.lilyai.app.domain.repository

import com.lilyai.app.data.remote.dto.EffectiveBudgetResponse
import com.lilyai.app.domain.model.*

interface ExpenseRepository {
    suspend fun createExpense(amount: Double, category: Category, note: String?, date: String): Expense
    suspend fun getExpenses(startDate: String? = null, endDate: String? = null, category: Category? = null): List<Expense>
    suspend fun deleteExpense(id: String)
    suspend fun getDailySummary(date: String? = null): ExpenseSummary
    suspend fun getWeeklySummary(date: String? = null): ExpenseSummary
    suspend fun getMonthlySummary(date: String? = null): ExpenseSummary
    suspend fun getDailyStatus(): DailyStatus
    suspend fun submitDay(date: String? = null)
    suspend fun syncPendingExpenses()
    suspend fun getBudget(): EffectiveBudgetResponse
    suspend fun setBudget(dailyBudget: Double): Unit
}

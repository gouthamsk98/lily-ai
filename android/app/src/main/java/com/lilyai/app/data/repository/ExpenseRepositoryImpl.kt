package com.lilyai.app.data.repository

import com.lilyai.app.data.local.ExpenseDao
import com.lilyai.app.data.local.ExpenseEntity
import com.lilyai.app.data.remote.ApiService
import com.lilyai.app.data.remote.dto.CreateExpenseRequest
import com.lilyai.app.data.remote.dto.SubmitDayRequest
import com.lilyai.app.domain.model.*
import com.lilyai.app.domain.repository.ExpenseRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val expenseDao: ExpenseDao,
) : ExpenseRepository {

    override suspend fun createExpense(
        amount: Double, category: Category, note: String?, date: String
    ): Expense {
        val request = CreateExpenseRequest(amount, category.name.lowercase(), note, date)
        return try {
            val response = apiService.createExpense(request)
            expenseDao.insert(response.toEntity())
            response.toDomain()
        } catch (e: Exception) {
            // Offline: save locally
            val localId = UUID.randomUUID().toString()
            val now = java.time.Instant.now().toString()
            val entity = ExpenseEntity(
                id = localId, userId = "", amount = amount,
                category = category.name.lowercase(), note = note,
                expenseDate = date, createdAt = now, updatedAt = now,
                synced = false,
            )
            expenseDao.insert(entity)
            Expense(localId, "", amount, category, note, date, now, now)
        }
    }

    override suspend fun getExpenses(
        startDate: String?, endDate: String?, category: Category?
    ): List<Expense> {
        return try {
            val response = apiService.getExpenses(startDate, endDate, category?.name?.lowercase())
            val entities = response.map { it.toEntity() }
            expenseDao.insertAll(entities)
            response.map { it.toDomain() }
        } catch (e: Exception) {
            // Return cached data
            expenseDao.getUnsyncedExpenses().map {
                Expense(it.id, it.userId, it.amount, Category.fromString(it.category),
                    it.note, it.expenseDate, it.createdAt, it.updatedAt)
            }
        }
    }

    override suspend fun deleteExpense(id: String) {
        try {
            apiService.deleteExpense(id)
        } catch (_: Exception) {}
        expenseDao.delete(id)
    }

    override suspend fun getDailySummary(date: String?): ExpenseSummary =
        apiService.getDailySummary(date).toDomain()

    override suspend fun getWeeklySummary(date: String?): ExpenseSummary =
        apiService.getWeeklySummary(date).toDomain()

    override suspend fun getMonthlySummary(date: String?): ExpenseSummary =
        apiService.getMonthlySummary(date).toDomain()

    override suspend fun getDailyStatus(): DailyStatus =
        apiService.getDailyStatus().toDomain()

    override suspend fun submitDay(date: String?) {
        apiService.submitDay(SubmitDayRequest(date))
    }

    override suspend fun syncPendingExpenses() {
        val unsynced = expenseDao.getUnsyncedExpenses()
        for (entity in unsynced) {
            try {
                val request = CreateExpenseRequest(
                    entity.amount, entity.category, entity.note, entity.expenseDate
                )
                val response = apiService.createExpense(request)
                expenseDao.delete(entity.id)
                expenseDao.insert(response.toEntity())
            } catch (_: Exception) {
                // Will retry on next sync
            }
        }
    }
}

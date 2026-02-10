package com.budgettracker.data.remote

import com.budgettracker.data.remote.dto.*
import retrofit2.http.*

interface ApiService {
    @GET("auth/me")
    suspend fun getMe(): UserResponse

    @POST("expenses")
    suspend fun createExpense(@Body request: CreateExpenseRequest): ExpenseResponse

    @GET("expenses")
    suspend fun getExpenses(
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("category") category: String? = null,
        @Query("per_page") perPage: Int = 50,
    ): List<ExpenseResponse>

    @DELETE("expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: String)

    @GET("analytics/daily")
    suspend fun getDailySummary(@Query("date") date: String? = null): ExpenseSummaryResponse

    @GET("analytics/weekly")
    suspend fun getWeeklySummary(@Query("date") date: String? = null): ExpenseSummaryResponse

    @GET("analytics/monthly")
    suspend fun getMonthlySummary(@Query("date") date: String? = null): ExpenseSummaryResponse

    @GET("daily-status")
    suspend fun getDailyStatus(): DailyStatusResponse

    @POST("daily-status/submit")
    suspend fun submitDay(@Body request: SubmitDayRequest): DailyStatusResponse

    @POST("notifications/register")
    suspend fun registerDevice(@Body request: Map<String, String>)
}

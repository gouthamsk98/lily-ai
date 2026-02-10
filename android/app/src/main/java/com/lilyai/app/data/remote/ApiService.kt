package com.lilyai.app.data.remote

import com.lilyai.app.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.Response
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
    suspend fun deleteExpense(@Path("id") id: String): Response<Unit>

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

    @POST("meeting-notes")
    suspend fun createMeetingNote(@Body request: CreateMeetingNoteRequest): MeetingNoteResponse

    @GET("meeting-notes")
    suspend fun getMeetingNotes(): List<MeetingNoteResponse>

    @GET("meeting-notes/{id}")
    suspend fun getMeetingNote(@Path("id") id: String): MeetingNoteResponse

    @DELETE("meeting-notes/{id}")
    suspend fun deleteMeetingNote(@Path("id") id: String): Response<Unit>

    @Multipart
    @POST("meeting-notes/{id}/upload")
    suspend fun uploadMeetingAudio(
        @Path("id") id: String,
        @Part audio: MultipartBody.Part,
    ): MeetingNoteResponse

    @GET("meeting-notes/{id}/transcription")
    suspend fun checkTranscription(@Path("id") id: String): MeetingNoteResponse

    @Multipart
    @POST("meeting-notes/{id}/photos")
    suspend fun uploadMeetingPhoto(
        @Path("id") id: String,
        @Part photo: MultipartBody.Part,
    ): MeetingPhotoResponse

    @GET("meeting-notes/{id}/photos")
    suspend fun getMeetingPhotos(@Path("id") id: String): List<MeetingPhotoResponse>

    @DELETE("meeting-notes/{meetingId}/photos/{photoId}")
    suspend fun deleteMeetingPhoto(
        @Path("meetingId") meetingId: String,
        @Path("photoId") photoId: String,
    ): Response<Unit>
}

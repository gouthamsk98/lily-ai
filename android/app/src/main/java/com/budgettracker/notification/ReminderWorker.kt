package com.budgettracker.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budgettracker.domain.repository.ExpenseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val expenseRepository: ExpenseRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val status = expenseRepository.getDailyStatus()
            if (!status.submitted) {
                NotificationHelper.showReminderNotification(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            // Still show notification if we can't check status (offline)
            NotificationHelper.showReminderNotification(applicationContext)
            Result.retry()
        }
    }
}

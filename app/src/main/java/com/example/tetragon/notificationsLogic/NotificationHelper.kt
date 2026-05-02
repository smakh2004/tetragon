package com.example.tetragon.notificationsLogic

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationHelper {
    private const val WORK_NAME = "DailyLessonReminder"

    fun scheduleDailyNotification(context: Context) {
        val delay = calculateDelayToSevenAM()

        val dailyRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            dailyRequest
        )
    }

    private fun calculateDelayToSevenAM(): Long {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()

        // Set the target time to 7:00:00 AM
        dueDate.set(Calendar.HOUR_OF_DAY, 7)
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)

        // If 7 AM has already passed today, schedule for 7 AM tomorrow
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }

        return dueDate.timeInMillis - currentDate.timeInMillis
    }

    fun cancelNotifications(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
package com.example.potty

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class DailySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as PottyApplication
        val repository = application.repository
        val prefs = PreferenceManager(applicationContext)
        val helper = NotificationHelper(applicationContext)

        // Check if enabled
        val isEnabled = prefs.isNotificationEnabled(PreferenceManager.DAILY_SUMMARY).first()
        if (!isEnabled) return Result.success()

        // Get Active Profile to filter data
        val profile = repository.activeProfile.first()
        val gid = profile?.googleId ?: "local_user"

        // Calculate Today's Spend for this user
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val expenses = repository.getAllExpenses(gid).first()
        val dailySpend = expenses.filter { !it.isIncome && it.timestamp.startsWith(todayStr) }.sumOf { it.amount }

        if (dailySpend > 0.0) {
            helper.showNotification(
                NotificationHelper.CHANNEL_SUMMARY,
                "Daily Spend Summary",
                "You spent ₹${String.format(Locale.US, "%.2f", dailySpend)} today.",
                1001
            )
        }

        return Result.success()
    }
}

package com.example.potty

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_SUMMARY = "daily_summary"
        const val CHANNEL_ALERTS = "subscription_alerts"
        const val CHANNEL_REMINDERS = "general_reminders"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val summaryChannel = NotificationChannel(
                CHANNEL_SUMMARY,
                "Daily Spend Summary",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows your total spending for the day at 9 PM"
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Subscription Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you before a subscription renews"
            }

            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "General Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General financial reminders and tips"
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(summaryChannel)
            manager.createNotificationChannel(alertsChannel)
            manager.createNotificationChannel(remindersChannel)
        }
    }

    fun showNotification(channelId: String, title: String, message: String, notificationId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use default icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                // Handle permission not granted
            }
        }
    }
}

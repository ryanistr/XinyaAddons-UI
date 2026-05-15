package com.rianixia.settings.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class OverlayService : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels(notificationManager)

        when (intent.action) {
            "com.rianixia.settings.SHOW_NOTIFICATION" -> {
                val title = intent.getStringExtra("title") ?: "System Notification"
                val text = intent.getStringExtra("text") ?: ""
                val notifId = intent.getIntExtra("id", DEFAULT_NOTIFICATION_ID)
                val isSilent = intent.getBooleanExtra("silent", true)
                val isOngoing = intent.getBooleanExtra("ongoing", false)

                val targetChannel = if (isSilent) SILENT_CHANNEL_ID else ALERT_CHANNEL_ID
                val targetPriority = if (isSilent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH

                val notification = NotificationCompat.Builder(context, targetChannel)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(targetPriority)
                    .setOngoing(isOngoing)
                    .build()

                notificationManager.notify(notifId, notification)
            }
            "com.rianixia.settings.CLEAR_NOTIFICATION" -> {
                val notifId = intent.getIntExtra("id", DEFAULT_NOTIFICATION_ID)
                notificationManager.cancel(notifId)
            }
        }
    }

    private fun createNotificationChannels(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val silentChannel = NotificationChannel(
                SILENT_CHANNEL_ID,
                "System Status (Silent)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background system notifications"
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(false)
            }
            manager.createNotificationChannel(silentChannel)

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "System Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical system alerts"
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        const val SILENT_CHANNEL_ID = "rianixia_status_silent_v2"
        const val ALERT_CHANNEL_ID = "rianixia_alerts_v2"
        const val DEFAULT_NOTIFICATION_ID = 5050
    }
}
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
        createNotificationChannel(notificationManager)

        when (intent.action) {
            "com.rianixia.settings.THERMAL_ALERT" -> {
                val temp = intent.getIntExtra("temp", 0)
                val intensity = intent.getFloatExtra("intensity", 0f)
                val percent = (intensity * 100).toInt()

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle("Thermal Mitigation Active")
                    .setContentText("Device at $temp°C. Throttling intensity: $percent%")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOngoing(true)
                    .build()

                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            "com.rianixia.settings.THERMAL_ALERT.CLEAR" -> {
                notificationManager.cancel(NOTIFICATION_ID)
            }
        }
    }

    private fun createNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Thermal Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "System notifications for device heating and thermal mitigation"
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "thermal_alert_channel"
        const val NOTIFICATION_ID = 5050
    }
}
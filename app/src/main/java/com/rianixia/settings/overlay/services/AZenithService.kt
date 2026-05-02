package com.rianixia.settings.overlay.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rianixia.settings.overlay.MainActivity
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.data.SystemProps
import kotlinx.coroutines.*

class AZenithService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitoringJob: Job? = null
    
    // State Cache
    private var lastPackageName: String = ""
    private var isProfileActive: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildIdleNotification())
        
        if (monitoringJob == null || monitoringJob?.isActive == false) {
            startMonitoring()
        }
        
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        monitoringJob = serviceScope.launch {
            while (isActive) {
                pollGameInfo()
                delay(1500) 
            }
        }
    }

    private suspend fun pollGameInfo() {
        // REPLACED: Shell execution with SystemProps
        val infoLine = SystemProps.get("sys.azenith.gameinfo")
        val parts = infoLine.split(" ")
        
        if (parts.isEmpty()) return

        val rawPackage = parts[0]
        
        if (rawPackage == "NULL" || rawPackage.isBlank()) {
            if (isProfileActive) {
                isProfileActive = false
                lastPackageName = ""
                updateNotification(idle = true)
            }
            return
        }

        if (rawPackage != lastPackageName) {
            lastPackageName = rawPackage
            isProfileActive = true
            
            val appLabel = getAppLabel(rawPackage)
            updateNotification(idle = false, appName = appLabel, pkgName = rawPackage)
            Log.d(TAG, "Performance Profile Applied: $appLabel ($rawPackage)")
        }
    }

    private fun updateNotification(idle: Boolean, appName: String = "", pkgName: String = "") {
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        if (idle) {
            notificationManager.notify(NOTIFICATION_ID, buildIdleNotification())
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val revertIntent = Intent(this, MainActivity::class.java)
            val revertPendingIntent = PendingIntent.getActivity(
                this, 1, revertIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            
            val largeIcon = getAppIcon(pkgName)

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.az_notif_active_title)) 
                .setContentText(getString(R.string.az_notif_active_desc, appName)) 
                .setSmallIcon(R.drawable.ic_launcher_foreground) 
                .setLargeIcon(largeIcon) 
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH) 
                .addAction(R.drawable.ic_launcher_foreground, "Settings", revertPendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildIdleNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AZenith Monitor")
            .setContentText("Engine Ready")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) 
            .build()
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName 
        }
    }
    
    private fun getAppIcon(packageName: String): Bitmap? {
        return try {
            val pm = packageManager
            val drawable = pm.getApplicationIcon(packageName)
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "azenith_profiles_v2",
                "Performance Profiles",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for active game optimization profiles"
                setShowBadge(true)
                enableVibration(true) 
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TAG = "AZenithService"
        const val CHANNEL_ID = "azenith_profiles_v2" 
        const val NOTIFICATION_ID = 9090
        const val ACTION_STOP_SERVICE = "com.rianixia.settings.overlay.STOP_AZENITH"
    }
}
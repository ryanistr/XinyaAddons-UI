package com.rianixia.settings.overlay.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rianixia.settings.overlay.MainActivity
import com.rianixia.settings.overlay.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

class EnforceDozeService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var powerManager: PowerManager
    private var screenReceiver: BroadcastReceiver? = null
    
    // Settings
    private var dozeDelayMs = 0L
    private var disableSensors = false
    private var disableWifi = false
    private var disableData = false

    // State tracking
    private var useRoot = false 

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        createNotificationChannel()
        reloadSettings()
        useRoot = ShellUtils.checkRoot()
        // Register receiver after root check to ensure executeCommand works correctly if needed immediately
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FIX: Always call startForeground immediately to prevent ForegroundServiceDidNotStartInTimeException
        startForeground(NOTIFICATION_ID, buildNotification())

        if (intent?.action == ACTION_RELOAD_SETTINGS) {
            reloadSettings()
            /*
            if (!powerManager.isInteractive) {
                scheduleDoze(dozeDelayMs)
            }
            */
        } else {
            if (!powerManager.isInteractive) {
                scheduleDoze(dozeDelayMs)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenReceiver()
        serviceScope.launch(Dispatchers.IO) {
            exitDoze()
        }
        // Give a small buffer for the exit command to run before killing scope
        try { Thread.sleep(50) } catch (e: Exception) {} 
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        // Global Screen State Monitor: 0 = OFF
                        serviceScope.launch(Dispatchers.IO) {
                            executeCommand("setprop persist.sys.rianixia.screen_state 0")
                        }
                        scheduleDoze(dozeDelayMs)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        // Global Screen State Monitor: 1 = ON
                        serviceScope.launch(Dispatchers.IO) {
                            executeCommand("setprop persist.sys.rianixia.screen_state 1")
                            exitDoze() 
                        }
                        dozeJob?.cancel()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            unregisterReceiver(it)
            screenReceiver = null
        }
    }

    private var dozeJob: kotlinx.coroutines.Job? = null

    private fun scheduleDoze(delayMs: Long) {
        dozeJob?.cancel()
        dozeJob = serviceScope.launch {
            if (delayMs > 0) delay(delayMs)
            enterDoze()
        }
    }

    private fun enterDoze() {
        Log.i(TAG, "Triggering Deep Sleep Sequence (Root: $useRoot)")
        
        // 1. Force Doze
        // Attempt normal command first
        val result = executeCommand("dumpsys deviceidle force-idle deep")
        if (result.contains("idle", ignoreCase = true)) {
            Log.d(TAG, "System Result: $result")
        } else {
            Log.e(TAG, "Doze Command Failed: $result")
            // Fallback: Set prop for AIO
            Log.w(TAG, "Fallback: Setting persist.sys.rianixia.enforcedoze.doze = true")
            executeCommand("setprop persist.sys.rianixia.enforcedoze.doze true")
        }
        
        // 2. Disable Sensors
        if (disableSensors) {
            if (useRoot) {
                Log.d(TAG, "Action: Restricting Sensors")
                val output = executeCommand("dumpsys sensorservice restrict")
                Log.d(TAG, "Sensor Result: $output")
            } else {
                // Non-root cannot use dumpsys sensorservice restrict. Use Prop Fallback.
                // Logic Inverted: false = disabled/restricted (to match wifi/data logic requested)
                Log.w(TAG, "Action: Restricting Sensors (via Prop Fallback)")
                executeCommand("setprop persist.sys.rianixia.enforcedoze.sensors false")
            }
        }

        // 3. Disable Data
        if (disableData) {
            Log.d(TAG, "Action: Disabling Mobile Data")
            val output = executeCommand("svc data disable")
            Log.d(TAG, "Data Result: $output")
            
            // If output indicates failure or we are non-root (svc can be flaky without root), ensure prop is set
            if (output.contains("Error") || output.contains("Permission denial") || !useRoot) {
                Log.w(TAG, "Fallback: Setting persist.sys.rianixia.enforcedoze.data = false")
                executeCommand("setprop persist.sys.rianixia.enforcedoze.data false")
            }
        }

        // 4. Disable WiFi
        if (disableWifi) {
            Log.d(TAG, "Action: Disabling Wi-Fi")
            val output = executeCommand("svc wifi disable")
            Log.d(TAG, "WiFi Result: $output")
            
            if (output.contains("Error") || output.contains("Permission denial") || !useRoot) {
                Log.w(TAG, "Fallback: Setting persist.sys.rianixia.enforcedoze.wifi = false")
                executeCommand("setprop persist.sys.rianixia.enforcedoze.wifi false")
            }
        }
    }

    private fun exitDoze() {
        Log.i(TAG, "Exiting Deep Sleep Sequence")
        dozeJob?.cancel()

        // 1. Restore Sensors
        if (disableSensors) {
            if (useRoot) {
                Log.d(TAG, "Action: Enabling Sensors")
                executeCommand("dumpsys sensorservice enable")
            } else {
                // Prop Fallback
                // Logic Inverted: true = enabled (to match wifi/data logic requested)
                executeCommand("setprop persist.sys.rianixia.enforcedoze.sensors true")
            }
        }

        // 2. Unforce Doze
        val unforce = executeCommand("dumpsys deviceidle unforce")
        val step = executeCommand("dumpsys deviceidle step")
        Log.d(TAG, "System Result: $unforce | $step")
        
        // Also reset Doze prop just in case AIO is watching it
        executeCommand("setprop persist.sys.rianixia.enforcedoze.doze false")
        
        // 3. Restore Data
        if (disableData) {
            Log.d(TAG, "Action: Enabling Mobile Data")
            val output = executeCommand("svc data enable")
            
            if (output.contains("Error") || output.contains("Permission denial") || !useRoot) {
                executeCommand("setprop persist.sys.rianixia.enforcedoze.data true")
            }
        }
        
        // 4. Restore WiFi
        if (disableWifi) {
            Log.d(TAG, "Action: Enabling Wi-Fi")
            val output = executeCommand("svc wifi enable")
            
            if (output.contains("Error") || output.contains("Permission denial") || !useRoot) {
                executeCommand("setprop persist.sys.rianixia.enforcedoze.wifi true")
            }
        }
    }

    private fun reloadSettings() {
        val prefs = getSharedPreferences("enforce_doze_prefs", Context.MODE_PRIVATE)
        dozeDelayMs = prefs.getInt("entry_delay", 0).toLong() * 1000L
        disableSensors = prefs.getBoolean("disable_sensors", false)
        disableWifi = prefs.getBoolean("disable_wifi", false)
        disableData = prefs.getBoolean("disable_data", false)
    }

    private fun executeCommand(command: String): String {
        return if (useRoot) {
            ShellUtils.executeRoot(command)
        } else {
            ShellUtils.executeShell(command)
        }
    }

    object ShellUtils {
        fun checkRoot(): Boolean {
            return try {
                val p = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(p.outputStream)
                os.writeBytes("id\n")
                os.writeBytes("exit\n")
                os.flush()
                p.waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }

        fun executeShell(command: String): String {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                readOutput(process)
            } catch (e: Exception) {
                "Exec Exception: ${e.message}"
            }
        }

        fun executeRoot(command: String): String {
            return try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                os.writeBytes("$command\n")
                os.writeBytes("exit\n")
                os.flush()
                readOutput(process)
            } catch (e: Exception) {
                "Root Exception: ${e.message}"
            }
        }

        private fun readOutput(process: Process): String {
            val output = StringBuilder()
            val stdOut = BufferedReader(InputStreamReader(process.inputStream))
            val stdErr = BufferedReader(InputStreamReader(process.errorStream))
            
            var line: String?
            while (stdOut.readLine().also { line = it } != null) {
                output.append(line).append(" ")
            }
            while (stdErr.readLine().also { line = it } != null) {
                output.append(line).append(" ")
            }
            process.waitFor()
            return output.toString().trim()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Enforce Doze Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Doze enforcer active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Enforce Doze Active")
            .setContentText("Maximizing deep sleep battery savings")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TAG = "EnforceDozeService"
        const val CHANNEL_ID = "enforce_doze_channel"
        const val NOTIFICATION_ID = 2938
        const val ACTION_RELOAD_SETTINGS = "com.rianixia.settings.overlay.RELOAD_DOZE_SETTINGS"
    }
}
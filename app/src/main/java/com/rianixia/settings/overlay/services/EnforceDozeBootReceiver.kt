package com.rianixia.settings.overlay.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class EnforceDozeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("enforce_doze_prefs", Context.MODE_PRIVATE)
            
            // Only start if the user has previously enabled the service
            if (prefs.getBoolean("service_enabled", false)) {
                Log.d("EnforceDozeBoot", "Boot completed, restarting Enforce Doze Service...")
                
                val serviceIntent = Intent(context, EnforceDozeService::class.java)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
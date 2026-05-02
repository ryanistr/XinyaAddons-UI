package com.rianixia.settings.overlay.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object AppPreferences {
    private const val TAG = "AppPreferences"
    private const val PREFS_NAME = "xinya_main_prefs"
    private const val KEY_FIRST_RUN = "is_first_run_v1"
    private const val KEY_HIDE_UV_WARNING = "hide_uv_warning"
    
    // System Property for Safety Mode
    private const val PROP_SAFETY_MODE = "persist.sys.rianixia.safe_mode_state"
    private const val LAUNCHER_ALIAS = "com.rianixia.settings.overlay.Launcher"

    // --- First Run ---
    fun isFirstRun(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FIRST_RUN, true)
    }

    fun setFirstRunComplete(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FIRST_RUN, false)
            .apply()
    }

    // --- UV Warning ---
    fun isUvWarningHidden(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_UV_WARNING, false)
    }

    fun setUvWarningHidden(context: Context, hidden: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_HIDE_UV_WARNING, hidden)
            .apply()
    }

    // --- Safety Mode ---
    fun getSafetyMode(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("getprop $PROP_SAFETY_MODE")
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val result = reader.readLine()?.trim()
            // Default to true if prop is "1" or empty (safety on by default for new installs)
            result == "1" || result.isNullOrEmpty()
        } catch (e: Exception) {
            true // Fail-safe default
        }
    }

    fun setSafetyMode(enabled: Boolean) {
        val value = if (enabled) "1" else "0"
        try {
            // Try standard setprop
            Runtime.getRuntime().exec("setprop $PROP_SAFETY_MODE $value")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set safety mode via standard shell", e)
            try {
                // Fallback to su
                Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop $PROP_SAFETY_MODE $value"))
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to set safety mode via SU", e2)
            }
        }
    }

    // --- Launcher Icon ---
    fun getLauncherIconState(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val componentName = ComponentName(context, LAUNCHER_ALIAS)
            val enabledSetting = pm.getComponentEnabledSetting(componentName)
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } catch (e: Exception) {
            false
        }
    }

    fun setLauncherIconState(context: Context, show: Boolean) {
        try {
            val packageManager = context.packageManager
            val componentName = ComponentName(context, LAUNCHER_ALIAS)
            
            val state = if (show) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            packageManager.setComponentEnabledSetting(
                componentName,
                state,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle launcher icon", e)
        }
    }
}
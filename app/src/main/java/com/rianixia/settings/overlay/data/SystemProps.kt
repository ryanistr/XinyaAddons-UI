package com.rianixia.settings.overlay.data

import android.annotation.SuppressLint
import android.util.Log
import java.lang.reflect.Method

/**
 * Reflection wrapper for android.os.SystemProperties.
 * Eliminates the need for shell execution (Runtime.exec), providing 
 * direct, high-performance access to the native property service.
 */
object SystemProps {
    private const val TAG = "SystemProps"
    private var getMethod: Method? = null
    private var setMethod: Method? = null

    init {
        try {
            @SuppressLint("PrivateApi")
            val clazz = Class.forName("android.os.SystemProperties")
            // SystemProperties.get(String key, String def)
            getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            // SystemProperties.set(String key, String val)
            setMethod = clazz.getMethod("set", String::class.java, String::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reflect SystemProperties", e)
        }
    }

    /**
     * Native get (android.os.SystemProperties.get)
     */
    fun get(key: String, default: String = ""): String {
        return try {
            getMethod?.invoke(null, key, default) as? String ?: default
        } catch (e: Exception) {
            Log.e(TAG, "Native get failed: $key", e)
            default
        }
    }

    /**
     * Native set (android.os.SystemProperties.set)
     * Requires appropriate SELinux permissions.
     */
    fun set(key: String, value: String) {
        try {
            setMethod?.invoke(null, key, value)
        } catch (e: Exception) {
            Log.e(TAG, "Native set failed: $key=$value", e)
        }
    }
}
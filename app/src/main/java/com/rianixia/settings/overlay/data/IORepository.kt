package com.rianixia.settings.overlay.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class IoDevice(
    val name: String,
    val path: String,
    val currentScheduler: String,
    val availableSchedulers: List<String>
)

object IORepository {
    private const val TAG = "IORepo"
    
    // Property Keys
    private const val PROP_DEVICE_LIST = "persist.sys.rianixia.io.devices"
    private const val PROP_DEV_PREFIX = "persist.sys.rianixia.io.dev."
    private const val PROP_TARGET_SCHED = "persist.sys.rianixia.io.scheduler"

    /**
     * Reads the device list and their states from system properties.
     */
    suspend fun getIoDevices(): List<IoDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<IoDevice>()
        
        // 1. Read the list of devices (e.g., "mmcblk0,sda,sdb")
        val deviceListStr = SystemProps.get(PROP_DEVICE_LIST, "")
        if (deviceListStr.isBlank()) {
            return@withContext emptyList()
        }

        val deviceNames = deviceListStr.split(",").filter { it.isNotBlank() }

        // 2. Fetch details for each device from properties
        deviceNames.forEach { name ->
            // Read prop: persist.sys.rianixia.io.dev.<name>
            val schedString = SystemProps.get("$PROP_DEV_PREFIX$name", "")
            
            if (schedString.isNotBlank()) {
                val (current, available) = parseSchedulerString(schedString)
                val fakePath = "/sys/block/$name"
                devices.add(IoDevice(name, fakePath, current, available))
            }
        }

        devices.sortedBy { it.name }
    }

    private fun parseSchedulerString(content: String): Pair<String, List<String>> {
        val available = mutableListOf<String>()
        var current = "none" 
        
        val tokens = content.split(Regex("\\s+"))
        tokens.forEach { token ->
            if (token.startsWith("[") && token.endsWith("]")) {
                val clean = token.substring(1, token.length - 1)
                current = clean
                available.add(clean)
            } else {
                available.add(token)
            }
        }
        return Pair(current, available)
    }

    suspend fun getTargetScheduler(): String = withContext(Dispatchers.IO) {
        SystemProps.get(PROP_TARGET_SCHED, "")
    }

    suspend fun setTargetScheduler(scheduler: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Setting global target scheduler property: $scheduler")
        SystemProps.set(PROP_TARGET_SCHED, scheduler)
    }
}
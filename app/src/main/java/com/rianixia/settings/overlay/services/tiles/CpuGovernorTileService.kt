package com.rianixia.settings.overlay.services.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.io.BufferedReader
import java.io.InputStreamReader

class CpuGovernorTileService : TileService() {
    // CORRECTED PROPERTY KEY to match CPURepository
    private val propKey = "persist.sys.rianixia.cpu.gov"

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val availableGovs = getAvailableGovernors()
        if (availableGovs.isEmpty()) return

        val currentState = getSystemProperty(propKey).ifEmpty { "schedutil" }
        val currentIndex = availableGovs.indexOf(currentState)
        val nextIndex = if (currentIndex != -1 && currentIndex < availableGovs.size - 1) currentIndex + 1 else 0
        val newState = availableGovs[nextIndex]

        setSystemProperty(propKey, newState)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val currentState = getSystemProperty(propKey).ifEmpty { "schedutil" }
        
        tile.state = Tile.STATE_ACTIVE
        tile.subtitle = currentState.uppercase()
        tile.updateTile()
    }

    private fun getAvailableGovernors(): List<String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cat /sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()?.trim() ?: ""
            process.waitFor()
            if (result.isNotEmpty()) {
                result.split(" ")
            } else {
                listOf("schedutil", "performance", "powersave")
            }
        } catch (e: Exception) {
            listOf("schedutil", "performance", "powersave")
        }
    }

    private fun setSystemProperty(key: String, value: String) {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val setMethod = clazz.getMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, key, value)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            getMethod.invoke(null, key, "") as? String ?: ""
        } catch (e: Exception) { "" }
    }
}
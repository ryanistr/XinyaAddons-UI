package com.rianixia.settings.overlay.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class CpuGovernorTileService : TileService() {
    private val propKey = "persist.sys.rianixia.cpu.global_gov"

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentState = getSystemProperty(propKey)
        val newState = when (currentState) {
            "schedutil" -> "performance"
            "performance" -> "powersave"
            "powersave" -> "schedutil"
            else -> "schedutil"
        }
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
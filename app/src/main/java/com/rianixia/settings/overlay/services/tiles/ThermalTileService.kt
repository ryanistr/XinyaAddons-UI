package com.rianixia.settings.overlay.services.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ThermalProfileTileService : TileService() {

    private val propKey = "persist.sys.rianixia.thermal-mode"

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentState = getSystemProperty(propKey).lowercase()
        val newState = when (currentState) {
            "default" -> "adaptive"
            "adaptive" -> "disabled"
            "disabled" -> "custom"
            "custom" -> "default"
            else -> "default"
        }
        setSystemProperty(propKey, newState)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val currentState = getSystemProperty(propKey).lowercase().ifEmpty { "default" }

        tile.state = if (currentState == "disabled") Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.subtitle = currentState.replaceFirstChar { it.uppercase() }
        tile.updateTile()
    }

    private fun setSystemProperty(key: String, value: String) {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val setMethod = clazz.getMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, key, value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            getMethod.invoke(null, key, "") as? String ?: ""
        } catch (e: Exception) { 
            "" 
        }
    }
}
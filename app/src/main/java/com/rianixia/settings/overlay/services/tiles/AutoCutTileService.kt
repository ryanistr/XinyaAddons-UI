package com.rianixia.settings.overlay.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class AutoCutTileService : TileService() {
    private val stateProp = "persist.sys.rianixia.autocut.state"
    private val percentProp = "persist.sys.rianixia.autocut.percent"

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentState = getSystemProperty(stateProp)
        val currentPercent = getSystemProperty(percentProp).toIntOrNull() ?: 80

        when {
            currentState == "0" || currentState == "" -> {
                setSystemProperty(stateProp, "1")
                setSystemProperty(percentProp, "80")
            }
            currentState == "1" && currentPercent < 90 -> {
                setSystemProperty(percentProp, "90")
            }
            currentState == "1" && currentPercent < 100 -> {
                setSystemProperty(percentProp, "100")
            }
            else -> {
                setSystemProperty(stateProp, "0")
            }
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val currentState = getSystemProperty(stateProp)
        val currentPercent = getSystemProperty(percentProp)

        if (currentState == "1") {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = "$currentPercent%"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = "Off"
        }
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
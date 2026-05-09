package com.rianixia.settings.overlay.services.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class FlagSecureTileService : TileService() {

    private val propKey = "persist.sys.rianixia.disable_flag_secure"

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentState = getSystemProperty(propKey)
        val newState = if (currentState == "1") "0" else "1"
        setSystemProperty(propKey, newState)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val currentState = getSystemProperty(propKey)

        if (currentState == "1") {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = "Disabled"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = "Enabled"
        }
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
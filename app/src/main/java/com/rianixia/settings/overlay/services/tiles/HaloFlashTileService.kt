package com.rianixia.settings.overlay.services.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class HaloFlashTileService : TileService() {
    private val flashProp = "persist.sys.rianixia.halolight.flash"

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentFlash = getSystemProperty(flashProp)
        
        if (currentFlash == "1" || currentFlash.equals("true", ignoreCase = true)) {
            setSystemProperty(flashProp, "0")
        } else {
            setSystemProperty(flashProp, "1")
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val currentFlash = getSystemProperty(flashProp)

        if (currentFlash == "1" || currentFlash.equals("true", ignoreCase = true)) {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = "Active"
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
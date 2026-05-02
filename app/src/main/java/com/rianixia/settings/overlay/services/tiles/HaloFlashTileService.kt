package com.rianixia.settings.overlay.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class HaloFlashTileService : TileService() {
    private val animProp = "persist.sys.rianixia.halolight.anim"
    private val powerProp = "persist.sys.rianixia.halolight.power"

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentAnim = getSystemProperty(animProp)
        
        if (currentAnim == "Static" && getSystemProperty(powerProp) == "100") {
            setSystemProperty(animProp, "off")
        } else {
            setSystemProperty(animProp, "Static")
            setSystemProperty(powerProp, "100")
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val currentAnim = getSystemProperty(animProp)
        val currentPower = getSystemProperty(powerProp)

        if (currentAnim == "Static" && currentPower == "100") {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = "Max Flash"
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
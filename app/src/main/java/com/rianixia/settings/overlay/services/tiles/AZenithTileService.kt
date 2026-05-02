package com.rianixia.settings.overlay.services

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.os.Build

class AZenithTileService : TileService() {
    private val propKey = "persist.sys.rianixia.azenith.global"

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentState = getSystemProperty(propKey)
        val newState = if (currentState == "1") "0" else "1"
        setSystemProperty(propKey, newState)
        
        val serviceIntent = Intent(this, AZenithService::class.java)
        if (newState == "1") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            stopService(serviceIntent)
        }
        
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        if (getSystemProperty(propKey) == "1") {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = "Active"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = "Offline"
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
package com.rianixia.settings.overlay.services.tiles

import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rianixia.settings.overlay.R

class HaloEffectTileService : TileService() {
    private val animProp = "persist.sys.rianixia.halolight.anim"
    private val prefsName = "xinya_app_prefs"
    private val cachedEffectKey = "cached_halo_effect"

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentAnim = getSystemProperty(animProp)
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val targetEffect = prefs.getString(cachedEffectKey, "FlowingLight") ?: "FlowingLight"

        if (currentAnim == "off" || currentAnim == "" || currentAnim == "0") {
            setSystemProperty(animProp, targetEffect)
        } else {
            prefs.edit().putString(cachedEffectKey, currentAnim).apply()
            setSystemProperty(animProp, "off")
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val currentAnim = getSystemProperty(animProp)

        if (currentAnim != "off" && currentAnim != "" && currentAnim != "0") {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = currentAnim
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = getString(R.string.tile_off)
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
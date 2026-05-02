package com.rianixia.settings.overlay.services

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.io.BufferedReader
import java.io.InputStreamReader

class ExtraDimTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentState = runShellCommand("getprop persist.sys.rianixia.display.extradim")
        val isCurrentlyOn = currentState == "1"
        val newState = !isCurrentlyOn
        
        runShellCommand("setprop persist.sys.rianixia.display.extradim ${if (newState) "1" else "0"}")
        
        if (newState) {
            val intensityStr = runShellCommand("getprop persist.sys.rianixia.display.dim_intensity")
            val intensity = intensityStr.toFloatOrNull() ?: 0.5f
            runShellCommand("appops set com.rianixia.settings.overlay SYSTEM_ALERT_WINDOW allow")
            
            val intent = Intent(this, ExtraDimService::class.java).apply {
                action = "UPDATE_DIM"
                putExtra("INTENSITY", intensity)
            }
            startService(intent)
        } else {
            val intent = Intent(this, ExtraDimService::class.java).apply {
                action = "STOP_DIM"
            }
            startService(intent)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val qsTile = qsTile ?: return
        val currentState = runShellCommand("getprop persist.sys.rianixia.display.extradim")
        qsTile.state = if (currentState == "1") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    private fun runShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()?.trim() ?: ""
            process.waitFor()
            result
        } catch (e: Exception) {
            ""
        }
    }
}
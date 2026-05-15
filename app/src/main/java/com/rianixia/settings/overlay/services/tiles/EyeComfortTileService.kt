package com.rianixia.settings.overlay.services.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.data.SystemProps
import java.util.Calendar

class EyeComfortTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val config = SystemProps.get("persist.sys.rianixia.display.schemeconfig", "1000 1000 1000 1000 1000 0.5 0 18:00 07:00")
        val parts = config.split(" ").toMutableList()
        if (parts.size < 9) return

        val currentSchedule = parts[6].toIntOrNull() ?: 0
        val nextSchedule = if (currentSchedule == 0) 1 else 0
        
        parts[6] = nextSchedule.toString()
        val newConfig = parts.joinToString(" ")
        SystemProps.set("persist.sys.rianixia.display.schemeconfig", newConfig)
        
        updateTileState()
        applyDisplayConfig()
        
        // Notify UI components if they are active
        val intent = android.content.Intent("com.rianixia.settings.overlay.EYE_CARE_CHANGED")
        sendBroadcast(intent)
    }

    private fun updateTileState() {
        val qsTile = qsTile ?: return
        val config = SystemProps.get("persist.sys.rianixia.display.schemeconfig", "1000 1000 1000 1000 1000 0.5 0 18:00 07:00")
        val parts = config.split(" ")
        if (parts.size < 9) return

        val schedule = parts[6].toIntOrNull() ?: 0
        val startTime = parts[7]
        val endTime = parts[8]

        when (schedule) {
            0 -> {
                qsTile.state = Tile.STATE_INACTIVE
                qsTile.subtitle = getString(R.string.tile_off)
            }
            1 -> {
                qsTile.state = Tile.STATE_ACTIVE
                qsTile.subtitle = getString(R.string.sd_eye_care_schedule_always)
            }
            2 -> {
                qsTile.state = Tile.STATE_ACTIVE
                qsTile.subtitle = "$startTime - $endTime"
            }
        }
        qsTile.updateTile()
    }

    private fun applyDisplayConfig() {
        Thread {
            try {
                val config = SystemProps.get("persist.sys.rianixia.display.schemeconfig", "1000 1000 1000 1000 1000 0.5 0 18:00 07:00")
                val parts = config.split(" ")
                if (parts.size >= 4) {
                    val redVal = parts[0].toFloatOrNull() ?: 1000f
                    val greenVal = parts[1].toFloatOrNull() ?: 1000f
                    val blueVal = parts[2].toFloatOrNull() ?: 1000f
                    val colorTemperature = if (parts.size >= 5) parts[4].toFloatOrNull() ?: 1000f else 1000f
                    val eyeCareIntensity = if (parts.size >= 6) parts[5].toFloatOrNull() ?: 0.5f else 0.5f
                    val eyeCareSchedule = if (parts.size >= 7) parts[6].toIntOrNull() ?: 0 else 0
                    val eyeCareStartTime = if (parts.size >= 8) parts[7] else "18:00"
                    val eyeCareEndTime = if (parts.size >= 9) parts[8] else "07:00"
                    
                    var eyeCareEnabled = false
                    if (eyeCareSchedule == 1) {
                        eyeCareEnabled = true
                    } else if (eyeCareSchedule == 2) {
                        val now = Calendar.getInstance()
                        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                        
                        val startParts = eyeCareStartTime.split(":").map { it.toIntOrNull() ?: 0 }
                        val endParts = eyeCareEndTime.split(":").map { it.toIntOrNull() ?: 0 }
                        if (startParts.size >= 2 && endParts.size >= 2) {
                            val startMinutes = startParts[0] * 60 + startParts[1]
                            val endMinutes = endParts[0] * 60 + endParts[1]

                            eyeCareEnabled = if (startMinutes < endMinutes) {
                                currentMinutes in startMinutes until endMinutes
                            } else {
                                currentMinutes >= startMinutes || currentMinutes < endMinutes
                            }
                        }
                    }

                    val tempFactor = (colorTemperature - 1000f) / 1000f
                    var r = redVal / 1000f
                    var g = greenVal / 1000f
                    var b = blueVal / 1000f
                    
                    if (tempFactor < 0) {
                        r += (-tempFactor * 0.15f)
                        b -= (-tempFactor * 0.15f)
                    } else if (tempFactor > 0) {
                        b += (tempFactor * 0.15f)
                        r -= (tempFactor * 0.15f)
                    }

                    if (eyeCareEnabled) {
                        val eyeCareFactor = eyeCareIntensity * 0.4f
                        r += eyeCareFactor
                        b -= eyeCareFactor
                    }
                    
                    r = r.coerceIn(0f, 2f)
                    b = b.coerceIn(0f, 2f)
                    
                    val colorCmd = "service call SurfaceFlinger 1015 i32 1 f $r f 0 f 0 f 0 f 0 f $g f 0 f 0 f 0 f 0 f $b f 0 f 0 f 0 f 0 f 1"
                    Runtime.getRuntime().exec(colorCmd).waitFor()
                    
                    Log.d("EyeComfortTile", "Applied Display Config: Color($r,$g,$b)")
                }
            } catch (e: Exception) {
                Log.e("EyeComfortTile", "Failed to apply display config", e)
            }
        }.start()
    }
}
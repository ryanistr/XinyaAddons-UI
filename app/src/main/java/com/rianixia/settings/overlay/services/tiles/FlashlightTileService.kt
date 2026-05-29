package com.rianixia.settings.overlay.services.tiles

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.data.AppPreferences
import com.rianixia.settings.overlay.data.TorchRepository
import com.rianixia.settings.overlay.ui.screens.FlashlightMenuActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlashlightTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val mode = AppPreferences.getFlashlightQsMode(this)
        
        when (mode) {
            0 -> {
                // Menu
                val intent = Intent(this, FlashlightMenuActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            }
            1 -> toggleTorch(TorchRepository.PATH_FRONT_TORCH, is360 = false)
            2 -> toggleTorch(TorchRepository.PATH_BACK_TORCH, is360 = false)
            3 -> toggleTorch(TorchRepository.PATH_BACK_TORCH, is360 = true)
        }
    }

    private fun toggleTorch(path: String, is360: Boolean) {
        scope.launch {
            val currentPrimary = TorchRepository.getTorchLevel(path)
            val newState = if (currentPrimary > 0) 0 else 1 // Toggle between 0 and 1
            
            TorchRepository.setTorchLevel(path, newState)
            
            if (is360) {
                // If 360, set the other torch to the same state
                val otherPath = if (path == TorchRepository.PATH_BACK_TORCH) TorchRepository.PATH_FRONT_TORCH else TorchRepository.PATH_BACK_TORCH
                TorchRepository.setTorchLevel(otherPath, newState)
            }
            
            updateTileState()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        scope.launch {
            val backLevel = TorchRepository.getTorchLevel(TorchRepository.PATH_BACK_TORCH)
            val frontLevel = TorchRepository.getTorchLevel(TorchRepository.PATH_FRONT_TORCH)
            
            withContext(Dispatchers.Main) {
                if (backLevel > 0 || frontLevel > 0) {
                    tile.state = Tile.STATE_ACTIVE
                    tile.subtitle = if (backLevel > 0 && frontLevel > 0) "360 Active"
                                   else if (backLevel > 0) "Back Active"
                                   else "Front Active"
                } else {
                    tile.state = Tile.STATE_INACTIVE
                    tile.subtitle = getString(R.string.tile_off)
                }
                tile.updateTile()
            }
        }
    }
}

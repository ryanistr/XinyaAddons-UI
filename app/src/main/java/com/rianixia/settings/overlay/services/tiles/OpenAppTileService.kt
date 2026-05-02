package com.rianixia.settings.overlay.services.tiles

import android.content.Intent
import android.service.quicksettings.TileService
import com.rianixia.settings.overlay.MainActivity

class OpenAppTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val unlockIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        sendBroadcast(unlockIntent)
        startActivityAndCollapse(intent)
    }
}
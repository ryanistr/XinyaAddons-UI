package com.rianixia.settings.overlay.services

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams

class ExtraDimService : Service() {

    private var windowManager: WindowManager? = null
    private var dimView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        dimView = View(this)
        dimView?.setBackgroundColor(Color.BLACK)

        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            2015, // TYPE_SECURE_SYSTEM_OVERLAY
            LayoutParams.FLAG_NOT_FOCUSABLE
                    or LayoutParams.FLAG_NOT_TOUCHABLE
                    or LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.alpha = 0f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        try {
            val privateFlagsField = LayoutParams::class.java.getField("privateFlags")
            var privateFlags = privateFlagsField.getInt(params)
            privateFlags = privateFlags or 0x20000000 // PRIVATE_FLAG_TRUSTED_OVERLAY
            privateFlagsField.setInt(params, privateFlags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            windowManager?.addView(dimView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                params.type = 2026 // Fallback to TYPE_DISPLAY_OVERLAY
                windowManager?.addView(dimView, params)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "UPDATE_DIM" -> {
                val intensity = intent.getFloatExtra("INTENSITY", 0f)
                val params = dimView?.layoutParams as? LayoutParams
                if (params != null) {
                    params.alpha = intensity
                    try {
                        windowManager?.updateViewLayout(dimView, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            "STOP_DIM" -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (dimView != null) {
                windowManager?.removeView(dimView)
                dimView = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
// File: app/src/main/java/com/rianixia/settings/overlay/ui/viewmodel/HomeViewModel.kt
package com.rianixia.settings.overlay.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.rianixia.settings.overlay.data.HomeDashboardState
import com.rianixia.settings.overlay.data.SystemStateAggregator
import com.rianixia.settings.overlay.data.ThermalProfile
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val aggregator = SystemStateAggregator(application)
    val uiState: StateFlow<HomeDashboardState> = aggregator.state

    fun setAutoCutEnabled(enabled: Boolean) {
        aggregator.setSystemProperty("persist.sys.rianixia.autocut.state", if(enabled) "1" else "0")
    }

    fun setAutoCutLimit(limit: Float) {
        aggregator.setSystemProperty("persist.sys.rianixia.autocut.percent", limit.toInt().toString())
    }

    fun setBypassEnabled(enabled: Boolean) {
        aggregator.setSystemProperty("persist.sys.rianixia.bypass_charge.state", if(enabled) "1" else "0")
    }

    fun setBypassThreshold(threshold: Float) {
        aggregator.setSystemProperty("persist.sys.rianixia.bypass_charge.threshold", threshold.toInt().toString())
    }

    fun setTempCutoffEnabled(enabled: Boolean) {
        aggregator.setSystemProperty("persist.sys.rianixia.thermal_charge_cut-off.state", if(enabled) "1" else "0")
    }

    fun setThermalProfile(profile: ThermalProfile) {
    }

    fun setCustomThrottleLimit(limit: Int) {
    }

    fun setEnforceDozeEnabled(enabled: Boolean) {
        aggregator.setEnforceDozeEnabled(enabled)
    }

    fun setEnforceDozeDelay(seconds: Int) {
        aggregator.updateEnforceDozeSetting("entry_delay", seconds)
    }

    fun setEnforceDozeSensors(enabled: Boolean) {
        aggregator.updateEnforceDozeSetting("disable_sensors", enabled)
    }

    fun setEnforceDozeWifi(enabled: Boolean) {
        aggregator.updateEnforceDozeSetting("disable_wifi", enabled)
    }

    fun setEnforceDozeData(enabled: Boolean) {
        aggregator.updateEnforceDozeSetting("disable_data", enabled)
    }

    override fun onCleared() {
        super.onCleared()
        aggregator.onCleared()
    }
}
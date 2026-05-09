package com.rianixia.settings.overlay.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rianixia.settings.overlay.data.HomeDashboardState
import com.rianixia.settings.overlay.data.SystemStateAggregator
import com.rianixia.settings.overlay.data.ThermalProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val aggregator = SystemStateAggregator(application)
    
    private val _totalUndervoltSteps = MutableStateFlow(0)
    private val _dynamicGovernor = MutableStateFlow("schedutil")

    val uiState: StateFlow<HomeDashboardState> = combine(
        aggregator.state,
        _totalUndervoltSteps,
        _dynamicGovernor
    ) { state, totalSteps, governor ->
        state.copy(
            totalUndervoltValue = totalSteps,
            cpuState = state.cpuState.copy(
                activeGovernor = governor.ifEmpty { state.cpuState.activeGovernor }
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = aggregator.state.value
    )

    init {
        startUndervoltPolling()
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            val gov = readPropAsString("persist.sys.rianixia.cpu.gov", "schedutil")
            _dynamicGovernor.value = gov
        }
    }

    private fun startUndervoltPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            val propKeys = listOf(
                "persist.sys.rianixia.undervolt-cluster.big",
                "persist.sys.rianixia.undervolt-cluster.bl",
                "persist.sys.rianixia.undervolt-cluster.little",
                "persist.sys.rianixia.undervolt-cluster.cci",
                "persist.sys.rianixia.undervolt-cluster.gpu",
                "persist.sys.rianixia.undervolt-cluster.gpu-high"
            )
            
            while (true) {
                var sum = 0
                for (key in propKeys) {
                    sum += readPropAsInt(key)
                }
                _totalUndervoltSteps.value = sum
                delay(2000) // 2-second polling interval
            }
        }
    }

    private fun readPropAsString(key: String, default: String = ""): String {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            reader.close()
            line?.trim()?.ifEmpty { default } ?: default
        } catch (e: Exception) {
            default
        }
    }

    private fun readPropAsInt(key: String, default: Int = 0): Int {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            reader.close()
            line?.trim()?.toIntOrNull() ?: default
        } catch (e: Exception) {
            default
        }
    }

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
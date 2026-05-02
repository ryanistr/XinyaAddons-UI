package com.rianixia.settings.overlay.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import com.rianixia.settings.overlay.services.EnforceDozeService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class SystemStateAggregator(private val context: Context) {

    private val _state = MutableStateFlow(HomeDashboardState())
    val state: StateFlow<HomeDashboardState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val deviceRepo = DeviceRepository(context)
    
    private val cpuLoadBuffer = ArrayDeque<Float>(40)

    init {
        startMonitoring()
        updateEnforceDozeState()
    }

    private fun startMonitoring() {
        scope.launch {
            deviceRepo.initialize()
            updateDeviceInfo()
            updateChargingConfig()
            updateUndervoltState()
            updateAZenithState()
        }

        // Fast Poll: CPU & Time (500ms)
        scope.launch {
            while (isActive) {
                updateCpuState()
                updateTimeStats()
                delay(500)
            }
        }

        // Medium Poll: Battery, Thermal, Configs & Feature States (2s)
        scope.launch {
            while (isActive) {
                updateBatteryState()
                updateThermalState()
                updateChargingConfig()
                updateUndervoltState()
                updateAZenithState() 
                delay(2000)
            }
        }

        // Slow Poll: Identity (60s)
        scope.launch {
            delay(500)
            while (isActive) {
                updateDeviceInfo()
                delay(60000)
            }
        }
    }

    private fun updateEnforceDozeState() {
        val prefs = context.getSharedPreferences("enforce_doze_prefs", Context.MODE_PRIVATE)
        _state.update { 
            it.copy(enforceDozeConfig = EnforceDozeConfig(
                isEnabled = prefs.getBoolean("service_enabled", false),
                delaySeconds = prefs.getInt("entry_delay", 0),
                disableSensors = prefs.getBoolean("disable_sensors", false),
                disableWifi = prefs.getBoolean("disable_wifi", false),
                disableData = prefs.getBoolean("disable_data", false)
            ))
        }
    }

    fun setEnforceDozeEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences("enforce_doze_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("service_enabled", enabled).apply()
        
        val intent = Intent(context, EnforceDozeService::class.java)
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
        updateEnforceDozeState()
    }

    fun updateEnforceDozeSetting(key: String, value: Any) {
        val prefs = context.getSharedPreferences("enforce_doze_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is String -> editor.putString(key, value)
        }
        editor.apply()
        
        if (prefs.getBoolean("service_enabled", false)) {
            val intent = Intent(context, EnforceDozeService::class.java)
            intent.action = EnforceDozeService.ACTION_RELOAD_SETTINGS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        updateEnforceDozeState()
    }

    private fun updateDeviceInfo() {
        val rawModel = Build.MODEL
        val board = Build.BOARD
        val hardware = Build.HARDWARE
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

        val propName = SystemProps.get("ro.vendor.oplus.market.name")
            .ifEmpty { SystemProps.get("ro.vendor.oplus.market.ename") }
        
        val marketName = if (propName.isNotEmpty()) {
            propName.replace("[", "").replace("]", "")
        } else {
            deviceRepo.getMarketName(rawModel)
        }
        
        val socModel = SystemProps.get("ro.soc.model")
        val vendorSocModel = SystemProps.get("ro.vendor.soc.model")
        val socExtName = SystemProps.get("ro.vendor.soc.model.external_name")
        val socPartName = SystemProps.get("ro.vendor.soc.model.part_name")
        val socManuf = SystemProps.get("ro.soc.manufacturer")

        val socEntry = deviceRepo.identifySoc(socModel, vendorSocModel, socExtName, socPartName, board, hardware)

        val finalSocName = if (socEntry != null) {
            val vendor = if (socEntry.vendor.isNotEmpty()) socEntry.vendor else socManuf
            "$vendor ${socEntry.name}".trim()
        } else {
            if (socManuf.isNotEmpty() && socModel.isNotEmpty()) {
                "$socManuf $socModel"
            } else if (socModel.isNotEmpty()) {
                 socModel
            } else {
                val cpuInfo = readSysFs("/proc/cpuinfo")
                val hwLine = cpuInfo?.lines()?.find { it.contains("Hardware") }
                val hwVal = hwLine?.split(":")?.getOrNull(1)?.trim()
                hwVal ?: board.uppercase()
            }
        }

        _state.update { 
            it.copy(deviceInfo = DeviceInfo(manufacturer, rawModel, marketName, finalSocName)) 
        }
    }

    private fun updateChargingConfig() {
        val autoCutState = SystemProps.get("persist.sys.rianixia.autocut.state") == "1"
        val autoCutVal = SystemProps.get("persist.sys.rianixia.autocut.percent").toIntOrNull() ?: 85
        val bypassState = SystemProps.get("persist.sys.rianixia.bypass_charge.state") == "1"
        val bypassVal = SystemProps.get("persist.sys.rianixia.bypass_charge.threshold").toIntOrNull() ?: 20
        val tempState = SystemProps.get("persist.sys.rianixia.thermal_charge_cut-off.state") == "1"

        val undervoltSupport = SystemProps.get("persist.sys.rianixia.undervolt.support") == "1"

        _state.update {
            it.copy(
                chargingConfig = ChargingConfig(
                    autoCutEnabled = autoCutState,
                    autoCutLimit = autoCutVal,
                    bypassEnabled = bypassState,
                    bypassThreshold = bypassVal,
                    tempCutoffEnabled = tempState
                ),
                isUndervoltSupported = undervoltSupport
            )
        }
    }
    
    private fun updateUndervoltState() {
        val clusters = listOf("big", "bl", "little", "cci", "gpu", "gpu-high")
        var isActive = false
        
        for (cluster in clusters) {
            val value = SystemProps.get("persist.sys.rianixia.undervolt-cluster.$cluster").toIntOrNull() ?: 0
            if (value < 0) {
                isActive = true
                break
            }
        }
        
        _state.update { it.copy(isUndervoltActive = isActive) }
    }

    private fun updateAZenithState() {
        val isGlobalEnabled = SystemProps.get("persist.sys.rianixia.azenith.global") == "1"
        _state.update { it.copy(isAZenithActive = isGlobalEnabled) }
    }

    private fun updateBatteryState() {
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)

            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val statusInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING || 
                           statusInt == BatteryManager.BATTERY_STATUS_FULL
            
            val statusText = when(statusInt) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> "Unknown"
            }

            val tempInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempC = tempInt / 10f

            var healthPercentVal = readSysFs("/sys/devices/platform/charger/battery_health_percent")
            if (healthPercentVal.isNullOrEmpty()) {
                val fallback = SystemProps.get("persist.sys.rianixia.battery.health")
                healthPercentVal = if (fallback.isNotEmpty()) fallback else null
            }

            var cycleCountVal = readSysFs("/sys/devices/platform/charger/tran_battery_cycle")
            if (cycleCountVal.isNullOrEmpty()) {
                val fallback = SystemProps.get("persist.sys.rianixia.battery.cycles")
                cycleCountVal = if (fallback.isNotEmpty()) fallback else null
            }

            _state.update { 
                it.copy(
                    batteryInfo = BatteryInfo(
                        levelPercent = level,
                        status = statusText,
                        temperature = tempC,
                        healthPercent = healthPercentVal ?: "--",
                        cycleCount = cycleCountVal ?: "--",
                        isCharging = isCharging
                    )
                )
            }
        } catch (e: Exception) { 
            e.printStackTrace()
        }
    }

    private fun updateThermalState() {
        val batteryTemp = _state.value.batteryInfo.temperature
        
        val modeProp = SystemProps.get("persist.sys.rianixia.thermal-mode").lowercase()
        val profile = when(modeProp) {
            "adaptive" -> ThermalProfile.ADAPTIVE
            "disabled" -> ThermalProfile.DISABLED
            "custom" -> ThermalProfile.CUSTOM
            else -> ThermalProfile.DEFAULT
        }
        
        var summary = "Optimal"
        var color = 0xFF4CAF50 

        when (profile) {
            ThermalProfile.DISABLED -> {
                summary = "DISABLED"
                color = 0xFFFF5252
            }
            ThermalProfile.CUSTOM -> {
                val limit = SystemProps.get("persist.sys.rianixia.thermal-custom").toIntOrNull() ?: 45
                summary = "Custom ($limit°C)"
                color = 0xFFAB47BC
            }
            ThermalProfile.ADAPTIVE -> {
                 summary = "Adaptive"
                 color = 0xFFFFB74D
            }
            ThermalProfile.DEFAULT -> {
                when {
                    batteryTemp > 41 -> { summary = "Throttling"; color = 0xFFFF5252 }
                    batteryTemp > 38 -> { summary = "Warm"; color = 0xFFFFC107 }
                    else -> { summary = "Optimal"; color = 0xFF4CAF50 }
                }
            }
        }

        _state.update {
            it.copy(thermalState = it.thermalState.copy(
                summary = summary,
                temperature = batteryTemp,
                color = color.toLong(),
                activeProfile = profile
            ))
        }
    }
    
    private fun updateCpuState() {
        try {
            var gov = readSysFs("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
            if (gov.isNullOrEmpty() || gov == "unknown") {
                gov = SystemProps.get("persist.sys.rianixia.cpu.gov")
            }
            if (gov.isNullOrEmpty()) gov = "unknown"
            
            var maxFreq = 0L
            val cpu0Freq = readSysFs("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")?.toLongOrNull() ?: 0L
            val numCores = Runtime.getRuntime().availableProcessors()
            
            for (i in 0 until numCores) {
                 val f = readSysFs("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")?.toLongOrNull() ?: 0L
                 if (f > maxFreq) maxFreq = f
            }

            val graphPoint = (maxFreq.toFloat() / 3500000f).coerceIn(0f, 1f)
            if (cpuLoadBuffer.size >= 40) cpuLoadBuffer.removeFirst()
            cpuLoadBuffer.addLast(graphPoint)

            val peakString = String.format("%.2f GHz", maxFreq / 1000000.0)

            _state.update {
                it.copy(cpuState = CpuState(
                    frequencies = listOf(String.format("%.2f", cpu0Freq / 1000000.0)),
                    activeGovernor = gov ?: "unknown",
                    peakFrequency = peakString,
                    graphPoints = cpuLoadBuffer.toList()
                ))
            }
        } catch (e: Exception) { }
    }
    
    private fun updateTimeStats() {
        val totalMillis = SystemClock.elapsedRealtime()
        val awakeMillis = SystemClock.uptimeMillis()
        val deepSleepMillis = totalMillis - awakeMillis
        
        val uHours = TimeUnit.MILLISECONDS.toHours(totalMillis)
        val uMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
        val uptimeStr = String.format("%dh %02dm", uHours, uMinutes)
        
        val dsHours = TimeUnit.MILLISECONDS.toHours(deepSleepMillis)
        val dsMinutes = TimeUnit.MILLISECONDS.toMinutes(deepSleepMillis) % 60
        val dsPercent = if (totalMillis > 0) ((deepSleepMillis.toDouble() / totalMillis.toDouble()) * 100).roundToInt() else 0
        
        val deepSleepStr = "$dsPercent% · ${dsHours}h ${dsMinutes}m"
        
        _state.update { it.copy(uptime = uptimeStr, deepSleep = deepSleepStr) }
    }

    // Pure File I/O for sysfs (Removed shell cat fallback)
    private fun readSysFs(path: String): String? {
        return try {
            File(path).takeIf { it.exists() && it.canRead() }?.readText()?.trim()
        } catch (e: Exception) { null }
    }

    // Wrappers for external consumers if needed
    fun setSystemProperty(key: String, value: String) {
        scope.launch(Dispatchers.IO) {
            SystemProps.set(key, value)
            delay(50) // Short delay for property service propagation
            updateChargingConfig()
        }
    }

    fun onCleared() {
        scope.cancel()
    }
}
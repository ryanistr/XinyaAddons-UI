package com.rianixia.settings.overlay.data

enum class ThermalProfile {
    DEFAULT, ADAPTIVE, DISABLED, CUSTOM
}

data class EnforceDozeConfig(
    val isEnabled: Boolean = false,
    val delaySeconds: Int = 0,
    val disableSensors: Boolean = false,
    val disableWifi: Boolean = false,
    val disableData: Boolean = false
)

data class HomeDashboardState(
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val batteryInfo: BatteryInfo = BatteryInfo(),
    val cpuState: CpuState = CpuState(),
    val thermalState: ThermalState = ThermalState(),
    val chargingConfig: ChargingConfig = ChargingConfig(),
    val uptime: String = "00:00:00",
    val deepSleep: String = "0% · 0h 0m",
    val enforceDozeConfig: EnforceDozeConfig = EnforceDozeConfig(),
    val isUndervoltSupported: Boolean = false,
    val isUndervoltActive: Boolean = false,
    val isAZenithActive: Boolean = false,
    val totalUndervoltValue: Int = 0
)

data class ChargingConfig(
    val autoCutEnabled: Boolean = false,
    val autoCutLimit: Int = 85,
    val bypassEnabled: Boolean = false,
    val bypassThreshold: Int = 20,
    val tempCutoffEnabled: Boolean = false
)

data class DeviceInfo(
    val manufacturer: String = "Unknown",
    val model: String = "Device",
    val marketName: String = "Loading...",
    val socName: String = "Detecting..."
)

data class BatteryInfo(
    val levelPercent: Int = 0,
    val status: String = "Discharging",
    val temperature: Float = 0f,
    val healthPercent: String = "--", 
    val cycleCount: String = "--",    
    val isCharging: Boolean = false
)

data class CpuState(
    val frequencies: List<String> = emptyList(),
    val activeGovernor: String = "unknown",
    val peakFrequency: String = "0.00 GHz",
    val graphPoints: List<Float> = List(40) { 0f }
)

data class ThermalState(
    val summary: String = "Normal",
    val temperature: Float = 0f,
    val color: Long = 0xFF4CAF50,
    val activeProfile: ThermalProfile = ThermalProfile.DEFAULT,
    val customThrottleLimit: Int = 45
)
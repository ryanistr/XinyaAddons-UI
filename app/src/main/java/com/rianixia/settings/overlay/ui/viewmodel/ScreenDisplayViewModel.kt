package com.rianixia.settings.overlay.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import com.rianixia.settings.overlay.services.ExtraDimService

data class ResolutionState(
    val currentRes: String = "Loading...",
    val physicalRes: String = "Unknown",
    val availableResolutions: List<String> = emptyList(),
    val pendingRes: String? = null,
    val originalRes: String? = null,
    val countdown: Int = 0
)

class ScreenDisplayViewModel(application: Application) : AndroidViewModel(application) {
    private val _vSyncEnabled = MutableStateFlow(true)
    val vSyncEnabled: StateFlow<Boolean> = _vSyncEnabled

    private val _extraDimEnabled = MutableStateFlow(false)
    val extraDimEnabled: StateFlow<Boolean> = _extraDimEnabled

    private val _extraDimIntensity = MutableStateFlow(0.5f)
    val extraDimIntensity: StateFlow<Float> = _extraDimIntensity

    private val _colorTemperature = MutableStateFlow(50f)
    val colorTemperature: StateFlow<Float> = _colorTemperature

    private val _fpsLock = MutableStateFlow(60)
    val fpsLock: StateFlow<Int> = _fpsLock

    private val _resState = MutableStateFlow(ResolutionState())
    val resState: StateFlow<ResolutionState> = _resState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        fetchDisplayStates()
        fetchCurrentResolution()
    }

    // --- Native Property Reflection (Bypasses 'sh' overhead) ---
    private fun setSystemProperty(key: String, value: String) {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val setMethod = clazz.getMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, key, value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSystemProperty(key: String, default: String = ""): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            val result = getMethod.invoke(null, key, default) as? String
            if (result.isNullOrBlank()) default else result
        } catch (e: Exception) { default }
    }

    private fun fetchDisplayStates() {
        viewModelScope.launch(Dispatchers.IO) {
            val vSyncState = getSystemProperty("persist.sys.rianixia.display.vsync", "-1")
            val extraDimState = getSystemProperty("persist.sys.rianixia.display.extradim", "0")
            val dimIntensityStr = getSystemProperty("persist.sys.rianixia.display.dim_intensity", "0.5")
            val dimIntensity = dimIntensityStr.toFloatOrNull() ?: 0.5f

            withContext(Dispatchers.Main) {
                _vSyncEnabled.value = vSyncState == "-1"
                _extraDimEnabled.value = extraDimState == "1"
                _extraDimIntensity.value = dimIntensity

                if (extraDimState == "1") {
                    applyExtraDimState(true, dimIntensity)
                }
            }
        }
    }

    fun fetchCurrentResolution() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = getSystemProperty("persist.sys.rianixia.res.current", "Unknown")
            val original = getSystemProperty("persist.sys.rianixia.res.original", "Unknown")
            val availableStr = getSystemProperty("persist.sys.rianixia.res.available", "")

            val dynamicResolutions = availableStr.split(",")
                .map { it.trim() } // Ensure clean parsing
                .filter { it.isNotBlank() }

            _resState.update {
                it.copy(
                    currentRes = current,
                    physicalRes = original,
                    availableResolutions = dynamicResolutions
                )
            }
        }
    }

    fun changeResolutionImmediate(newRes: String) {
        val current = _resState.value.currentRes
        if (current == newRes || _resState.value.pendingRes != null || (newRes == "Reset" && current == _resState.value.physicalRes)) return

        _resState.update { it.copy(pendingRes = newRes, originalRes = current, countdown = 10) }

        viewModelScope.launch(Dispatchers.IO) {
            setSystemProperty("persist.sys.rianixia.res.target", newRes)

            delay(1000) // Allow Rust daemon to apply via Binder and update props
            fetchCurrentResolution()

            withContext(Dispatchers.Main) {
                countdownJob?.cancel()
                countdownJob = launch {
                    for (i in 10 downTo 1) {
                        _resState.update { it.copy(countdown = i) }
                        delay(1000)
                    }
                    revertResolution()
                }
            }
        }
    }

    fun confirmResolution() {
        countdownJob?.cancel()
        _resState.update { it.copy(pendingRes = null, originalRes = null) }
        fetchCurrentResolution()
    }

    fun revertResolution() {
        countdownJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val original = _resState.value.originalRes
            if (original != null && original != "Unknown" && original != "Reset") {
                setSystemProperty("persist.sys.rianixia.res.target", original)
            } else {
                setSystemProperty("persist.sys.rianixia.res.target", "Reset")
            }
            delay(1000)
            _resState.update { it.copy(pendingRes = null, originalRes = null) }
            fetchCurrentResolution()
        }
    }

    // Still required for AppOps execution, as AppOps isn't tied to System Properties
    private fun runShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                result.append(line).append("\n")
            }
            process.waitFor()
            result.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun toggleVSync(enabled: Boolean) {
        _vSyncEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            setSystemProperty("persist.sys.rianixia.display.vsync", if (enabled) "-1" else "0")
        }
    }

    fun toggleExtraDim(enabled: Boolean) {
        _extraDimEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            setSystemProperty("persist.sys.rianixia.display.extradim", if (enabled) "1" else "0")
            applyExtraDimState(enabled, _extraDimIntensity.value)
        }
    }

    fun setExtraDimIntensity(intensity: Float) {
        _extraDimIntensity.value = intensity
        viewModelScope.launch(Dispatchers.IO) {
            setSystemProperty("persist.sys.rianixia.display.dim_intensity", intensity.toString())
        }
        if (_extraDimEnabled.value) {
            applyExtraDimState(true, intensity)
        }
    }

    private fun applyExtraDimState(enabled: Boolean, intensity: Float) {
        val context = getApplication<Application>()
        if (enabled) {
            runShellCommand("appops set com.rianixia.settings.overlay SYSTEM_ALERT_WINDOW allow")
            val intent = Intent(context, ExtraDimService::class.java).apply {
                action = "UPDATE_DIM"
                putExtra("INTENSITY", intensity)
            }
            context.startService(intent)
        } else {
            val intent = Intent(context, ExtraDimService::class.java).apply {
                action = "STOP_DIM"
            }
            context.startService(intent)
        }
    }

    fun setColorTemperature(temp: Float) { _colorTemperature.value = temp }
    fun setFpsLock(fps: Int) { _fpsLock.value = fps }
}
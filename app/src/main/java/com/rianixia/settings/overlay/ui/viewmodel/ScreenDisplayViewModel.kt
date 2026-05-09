package com.rianixia.settings.overlay.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
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
    
    companion object {
        private const val TAG = "ScreenDisplayVM"
    }

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
        Log.i(TAG, "Initializing ScreenDisplayViewModel...")
        fetchDisplayStates()
        fetchCurrentResolution()
    }

    private fun setSystemProperty(key: String, value: String) {
        Log.d(TAG, "setSystemProperty: Requesting write -> [$key] = [$value]")
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val setMethod = clazz.getMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, key, value)
            Log.d(TAG, "setSystemProperty: SUCCESS -> [$key] = [$value]")
        } catch (e: Exception) {
            Log.e(TAG, "setSystemProperty: FAILED to write [$key]", e)
            e.printStackTrace()
        }
    }

    private fun getSystemProperty(key: String, default: String = ""): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            val result = getMethod.invoke(null, key, default) as? String
            val finalResult = if (result.isNullOrBlank()) default else result
            Log.d(TAG, "getSystemProperty: Read [$key] -> [$finalResult]")
            finalResult
        } catch (e: Exception) {
            Log.e(TAG, "getSystemProperty: FAILED to read [$key], falling back to default [$default]", e)
            default
        }
    }

    fun fetchDisplayStates() {
        Log.d(TAG, "fetchDisplayStates: Starting state fetch")
        viewModelScope.launch(Dispatchers.IO) {
            val vSyncState = getSystemProperty("persist.sys.rianixia.display.vsync", "-1")
            val extraDimState = getSystemProperty("persist.sys.rianixia.display.extradim", "0")
            val dimIntensityStr = getSystemProperty("persist.sys.rianixia.display.dim_intensity", "0.5")
            
            val dimIntensity = dimIntensityStr.toFloatOrNull() ?: 0.5f

            withContext(Dispatchers.Main) {
                _vSyncEnabled.value = vSyncState == "-1"
                _extraDimEnabled.value = extraDimState == "1"
                _extraDimIntensity.value = dimIntensity
                Log.d(TAG, "fetchDisplayStates: States updated in UI. VSync:${_vSyncEnabled.value}, ExtraDim:${_extraDimEnabled.value}")

                if (extraDimState == "1") {
                    applyExtraDimState(true, dimIntensity)
                }
            }
        }
    }

    fun fetchCurrentResolution() {
        Log.d(TAG, "fetchCurrentResolution: Starting fetch")
        viewModelScope.launch(Dispatchers.IO) {
            val current = getSystemProperty("persist.sys.rianixia.res.current", "Unknown")
            val original = getSystemProperty("persist.sys.rianixia.res.original", "Unknown")
            val availableStr = getSystemProperty("persist.sys.rianixia.res.available", "")
            
            val dynamicResolutions = availableStr.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            Log.d(TAG, "fetchCurrentResolution: Parsed UI Data -> Current: $current | Original: $original | Available: $dynamicResolutions")

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
        Log.i(TAG, "changeResolutionImmediate: Invoked for target -> $newRes")
        val current = _resState.value.currentRes
        
        if (current == newRes || _resState.value.pendingRes != null || (newRes == "Reset" && current == _resState.value.physicalRes)) {
            Log.w(TAG, "changeResolutionImmediate: Aborted due to redundant request or existing pending state.")
            return
        }

        Log.d(TAG, "changeResolutionImmediate: Transitioning to pending state for -> $newRes")
        _resState.update { it.copy(pendingRes = newRes, originalRes = current, countdown = 10) }
        
        viewModelScope.launch(Dispatchers.IO) {
            setSystemProperty("persist.sys.rianixia.res.target", newRes)
            delay(1000)
            
            fetchCurrentResolution()
            
            withContext(Dispatchers.Main) {
                countdownJob?.cancel()
                countdownJob = launch {
                    for (i in 10 downTo 1) {
                        _resState.update { it.copy(countdown = i) }
                        delay(1000)
                    }
                    Log.w(TAG, "changeResolutionImmediate: Countdown expired. Reverting.")
                    revertResolution()
                }
            }
        }
    }

    fun confirmResolution() {
        Log.i(TAG, "confirmResolution: User confirmed resolution changes")
        countdownJob?.cancel()
        _resState.update { it.copy(pendingRes = null, originalRes = null) }
        fetchCurrentResolution()
    }

    fun revertResolution() {
        Log.i(TAG, "revertResolution: Reverting to original resolution")
        countdownJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val original = _resState.value.originalRes
            if (original != null && original != "Unknown" && original != "Reset") {
                Log.d(TAG, "revertResolution: Restoring original -> $original")
                setSystemProperty("persist.sys.rianixia.res.target", original)
            } else {
                Log.d(TAG, "revertResolution: Resetting to native")
                setSystemProperty("persist.sys.rianixia.res.target", "Reset")
            }
            delay(1000)
            _resState.update { it.copy(pendingRes = null, originalRes = null) }
            fetchCurrentResolution()
        }
    }

    private fun runShellCommand(command: String): String {
        Log.d(TAG, "runShellCommand: Executing -> $command")
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                result.append(line).append("\n")
            }
            process.waitFor()
            val output = result.toString().trim()
            Log.d(TAG, "runShellCommand: Output -> $output")
            output
        } catch (e: Exception) {
            Log.e(TAG, "runShellCommand: Failed", e)
            ""
        }
    }

    fun toggleVSync(enabled: Boolean) {
        Log.d(TAG, "toggleVSync: Invoked -> $enabled")
        _vSyncEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            setSystemProperty("persist.sys.rianixia.display.vsync", if (enabled) "-1" else "0")
        }
    }

    fun toggleExtraDim(enabled: Boolean) {
        Log.d(TAG, "toggleExtraDim: Invoked -> $enabled")
        _extraDimEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            setSystemProperty("persist.sys.rianixia.display.extradim", if (enabled) "1" else "0")
            applyExtraDimState(enabled, _extraDimIntensity.value)
        }
    }

    fun setExtraDimIntensity(intensity: Float) {
        Log.d(TAG, "setExtraDimIntensity: Invoked -> $intensity")
        _extraDimIntensity.value = intensity
        viewModelScope.launch(Dispatchers.IO) {
            setSystemProperty("persist.sys.rianixia.display.dim_intensity", intensity.toString())
        }
        if (_extraDimEnabled.value) {
            applyExtraDimState(true, intensity)
        }
    }

    private fun applyExtraDimState(enabled: Boolean, intensity: Float) {
        Log.d(TAG, "applyExtraDimState: Routing to ExtraDimService -> Enabled: $enabled | Intensity: $intensity")
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
package com.rianixia.settings.overlay.ui.viewmodel

import android.app.Application
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rianixia.settings.overlay.services.ExtraDimService
import com.rianixia.settings.overlay.services.tiles.VSyncTileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

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

    private val _redVal = MutableStateFlow(1000f)
    val redVal: StateFlow<Float> = _redVal.asStateFlow()
    private val _greenVal = MutableStateFlow(1000f)
    val greenVal: StateFlow<Float> = _greenVal.asStateFlow()
    private val _blueVal = MutableStateFlow(1000f)
    val blueVal: StateFlow<Float> = _blueVal.asStateFlow()
    private val _saturationVal = MutableStateFlow(1000f)
    val saturationVal: StateFlow<Float> = _saturationVal.asStateFlow()

    private val _colorTemperature = MutableStateFlow(1000f) // 0 (Warm) - 2000 (Cold), 1000 (Neutral)
    val colorTemperature: StateFlow<Float> = _colorTemperature.asStateFlow()

    private val _presets = MutableStateFlow<Map<String, String>>(emptyMap())
    val presets: StateFlow<Map<String, String>> = _presets.asStateFlow()

    private val _fpsLock = MutableStateFlow(60)
    val fpsLock: StateFlow<Int> = _fpsLock

    private val _resState = MutableStateFlow(ResolutionState())
    val resState: StateFlow<ResolutionState> = _resState.asStateFlow()

    private var countdownJob: Job? = null

    private val vsyncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VSyncTileService.ACTION_VSYNC_CHANGED) {
                fetchDisplayStates()
            }
        }
    }

    init {
        Log.i(TAG, "Initializing ScreenDisplayViewModel...")
        
        val filter = IntentFilter(VSyncTileService.ACTION_VSYNC_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(vsyncReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(vsyncReceiver, filter)
        }

        fetchDisplayStates()
        fetchCurrentResolution()
        loadColorCalibration()
        loadPresets()
    }

    private fun loadPresets() {
        val prefs = getApplication<Application>().getSharedPreferences("color_presets", Context.MODE_PRIVATE)
        _presets.value = prefs.all.mapValues { it.value.toString() }
    }

    fun savePreset(name: String) {
        val prefs = getApplication<Application>().getSharedPreferences("color_presets", Context.MODE_PRIVATE)
        val config = "${_redVal.value.toInt()} ${_greenVal.value.toInt()} ${_blueVal.value.toInt()} ${_saturationVal.value.toInt()} ${_colorTemperature.value.toInt()}"
        prefs.edit().putString(name, config).apply()
        loadPresets()
    }

    fun loadPreset(name: String) {
        val config = _presets.value[name] ?: return
        val parts = config.split(" ").mapNotNull { it.toFloatOrNull() }
        if (parts.size >= 4) {
            _redVal.value = parts[0]
            _greenVal.value = parts[1]
            _blueVal.value = parts[2]
            _saturationVal.value = parts[3]
            if (parts.size >= 5) {
                _colorTemperature.value = parts[4]
            }
            applyColorCalibration()
            applySaturation()
            saveColorCalibration()
        }
    }

    fun deletePreset(name: String) {
        val prefs = getApplication<Application>().getSharedPreferences("color_presets", Context.MODE_PRIVATE)
        prefs.edit().remove(name).apply()
        loadPresets()
    }

    fun exportPresetToFile(context: Context, name: String) {
        val config = _presets.value[name] ?: return
        val parts = config.split(" ").map { it.toIntOrNull() ?: 1000 }
        val json = JSONObject().apply {
            put("name", name)
            put("red", parts[0])
            put("green", parts[1])
            put("blue", parts[2])
            put("saturation", parts[3])
            if (parts.size >= 5) put("temperature", parts[4])
        }.toString(4)

        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Preset_${name.replace(" ", "_")}.json")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                Toast.makeText(context, "Preset exported to Downloads", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export preset", e)
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun importPresetFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.use { it.readText() }
                val json = JSONObject(jsonString)
                
                val name = json.optString("name", "Imported_${System.currentTimeMillis()}")
                val red = json.optInt("red", 1000)
                val green = json.optInt("green", 1000)
                val blue = json.optInt("blue", 1000)
                val saturation = json.optInt("saturation", 1000)
                val temperature = json.optInt("temperature", 1000)

                val config = "$red $green $blue $saturation $temperature"
                
                withContext(Dispatchers.Main) {
                    val prefs = context.getSharedPreferences("color_presets", Context.MODE_PRIVATE)
                    prefs.edit().putString(name, config).apply()
                    loadPresets()
                    Toast.makeText(context, "Preset '$name' imported", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import preset", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: Invalid file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadColorCalibration() {
        viewModelScope.launch(Dispatchers.IO) {
            val config = getSystemProperty("persist.sys.rianixia.display.schemeconfig", "1000 1000 1000 1000 1000")
            val parts = config.split(" ").mapNotNull { it.toFloatOrNull() }
            if (parts.size >= 4) {
                withContext(Dispatchers.Main) {
                    _redVal.value = parts[0]
                    _greenVal.value = parts[1]
                    _blueVal.value = parts[2]
                    _saturationVal.value = parts[3]
                    if (parts.size >= 5) {
                        _colorTemperature.value = parts[4]
                    }
                    
                    applyColorCalibration()
                    applySaturation()
                }
            }
        }
    }

    private fun saveColorCalibration() {
        viewModelScope.launch(Dispatchers.IO) {
            val config = "${_redVal.value.toInt()} ${_greenVal.value.toInt()} ${_blueVal.value.toInt()} ${_saturationVal.value.toInt()} ${_colorTemperature.value.toInt()}"
            setSystemProperty("persist.sys.rianixia.display.schemeconfig", config)
        }
    }

    fun updateColorCalibration(red: Float? = null, green: Float? = null, blue: Float? = null, saturation: Float? = null, temperature: Float? = null) {
        red?.let { _redVal.value = it }
        green?.let { _greenVal.value = it }
        blue?.let { _blueVal.value = it }
        saturation?.let { _saturationVal.value = it }
        temperature?.let { _colorTemperature.value = it }
        
        if (saturation != null) {
            applySaturation()
        } else {
            applyColorCalibration()
        }
    }

    fun finishColorCalibration() {
        saveColorCalibration()
    }

    fun resetColorCalibration() {
        _redVal.value = 1000f
        _greenVal.value = 1000f
        _blueVal.value = 1000f
        _saturationVal.value = 1000f
        _colorTemperature.value = 1000f
        
        applyColorCalibration()
        applySaturation()
        saveColorCalibration()
    }

    private fun applyColorCalibration() {
        val tempFactor = (_colorTemperature.value - 1000f) / 1000f // -1.0 (Warm) to 1.0 (Cold)
        
        var r = _redVal.value / 1000f
        var g = _greenVal.value / 1000f
        var b = _blueVal.value / 1000f
        
        if (tempFactor < 0) { // Warm: Red up, Blue down
            r += (-tempFactor * 0.15f)
            b -= (-tempFactor * 0.15f)
        } else if (tempFactor > 0) { // Cold: Blue up, Red down
            b += (tempFactor * 0.15f)
            r -= (tempFactor * 0.15f)
        }
        
        r = r.coerceIn(0f, 2f)
        b = b.coerceIn(0f, 2f)
        
        // Service call 1015 for RGB matrix calibration
        val cmd = "service call SurfaceFlinger 1015 i32 1 f $r f 0 f 0 f 0 f 0 f $g f 0 f 0 f 0 f 0 f $b f 0 f 0 f 0 f 0 f 1"
        viewModelScope.launch(Dispatchers.IO) {
            runShellCommand(cmd)
        }
    }

    private fun applySaturation() {
        val s = _saturationVal.value / 1000f
        // Service call 1022 for Saturation
        val cmd = "service call SurfaceFlinger 1022 f $s"
        viewModelScope.launch(Dispatchers.IO) {
            runShellCommand(cmd)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(vsyncReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver", e)
        }
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
            
            withContext(Dispatchers.Main) {
                val context = getApplication<Application>()
                TileService.requestListeningState(
                    context,
                    ComponentName(context, VSyncTileService::class.java)
                )
            }
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
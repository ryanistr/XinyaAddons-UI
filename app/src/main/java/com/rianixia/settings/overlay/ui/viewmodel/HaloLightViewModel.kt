package com.rianixia.settings.overlay.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

class HaloLightViewModel : ViewModel() {
    private val _isHaloEnabled = MutableStateFlow(false)
    val isHaloEnabled: StateFlow<Boolean> = _isHaloEnabled

    private val _brightness = MutableStateFlow(100f)
    val brightness: StateFlow<Float> = _brightness

    private val _activeEffect = MutableStateFlow("Static")
    val activeEffect: StateFlow<String> = _activeEffect

    init {
        loadState()
    }

    private fun loadState() {
        viewModelScope.launch(Dispatchers.IO) {
            val animProp = getSystemProperty("persist.sys.rianixia.halolight.anim")
            val powerProp = getSystemProperty("persist.sys.rianixia.halolight.power").toFloatOrNull() ?: 100f
            
            // Check if light is actually on based on prop value[cite: 1, 2]
            val enabled = animProp.isNotEmpty() && !animProp.equals("off", ignoreCase = true) && animProp != "0"
            
            _isHaloEnabled.value = enabled
            if (enabled) {
                _activeEffect.value = animProp
            }
            _brightness.value = powerProp
        }
    }

    fun toggleHalo(enabled: Boolean) {
        _isHaloEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            if (enabled) {
                // Restore previous or default effect when toggling ON
                setSystemProperty("persist.sys.rianixia.halolight.anim", _activeEffect.value)
            } else {
                // Send "off" to trigger the turnoff() logic in the daemon
                setSystemProperty("persist.sys.rianixia.halolight.anim", "off")
            }
        }
    }

    fun setBrightness(value: Float) {
        _brightness.value = value
        viewModelScope.launch(Dispatchers.IO) {
            // Write brightness directly to the power prop observed by the daemon[cite: 1, 2]
            setSystemProperty("persist.sys.rianixia.halolight.power", value.toInt().toString())
        }
    }

    fun setEffect(effect: String) {
        _activeEffect.value = effect
        if (_isHaloEnabled.value) {
            viewModelScope.launch(Dispatchers.IO) {
                // Update the anim prop observed by the daemon[cite: 1, 2]
                setSystemProperty("persist.sys.rianixia.halolight.anim", effect)
            }
        }
    }

    private fun setSystemProperty(key: String, value: String) {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val setMethod = clazz.getMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, key, value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            getMethod.invoke(null, key, "") as? String ?: ""
        } catch (e: Exception) { "" }
    }
}
package com.rianixia.settings.overlay.ui.viewmodel

import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.rianixia.settings.overlay.data.AppPreferences
import java.io.BufferedReader
import java.io.InputStreamReader

data class SystemTunerState(
    val isFlagSecureDisabled: Boolean = false,
    val isRotationButtonHidden: Boolean = false,
    val flashlightQsMode: Int = 0
)

class SystemViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SystemTunerState())
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val TAG = "SystemViewModel"
    }

    // Property Keys
    private val PROP_DISABLE_SECURE = "persist.sys.rianixia.disable_flag_secure"
    private val SETTING_ROTATION = "show_rotation_suggestions"

    init {
        Log.i(TAG, "Initializing SystemViewModel...")
        loadState()
    }

    private fun loadState() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "loadState: Starting state refresh")

            // 1. Check System Property (Flag Secure)
            val secureProp = getSystemProperty(PROP_DISABLE_SECURE)
            val secure = secureProp == "1"
            
            // 2. Check Secure Setting (Rotation)
            val rotationValue = try {
                Settings.Secure.getInt(
                    getApplication<Application>().contentResolver, 
                    SETTING_ROTATION
                )
            } catch (e: Settings.SettingNotFoundException) {
                Log.w(TAG, "loadState: Rotation setting not found, defaulting to 1")
                1 // Default 1 = Shown
            }
            // Value 0 means Hidden. Value 1 means Shown.
            val rotationHidden = rotationValue == 0
            
            val flashQsMode = AppPreferences.getFlashlightQsMode(getApplication<Application>())

            _uiState.update { 
                it.copy(
                    isFlagSecureDisabled = secure, 
                    isRotationButtonHidden = rotationHidden,
                    flashlightQsMode = flashQsMode
                ) 
            }
            Log.i(TAG, "loadState: Updated. SecureDisabled=$secure, RotationHidden=$rotationHidden, FlashlightQsMode=$flashQsMode")
        }
    }

    fun toggleFlagSecure(enabled: Boolean) {
        Log.i(TAG, "toggleFlagSecure: Requesting toggle to $enabled")
        viewModelScope.launch(Dispatchers.IO) {
            val newVal = if (enabled) "1" else "0"
            if (setSystemProperty(PROP_DISABLE_SECURE, newVal)) {
                _uiState.update { it.copy(isFlagSecureDisabled = enabled) }
                Log.i(TAG, "toggleFlagSecure: Success")
            } else {
                Log.e(TAG, "toggleFlagSecure: Failed. Permissions missing?")
            }
        }
    }

    fun toggleRotationButton(isHidden: Boolean) {
        Log.i(TAG, "toggleRotationButton: User toggled. Target Hidden State: $isHidden")
        viewModelScope.launch(Dispatchers.IO) {
            val newVal = if (isHidden) 0 else 1
            try {
                val success = Settings.Secure.putInt(
                    getApplication<Application>().contentResolver,
                    SETTING_ROTATION,
                    newVal
                )
                if (success) {
                    _uiState.update { it.copy(isRotationButtonHidden = isHidden) }
                } else {
                    Log.e(TAG, "toggleRotationButton: Failed. Write returned false.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "toggleRotationButton: Unexpected error", e)
            }
        }
    }

    fun setFlashlightQsMode(mode: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            AppPreferences.setFlashlightQsMode(getApplication(), mode)
            _uiState.update { it.copy(flashlightQsMode = mode) }
        }
    }

    private fun getSystemProperty(key: String): String {
        return runShellCommand("getprop $key")
    }

    private fun setSystemProperty(key: String, value: String): Boolean {
        return runShellCommandBool("setprop $key $value")
    }

    private fun runShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()?.trim() ?: ""
            process.destroy()
            result
        } catch (e: Exception) { 
            Log.e(TAG, "runShellCommand: Error executing '$command'", e)
            "" 
        }
    }

    private fun runShellCommandBool(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val exitCode = process.waitFor()
            process.destroy()
            exitCode == 0
        } catch (e: Exception) { 
            Log.e(TAG, "runShellCommandBool: Error executing '$command'", e)
            false 
        }
    }
}
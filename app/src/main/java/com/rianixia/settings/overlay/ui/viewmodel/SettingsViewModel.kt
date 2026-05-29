package com.rianixia.settings.overlay.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rianixia.settings.overlay.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppSettingsState(
    val isLauncherIconEnabled: Boolean = false,
    val isSafetyModeEnabled: Boolean = true,
    val flashlightQsMode: Int = 0
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppSettingsState())
    val uiState = _uiState.asStateFlow()

    init {
        loadState()
    }

    private fun loadState() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            
            // Fetch states from the centralized AppPreferences
            val isIconEnabled = AppPreferences.getLauncherIconState(context)
            val isSafetyEnabled = AppPreferences.getSafetyMode()
            val flashQsMode = AppPreferences.getFlashlightQsMode(context)

            _uiState.update { 
                it.copy(
                    isLauncherIconEnabled = isIconEnabled,
                    isSafetyModeEnabled = isSafetyEnabled,
                    flashlightQsMode = flashQsMode
                ) 
            }
        }
    }

    fun toggleLauncherIcon(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            AppPreferences.setLauncherIconState(getApplication(), enabled)
            _uiState.update { it.copy(isLauncherIconEnabled = enabled) }
        }
    }

    fun toggleSafetyMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            AppPreferences.setSafetyMode(enabled)
            _uiState.update { it.copy(isSafetyModeEnabled = enabled) }
        }
    }

    fun setFlashlightQsMode(mode: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            AppPreferences.setFlashlightQsMode(getApplication(), mode)
            _uiState.update { it.copy(flashlightQsMode = mode) }
        }
    }
}
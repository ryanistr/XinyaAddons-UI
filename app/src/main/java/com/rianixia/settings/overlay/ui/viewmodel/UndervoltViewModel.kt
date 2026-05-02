package com.rianixia.settings.overlay.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// -- Data Models --

data class VoltageParam(
    val id: String,
    val label: String,
    val value: Int = 0,
    val isManualInput: Boolean = false, // Toggles between Slider and TextField
    val defaultVal: Int = 0,
    val range: IntRange = 0..30
)

data class UndervoltUiState(
    val isMasterEnabled: Boolean = false,
    val cpuParams: List<VoltageParam> = listOf(
        VoltageParam("cpu_b", "Big Core (B)"),
        VoltageParam("cpu_bl", "Big–Little (BL)"),
        VoltageParam("cpu_l", "Little Core (L)"),
        VoltageParam("cpu_cci", "CCI")
    ),
    val gpuParams: List<VoltageParam> = listOf(
        VoltageParam("gpu_u", "GPU Undervolt"),
        VoltageParam("gpu_h", "GPU High Undervolt")
    )
)

// -- ViewModel --

class UndervoltViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UndervoltUiState())
    val uiState: StateFlow<UndervoltUiState> = _uiState.asStateFlow()

    // Simulate loading saved prefs
    init {
        // In a real app, load from DataStore here
    }

    fun toggleMaster(enabled: Boolean) {
        _uiState.update { it.copy(isMasterEnabled = enabled) }
    }

    fun updateValue(id: String, newValue: Int) {
        // Validation logic can go here (e.g., simplistic clamp if strict)
        // For manual input, we allow exceeding bounds, so we just pass it through
        _uiState.update { state ->
            state.copy(
                cpuParams = state.cpuParams.map { if (it.id == id) it.copy(value = newValue) else it },
                gpuParams = state.gpuParams.map { if (it.id == id) it.copy(value = newValue) else it }
            )
        }
    }

    fun toggleInputMode(id: String) {
        _uiState.update { state ->
            state.copy(
                cpuParams = state.cpuParams.map { if (it.id == id) it.copy(isManualInput = !it.isManualInput) else it },
                gpuParams = state.gpuParams.map { if (it.id == id) it.copy(isManualInput = !it.isManualInput) else it }
            )
        }
    }

    fun resetToDefaults() {
        _uiState.update { state ->
            state.copy(
                cpuParams = state.cpuParams.map { it.copy(value = it.defaultVal, isManualInput = false) },
                gpuParams = state.gpuParams.map { it.copy(value = it.defaultVal, isManualInput = false) }
            )
        }
    }
}
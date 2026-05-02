package com.rianixia.settings.overlay.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.megatronking.stringfog.annotation.StringFogIgnore
import com.rianixia.settings.overlay.data.IORepository
import com.rianixia.settings.overlay.data.IoDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class IOUiState(
    val isLoading: Boolean = true,
    val devices: List<IoDevice> = emptyList(),
    val unifiedAvailableSchedulers: List<String> = emptyList(),
    val currentEffectiveScheduler: String = "unknown",
    val targetScheduler: String = "",
    val isMixedState: Boolean = false
)

@StringFogIgnore
interface IoEvent {
    @StringFogIgnore
    data class SchedulerChanged(val schedulerName: String) : IoEvent
}

@StringFogIgnore
class IOViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(IOUiState())
    val uiState: StateFlow<IOUiState> = _uiState.asStateFlow()

    private val _events = Channel<IoEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var monitoringJob: Job? = null
    
    // Track previous state to determine if a change occurred for Toast triggers
    private var previousDevicesState: Map<String, String> = emptyMap()

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = viewModelScope.launch {
            // Initial load
            refreshData(isInitial = true)
            
            // Polling loop (Property Observer fallback)
            while (isActive) {
                delay(2000) // 2-second polling interval
                refreshData(isInitial = false)
            }
        }
    }

    private suspend fun refreshData(isInitial: Boolean) {
        val devices = IORepository.getIoDevices()
        val savedTarget = IORepository.getTargetScheduler()

        if (devices.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, devices = emptyList()) }
            return
        }

        // 1. Calculate Unified State
        val allSchedulers = devices.flatMap { it.availableSchedulers }.toSet().sorted()
        val firstActive = devices.first().currentScheduler
        val isMixed = devices.any { it.currentScheduler != firstActive }
        val effectiveCurrent = if (isMixed) "Mixed" else firstActive

        // 2. Toast Logic: Check for confirmed changes
        if (!isInitial) {
            checkForStateChange(devices)
        }
        
        // Update internal tracking map
        previousDevicesState = devices.associate { it.name to it.currentScheduler }

        // 3. Update UI State
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                devices = devices,
                unifiedAvailableSchedulers = allSchedulers,
                currentEffectiveScheduler = effectiveCurrent,
                targetScheduler = savedTarget.ifBlank { effectiveCurrent },
                isMixedState = isMixed
            )
        }
    }

    private suspend fun checkForStateChange(newDevices: List<IoDevice>) {
        if (previousDevicesState.isEmpty()) return

        val changedDevice = newDevices.find { device ->
            val oldSched = previousDevicesState[device.name]
            oldSched != null && oldSched != device.currentScheduler
        }

        if (changedDevice != null) {
            _events.send(IoEvent.SchedulerChanged(changedDevice.currentScheduler))
        }
    }

    fun setScheduler(scheduler: String) {
        viewModelScope.launch {
            IORepository.setTargetScheduler(scheduler)
            delay(100)
            refreshData(isInitial = false)
        }
    }
}
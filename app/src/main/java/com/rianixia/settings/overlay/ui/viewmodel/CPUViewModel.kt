package com.rianixia.settings.overlay.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rianixia.settings.overlay.data.CPURepository
import com.rianixia.settings.overlay.data.CpuCluster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClusterState(
    val cluster: CpuCluster,
    val currentMin: Int,
    val currentMax: Int,
    val currentGov: String,
    val validMinOptions: List<Int>,
    val validMaxOptions: List<Int>
)

data class CPUUiState(
    val isLoading: Boolean = true,
    val isAdvancedMode: Boolean = false,
    val isUltraSaver: Boolean = false,
    val totalCores: Int = 0,
    val hasChanges: Boolean = false,
    
    val globalAvailableGovernors: List<String> = emptyList(),
    val currentGlobalGovernor: String = "",
    val globalScale: Float = 100f,
    
    val clusterStates: List<ClusterState> = emptyList()
)

class CPUViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CPUUiState())
    val uiState: StateFlow<CPUUiState> = _uiState.asStateFlow()

    private var initialState: CPUUiState? = null

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val clusters = CPURepository.getClusters()
            val globalGovs = CPURepository.getGlobalAvailableGovernors()
            
            val mode = CPURepository.getProp(CPURepository.Props.MODE, "0") == "1"
            val saver = CPURepository.getProp(CPURepository.Props.ULTRA_SAVER, "0") == "1"
            val currentGlobalGov = CPURepository.getProp(CPURepository.Props.GLOBAL_GOV, globalGovs.firstOrNull() ?: "schedutil")
            val scale = CPURepository.getProp(CPURepository.Props.GLOBAL_SCALE, "100").toFloatOrNull() ?: 100f

            val clusterStates = clusters.map { cluster ->
                // Ensure Min is at least Hardware Min
                val safeMinDefault = cluster.availableFreqs.filter { it >= cluster.systemMinFreq }.firstOrNull() ?: cluster.systemMinFreq
                val safeMaxDefault = cluster.availableFreqs.lastOrNull() ?: safeMinDefault

                var storedMin = CPURepository.getProp(CPURepository.Props.clusterMin(cluster.id), safeMinDefault.toString()).toInt()
                var storedMax = CPURepository.getProp(CPURepository.Props.clusterMax(cluster.id), safeMaxDefault.toString()).toInt()
                val storedGov = CPURepository.getProp(CPURepository.Props.clusterGov(cluster.id), cluster.availableGovs.firstOrNull() ?: "schedutil")

                // Enforce Constraints on Load
                if (storedMin < cluster.systemMinFreq) storedMin = cluster.systemMinFreq
                if (storedMax < storedMin) storedMax = storedMin

                val validMinOptions = cluster.availableFreqs.filter { it >= cluster.systemMinFreq }
                val validMaxOptions = cluster.availableFreqs.filter { it >= storedMin }

                ClusterState(cluster, storedMin, storedMax, storedGov, validMinOptions, validMaxOptions)
            }

            val totalCoreCount = clusters.sumOf { it.cores.size }

            val loadedState = CPUUiState(
                isLoading = false,
                isAdvancedMode = mode,
                isUltraSaver = saver,
                totalCores = totalCoreCount,
                hasChanges = false,
                globalAvailableGovernors = globalGovs,
                currentGlobalGovernor = currentGlobalGov,
                globalScale = scale,
                clusterStates = clusterStates
            )

            initialState = loadedState
            _uiState.value = loadedState
        }
    }

    private fun checkChanges(currentState: CPUUiState) {
        val initial = initialState ?: return
        val changed = currentState.isAdvancedMode != initial.isAdvancedMode ||
                      currentState.isUltraSaver != initial.isUltraSaver ||
                      currentState.currentGlobalGovernor != initial.currentGlobalGovernor ||
                      currentState.globalScale != initial.globalScale ||
                      currentState.clusterStates != initial.clusterStates
        
        _uiState.update { it.copy(hasChanges = changed) }
    }

    fun saveChanges() {
        val current = _uiState.value
        viewModelScope.launch {
            CPURepository.setProp(CPURepository.Props.MODE, if (current.isAdvancedMode) "1" else "0")
            CPURepository.setProp(CPURepository.Props.ULTRA_SAVER, if (current.isUltraSaver) "1" else "0")
            
            if (!current.isAdvancedMode) {
                CPURepository.setProp(CPURepository.Props.GLOBAL_GOV, current.currentGlobalGovernor)
                CPURepository.setProp(CPURepository.Props.GLOBAL_SCALE, current.globalScale.toInt().toString())
            } else {
                current.clusterStates.forEach { cluster ->
                    CPURepository.setProp(CPURepository.Props.clusterMin(cluster.cluster.id), cluster.currentMin.toString())
                    CPURepository.setProp(CPURepository.Props.clusterMax(cluster.cluster.id), cluster.currentMax.toString())
                    CPURepository.setProp(CPURepository.Props.clusterGov(cluster.cluster.id), cluster.currentGov)
                }
            }
            
            initialState = current.copy(hasChanges = false)
            _uiState.update { it.copy(hasChanges = false) }
        }
    }

    fun toggleMode(advanced: Boolean) {
        _uiState.update { 
            val newState = it.copy(isAdvancedMode = advanced)
            checkChanges(newState)
            newState
        }
    }

    fun toggleUltraSaver(enabled: Boolean) {
        _uiState.update { 
            val newState = it.copy(isUltraSaver = enabled)
            checkChanges(newState)
            newState
        }
    }

    fun setGlobalGovernor(gov: String) {
        _uiState.update { 
            val newState = it.copy(currentGlobalGovernor = gov)
            checkChanges(newState)
            newState
        }
    }

    fun setGlobalScale(value: Float) {
        _uiState.update { 
            val newState = it.copy(globalScale = value)
            checkChanges(newState)
            newState
        }
    }

    fun applyPreset(type: PresetType) {
        _uiState.update { state ->
            val newState = when(type) {
                PresetType.PERFORMANCE -> state.copy(globalScale = 100f, currentGlobalGovernor = "performance")
                PresetType.BALANCED -> state.copy(globalScale = 80f, currentGlobalGovernor = "schedutil")
                PresetType.POWERSAVE -> state.copy(globalScale = 50f, currentGlobalGovernor = "powersave")
            }
            checkChanges(newState)
            newState
        }
    }

    fun setClusterFreq(clusterId: Int, isMax: Boolean, freq: Int) {
        _uiState.update { state ->
            val newClusters = state.clusterStates.map { 
                if (it.cluster.id == clusterId) {
                    val cluster = it.cluster
                    var newMin = it.currentMin
                    var newMax = it.currentMax

                    if (isMax) {
                        newMax = freq
                        // Ensure Min is not > Max
                        if (newMin > newMax) newMin = newMax
                    } else {
                        newMin = freq
                        // Ensure Max is not < Min
                        if (newMax < newMin) newMax = newMin
                    }

                    // Recalculate valid options
                    val validMinOptions = cluster.availableFreqs.filter { f -> f >= cluster.systemMinFreq && f <= newMax }
                    val validMaxOptions = cluster.availableFreqs.filter { f -> f >= newMin }

                    it.copy(
                        currentMin = newMin,
                        currentMax = newMax,
                        validMinOptions = validMinOptions,
                        validMaxOptions = validMaxOptions
                    )
                } else it
            }
            val newState = state.copy(clusterStates = newClusters)
            checkChanges(newState)
            newState
        }
    }

    fun setClusterGovernor(clusterId: Int, gov: String) {
        _uiState.update { state ->
            val newClusters = state.clusterStates.map {
                if (it.cluster.id == clusterId) it.copy(currentGov = gov) else it
            }
            val newState = state.copy(clusterStates = newClusters)
            checkChanges(newState)
            newState
        }
    }

    fun setAllClusterGovernors(gov: String) {
        _uiState.update { state ->
            val newClusters = state.clusterStates.map { it.copy(currentGov = gov) }
            val newState = state.copy(clusterStates = newClusters)
            checkChanges(newState)
            newState
        }
    }
}

enum class PresetType { PERFORMANCE, BALANCED, POWERSAVE }
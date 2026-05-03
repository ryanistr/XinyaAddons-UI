// File: app/src/main/java/com/rianixia/settings/overlay/ui/viewmodel/AZenithViewModel.kt
package com.rianixia.settings.overlay.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?,
    val isEnabled: Boolean
)

data class AZenithState(
    val isGlobalEnabled: Boolean = false,
    val cpuFreqLimit: Float = 100f,
    val isDndEnabled: Boolean = false,
    val isMemCleanEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val apps: List<AppItem> = emptyList()
)

class AZenithViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(AZenithState())
    val uiState: StateFlow<AZenithState> = _uiState.asStateFlow()

    private val PROP_GLOBAL = "persist.sys.rianixia.azenith.global"
    private val PROP_CPU = "persist.sys.rianixia.azenith.cpu"
    private val PROP_DND = "persist.sys.rianixia.azenith.dnd"
    private val PROP_MEM = "persist.sys.rianixia.azenith.mem"
    private val gameListFile = File(application.filesDir, "gamelist.txt")

    init {
        ensureGamelistFileExists()
        loadState()
    }

    private fun ensureGamelistFileExists() {
        if (!gameListFile.exists()) {
            copyAssetToInternal()
        }
    }

    private fun copyAssetToInternal() {
        try {
            val assetManager = getApplication<Application>().assets
            val assets = assetManager.list("")
            if (assets?.contains("gamelist.txt") == true) {
                assetManager.open("gamelist.txt").use { input ->
                    gameListFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                gameListFile.writeText("")
            }
        } catch (e: Exception) {
            if(!gameListFile.exists()) gameListFile.writeText("")
        }
    }

    private fun loadState() {
        viewModelScope.launch {
            val global = withContext(Dispatchers.IO) { getSystemProperty(PROP_GLOBAL) == "1" }
            val cpu = withContext(Dispatchers.IO) { getSystemProperty(PROP_CPU).toIntOrNull()?.toFloat() ?: 100f }
            val dnd = withContext(Dispatchers.IO) { getSystemProperty(PROP_DND) == "1" }
            val mem = withContext(Dispatchers.IO) { getSystemProperty(PROP_MEM) == "1" }

            _uiState.update { 
                it.copy(
                    isGlobalEnabled = global,
                    cpuFreqLimit = cpu,
                    isDndEnabled = dnd,
                    isMemCleanEnabled = mem
                ) 
            }

            val appList = withContext(Dispatchers.IO) {
                loadInstalledApps()
            }
            
            _uiState.update { 
                it.copy(apps = appList, isLoading = false) 
            }
        }
    }

    private fun loadInstalledApps(): List<AppItem> {
        val pm = getApplication<Application>().packageManager
        val enabledPackages = if (gameListFile.exists()) {
            gameListFile.readLines().map { it.trim() }.toSet()
        } else {
            emptySet()
        }

        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        return installed.filter { appInfo ->
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }.map { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString()
            val pkg = appInfo.packageName
            val icon = pm.getApplicationIcon(appInfo).toBitmap().asImageBitmap()
            val enabled = enabledPackages.contains(pkg)
            AppItem(label, pkg, icon, enabled)
        }.sortedBy { it.label }
    }

    fun toggleGlobal(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            setSystemProperty(PROP_GLOBAL, if (enabled) "1" else "0")
            _uiState.update { it.copy(isGlobalEnabled = enabled) }
        }
    }

    fun setCpuLimit(limit: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            setSystemProperty(PROP_CPU, limit.toInt().toString())
            _uiState.update { it.copy(cpuFreqLimit = limit) }
        }
    }

    fun toggleDnd(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            setSystemProperty(PROP_DND, if (enabled) "1" else "0")
            _uiState.update { it.copy(isDndEnabled = enabled) }
        }
    }

    fun toggleMemClean(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            setSystemProperty(PROP_MEM, if (enabled) "1" else "0")
            _uiState.update { it.copy(isMemCleanEnabled = enabled) }
        }
    }

    fun toggleApp(packageName: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { state ->
                val updatedApps = state.apps.map { app ->
                    if (app.packageName == packageName) {
                        app.copy(isEnabled = enabled)
                    } else {
                        app
                    }
                }
                state.copy(apps = updatedApps)
            }

            val currentList = if (gameListFile.exists()) {
                gameListFile.readLines().map { it.trim() }.toMutableSet()
            } else {
                mutableSetOf()
            }

            if (enabled) {
                currentList.add(packageName)
            } else {
                currentList.remove(packageName)
            }

            try {
                gameListFile.writeText(currentList.joinToString("\n"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine()?.trim() ?: ""
        } catch (e: Exception) { "" }
    }

    private fun setSystemProperty(key: String, value: String) {
        try {
            Runtime.getRuntime().exec(arrayOf("setprop", key, value)).waitFor()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable) return this.bitmap
        
        val bitmap = Bitmap.createBitmap(
            intrinsicWidth.coerceAtLeast(1),
            intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}
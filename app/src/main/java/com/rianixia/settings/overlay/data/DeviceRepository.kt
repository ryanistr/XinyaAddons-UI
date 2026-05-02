package com.rianixia.settings.overlay.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class DeviceRepository(private val context: Context) {

    private val deviceMap = HashMap<String, String>() // Model Code -> Market Name
    private val socMap = HashMap<String, SocEntry>()  // Board/Hardware/Prop -> Soc Info

    data class SocEntry(
        val vendor: String,
        val name: String,
        val fab: String
    )

    suspend fun initialize() = withContext(Dispatchers.IO) {
        loadDeviceDatabase()
        loadSocDatabase()
    }

    private fun loadDeviceDatabase() {
        try {
            val jsonString = readAsset("device_info.json") ?: return
            val json = JSONObject(jsonString)
            val keys = json.keys()
            
            while (keys.hasNext()) {
                val marketName = keys.next()
                val modelCode = json.getString(marketName)
                deviceMap[modelCode] = marketName
            }
        } catch (e: Exception) {
            Log.e("DeviceRepo", "Failed to load device_info.json", e)
        }
    }

    private fun loadSocDatabase() {
        try {
            val jsonString = readAsset("soclist.json") ?: return
            val json = JSONObject(jsonString)
            val keys = json.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                val entry = json.getJSONObject(key)
                
                val vendor = entry.optString("VENDOR", "")
                val name = entry.optString("NAME", "Unknown SoC")
                val fab = entry.optString("FAB", "")

                val socEntry = SocEntry(vendor, name, fab)
                // Normalize keys to lowercase for easier matching
                socMap[key.lowercase()] = socEntry
            }
        } catch (e: Exception) {
            Log.e("DeviceRepo", "Failed to load soclist.json", e)
        }
    }

    private fun readAsset(fileName: String): String? {
        return try {
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getMarketName(model: String): String {
        return deviceMap[model] ?: run {
            val cleanModel = model.split("/").firstOrNull() ?: model
            deviceMap[cleanModel] ?: model
        }
    }

    /**
     * Tries to identify the SoC by checking a list of potential keys.
     */
    fun identifySoc(vararg keys: String): SocEntry? {
        for (key in keys) {
            if (key.isBlank()) continue
            val match = socMap[key.lowercase()]
            if (match != null) {
                Log.d("DeviceRepo", "SoC Identified: '$key' -> ${match.name}")
                return match
            }
        }
        return null
    }
}
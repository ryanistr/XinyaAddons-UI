package com.rianixia.settings.overlay.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object TorchRepository {
    private const val TAG = "TorchRepo"
    
    const val PATH_BACK_TORCH = "/sys/class/torch/torch/torch_level"
    const val PATH_FRONT_TORCH = "/sys/class/sub_torch/sub_torch/sub_torch_level"

    suspend fun getTorchLevel(path: String): Int = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists()) {
                val level = file.readText().trim().toIntOrNull() ?: 0
                return@withContext level
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read torch level from $path", e)
        }
        return@withContext 0
    }

    suspend fun setTorchLevel(path: String, level: Int) = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists() && file.canWrite()) {
                file.writeText(level.toString())
            } else {
                val cmd = "echo $level > $path"
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                p.waitFor()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set torch level $level to $path", e)
        }
    }
}

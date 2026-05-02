package com.rianixia.settings.overlay.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CpuCluster(
    val id: Int,
    val name: String,
    val cores: List<Int>,
    val availableFreqs: List<Int>, // In KHz, all possible steps
    val systemMinFreq: Int, // Hardware floor (cpuinfo_min_freq)
    val availableGovs: List<String>
)

object CPURepository {
    private const val CPU_BASE_PATH = "/sys/devices/system/cpu"
    private const val PROP_PREFIX = "persist.sys.rianixia.cpu."

    object Props {
        const val MODE = "${PROP_PREFIX}mode" 
        const val GLOBAL_GOV = "${PROP_PREFIX}gov"
        const val GLOBAL_SCALE = "${PROP_PREFIX}scale"
        const val ULTRA_SAVER = "${PROP_PREFIX}saver"
        
        fun clusterMin(id: Int) = "${PROP_PREFIX}policy${id}.min"
        fun clusterMax(id: Int) = "${PROP_PREFIX}policy${id}.max"
        fun clusterGov(id: Int) = "${PROP_PREFIX}policy${id}.gov"
    }

    suspend fun getClusters(): List<CpuCluster> = withContext(Dispatchers.IO) {
        val clusters = mutableListOf<CpuCluster>()
        val processedCpus = mutableSetOf<Int>()

        // Scan up to 16 cores
        for (i in 0 until 16) {
            if (processedCpus.contains(i)) continue

            val cpuDir = File("$CPU_BASE_PATH/cpu$i")
            if (!cpuDir.exists()) break 

            val freqDir = File(cpuDir, "cpufreq")
            if (!freqDir.exists()) continue

            try {
                // 1. Identify Cluster Members
                val relatedCpusRaw = try {
                    File(freqDir, "related_cpus").readText().trim()
                } catch (e: Exception) { "$i" }
                val coreIds = parseCpuRange(relatedCpusRaw)
                processedCpus.addAll(coreIds)

                // 2. Read Frequencies (Available Steps)
                val freqs = File(freqDir, "scaling_available_frequencies")
                    .readText().trim().split(Regex("\\s+"))
                    .mapNotNull { it.toIntOrNull() }
                    .sorted()

                // 3. Read Hardware Minimum (Absolute Floor)
                val hwMin = try {
                    File(freqDir, "cpuinfo_min_freq").readText().trim().toInt()
                } catch (e: Exception) {
                    freqs.firstOrNull() ?: 0
                }

                // 4. Read Governors
                val govs = File(freqDir, "scaling_available_governors")
                    .readText().trim().split(Regex("\\s+"))
                    .filter { it.isNotBlank() }

                if (freqs.isNotEmpty()) {
                    val name = when {
                        coreIds.contains(0) -> "Little Cluster"
                        coreIds.size == 1 && i > 0 -> "Prime Core"
                        else -> "Performance Cluster ${clusters.size}"
                    }
                    
                    clusters.add(CpuCluster(i, name, coreIds, freqs, hwMin, govs))
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (clusters.isEmpty()) {
            // Mock for preview if system read fails (e.g. non-rooted emulator)
            listOf(
                CpuCluster(0, "Cluster 0", listOf(0,1,2,3), listOf(300000, 1000000, 1800000), 300000, listOf("schedutil", "performance")),
                CpuCluster(4, "Cluster 4", listOf(4,5,6,7), listOf(300000, 1200000, 2200000), 300000, listOf("schedutil", "performance"))
            )
        } else {
            clusters
        }
    }

    suspend fun getGlobalAvailableGovernors(): List<String> = withContext(Dispatchers.IO) {
        try {
            File("$CPU_BASE_PATH/cpu0/cpufreq/scaling_available_governors")
                .readText().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        } catch (e: Exception) {
            listOf("schedutil", "performance", "powersave")
        }
    }

    private fun parseCpuRange(range: String): List<Int> {
        val cores = mutableListOf<Int>()
        range.split(Regex("[\\s,]")).forEach { part ->
            if (part.contains("-")) {
                val bounds = part.split("-")
                if (bounds.size == 2) {
                    val start = bounds[0].toIntOrNull() ?: 0
                    val end = bounds[1].toIntOrNull() ?: 0
                    for (c in start..end) cores.add(c)
                }
            } else {
                part.toIntOrNull()?.let { cores.add(it) }
            }
        }
        return cores.sorted()
    }

    // REPLACED: Shell execution with SystemProps reflection
    fun getProp(key: String, default: String): String {
        return SystemProps.get(key, default)
    }

    fun setProp(key: String, value: String) {
        SystemProps.set(key, value)
    }
}
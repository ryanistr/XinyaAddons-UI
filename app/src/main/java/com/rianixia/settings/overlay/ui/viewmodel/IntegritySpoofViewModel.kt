// File: main/java/com/rianixia/settings/overlay/ui/viewmodel/IntegritySpoofViewModel.kt
package com.rianixia.settings.overlay.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GamePropProfile(
    val key: String,
    var brand: String,
    var manufacturer: String,
    var model: String,
    var device: String = "",
    var packages: MutableList<String> = mutableListOf()
)

data class ResolvedPackage(
    val packageName: String,
    val label: String,
    val isInstalled: Boolean
)

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

data class IntegritySpoofState(
    val isPifEnabled: Boolean = false,
    val pifMode: String = "cloud",
    val hasCustomKeybox: Boolean = false,
    val isPifAutoUpdate: Boolean = false,
    val isPifUpdating: Boolean = false,
    val isPifFetching: Boolean = false,
    
    val isPhotosEnabled: Boolean = false,
    val isNetflixEnabled: Boolean = false,
    
    val isGamePropsEnabled: Boolean = false,
    val gameProfiles: List<GamePropProfile> = emptyList(),
    val resolvedPackages: Map<String, ResolvedPackage> = emptyMap(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    
    val hasUnsavedChanges: Boolean = false,
    
    val showImportError: Boolean = false,
    val isLoading: Boolean = true,
    val isAppsLoading: Boolean = true
)

class IntegritySpoofViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(IntegritySpoofState())
    val uiState: StateFlow<IntegritySpoofState> = _uiState.asStateFlow()

    private val gamePropsFile = File(application.filesDir, "gameprops.json")
    private val customKeyboxFile = File(application.filesDir, "keybox.xml")

    private object Props {
        const val PIF_ENABLE = "persist.sys.rianixia.pif.enable"
        const val PIF_MODE = "persist.sys.rianixia.pif.mode"
        const val PIF_AUTO_UPDATE = "persist.sys.rianixia.pif-auto"
        const val PIF_CUSTOM_UPDATE = "persist.sys.rianixia.pif.custom_update"
        
        const val PHOTOS_ENABLE = "persist.sys.rianixia.photos.unlimited"
        const val NETFLIX_ENABLE = "persist.sys.rianixia.netflix.unlock"
        const val GAME_ENABLE = "persist.sys.rianixia.game.props"
        const val GAME_CHANGED = "persist.sys.rianixia.game.changed"
    }

    init {
        loadState()
    }

    private fun loadState() {
        viewModelScope.launch {
            val sysProps = withContext(Dispatchers.IO) {
                mapOf(
                    "pif" to getSystemProp(Props.PIF_ENABLE),
                    "pif_mode" to getSystemProp(Props.PIF_MODE).ifEmpty { "cloud" },
                    "auto" to getSystemProp(Props.PIF_AUTO_UPDATE),
                    "photos" to getSystemProp(Props.PHOTOS_ENABLE),
                    "netflix" to getSystemProp(Props.NETFLIX_ENABLE),
                    "game" to getSystemProp(Props.GAME_ENABLE)
                )
            }

            val hasKeybox = customKeyboxFile.exists()

            _uiState.update {
                it.copy(
                    isPifEnabled = sysProps["pif"] == "1" || sysProps["pif"] == "true",
                    pifMode = sysProps["pif_mode"] ?: "cloud",
                    hasCustomKeybox = hasKeybox,
                    isPifAutoUpdate = sysProps["auto"] == "true" || sysProps["auto"] == "1",
                    isPhotosEnabled = sysProps["photos"] == "1" || sysProps["photos"] == "true",
                    isNetflixEnabled = sysProps["netflix"] == "1" || sysProps["netflix"] == "true",
                    isGamePropsEnabled = sysProps["game"] == "1" || sysProps["game"] == "true",
                    isLoading = false,
                    hasUnsavedChanges = false
                )
            }

            launch(Dispatchers.IO) {
                ensureGamePropsFileExists()
                val profiles = readGamePropsJson()
                val resolved = resolvePackageInfo(profiles)
                
                _uiState.update { it.copy(gameProfiles = profiles, resolvedPackages = resolved) }

                val allApps = loadAllInstalledApps()
                _uiState.update { it.copy(installedApps = allApps, isAppsLoading = false) }
            }
        }
    }

    private fun getSystemProp(key: String): String {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            process.inputStream.bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            ""
        }
    }

    private fun setSystemProp(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec("setprop $key $value")
            } catch (e: Exception) {
                Log.e("IntegrityViewModel", "Failed to set prop $key", e)
            }
        }
    }

    private fun getPifFileDate(format: String = "MM/dd/yyyy"): String? {
        return try {
            val process = Runtime.getRuntime().exec("stat -c %Y /data/PIF.apk")
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            
            if (output.isNotEmpty() && output.all { char -> char.isDigit() }) {
                val epochSeconds = output.toLong()
                val date = Date(epochSeconds * 1000L)
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.format(date)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun ensureGamePropsFileExists() {
        if (!gamePropsFile.exists()) {
            copyAssetToInternal()
        }
    }

    private fun copyAssetToInternal() {
        try {
            val assetManager = getApplication<Application>().assets
            val assets = assetManager.list("")
            if (assets?.contains("gameprops.json") == true) {
                assetManager.open("gameprops.json").use { input ->
                    gamePropsFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                gamePropsFile.writeText("{}")
            }
        } catch (e: Exception) {
            if(!gamePropsFile.exists()) gamePropsFile.writeText("{}")
        }
    }

    private fun readGamePropsJson(): List<GamePropProfile> {
        val profiles = mutableListOf<GamePropProfile>()
        try {
            if (!gamePropsFile.exists()) return emptyList()
            val content = gamePropsFile.readText()
            if (content.isBlank()) return emptyList()

            val json = JSONObject(content)
            json.keys().forEach { key ->
                val obj = json.getJSONObject(key)
                val pkgsJson = obj.optJSONArray("PKGNAMES") ?: JSONArray()
                val pkgList = mutableListOf<String>()
                for (i in 0 until pkgsJson.length()) {
                    pkgList.add(pkgsJson.getString(i))
                }

                profiles.add(
                    GamePropProfile(
                        key = key,
                        brand = obj.optString("BRAND", ""),
                        manufacturer = obj.optString("MANUFACTURER", ""),
                        model = obj.optString("MODEL", ""),
                        device = obj.optString("DEVICE", ""),
                        packages = pkgList
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("IntegrityViewModel", "Error reading gameprops.json", e)
        }
        return profiles
    }

    private fun writeGamePropsJson(profiles: List<GamePropProfile>) {
        try {
            val root = JSONObject()
            profiles.forEach { profile ->
                val obj = JSONObject()
                obj.put("BRAND", profile.brand)
                obj.put("MANUFACTURER", profile.manufacturer)
                obj.put("MODEL", profile.model)
                if (profile.device.isNotEmpty()) obj.put("DEVICE", profile.device)

                val pkgArray = JSONArray()
                profile.packages.forEach { pkgArray.put(it) }
                obj.put("PKGNAMES", pkgArray)

                root.put(profile.key, obj)
            }
            gamePropsFile.writeText(root.toString(2))
            notifyGamePropsChanged()
        } catch (e: IOException) {
            Log.e("IntegrityViewModel", "Error writing gameprops.json", e)
        }
    }

    private fun notifyGamePropsChanged() {
        setSystemProp(Props.GAME_CHANGED, "false")
        setSystemProp(Props.GAME_CHANGED, "true")
    }

    fun saveGamePropsChanges() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentProfiles = _uiState.value.gameProfiles
            writeGamePropsJson(currentProfiles)
            _uiState.update { it.copy(hasUnsavedChanges = false) }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Changes saved successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportPresetToDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profiles = _uiState.value.gameProfiles
                val root = JSONObject()
                profiles.forEach { profile ->
                    val obj = JSONObject()
                    obj.put("BRAND", profile.brand)
                    obj.put("MANUFACTURER", profile.manufacturer)
                    obj.put("MODEL", profile.model)
                    if (profile.device.isNotEmpty()) obj.put("DEVICE", profile.device)

                    val pkgArray = JSONArray()
                    profile.packages.forEach { pkgArray.put(it) }
                    obj.put("PKGNAMES", pkgArray)

                    root.put(profile.key, obj)
                }

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val destFile = File(downloadsDir, "gameprops_export_$timestamp.json")
                
                destFile.writeText(root.toString(2))
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Saved to Downloads/gameprops_export_$timestamp.json", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("IntegrityViewModel", "Export failed", e)
            }
        }
    }

    fun importPreset(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = getApplication<Application>().contentResolver
                contentResolver.openInputStream(uri)?.use { input ->
                    val content = input.bufferedReader().use { it.readText() }
                    JSONObject(content)
                    gamePropsFile.writeText(content)
                }

                val profiles = readGamePropsJson()
                val resolved = resolvePackageInfo(profiles)

                _uiState.update { 
                    it.copy(
                        gameProfiles = profiles, 
                        resolvedPackages = resolved,
                        hasUnsavedChanges = false
                    ) 
                }
                notifyGamePropsChanged()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Preset imported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("IntegrityViewModel", "Import failed", e)
                _uiState.update { it.copy(showImportError = true) }
            }
        }
    }

    fun dismissImportError() {
        _uiState.update { it.copy(showImportError = false) }
    }

    fun resetToTemplate() {
        viewModelScope.launch(Dispatchers.IO) {
            copyAssetToInternal()
            val profiles = readGamePropsJson()
            val resolved = resolvePackageInfo(profiles)
            _uiState.update { 
                it.copy(
                    gameProfiles = profiles, 
                    resolvedPackages = resolved,
                    hasUnsavedChanges = false
                ) 
            }
            notifyGamePropsChanged()
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Reset to default template", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun togglePif(enabled: Boolean) {
        _uiState.update { it.copy(isPifEnabled = enabled) }
        setSystemProp(Props.PIF_ENABLE, if(enabled) "1" else "0")
    }

    fun setPifMode(mode: String) {
        if (_uiState.value.pifMode == mode) return
        _uiState.update { it.copy(pifMode = mode) }
        setSystemProp(Props.PIF_MODE, mode)
        
        viewModelScope.launch(Dispatchers.Main) {
            val msg = if (mode == "cloud") "Cloud Mode Selected" else "Custom Mode Selected"
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun importCustomKeybox(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = getApplication<Application>().contentResolver
                contentResolver.openInputStream(uri)?.use { input ->
                    customKeyboxFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                _uiState.update { it.copy(hasCustomKeybox = true) }
                setSystemProp(Props.PIF_CUSTOM_UPDATE, "1")
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Keybox imported successfully", Toast.LENGTH_SHORT).show()
                }
                if (_uiState.value.pifMode == "custom") {
                    fetchCustomPifJson()
                }
            } catch (e: Exception) {
                Log.e("IntegrityViewModel", "Keybox import failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Failed to import keybox", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun fetchCustomPifJson() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isPifFetching = true) }
            togglePifAutoUpdate(false)
            val filesDir = getApplication<Application>().filesDir
            val scriptFile = File(filesDir, "fetch_pif.sh")
            scriptFile.writeText("""#!/bin/sh
CURRENT_DIR="${filesDir.absolutePath}"
TEMPDIR="${'$'}CURRENT_DIR/playintegrityfix_temp"

mkdir -p "${'$'}TEMPDIR"
cd "${'$'}TEMPDIR" || exit 1

download_fail() {
    dl_domain=${'$'}(echo "${'$'}1" | awk -F[/:] '{print ${'$'}4}')
    rm -rf "${'$'}TEMPDIR"
    echo "[!] Download failed: ${'$'}1 (${'$'}dl_domain)"
    exit 1
}

download() { curl --connect-timeout 10 -s "${'$'}1" > "${'$'}2" || download_fail "${'$'}1"; }

set_random_beta() {
    if [ "${'$'}(echo "${'$'}MODEL_LIST" | wc -l)" -ne "${'$'}(echo "${'$'}PRODUCT_LIST" | wc -l)" ]; then
        MODEL="Pixel 6"
        PRODUCT="oriole_beta"
    else
        count=${'$'}(echo "${'$'}MODEL_LIST" | wc -l)
        rand_index=${'$'}(( ${'$'}${'$'} % count ))
        MODEL=${'$'}(echo "${'$'}MODEL_LIST" | sed -n "${'$'}((rand_index + 1))p")
        PRODUCT=${'$'}(echo "${'$'}PRODUCT_LIST" | sed -n "${'$'}((rand_index + 1))p")
    fi
}

download https://developer.android.com/about/versions PIXEL_VERSIONS_HTML
LATEST_URL=${'$'}(grep -o 'https://developer.android.com/about/versions/.*[0-9]"' PIXEL_VERSIONS_HTML | sort -ru | cut -d\" -f1 | head -n1)
download "${'$'}LATEST_URL" PIXEL_LATEST_HTML

FI_URL="https://developer.android.com${'$'}(grep -o 'href=".*download.*"' PIXEL_LATEST_HTML | grep 'qpr' | cut -d\" -f2 | head -n1)"
download "${'$'}FI_URL" PIXEL_FI_HTML

MODEL_LIST="${'$'}(grep -A1 'tr id=' PIXEL_FI_HTML | grep 'td' | sed 's;.*<td>\(.*\)</td>.*;\1;')"
PRODUCT_LIST="${'$'}(grep 'tr id=' PIXEL_FI_HTML | sed 's;.*<tr id="\(.*\)">.*;\1_beta;')"

if [ -z "${'$'}PRODUCT" ] || ! echo "${'$'}PRODUCT_LIST" | grep -q "${'$'}PRODUCT"; then
    set_random_beta
fi

DEVICE="${'$'}(echo "${'$'}PRODUCT" | sed 's/_beta//')"
BRAND="google"
MANUFACTURER="Google"
FIRST_API_LEVEL="31"

download https://flash.android.com PIXEL_FLASH_HTML
FLASH_KEY=${'$'}(grep -o '<body data-client-config=.*' PIXEL_FLASH_HTML | cut -d\; -f2 | cut -d\& -f1)

curl --connect-timeout 10 -H "Referer: https://flash.android.com" -s "https://content-flashstation-pa.googleapis.com/v1/builds?product=${'$'}PRODUCT&key=${'$'}FLASH_KEY" > PIXEL_STATION_JSON || download_fail "https://flash.android.com"

tac PIXEL_STATION_JSON | grep -m1 -A13 '"canary": true' > PIXEL_CANARY_JSON
ID="${'$'}(grep 'releaseCandidateName' PIXEL_CANARY_JSON | cut -d\" -f4)"
INCREMENTAL="${'$'}(grep 'buildId' PIXEL_CANARY_JSON | cut -d\" -f4)"
FINGERPRINT="${'$'}BRAND/${'$'}PRODUCT/${'$'}DEVICE:CANARY/${'$'}ID/${'$'}INCREMENTAL:user/release-keys"

download https://source.android.com/docs/security/bulletin/pixel PIXEL_SECBULL_HTML
CANARY_ID="${'$'}(grep '"id"' PIXEL_CANARY_JSON | sed -e 's;.*canary-\(.*\)".*;\1;' -e 's;^\(.\{4\}\);\1-;')"
SECURITY_PATCH="${'$'}(grep "<td>${'$'}CANARY_ID" PIXEL_SECBULL_HTML | sed 's;.*<td>\(.*\)</td>;\1;')"

if [ -z "${'$'}ID" ] || [ -z "${'$'}INCREMENTAL" ]; then
    echo "[!] Failed to get fingerprint data"
    cd "${'$'}CURRENT_DIR" || exit 1
    rm -rf "${'$'}TEMPDIR"
    exit 1
fi

if [ -z "${'$'}SECURITY_PATCH" ]; then
    SECURITY_PATCH="${'$'}{CANARY_ID}-05"
fi

cat <<EOF > "${'$'}CURRENT_DIR/pif.json"
{
  "MANUFACTURER": "${'$'}MANUFACTURER",
  "BRAND": "${'$'}BRAND",
  "DEVICE": "${'$'}DEVICE",
  "PRODUCT": "${'$'}PRODUCT",
  "MODEL": "${'$'}MODEL",
  "FINGERPRINT": "${'$'}FINGERPRINT",
  "SECURITY_PATCH": "${'$'}SECURITY_PATCH",
  "FIRST_API_LEVEL": "${'$'}FIRST_API_LEVEL"
}
EOF

cd "${'$'}CURRENT_DIR" || exit 1
rm -rf "${'$'}TEMPDIR"
""")
            scriptFile.setExecutable(true)
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", scriptFile.absolutePath))
                process.waitFor()
            } catch (e: Exception) {
                Log.e("IntegrityViewModel", "Failed to execute fetch_pif.sh", e)
            } finally {
                _uiState.update { it.copy(isPifFetching = false) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Fingerprint fetched and saved.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun togglePifAutoUpdate(enabled: Boolean) {
        _uiState.update { it.copy(isPifAutoUpdate = enabled) }
        setSystemProp(Props.PIF_AUTO_UPDATE, if(enabled) "true" else "false")
    }

    fun triggerPifUpdate() {
        if (_uiState.value.isPifUpdating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPifUpdating = true) }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Checking for PIF updates...", Toast.LENGTH_SHORT).show()
            }

            val oldDate = withContext(Dispatchers.IO) { getPifFileDate() }

            withContext(Dispatchers.IO) {
                val currentState = getSystemProp(Props.PIF_AUTO_UPDATE)
                val isTrue = currentState == "true" || currentState == "1"
                if (isTrue) {
                    setSystemProp(Props.PIF_AUTO_UPDATE, "false")
                    delay(1000)
                    setSystemProp(Props.PIF_AUTO_UPDATE, "true")
                } else {
                    setSystemProp(Props.PIF_AUTO_UPDATE, "true")
                    delay(1000)
                    setSystemProp(Props.PIF_AUTO_UPDATE, "false")
                }
            }

            var success = false
            var message = "You are already on the latest version."
            var loops = 0
            val maxLoops = 20

            withContext(Dispatchers.IO) {
                delay(1000)
                
                while (loops < maxLoops) {
                    val newDate = getPifFileDate()
                    
                    if (oldDate != null && newDate != null && oldDate != newDate) {
                        message = "Updated PIF from $oldDate to $newDate"
                        success = true
                        break
                    } else if (oldDate == null && newDate != null) {
                        message = "Installed PIF ($newDate)"
                        success = true
                        break
                    }
                    
                    delay(1000)
                    loops++
                }
            }
            
            _uiState.update { it.copy(isPifUpdating = false) }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun togglePhotosSpoof(enabled: Boolean) {
        _uiState.update { it.copy(isPhotosEnabled = enabled) }
        setSystemProp(Props.PHOTOS_ENABLE, if(enabled) "1" else "0")
    }

    fun toggleNetflixSpoof(enabled: Boolean) {
        _uiState.update { it.copy(isNetflixEnabled = enabled) }
        setSystemProp(Props.NETFLIX_ENABLE, if(enabled) "1" else "0")
    }

    fun toggleGamePropsSpoof(enabled: Boolean) {
        _uiState.update { it.copy(isGamePropsEnabled = enabled) }
        setSystemProp(Props.GAME_ENABLE, if(enabled) "1" else "0")
    }

    private fun loadAllInstalledApps(): List<InstalledAppInfo> {
        val pm = getApplication<Application>().packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.map {
            InstalledAppInfo(
                label = pm.getApplicationLabel(it).toString(),
                packageName = it.packageName
            )
        }.sortedBy { it.label.lowercase() }
    }

    private fun resolvePackageInfo(profiles: List<GamePropProfile>): Map<String, ResolvedPackage> {
        val pm = getApplication<Application>().packageManager
        val map = HashMap<String, ResolvedPackage>()

        profiles.flatMap { it.packages }.distinct().forEach { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(info).toString()
                map[pkg] = ResolvedPackage(pkg, label, true)
            } catch (e: PackageManager.NameNotFoundException) {
                map[pkg] = ResolvedPackage(pkg, pkg, false)
            }
        }
        return map
    }

    fun addProfile(key: String) {
        val currentList = _uiState.value.gameProfiles.toMutableList()
        if (currentList.any { it.key == key }) return

        val newProfile = GamePropProfile(key, "Generic", "Generic", "Model X", "", mutableListOf())
        currentList.add(newProfile)
        updateLocalState(currentList)
    }

    fun updateProfileField(key: String, field: String, value: String) {
        val currentList = _uiState.value.gameProfiles.map { profile ->
            if (profile.key == key) {
                when (field) {
                    "BRAND" -> profile.copy(brand = value)
                    "MANUFACTURER" -> profile.copy(manufacturer = value)
                    "MODEL" -> profile.copy(model = value)
                    "DEVICE" -> profile.copy(device = value)
                    else -> profile
                }
            } else profile
        }
        updateLocalState(currentList)
    }

    fun addPackageToProfile(profileKey: String, packageName: String) {
        if (packageName.isBlank()) return
        val currentList = _uiState.value.gameProfiles.map { profile ->
            if (profile.key == profileKey && !profile.packages.contains(packageName)) {
                val newPkgs = profile.packages.toMutableList()
                newPkgs.add(packageName)
                profile.copy(packages = newPkgs)
            } else profile
        }
        updateLocalState(currentList)
    }

    fun removePackageFromProfile(profileKey: String, packageName: String) {
        val currentList = _uiState.value.gameProfiles.map { profile ->
            if (profile.key == profileKey) {
                val newPkgs = profile.packages.toMutableList()
                newPkgs.remove(packageName)
                profile.copy(packages = newPkgs)
            } else profile
        }
        updateLocalState(currentList)
    }

    fun deleteProfile(key: String) {
        val currentList = _uiState.value.gameProfiles.filter { it.key != key }
        updateLocalState(currentList)
    }

    private fun updateLocalState(profiles: List<GamePropProfile>) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = resolvePackageInfo(profiles)
            _uiState.update { 
                it.copy(
                    gameProfiles = profiles, 
                    resolvedPackages = resolved,
                    hasUnsavedChanges = true
                ) 
            }
        }
    }
}
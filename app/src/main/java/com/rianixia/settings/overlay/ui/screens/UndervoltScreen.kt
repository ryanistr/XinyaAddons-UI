package com.rianixia.settings.overlay.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.data.AppPreferences
import com.rianixia.settings.overlay.ui.components.BouncyLazyColumn
import com.rianixia.settings.overlay.ui.components.GradientBlurAppBar
import com.rianixia.settings.overlay.ui.components.MaterialGlassScaffold
import com.rianixia.settings.overlay.ui.components.frostedGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VoltageParam(
    val id: String,
    val propKey: String,
    val labelRes: Int,
    val value: Int = 0
)

enum class UndervoltRiskLevel {
    SAFE,       
    LIGHT,      
    STRONG,     
    EXTREME,    
    LOCKED      
}

data class UndervoltState(
    val isAdvancedMode: Boolean = false,
    val isGpuSectionEnabled: Boolean = false,
    val isConfirmedExtreme: Boolean = false,
    val cpuParams: List<VoltageParam> = listOf(
        VoltageParam("cpu_b", "big", R.string.uv_label_big),
        VoltageParam("cpu_bl", "bl", R.string.uv_label_bl),
        VoltageParam("cpu_l", "little", R.string.uv_label_little),
        VoltageParam("cpu_cci", "cci", R.string.uv_label_cci)
    ),
    val gpuParams: List<VoltageParam> = listOf(
        VoltageParam("gpu_u", "gpu", R.string.uv_label_gpu),
        VoltageParam("gpu_h", "gpu-high", R.string.uv_label_gpu_high)
    )
) {
    val riskLevel: UndervoltRiskLevel
        get() {
            val lowestValue = (cpuParams + gpuParams).minOfOrNull { it.value } ?: 0
            return when {
                lowestValue <= -75 && !isAdvancedMode -> UndervoltRiskLevel.LOCKED
                lowestValue < -50 -> UndervoltRiskLevel.EXTREME
                lowestValue in -50..-26 -> UndervoltRiskLevel.STRONG
                lowestValue in -25..-11 -> UndervoltRiskLevel.LIGHT
                else -> UndervoltRiskLevel.SAFE
            }
        }

    val sliderRange: ClosedFloatingPointRange<Float>
        get() = if (isAdvancedMode) -125f..0f else -25f..0f
}

object PropManager {
    private const val PREFIX = "persist.sys.rianixia.undervolt-cluster."
    private const val SUPPORT_KEY = "persist.sys.rianixia.undervolt.support"

    fun isSupported(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("getprop $SUPPORT_KEY")
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val line = reader.readLine()?.trim()
            line == "1"
        } catch (e: Exception) {
            false
        }
    }

    fun get(key: String, default: Int): Int {
        return try {
            val p = Runtime.getRuntime().exec("getprop $PREFIX$key")
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val line = reader.readLine()
            line?.toIntOrNull() ?: default
        } catch (e: Exception) {
            default
        }
    }

    fun set(key: String, value: Int) {
        try {
            Runtime.getRuntime().exec("setprop $PREFIX$key $value")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun UndervoltScreen(navController: NavController) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(UndervoltState()) }
    var initialState by remember { mutableStateOf(UndervoltState()) }
    var isLoaded by remember { mutableStateOf(false) }
    var isSupported by remember { mutableStateOf(true) }

    var showRiskStage1 by remember { mutableStateOf(false) }
    var showRiskStage2 by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(!AppPreferences.isUvWarningHidden(context)) }
    var showAdvancedModeWarning by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }
    var pendingUpdate by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val toastConfigApplied = stringResource(R.string.toast_config_applied)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (!PropManager.isSupported()) {
                withContext(Dispatchers.Main) {
                    isSupported = false
                    isLoaded = true
                }
                return@withContext
            }

            val loadedCpu = state.cpuParams.map { it.copy(value = PropManager.get(it.propKey, 0)) }
            val loadedGpu = state.gpuParams.map { it.copy(value = PropManager.get(it.propKey, 0)) }
            
            val loadedState = state.copy(
                cpuParams = loadedCpu,
                gpuParams = loadedGpu,
                isAdvancedMode = (loadedCpu + loadedGpu).any { it.value < -25 },
                isGpuSectionEnabled = loadedGpu.any { it.value != 0 }
            )
            
            withContext(Dispatchers.Main) {
                state = loadedState
                initialState = loadedState
                isLoaded = true
                isSupported = true
            }
        }
    }

    fun saveConfiguration() {
        scope.launch(Dispatchers.IO) {
            state.cpuParams.forEach { PropManager.set(it.propKey, it.value) }
            state.gpuParams.forEach { PropManager.set(it.propKey, it.value) }
            withContext(Dispatchers.Main) {
                initialState = state 
                Toast.makeText(context, toastConfigApplied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateParam(id: String, newValue: Int) {
        val newCpu = state.cpuParams.map { if (it.id == id) it.copy(value = newValue) else it }
        val newGpu = state.gpuParams.map { if (it.id == id) it.copy(value = newValue) else it }
        state = state.copy(cpuParams = newCpu, gpuParams = newGpu)
    }

    fun onValueChangeAttempt(id: String, newValue: Int) {
        if (!state.isAdvancedMode && newValue < -25) return

        if (newValue < -50 && !state.isConfirmedExtreme) {
            pendingUpdate = id to newValue
            showRiskStage1 = true
        } else {
            updateParam(id, newValue)
        }
    }

    fun applyRecommended() {
        state = state.copy(
            cpuParams = state.cpuParams.map { 
                when(it.propKey) {
                    "big", "bl", "little" -> it.copy(value = -11)
                    "cci" -> it.copy(value = -7)
                    else -> it
                }
            },
            gpuParams = state.gpuParams.map { it.copy(value = 0) },
            isGpuSectionEnabled = false
        )
    }

    val hasChanges = isLoaded && (state.cpuParams != initialState.cpuParams || state.gpuParams != initialState.gpuParams)

    MaterialGlassScaffold {
        Box(Modifier.fillMaxSize()) {
            
            if (!isLoaded) {
                 Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (!isSupported) {
                 Column(
                     modifier = Modifier
                         .fillMaxSize()
                         .padding(32.dp),
                     verticalArrangement = Arrangement.Center,
                     horizontalAlignment = Alignment.CenterHorizontally
                 ) {
                     Icon(
                         imageVector = Icons.Rounded.Cancel,
                         contentDescription = null,
                         tint = MaterialTheme.colorScheme.error,
                         modifier = Modifier.size(64.dp)
                     )
                     Spacer(Modifier.height(16.dp))
                     Text(
                         text = stringResource(R.string.uv_not_supported_title),
                         style = MaterialTheme.typography.headlineSmall,
                         fontWeight = FontWeight.Bold,
                         color = MaterialTheme.colorScheme.onSurface
                     )
                     Spacer(Modifier.height(8.dp))
                     Text(
                         text = stringResource(R.string.uv_not_supported_desc),
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         textAlign = TextAlign.Center
                     )
                     Spacer(Modifier.height(32.dp))
                     Button(
                         onClick = { navController.popBackStack() },
                         colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                     ) {
                         Text(stringResource(R.string.btn_go_back))
                     }
                 }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                ) {
                    BouncyLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 80.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp) 
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }

                        item {
                            VoltageStrategyBreaker(
                                title = stringResource(R.string.uv_advanced_mode),
                                status = if (state.isAdvancedMode) stringResource(R.string.status_unlocked) else stringResource(R.string.status_safe_mode),
                                desc = stringResource(R.string.uv_advanced_desc),
                                isActive = state.isAdvancedMode,
                                color = MaterialTheme.colorScheme.primary,
                                icon = Icons.Rounded.Tune,
                                onClick = {
                                    if (!state.isAdvancedMode) {
                                        showAdvancedModeWarning = true
                                    } else {
                                        state = state.copy(
                                            isAdvancedMode = false,
                                            cpuParams = state.cpuParams.map { it.copy(value = it.value.coerceAtLeast(-25)) },
                                            gpuParams = state.gpuParams.map { it.copy(value = it.value.coerceAtLeast(-25)) }
                                        )
                                    }
                                }
                            )
                        }

                        item {
                            GlassActionButton(
                                text = stringResource(R.string.btn_load_recommended),
                                icon = Icons.Rounded.Star,
                                onClick = { applyRecommended() }
                            )
                        }

                        item {
                            RiskMonitor(state.riskLevel)
                        }

                        item {
                            VoltageSectionHeader(stringResource(R.string.cpu_eem_offsets).uppercase())
                        }
                        
                        items(state.cpuParams) { param ->
                            VoltageCard(
                                param = param,
                                range = state.sliderRange,
                                isAdvancedMode = state.isAdvancedMode,
                                onValueChange = { onValueChangeAttempt(param.id, it) }
                            )
                        }

                        item {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                        
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .frostedGlass(
                                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                        borderColor = Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { state = state.copy(isGpuSectionEnabled = !state.isGpuSectionEnabled) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.uv_gpu_settings_title),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    letterSpacing = 2.sp
                                )
                                Switch(
                                    checked = state.isGpuSectionEnabled,
                                    onCheckedChange = { state = state.copy(isGpuSectionEnabled = it) },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }

                        item {
                            AnimatedVisibility(visible = state.isGpuSectionEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .frostedGlass(
                                                backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                                                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(16.dp))
                                        Text(
                                            stringResource(R.string.uv_gpu_warning),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                        )
                                    }

                                    state.gpuParams.forEach { param ->
                                        VoltageCard(
                                            param = param,
                                            range = state.sliderRange,
                                            isAdvancedMode = state.isAdvancedMode,
                                            onValueChange = { onValueChangeAttempt(param.id, it) }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            TechnicalNotes()
                            Spacer(Modifier.height(100.dp))
                        }
                    }
                }
            }

            GradientBlurAppBar(
                title = stringResource(R.string.undervolt),
                icon = Icons.Rounded.Bolt,
                onBackClick = { navController.popBackStack() },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter),
                addStatusBarPadding = true,
                actions = {
                    if(isSupported && isLoaded) {
                        Surface(
                            onClick = { showInfoDialog = true },
                            shape = CircleShape,
                            shadowElevation = 8.dp,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(40.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = stringResource(R.string.info),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            )

            AnimatedVisibility(
                visible = hasChanges && isSupported,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                FloatingActionButton(
                    onClick = { saveConfiguration() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Icon(Icons.Rounded.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAdvancedModeWarning) {
        AlertDialog(
            onDismissRequest = { showAdvancedModeWarning = false },
            icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.dialog_uv_advanced_title)) },
            text = { 
                Text(stringResource(R.string.dialog_uv_advanced_msg)) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        state = state.copy(isAdvancedMode = true)
                        showAdvancedModeWarning = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_accept_risk))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdvancedModeWarning = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showRiskStage1) {
        AlertDialog(
            onDismissRequest = { showRiskStage1 = false },
            icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.dialog_uv_stage1_title)) },
            text = { Text(stringResource(R.string.dialog_uv_stage1_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showRiskStage1 = false
                        showRiskStage2 = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Text(stringResource(R.string.btn_understand))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRiskStage1 = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showRiskStage2) {
        AlertDialog(
            onDismissRequest = { showRiskStage2 = false },
            icon = { Icon(Icons.Rounded.Dangerous, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.dialog_uv_stage2_title)) },
            text = { 
                Text(
                    stringResource(R.string.dialog_uv_stage2_msg),
                    fontWeight = FontWeight.Bold
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingUpdate?.let { (id, value) ->
                            state = state.copy(isConfirmedExtreme = true)
                            updateParam(id, value)
                        }
                        showRiskStage2 = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_accept_responsibility))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRiskStage2 = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showInfoDialog) {
        UndervoltInfoDialog(context = context) { showInfoDialog = false }
    }
}

@Composable
private fun VoltageSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun VoltageStrategyBreaker(
    title: String,
    status: String,
    desc: String,
    isActive: Boolean,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val bgAlpha by animateFloatAsState(if (isActive) 0.15f else 0.05f, label = "bg")
    val borderAlpha by animateFloatAsState(if (isActive) 0.5f else 0.1f, label = "border")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = bgAlpha))
            .border(1.dp, color.copy(alpha = borderAlpha), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color)
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isActive) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.width(40.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.5f)
                            .align(if (isActive) Alignment.CenterEnd else Alignment.CenterStart)
                            .background(if (isActive) color else MaterialTheme.colorScheme.outline)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass(
                backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RiskMonitor(risk: UndervoltRiskLevel) {
    val color = when (risk) {
        UndervoltRiskLevel.SAFE -> MaterialTheme.colorScheme.primary
        UndervoltRiskLevel.LIGHT -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    
    val message = when (risk) {
        UndervoltRiskLevel.SAFE -> stringResource(R.string.uv_risk_safe)
        UndervoltRiskLevel.LIGHT -> stringResource(R.string.uv_risk_light)
        UndervoltRiskLevel.STRONG -> stringResource(R.string.uv_risk_strong)
        else -> stringResource(R.string.uv_risk_extreme)
    }

    val icon = when (risk) {
        UndervoltRiskLevel.SAFE -> Icons.Rounded.CheckCircle
        UndervoltRiskLevel.LIGHT -> Icons.Rounded.Info
        UndervoltRiskLevel.STRONG -> Icons.Rounded.Warning
        else -> Icons.Rounded.Dangerous
    }

    val animatedColor by animateColorAsState(targetValue = color, label = "risk_color")

    AnimatedVisibility(
        visible = risk != UndervoltRiskLevel.SAFE,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .frostedGlass(
                    backgroundColor = animatedColor.copy(alpha = 0.1f),
                    borderColor = animatedColor.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = animatedColor)
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun VoltageCard(
    param: VoltageParam,
    range: ClosedFloatingPointRange<Float>,
    isAdvancedMode: Boolean,
    onValueChange: (Int) -> Unit
) {
    var isManualInput by remember { mutableStateOf(false) }
    LaunchedEffect(isAdvancedMode) { if (!isAdvancedMode) isManualInput = false }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass(
                backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = stringResource(param.labelRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = stringResource(R.string.uv_eem_offset), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            TextButton(onClick = { isManualInput = !isManualInput }, enabled = isAdvancedMode) {
                Text(text = "${param.value}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isManualInput) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                if (isAdvancedMode) {
                    Spacer(Modifier.width(4.dp))
                    Icon(imageVector = if (isManualInput) Icons.Rounded.Edit else Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (isManualInput && isAdvancedMode) {
            var textValue by remember(param.value) { mutableStateOf(param.value.toString()) }
            OutlinedTextField(
                value = textValue,
                onValueChange = { 
                    textValue = it
                    it.toIntOrNull()?.let { num -> onValueChange(num) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.uv_input_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.05f)
                )
            )
        } else {
            Slider(value = param.value.toFloat(), onValueChange = { onValueChange(it.roundToInt()) }, valueRange = range, steps = 0)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${range.start.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${range.endInclusive.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TechnicalNotes() {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(text = stringResource(R.string.uv_notes_title), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 8.dp))
        Text(text = stringResource(R.string.uv_notes_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.uv_critical_warning), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, lineHeight = 16.sp)
    }
}

@Composable
private fun UndervoltInfoDialog(context: android.content.Context, onDismiss: () -> Unit) {
    var dontShowAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_uv_info_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.dialog_uv_info_1), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.dialog_uv_info_2), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.dialog_uv_info_3), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.uv_warning_step), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain }
                        .padding(top = 8.dp)
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.uv_dont_show_again), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (dontShowAgain) {
                    AppPreferences.setUvWarningHidden(context, true)
                }
                onDismiss()
            }) {
                Text(stringResource(R.string.btn_got_it))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}
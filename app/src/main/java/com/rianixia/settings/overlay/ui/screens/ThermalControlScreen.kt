package com.rianixia.settings.overlay.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.data.ThermalProfile
import com.rianixia.settings.overlay.ui.components.*
import com.rianixia.settings.overlay.ui.viewmodel.HomeViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ThermalPropManager {
    private const val MODE_KEY = "persist.sys.rianixia.thermal-mode"
    private const val CUSTOM_KEY = "persist.sys.rianixia.thermal-custom"

    fun getMode(): ThermalProfile {
        return try {
            val p = Runtime.getRuntime().exec("getprop $MODE_KEY")
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val line = reader.readLine()?.trim()?.lowercase()
            when (line) {
                "adaptive" -> ThermalProfile.ADAPTIVE
                "disabled" -> ThermalProfile.DISABLED
                "custom" -> ThermalProfile.CUSTOM
                else -> ThermalProfile.DEFAULT
            }
        } catch (e: Exception) {
            ThermalProfile.DEFAULT
        }
    }

    fun setMode(profile: ThermalProfile) {
        val value = when(profile) {
            ThermalProfile.ADAPTIVE -> "adaptive"
            ThermalProfile.DISABLED -> "disabled"
            ThermalProfile.CUSTOM -> "custom"
            else -> "default"
        }
        try { Runtime.getRuntime().exec("setprop $MODE_KEY $value") } catch (e: Exception) { e.printStackTrace() }
    }

    fun getCustomLimit(default: Int): Int {
        return try {
            val p = Runtime.getRuntime().exec("getprop $CUSTOM_KEY")
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val line = reader.readLine()
            line?.toIntOrNull() ?: default
        } catch (e: Exception) {
            default
        }
    }

    fun setCustomLimit(value: Int) {
        try { Runtime.getRuntime().exec("setprop $CUSTOM_KEY $value") } catch (e: Exception) { e.printStackTrace() }
    }
}

@Composable
fun ThermalControlScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    
    var activeProfile by remember { mutableStateOf(ThermalProfile.DEFAULT) }
    var customLimit by remember { mutableIntStateOf(45) }
    var isLoaded by remember { mutableStateOf(false) }
    
    var highTempAllowed by remember { mutableStateOf(false) }
    
    var showInfoDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var showHighTempWarning by remember { mutableStateOf(false) }
    var pendingProfile by remember { mutableStateOf<ThermalProfile?>(null) }

    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val mode = ThermalPropManager.getMode()
            val limit = ThermalPropManager.getCustomLimit(45)
            withContext(Dispatchers.Main) {
                activeProfile = mode
                customLimit = limit
                isLoaded = true
            }
        }
    }

    val isDark = isSystemInDarkTheme()
    
    val targetColor = when(activeProfile) {
        ThermalProfile.DEFAULT -> if(isDark) Color(0xFF42A5F5) else Color(0xFF1976D2)
        ThermalProfile.ADAPTIVE -> if(isDark) Color(0xFFFFB74D) else Color(0xFFF57C00)
        ThermalProfile.DISABLED -> if(isDark) Color(0xFFEF5350) else Color(0xFFD32F2F)
        ThermalProfile.CUSTOM -> if(isDark) Color(0xFFAB47BC) else Color(0xFF7B1FA2)
    }
    
    val animatedColor by animateColorAsState(
        targetValue = targetColor, 
        animationSpec = tween(1000, easing = LinearOutSlowInEasing),
        label = "themeColor"
    )

    fun updateProfile(profile: ThermalProfile) {
        activeProfile = profile
        scope.launch(Dispatchers.IO) { ThermalPropManager.setMode(profile) }
    }

    fun onProfileClicked(profile: ThermalProfile) {
        if (profile == ThermalProfile.DISABLED) {
            pendingProfile = profile
            showWarningDialog = true
        } else {
            updateProfile(profile)
        }
    }

    fun updateCustomLimit(value: Int) {
        customLimit = value
        scope.launch(Dispatchers.IO) { ThermalPropManager.setCustomLimit(value) }
    }

    MaterialGlassScaffold {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Content Layer
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            ) {
                if (!isLoaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    // Standard LazyColumn (No Bounce)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 80.dp, bottom = 48.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                ThermalReactor(
                                    currentTemp = state.batteryInfo.temperature.toInt(),
                                    throttleLimit = customLimit,
                                    activeProfile = activeProfile,
                                    color = animatedColor
                                )
                            }
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AnimatedVisibility(
                                    visible = activeProfile == ThermalProfile.CUSTOM,
                                    enter = slideInVertically { it / 2 } + fadeIn() + expandVertically(),
                                    exit = slideOutVertically { it / 2 } + fadeOut() + shrinkVertically()
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            stringResource(R.string.throttle_threshold),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            letterSpacing = 2.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        ThermalFuelRodSlider(
                                            value = customLimit,
                                            onValueChange = { newValue ->
                                                if (newValue > 50 && !highTempAllowed) {
                                                    showHighTempWarning = true
                                                } else {
                                                    updateCustomLimit(newValue)
                                                }
                                            },
                                            range = 35..90,
                                            color = animatedColor
                                        )
                                        Spacer(Modifier.height(24.dp))
                                    }
                                }

                                ThermalModeRail(
                                    activeProfile = activeProfile,
                                    onProfileSelected = { onProfileClicked(it) },
                                    activeColor = animatedColor
                                )
                            }
                        }
                    }
                }
            }

            // Header Layer
            GradientBlurAppBar(
                title = stringResource(R.string.thermal_engine),
                icon = Icons.Rounded.Thermostat,
                onBackClick = { navController.popBackStack() },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter),
                addStatusBarPadding = true,
                actions = {
                    Surface(
                        onClick = { showInfoDialog = true },
                        shape = CircleShape,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(48.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = stringResource(R.string.info)
                            )
                        }
                    }
                }
            )
        }
    }

    // Dialogs
    if (showInfoDialog) ThermalInfoDialog { showInfoDialog = false }
    
    if (showWarningDialog) {
        ThermalWarningDialog(
            onDismiss = { showWarningDialog = false }, 
            onConfirm = { 
                pendingProfile?.let { updateProfile(it) }
                showWarningDialog = false 
            }
        )
    }
    
    if (showHighTempWarning) {
        AlertDialog(
            onDismissRequest = { showHighTempWarning = false },
            icon = { Icon(Icons.Rounded.Thermostat, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.dialog_high_temp_title)) },
            text = { Text(stringResource(R.string.dialog_high_temp_msg)) },
            confirmButton = { 
                Button(
                    onClick = { 
                        highTempAllowed = true
                        updateCustomLimit(50)
                        showHighTempWarning = false 
                    }, 
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { 
                    Text(stringResource(R.string.btn_allow_high_temp)) 
                } 
            },
            dismissButton = { TextButton(onClick = { showHighTempWarning = false }) { Text(stringResource(R.string.btn_cancel)) } },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }
}

@Composable
fun ThermalReactor(
    currentTemp: Int,
    throttleLimit: Int,
    activeProfile: ThermalProfile,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "reactor")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "rotation"
    )

    val ringColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val dashColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().blur(50.dp)) {
            drawCircle(color = color.copy(alpha = 0.2f * pulse), radius = size.minDimension / 2.2f)
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = size.minDimension / 2
            val radius = center - 10.dp.toPx()
            
            drawCircle(color = ringColor, radius = radius, style = Stroke(width = 24.dp.toPx()))
            
            rotate(rotation) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, dashColor, Color.Transparent)),
                    radius = radius - 30.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 40f), 0f))
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when(activeProfile) {
                    ThermalProfile.DEFAULT -> stringResource(R.string.mode_default)
                    ThermalProfile.ADAPTIVE -> stringResource(R.string.mode_adaptive)
                    ThermalProfile.DISABLED -> stringResource(R.string.mode_disabled)
                    ThermalProfile.CUSTOM -> "$throttleLimit°C"
                },
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            
            Spacer(Modifier.height(4.dp))
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.15f))
                    .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.temp_current_fmt, currentTemp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            
            Spacer(Modifier.height(12.dp))

            Text(
                text = when(activeProfile) {
                    ThermalProfile.DEFAULT -> stringResource(R.string.desc_default)
                    ThermalProfile.ADAPTIVE -> stringResource(R.string.desc_adaptive)
                    ThermalProfile.DISABLED -> stringResource(R.string.desc_disabled)
                    ThermalProfile.CUSTOM -> stringResource(R.string.desc_custom)
                }.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun ThermalModeRail(
    activeProfile: ThermalProfile,
    onProfileSelected: (ThermalProfile) -> Unit,
    activeColor: Color
) {
    val profiles = listOf(
        Triple(ThermalProfile.DEFAULT, stringResource(R.string.profile_os), Icons.Rounded.Android),
        Triple(ThermalProfile.ADAPTIVE, stringResource(R.string.profile_ai), Icons.Rounded.AutoAwesome),
        Triple(ThermalProfile.DISABLED, stringResource(R.string.profile_off), Icons.Rounded.Block),
        Triple(ThermalProfile.CUSTOM, stringResource(R.string.profile_man), Icons.Rounded.Tune)
    )

    Box(
        modifier = Modifier.fillMaxWidth().height(80.dp).frostedGlass(
            backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
            shape = RoundedCornerShape(40.dp)
        ).padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            profiles.forEach { (profile, label, icon) ->
                val isActive = activeProfile == profile
                val widthWeight by animateFloatAsState(if (isActive) 2f else 1f, label = "width")
                val alpha by animateFloatAsState(if (isActive) 1f else 0.5f, label = "alpha")

                Box(
                    modifier = Modifier.weight(widthWeight).fillMaxHeight().clip(RoundedCornerShape(32.dp))
                        .background(if (isActive) activeColor.copy(alpha = 0.15f) else Color.Transparent)
                        .border(1.dp, if (isActive) activeColor.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(32.dp))
                        .clickable { onProfileSelected(profile) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, label, tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp).alpha(alpha))
                        AnimatedVisibility(visible = isActive) {
                            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = activeColor, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThermalFuelRodSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    color: Color
) {
    var sliderWidth by remember { mutableFloatStateOf(0f) }
    var rawValue by remember { mutableFloatStateOf(value.toFloat()) }
    
    SideEffect { 
        if (abs(rawValue - value) > 1.5f) rawValue = value.toFloat() 
    }
    
    val progress = ((rawValue - range.first) / (range.last - range.first)).coerceIn(0f, 1f)

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val hatchColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val handleColor = MaterialTheme.colorScheme.inverseSurface
    val handleTextColor = MaterialTheme.colorScheme.inverseOnSurface
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .onSizeChanged { sliderWidth = it.width.toFloat() }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (sliderWidth > 0) {
                        val rangeSpan = range.last - range.first
                        val valuePerPixel = rangeSpan.toFloat() / sliderWidth
                        rawValue = (rawValue + (dragAmount * valuePerPixel)).coerceIn(range.first.toFloat(), range.last.toFloat())
                        val newValue = rawValue.roundToInt()
                        if (newValue != value) onValueChange(newValue)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 10.dp.toPx()
            val count = (size.width / step).toInt()
            for (i in 0..count) {
                drawLine(hatchColor, Offset(i * step, 0f), Offset(i * step + step/2, size.height))
            }
        }
        Box(
            modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.3f), color)))
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${range.first}°C", style = MaterialTheme.typography.labelSmall, color = textColor)
            Box(
                modifier = Modifier
                    .background(handleColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("$value°C", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = handleTextColor)
            }
            Text("${range.last}°C", style = MaterialTheme.typography.labelSmall, color = textColor)
        }
    }
}

@Composable
fun ThermalInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_info_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoItem(Icons.Rounded.Android, stringResource(R.string.info_os_title), stringResource(R.string.info_os_desc))
                InfoItem(Icons.Rounded.AutoAwesome, stringResource(R.string.info_ai_title), stringResource(R.string.info_ai_desc))
                InfoItem(Icons.Rounded.Block, stringResource(R.string.info_disabled_title), stringResource(R.string.info_disabled_desc))
                InfoItem(Icons.Rounded.Tune, stringResource(R.string.info_custom_title), stringResource(R.string.info_custom_desc))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_got_it)) } },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
fun ThermalWarningDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.dialog_warning_title)) },
        text = { Text(stringResource(R.string.dialog_warning_msg)) },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.btn_disable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
fun InfoItem(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
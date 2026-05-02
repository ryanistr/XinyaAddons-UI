package com.rianixia.settings.overlay.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.ui.components.*
import com.rianixia.settings.overlay.ui.viewmodel.HomeViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun BatteryCenterScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showGuideDialog by remember { mutableStateOf(false) }
    
    // Reporting Dialog State
    var showReportDialog by remember { mutableStateOf<ReportType?>(null) }
    val uriHandler = LocalUriHandler.current

    // Theme Color Logic
    val statusColor = when {
        state.batteryInfo.temperature > 40 -> MaterialTheme.colorScheme.error 
        state.batteryInfo.levelPercent < 20 -> MaterialTheme.colorScheme.error 
        state.chargingConfig.bypassEnabled && state.batteryInfo.isCharging -> MaterialTheme.colorScheme.tertiary
        state.batteryInfo.isCharging -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    
    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(1000, easing = LinearOutSlowInEasing),
        label = "themeColor"
    )

    val hazeState = remember { HazeState() }

    MaterialGlassScaffold {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Main Content Layer
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            ) {
                // MAIN CONTENT (Bouncy Scroll)
                BouncyLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 80.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // A. THE REACTOR
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            BatteryReactor(
                                level = state.batteryInfo.levelPercent,
                                isCharging = state.batteryInfo.isCharging,
                                limitEnabled = state.chargingConfig.autoCutEnabled,
                                limitValue = state.chargingConfig.autoCutLimit,
                                color = animatedColor
                            )
                        }
                    }

                    // B. DIAGNOSTIC RAIL
                    item {
                        DiagnosticRail(
                            temp = state.batteryInfo.temperature,
                            healthPercent = state.batteryInfo.healthPercent,
                            cycles = state.batteryInfo.cycleCount,
                            color = animatedColor,
                            onHealthClick = { showReportDialog = ReportType.HEALTH },
                            onCyclesClick = { showReportDialog = ReportType.CYCLES }
                        )
                    }

                    // C. STRATEGIES
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SectionHeader(stringResource(R.string.power_strategy))

                            // 1. AUTO CUT
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                StrategyBreaker(
                                    title = stringResource(R.string.guide_autocut_title),
                                    status = if (state.chargingConfig.autoCutEnabled) stringResource(R.string.status_active) else stringResource(R.string.status_disabled),
                                    desc = stringResource(R.string.guide_autocut_desc),
                                    isActive = state.chargingConfig.autoCutEnabled,
                                    color = MaterialTheme.colorScheme.primary,
                                    icon = Icons.Rounded.BatteryChargingFull,
                                    onClick = { viewModel.setAutoCutEnabled(!state.chargingConfig.autoCutEnabled) }
                                )

                                AnimatedVisibility(
                                    visible = state.chargingConfig.autoCutEnabled,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    BatteryFeatureSlider(
                                        title = stringResource(R.string.limit_charge_limit),
                                        desc = "${stringResource(R.string.limit_stops_at)} ${state.chargingConfig.autoCutLimit}%",
                                        value = state.chargingConfig.autoCutLimit.toFloat(),
                                        onValueChange = { viewModel.setAutoCutLimit(it.toFloat()) },
                                        range = 80f..100f,
                                        steps = 3, // 80, 85, 90, 95, 100
                                        color = animatedColor,
                                        icon = Icons.Rounded.BatteryChargingFull
                                    )
                                }
                            }

                            // 2. AC BYPASS
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                StrategyBreaker(
                                    title = stringResource(R.string.strategy_bypass_title),
                                    status = if (state.chargingConfig.bypassEnabled) stringResource(R.string.status_active) else stringResource(R.string.status_disabled),
                                    desc = stringResource(R.string.strategy_bypass_desc),
                                    isActive = state.chargingConfig.bypassEnabled,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    icon = Icons.Rounded.Bolt,
                                    onClick = { viewModel.setBypassEnabled(!state.chargingConfig.bypassEnabled) }
                                )

                                AnimatedVisibility(
                                    visible = state.chargingConfig.bypassEnabled,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    BatteryFeatureSlider(
                                        title = stringResource(R.string.bypass_resume_title),
                                        desc = stringResource(R.string.bypass_resume_desc, state.chargingConfig.bypassThreshold),
                                        value = state.chargingConfig.bypassThreshold.toFloat(),
                                        onValueChange = { viewModel.setBypassThreshold(it.toFloat()) },
                                        range = 20f..80f,
                                        steps = 2, // 20, 40, 60, 80
                                        color = animatedColor,
                                        icon = Icons.Rounded.Bolt
                                    )
                                }
                            }

                            // 3. THERMAL CUTOFF
                            StrategyBreaker(
                                title = stringResource(R.string.strategy_thermal_title),
                                status = if (state.chargingConfig.tempCutoffEnabled) stringResource(R.string.status_active) else stringResource(R.string.status_disabled),
                                desc = stringResource(R.string.strategy_thermal_desc),
                                isActive = state.chargingConfig.tempCutoffEnabled,
                                color = MaterialTheme.colorScheme.error,
                                icon = Icons.Rounded.Thermostat,
                                onClick = { viewModel.setTempCutoffEnabled(!state.chargingConfig.tempCutoffEnabled) }
                            )
                        }
                    }

                    // D. DOZE MANAGER
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SectionHeader(stringResource(R.string.doze_title).uppercase())

                            StrategyBreaker(
                                title = stringResource(R.string.doze_title),
                                status = if (state.enforceDozeConfig.isEnabled) stringResource(R.string.doze_status_running) else stringResource(R.string.doze_status_stopped),
                                desc = stringResource(R.string.doze_desc),
                                isActive = state.enforceDozeConfig.isEnabled,
                                color = MaterialTheme.colorScheme.secondary,
                                icon = Icons.Rounded.Bedtime,
                                onClick = { viewModel.setEnforceDozeEnabled(!state.enforceDozeConfig.isEnabled) }
                            )

                            AnimatedVisibility(visible = state.enforceDozeConfig.isEnabled) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .frostedGlass(
                                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                            borderColor = Color.Transparent,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.doze_config_title),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary
                                    )

                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                stringResource(R.string.doze_delay),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                "${state.enforceDozeConfig.delaySeconds}s",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Slider(
                                            value = state.enforceDozeConfig.delaySeconds.toFloat(),
                                            onValueChange = { viewModel.setEnforceDozeDelay(it.toInt()) },
                                            valueRange = 0f..300f,
                                            steps = 29
                                        )
                                    }
                                    
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    SettingSwitch(
                                        label = stringResource(R.string.doze_disable_sensors),
                                        checked = state.enforceDozeConfig.disableSensors,
                                        onCheckedChange = { viewModel.setEnforceDozeSensors(it) }
                                    )
                                    SettingSwitch(
                                        label = stringResource(R.string.doze_disable_wifi),
                                        checked = state.enforceDozeConfig.disableWifi,
                                        onCheckedChange = { viewModel.setEnforceDozeWifi(it) }
                                    )
                                    SettingSwitch(
                                        label = stringResource(R.string.doze_disable_data),
                                        checked = state.enforceDozeConfig.disableData,
                                        onCheckedChange = { viewModel.setEnforceDozeData(it) }
                                    )
                                }
                            }
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(48.dp)) }
                }
            }

            // Header Layer
            GradientBlurAppBar(
                title = stringResource(R.string.power_matrix),
                icon = Icons.Rounded.BatteryChargingFull,
                onBackClick = { navController.popBackStack() },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter),
                addStatusBarPadding = true,
                actions = {
                    Surface(
                        onClick = { showGuideDialog = true },
                        shape = CircleShape,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(48.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = stringResource(R.string.info),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            )
        }
    }

    // DIALOGS
    if (showGuideDialog) {
        PowerGuideDialog { showGuideDialog = false }
    }

    showReportDialog?.let { type ->
        ReportInfoDialog(
            type = type,
            onDismiss = { showReportDialog = null },
            onReport = { uriHandler.openUri("https://t.me/xiayap") }
        )
    }
}

// ==========================================
// 1. DIALOG COMPONENTS
// ==========================================

enum class ReportType(val titleRes: Int, val path: String) {
    HEALTH(R.string.title_batt_health, "/sys/devices/platform/charger/battery_health_percent"),
    CYCLES(R.string.title_batt_cycles, "/sys/devices/platform/charger/tran_battery_cycle")
}

@Composable
fun ReportInfoDialog(
    type: ReportType,
    onDismiss: () -> Unit,
    onReport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = stringResource(R.string.dialog_report_title_fmt, stringResource(type.titleRes)), 
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.dialog_report_desc_1),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = type.path,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = stringResource(R.string.dialog_report_desc_2),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = stringResource(R.string.dialog_report_desc_3),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onReport,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text(stringResource(R.string.btn_report_issue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_got_it))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun PowerGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_guide_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PowerGuideItem(Icons.Rounded.Bolt, stringResource(R.string.guide_bypass_title), stringResource(R.string.guide_bypass_desc))
                PowerGuideItem(Icons.Rounded.Thermostat, stringResource(R.string.guide_thermal_title), stringResource(R.string.guide_thermal_desc))
                PowerGuideItem(Icons.Rounded.Bedtime, stringResource(R.string.guide_doze_title), stringResource(R.string.guide_doze_desc))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_got_it))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
private fun PowerGuideItem(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ==========================================
// 2. THE REACTOR (Fixed & Restored)
// ==========================================
@Composable
fun BatteryReactor(
    level: Int,
    isCharging: Boolean,
    limitEnabled: Boolean,
    limitValue: Int,
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

    // Dynamic Ring Color
    val ringColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val dashColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    Box(
        contentAlignment = Alignment.Center, 
        modifier = Modifier.size(300.dp)
    ) {
        // Blurred Glow
        Canvas(modifier = Modifier.fillMaxSize().blur(50.dp)) {
            drawCircle(color = color.copy(alpha = 0.2f * pulse), radius = size.minDimension / 2.2f)
        }
        
        // Rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = size.minDimension / 2
            val radius = center - 10.dp.toPx()
            val strokeWidth = 24.dp.toPx()
            
            // 1. Static Track
            drawCircle(color = ringColor, radius = radius, style = Stroke(width = strokeWidth))
            
            // 2. Rotating Dashes (Decorative)
            rotate(rotation) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, dashColor, Color.Transparent)),
                    radius = radius - 30.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 40f), 0f))
                )
            }
            
            // 3. Bright Battery Level Loop
            val levelSweep = (level / 100f) * 360f
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = levelSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // 4. Limit Indicator
            if (limitEnabled) {
                 val limitAngle = (limitValue / 100f) * 360f
                 val angleRad = (limitAngle - 90) * (PI / 180f)
                 
                 val markerCenter = Offset(
                    (center + radius * cos(angleRad)).toFloat(),
                    (center + radius * sin(angleRad)).toFloat()
                 )
                 
                 drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = markerCenter
                 )
            }
        }
        
        // Central Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$level%",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            
            Spacer(Modifier.height(4.dp))
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f))
                    .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isCharging) stringResource(R.string.charging_status) else stringResource(R.string.discharging_status),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    letterSpacing = 1.sp
                )
            }
            
            if (limitEnabled) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${stringResource(R.string.limit_stops_at)} $limitValue%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==========================================
// 3. BATTERY FEATURE SLIDER (Styled like AZenith)
// ==========================================
@Composable
private fun BatteryFeatureSlider(
    title: String,
    desc: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    color: Color,
    icon: ImageVector
) {
    val animatedColor by animateColorAsState(targetValue = color, label = "sliderColor")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass(
                backgroundColor = animatedColor.copy(alpha = 0.1f),
                borderColor = animatedColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(animatedColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = animatedColor)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    modifier = Modifier
                        .background(animatedColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${value.roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = animatedColor
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = animatedColor,
                    activeTrackColor = animatedColor,
                    inactiveTrackColor = animatedColor.copy(alpha = 0.2f)
                )
            )
        }
    }
}

// ... Reused Components ...

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f) 
        )
    }
}

@Composable
fun DiagnosticRail(
    temp: Float,
    healthPercent: String,
    cycles: String,
    color: Color,
    onHealthClick: () -> Unit,
    onCyclesClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .frostedGlass(
                backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Temp (Non-clickable for now)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Thermostat, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text("$temp°C", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.diag_temp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Box(Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))

        // Health (Clickable)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onHealthClick() }
                .padding(4.dp)
        ) {
            Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            // Display formatted percent if it's a number, otherwise just the raw value
            val displayHealth = if(healthPercent.all { it.isDigit() }) "$healthPercent%" else healthPercent
            Text(displayHealth, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.diag_health), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Box(Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))

        // Cycles (Clickable)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onCyclesClick() }
                .padding(4.dp)
        ) {
            Icon(Icons.Rounded.Autorenew, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(cycles, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.diag_cycles), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StrategyBreaker(
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
            .frostedGlass(
                backgroundColor = color.copy(alpha = bgAlpha),
                borderColor = color.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(20.dp)
            )
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
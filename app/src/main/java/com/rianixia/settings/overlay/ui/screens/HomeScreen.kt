package com.rianixia.settings.overlay.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val hazeState = remember { HazeState() }

    val thermalColorTarget = when {
        state.thermalState.summary == "DISABLED" -> Color(state.thermalState.color) 
        state.thermalState.temperature > 45f -> MaterialTheme.colorScheme.error
        state.thermalState.temperature >= 38f -> Color(0xFFFF9800)
        else -> Color(state.thermalState.color)
    }
    
    val animatedThermalColor by animateColorAsState(
        targetValue = thermalColorTarget,
        animationSpec = tween(500),
        label = "thermalColor"
    )

    MaterialGlassScaffold {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            ) {
                BouncyLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 100.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        SystemIdentityHeader(
                            deviceName = state.deviceInfo.marketName,
                            socName = state.deviceInfo.socName
                        )
                    }

                    item {
                        PowerRail(
                            isCharging = state.batteryInfo.isCharging,
                            uptime = state.uptime,
                            deepSleep = state.deepSleep,
                            batteryLevel = state.batteryInfo.levelPercent
                        )
                    }

                    item {
                        CpuFluxTerminal(
                            graphPoints = state.cpuState.graphPoints,
                            peakFreq = state.cpuState.peakFrequency,
                            governor = state.cpuState.activeGovernor,
                            onClick = { navController.navigate("cpu_control") } 
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            TelemetryModule(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.pwr_cell),
                                value = "${state.batteryInfo.levelPercent}%",
                                subValue = state.batteryInfo.status,
                                icon = Icons.Rounded.BatteryStd,
                                color = if(state.batteryInfo.levelPercent < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                onClick = { navController.navigate("battery_center") }
                            ) {
                                LinearProgressIndicator(
                                    progress = { state.batteryInfo.levelPercent / 100f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = if(state.batteryInfo.levelPercent < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            }

                            TelemetryModule(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.therm_sens),
                                value = "${String.format("%.1f", state.thermalState.temperature)}°C",
                                subValue = state.thermalState.summary,
                                icon = Icons.Rounded.Thermostat,
                                color = animatedThermalColor,
                                onClick = { navController.navigate("thermal_control") }
                            ) {
                                val progress = ((state.thermalState.temperature - 20f) / 40f).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = animatedThermalColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(R.string.quick_protocols),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                QuickProtocolCard(
                                    title = stringResource(R.string.protocol_gaming),
                                    icon = Icons.Rounded.RocketLaunch,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    isActive = state.isAZenithActive,
                                    activeStatusText = "ON",
                                    onClick = { navController.navigate("azenith") } 
                                )
                                
                                if (state.isUndervoltSupported) {
                                    QuickProtocolCard(
                                        title = stringResource(R.string.protocol_efficiency),
                                        icon = Icons.Rounded.Eco,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.secondary,
                                        isActive = state.isUndervoltActive,
                                        activeStatusText = if (state.totalUndervoltValue < 0) "${state.totalUndervoltValue * 6}mV" else "ON",
                                        onClick = { navController.navigate("undervolt") } 
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SystemIdentityHeader(deviceName: String, socName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "status")
    val blink by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = blink))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.system_operational),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            deviceName,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            socName.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontFamily = MaterialTheme.typography.labelLarge.fontFamily
        )
    }
}

@Composable
fun PowerRail(
    isCharging: Boolean,
    uptime: String,
    deepSleep: String,
    batteryLevel: Int
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .frostedGlass(
                backgroundColor = colorScheme.surfaceVariant.copy(alpha = 0.3f),
                borderColor = colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isCharging) Icons.Rounded.Bolt else Icons.Rounded.BatteryStd,
                    contentDescription = null,
                    tint = if (isCharging) colorScheme.primary else colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isCharging) stringResource(R.string.ac_power_source) else stringResource(R.string.battery_source),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
            }

            Box(Modifier.width(1.dp).height(24.dp).background(colorScheme.outlineVariant.copy(alpha = 0.3f)))

            Text(
                stringResource(R.string.uptime_fmt, uptime),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )

            Box(Modifier.width(1.dp).height(24.dp).background(colorScheme.outlineVariant.copy(alpha = 0.3f)))

            Text(
                text = stringResource(R.string.deep_sleep_fmt, deepSleep), 
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CpuFluxTerminal(
    graphPoints: List<Float>,
    peakFreq: String,
    governor: String,
    onClick: () -> Unit
) {
    val color = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .frostedGlass(
                backgroundColor = bg,
                borderColor = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onClick() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
            val path = Path()
            val w = size.width
            val h = size.height
            val step = w / (graphPoints.size - 1).coerceAtLeast(1)
            
            if (graphPoints.isNotEmpty()) {
                path.moveTo(0f, h)
                
                for (i in graphPoints.indices) {
                    val x = i * step
                    val y = h - (graphPoints[i] * h)
                    
                    if (i == 0) path.lineTo(x, y)
                    else {
                        val prevX = (i - 1) * step
                        val prevY = h - (graphPoints[i - 1] * h)
                        path.cubicTo(
                            (prevX + x) / 2, prevY,
                            (prevX + x) / 2, y,
                            x, y
                        )
                    }
                }
                path.lineTo(w, h)
                path.close()
                
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.05f)),
                        startY = 0f,
                        endY = h
                    )
                )
                
                val linePath = Path()
                for (i in graphPoints.indices) {
                    val x = i * step
                    val y = h - (graphPoints[i] * h)
                    if(i==0) linePath.moveTo(x,y) else {
                        val prevX = (i - 1) * step
                        val prevY = h - (graphPoints[i - 1] * h)
                        linePath.cubicTo((prevX + x)/2, prevY, (prevX + x)/2, y, x, y)
                    }
                }
                drawPath(linePath, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.cpu_flux), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
                Box(Modifier.size(6.dp).clip(CircleShape).background(if(graphPoints.lastOrNull() ?: 0f > 0.8f) MaterialTheme.colorScheme.error else color))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(peakFreq, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(governor, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.Speed, null, tint = color.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun TelemetryModule(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    visualContent: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .height(130.dp)
            .frostedGlass(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
                Icon(icon, null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }

            Column {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            visualContent()
        }
    }
}

@Composable
fun QuickProtocolCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier,
    color: Color,
    isActive: Boolean,
    activeStatusText: String = "ON",
    onClick: () -> Unit
) {
    val bgAlpha = if (isActive) 0.3f else 0.15f
    val borderAlpha = if (isActive) 0.8f else 0.3f
    val activeLabel = if (isActive) activeStatusText else ""

    Box(
        modifier = modifier
            .height(64.dp)
            .frostedGlass(
                backgroundColor = color.copy(alpha = bgAlpha),
                borderColor = color.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (isActive) {
                    Text(activeLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color, fontSize = 8.sp)
                }
            }
        }
        
        if (isActive) {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).size(8.dp).clip(CircleShape).background(color)
            )
        } else {
            Icon(
                Icons.Rounded.ChevronRight, 
                null, 
                tint = color.copy(alpha = 0.5f), 
                modifier = Modifier.align(Alignment.CenterEnd).size(18.dp)
            )
        }
    }
}
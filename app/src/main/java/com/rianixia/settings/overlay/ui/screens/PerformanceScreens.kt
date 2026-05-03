package com.rianixia.settings.overlay.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.ui.components.*
import com.rianixia.settings.overlay.ui.viewmodel.HomeViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

// ==========================================
// ROOT PERFORMANCE DASHBOARD
// ==========================================

@Composable
fun PerformanceScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = viewModel()
) {
    val state by homeViewModel.uiState.collectAsState()

    MaterialGlassScaffold {
        BouncyLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 100.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header
            item {
                Column(Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                    Text(
                        stringResource(R.string.perf_matrix),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        stringResource(R.string.sys_authority),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // 2. Primary Subsystem: CPU Core Cluster
            item {
                CpuClusterCard(onClick = { navController.navigate("cpu_control") })
            }

            // 3. Secondary Subsystems: Split Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Game Boost Module
                    GameBoostCard(
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("azenith") }
                    )
                    
                    // Undervolt Module
                    UndervoltCard(
                        modifier = Modifier.weight(1f),
                        undervoltValue = state.totalUndervoltValue,
                        onClick = { navController.navigate("undervolt") }
                    )
                }
            }

            // 4. Tertiary Subsystem: I/O Foundation
            item {
                IoSchedulerCard(onClick = { navController.navigate("io_scheduler") })
            }
        }
    }
}

// ==========================================
// DASHBOARD COMPONENTS
// ==========================================

@Composable
fun CpuClusterCard(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val coreLoads = remember { mutableStateListOf(0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            while (true) {
                for (i in 0 until 8) {
                    try {
                        val curFreq = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").readText().trim().toFloat()
                        val maxFreq = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toFloat()
                        coreLoads[i] = if (maxFreq > 0) curFreq / maxFreq else 0.1f
                    } catch (e: Exception) {
                        coreLoads[i] = 0.1f // Fallback for offline cores or denied access
                    }
                }
                delay(800)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable { onClick() }
            .frostedGlass(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(28.dp),
                accentColor = color
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.05f)) {
            val step = 20.dp.toPx()
            val count = (size.width / step).toInt()
            for (i in 0 until count) {
                drawLine(
                    color = color,
                    start = Offset(i * step, 0f),
                    end = Offset(i * step, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sys_cpu), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
                }
                Icon(Icons.Rounded.Memory, null, tint = color.copy(alpha = 0.5f))
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                repeat(8) { index ->
                    AnimatedLoadBar(color = color, loadPercentage = coreLoads[index])
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.gov_control), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.freq_scaling), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AnimatedLoadBar(color: Color, loadPercentage: Float) {
    val heightScale by animateFloatAsState(
        targetValue = loadPercentage.coerceIn(0.1f, 1f),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "height"
    )

    Box(
        modifier = Modifier
            .width(8.dp)
            .fillMaxHeight(heightScale)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.6f))
    )
}

@Composable
fun GameBoostCard(modifier: Modifier, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.tertiary
    DashboardModule(
        modifier = modifier,
        label = stringResource(R.string.gm_bst),
        title = stringResource(R.string.azenith),
        icon = Icons.Rounded.RocketLaunch,
        color = color,
        onClick = onClick
    ) {
        // Visual: Turbo Gauge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.7f) // Mock value
                    .background(color)
            )
        }
    }
}

@Composable
fun UndervoltCard(modifier: Modifier, undervoltValue: Int, onClick: () -> Unit) {
    val isActive = undervoltValue < 0
    val color = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    
    val displayValue = if (isActive) "${kotlin.math.abs(undervoltValue)}" else "0"
    
    DashboardModule(
        modifier = modifier,
        label = stringResource(R.string.pwr_vlt),
        title = stringResource(R.string.undervolt),
        icon = Icons.Rounded.Bolt,
        color = color,
        onClick = onClick
    ) {
        Text(
            text = "$displayValue Steps",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

@Composable
fun DashboardModule(
    modifier: Modifier,
    label: String,
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .height(140.dp)
            .clickable { onClick() }
            .frostedGlass(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp),
                accentColor = color
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
                Icon(icon, null, tint = color.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }

            // Custom Visual Content
            Box(Modifier.padding(vertical = 12.dp)) {
                content()
            }

            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun IoSchedulerCard(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable { onClick() }
            .frostedGlass(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp),
                accentColor = color
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Block
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Storage, null, tint = color)
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.io_sched_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
                Text(stringResource(R.string.storage_queue), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.optimization), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

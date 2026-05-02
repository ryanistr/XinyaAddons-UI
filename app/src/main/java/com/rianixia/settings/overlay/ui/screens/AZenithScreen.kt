package com.rianixia.settings.overlay.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.ui.components.BouncyLazyColumn
import com.rianixia.settings.overlay.ui.components.GradientBlurAppBar
import com.rianixia.settings.overlay.ui.components.MaterialGlassScaffold
import com.rianixia.settings.overlay.ui.components.frostedGlass
import com.rianixia.settings.overlay.ui.viewmodel.AZenithViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.roundToInt

@Composable
fun AZenithScreen(
    navController: NavController,
    viewModel: AZenithViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }
    
    // Local state for immediate UI feedback, synced with ViewModel
    var cpuFreqLimit by remember(state.cpuFreqLimit) { mutableFloatStateOf(state.cpuFreqLimit) }
    var dndGaming by remember(state.isDndEnabled) { mutableStateOf(state.isDndEnabled) }
    var aggrMemClean by remember(state.isMemCleanEnabled) { mutableStateOf(state.isMemCleanEnabled) }

    val context = LocalContext.current
    var recommendedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    val hazeState = remember { HazeState() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val assetManager = context.assets
                val list = assetManager.list("")
                if (list?.contains("gamelist.txt") == true) {
                    val inputStream = assetManager.open("gamelist.txt")
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val packages = mutableSetOf<String>()
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            val pkg = line.replace(Regex("\\[.*?\\]"), "").trim()
                            if (pkg.isNotEmpty()) packages.add(pkg)
                        }
                    }
                    recommendedPackages = packages
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val displayApps = remember(state.apps, recommendedPackages) {
        state.apps.sortedWith(Comparator { a, b ->
            val aIsRec = a.packageName in recommendedPackages
            val bIsRec = b.packageName in recommendedPackages

            if (aIsRec != bIsRec) return@Comparator if (aIsRec) -1 else 1
            if (a.isEnabled != b.isEnabled) return@Comparator if (a.isEnabled) -1 else 1
            return@Comparator a.label.compareTo(b.label, ignoreCase = true)
        })
    }

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
                BouncyLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Added top padding (80.dp) so content starts below the floating header
                    contentPadding = PaddingValues(top = 80.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        GlobalControl(
                            isEnabled = state.isGlobalEnabled,
                            onToggle = { viewModel.toggleGlobal(it) }
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.az_protocols_title),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                            )
                            
                            val sliderColor = androidx.compose.ui.graphics.lerp(
                                start = Color(0xFF2196F3), 
                                stop = MaterialTheme.colorScheme.error, 
                                fraction = (cpuFreqLimit / 100f).coerceIn(0f, 1f)
                            )
                            
                            AZenithFeatureSlider(
                                title = stringResource(R.string.az_freq_title),
                                desc = stringResource(R.string.az_freq_desc),
                                value = cpuFreqLimit,
                                onValueChange = { 
                                    cpuFreqLimit = it
                                    viewModel.setCpuLimit(it) 
                                },
                                range = 5f..100f,
                                steps = 18,
                                color = sliderColor,
                                icon = Icons.Rounded.Thermostat
                            )
                            
                            AZenithFeatureSwitch(
                                title = stringResource(R.string.az_dnd_title),
                                desc = stringResource(R.string.az_dnd_desc),
                                isActive = dndGaming,
                                color = MaterialTheme.colorScheme.tertiary,
                                icon = Icons.Rounded.DoNotDisturb,
                                onClick = { 
                                    dndGaming = !dndGaming
                                    viewModel.toggleDnd(dndGaming)
                                }
                            )
                            
                            AZenithFeatureSwitch(
                                title = stringResource(R.string.az_mem_title),
                                desc = stringResource(R.string.az_mem_desc),
                                isActive = aggrMemClean,
                                color = MaterialTheme.colorScheme.secondary,
                                icon = Icons.Rounded.CleaningServices,
                                onClick = { 
                                    aggrMemClean = !aggrMemClean
                                    viewModel.toggleMemClean(aggrMemClean)
                                }
                            )
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.az_apps_title_fmt, state.apps.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 24.dp, top = 8.dp)
                        )
                    }

                    if (state.isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        items(displayApps, key = { it.packageName }) { app ->
                            val isRecommended = app.packageName in recommendedPackages
                            // We do not auto-toggle; we just visualize recommendation.
                            
                            AppItemRow(
                                label = app.label,
                                pkg = app.packageName,
                                icon = app.icon,
                                isEnabled = app.isEnabled,
                                isGlobalEnabled = state.isGlobalEnabled,
                                isRecommended = isRecommended,
                                onToggle = { viewModel.toggleApp(app.packageName, it) }
                            )
                        }
                    }
                }
            }

            // Header Overlay Layer
            GradientBlurAppBar(
                title = stringResource(R.string.azenith),
                icon = Icons.Rounded.Bolt,
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
                            Icon(Icons.Rounded.Info, stringResource(R.string.info))
                        }
                    }
                }
            )
        }
    }

    if (showInfoDialog) {
        AZenithInfoDialog { showInfoDialog = false }
    }
}

@Composable
private fun AZenithInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.azenith)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AZenithInfoItem(Icons.Rounded.Bolt, stringResource(R.string.az_info_injection_title), stringResource(R.string.az_info_injection_desc))
                AZenithInfoItem(Icons.Rounded.Speed, stringResource(R.string.az_info_boost_title), stringResource(R.string.az_info_boost_desc))
                AZenithInfoItem(Icons.Rounded.Memory, stringResource(R.string.az_info_mem_title), stringResource(R.string.az_info_mem_desc))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_got_it)) } },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
private fun AZenithInfoItem(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GlobalControl(isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(110.dp)
            .frostedGlass(
                backgroundColor = if(isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                borderColor = if(isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                              else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onToggle(!isEnabled) }
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.az_master_switch),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if(isEnabled) stringResource(R.string.az_engine_active) else stringResource(R.string.az_engine_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = if(isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun AZenithFeatureSlider(
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

@Composable
private fun AZenithFeatureSwitch(
    title: String,
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
            .heightIn(min = 100.dp) // Allow dynamic expansion
            .frostedGlass(
                backgroundColor = color.copy(alpha = bgAlpha),
                borderColor = color.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), // Fill width, height determined by content
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
                    // Removed maxLines so text wraps properly on high DPI/small screens
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Switch(
                checked = isActive,
                onCheckedChange = { onClick() },
                modifier = Modifier.scale(0.8f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = color,
                    checkedTrackColor = color.copy(alpha = 0.5f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun AppItemRow(
    label: String,
    pkg: String,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    isEnabled: Boolean,
    isGlobalEnabled: Boolean,
    isRecommended: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val alpha by animateFloatAsState(targetValue = if (isGlobalEnabled) 1f else 0.5f, label = "alpha")
    val scale by animateFloatAsState(targetValue = if (isEnabled && isGlobalEnabled) 1.02f else 1f, label = "scale")
    
    val backgroundColor = when {
        isEnabled && isGlobalEnabled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        isRecommended -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    }
    
    val borderColor = if (isEnabled && isGlobalEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(80.dp)
            .scale(scale)
            .alpha(alpha)
            .frostedGlass(
                backgroundColor = backgroundColor,
                borderColor = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(enabled = isGlobalEnabled) { onToggle(!isEnabled) }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (isRecommended) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .frostedGlass(
                                    backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                    borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                stringResource(R.string.az_tag_recommended),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
                Text(
                    text = pkg,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle(it) },
                enabled = isGlobalEnabled,
                modifier = Modifier.scale(0.8f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
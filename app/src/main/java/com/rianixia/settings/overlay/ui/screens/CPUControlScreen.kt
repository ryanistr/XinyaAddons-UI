package com.rianixia.settings.overlay.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.ui.components.*
import com.rianixia.settings.overlay.ui.viewmodel.CPUViewModel
import com.rianixia.settings.overlay.ui.viewmodel.ClusterState
import com.rianixia.settings.overlay.ui.viewmodel.PresetType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt

@Composable
fun CpuControlScreen(
    navController: NavController,
    viewModel: CPUViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val hazeState = remember { HazeState() }
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }
    
    // animateColorAsState is in androidx.compose.animation, not core
    val modeColor by animateColorAsState(
        targetValue = if (state.isUltraSaver) MaterialTheme.colorScheme.tertiary 
                      else if (state.isAdvancedMode) MaterialTheme.colorScheme.error 
                      else MaterialTheme.colorScheme.primary,
        label = "modeColor"
    )

    MaterialGlassScaffold {
        Box(Modifier.fillMaxSize()) {
            // Main Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            ) {
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = modeColor)
                    }
                } else {
                    BouncyLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 90.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Mode Switcher
                        item {
                            ModeSegmentedControl(
                                isAdvanced = state.isAdvancedMode,
                                onModeChange = { viewModel.toggleMode(it) },
                                activeColor = modeColor,
                                hazeState = hazeState
                            )
                        }

                        // 2. Animated Content for Tabs
                        item {
                            AnimatedContent(
                                targetState = state.isAdvancedMode,
                                transitionSpec = {
                                    if (targetState) {
                                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> -width } + fadeOut()
                                    } else {
                                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> width } + fadeOut()
                                    }
                                },
                                label = "modeTransition"
                            ) { isAdvanced ->
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (!isAdvanced) {
                                        // --- SIMPLE MODE CONTENT ---
                                        SectionTitle(stringResource(R.string.cpu_section_profiles))
                                        
                                        PresetRow(
                                            onPresetSelect = { viewModel.applyPreset(it) },
                                            activeColor = modeColor
                                        )

                                        SectionTitle(stringResource(R.string.cpu_section_global))

                                        GlobalControlCard(
                                            currentGov = state.currentGlobalGovernor,
                                            availableGovs = state.globalAvailableGovernors,
                                            scale = state.globalScale,
                                            isSaver = state.isUltraSaver,
                                            onGovChange = { viewModel.setGlobalGovernor(it) },
                                            onScaleChange = { viewModel.setGlobalScale(it) },
                                            onSaverToggle = { viewModel.toggleUltraSaver(it) },
                                            color = modeColor,
                                            hazeState = hazeState
                                        )
                                    } else {
                                        // --- ADVANCED MODE CONTENT ---
                                        AdvancedHeader(
                                            color = modeColor,
                                            coreCount = state.totalCores,
                                            globalGovs = state.globalAvailableGovernors,
                                            onGlobalGovChange = { viewModel.setAllClusterGovernors(it) },
                                            hazeState = hazeState
                                        )
                                        
                                        state.clusterStates.forEach { clusterState ->
                                            ClusterConfigCard(
                                                state = clusterState,
                                                isSaverActive = state.isUltraSaver,
                                                onFreqChange = { isMax, freq -> 
                                                    viewModel.setClusterFreq(clusterState.cluster.id, isMax, freq) 
                                                },
                                                onGovChange = { gov ->
                                                    viewModel.setClusterGovernor(clusterState.cluster.id, gov)
                                                },
                                                color = modeColor,
                                                hazeState = hazeState
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Technical Footer
                        item {
                            Spacer(Modifier.height(24.dp))
                            TechnicalFooter(isAdvanced = state.isAdvancedMode)
                        }
                    }
                }
            }

            // AppBar
            GradientBlurAppBar(
                title = stringResource(R.string.cpu_control_title),
                icon = Icons.Rounded.Memory,
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
                        modifier = Modifier
                            .size(40.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Info, stringResource(R.string.info), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )

            // Save FAB
            AnimatedVisibility(
                visible = state.hasChanges,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
            ) {
                FloatingActionButton(
                    onClick = { 
                        viewModel.saveChanges()
                        Toast.makeText(context, R.string.toast_config_applied, Toast.LENGTH_SHORT).show()
                    },
                    containerColor = modeColor,
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

    if (showInfoDialog) {
        CpuInfoDialog(onDismiss = { showInfoDialog = false })
    }
}

// ==========================================
// SUB-COMPONENTS
// ==========================================

@Composable
private fun ModeSegmentedControl(
    isAdvanced: Boolean,
    onModeChange: (Boolean) -> Unit,
    activeColor: Color,
    hazeState: HazeState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .frostedGlass(
                backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(28.dp),
                hazeState = hazeState
            )
            .padding(4.dp)
    ) {
        ModeTab(
            title = stringResource(R.string.cpu_mode_simple),
            isSelected = !isAdvanced,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(false) }
        )
        ModeTab(
            title = stringResource(R.string.cpu_mode_advanced),
            isSelected = isAdvanced,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(true) }
        )
    }
}

@Composable
private fun ModeTab(
    title: String,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val animatedBg by animateColorAsState(if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent)
    val animatedBorder by animateColorAsState(if (isSelected) color.copy(alpha = 0.5f) else Color.Transparent)
    val animatedText by animateColorAsState(if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(animatedBg)
            .border(1.dp, animatedBorder, RoundedCornerShape(24.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = animatedText,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun PresetRow(onPresetSelect: (PresetType) -> Unit, activeColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PresetCard(
            title = stringResource(R.string.cpu_preset_perf),
            icon = Icons.Rounded.Speed,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
            onClick = { onPresetSelect(PresetType.PERFORMANCE) }
        )
        PresetCard(
            title = stringResource(R.string.cpu_preset_balance),
            icon = Icons.Rounded.Balance,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
            onClick = { onPresetSelect(PresetType.BALANCED) }
        )
        PresetCard(
            title = stringResource(R.string.cpu_preset_power),
            icon = Icons.Rounded.BatterySaver,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
            onClick = { onPresetSelect(PresetType.POWERSAVE) }
        )
    }
}

@Composable
private fun PresetCard(title: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(90.dp)
            .frostedGlass(
                backgroundColor = color.copy(alpha = 0.1f),
                borderColor = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun GlobalControlCard(
    currentGov: String,
    availableGovs: List<String>,
    scale: Float,
    isSaver: Boolean,
    onGovChange: (String) -> Unit,
    onScaleChange: (Float) -> Unit,
    onSaverToggle: (Boolean) -> Unit,
    color: Color,
    hazeState: HazeState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass(
                backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                borderColor = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(24.dp),
                hazeState = hazeState
            )
            .padding(20.dp)
    ) {
        GlassDropdown(
            label = stringResource(R.string.cpu_gov_title),
            options = availableGovs,
            selectedOption = currentGov,
            onOptionSelected = onGovChange,
            itemLabelMapper = { it.uppercase() },
            enabled = !isSaver,
            color = color,
            hazeState = hazeState
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.cpu_freq_cap), 
                style = MaterialTheme.typography.titleSmall, 
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text("${scale.roundToInt()}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(12.dp))
        Slider(
            value = scale,
            onValueChange = onScaleChange,
            valueRange = 25f..100f,
            steps = 2,
            enabled = !isSaver,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.2f)
            )
        )

        Spacer(Modifier.height(24.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if(isSaver) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .clickable { onSaverToggle(!isSaver) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Bolt, null, tint = if(isSaver) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.cpu_ultra_saver), 
                    style = MaterialTheme.typography.titleSmall, 
                    fontWeight = FontWeight.Bold,
                    color = if(isSaver) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.cpu_ultra_saver_desc), 
                    style = MaterialTheme.typography.bodySmall, 
                    color = if(isSaver) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha=0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isSaver,
                onCheckedChange = onSaverToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.tertiary)
            )
        }
    }
}

@Composable
private fun AdvancedHeader(
    color: Color, 
    coreCount: Int,
    globalGovs: List<String>,
    onGlobalGovChange: (String) -> Unit,
    hazeState: HazeState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .frostedGlass(
                backgroundColor = color.copy(alpha = 0.1f),
                borderColor = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                hazeState = hazeState
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.cpu_advanced_warning),
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.cpu_cores_detected_fmt, coreCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            GlassDropdown(
                label = "",
                options = globalGovs,
                selectedOption = stringResource(R.string.cpu_change_all_govs),
                onOptionSelected = onGlobalGovChange,
                itemLabelMapper = { it.uppercase() },
                color = color,
                hazeState = hazeState,
                modifier = Modifier.width(180.dp)
            )
        }
    }
}

@Composable
private fun ClusterConfigCard(
    state: ClusterState,
    isSaverActive: Boolean,
    onFreqChange: (isMax: Boolean, freq: Int) -> Unit,
    onGovChange: (String) -> Unit,
    color: Color,
    hazeState: HazeState
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass(
                backgroundColor = color.copy(alpha = 0.15f),
                borderColor = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp),
                hazeState = hazeState
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp, 24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    state.cluster.name, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.cpu_cluster_cores_fmt, state.cluster.cores.joinToString(", ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassDropdown(
            label = stringResource(R.string.cpu_gov_title),
            options = state.cluster.availableGovs,
            selectedOption = state.currentGov,
            onOptionSelected = onGovChange,
            itemLabelMapper = { it.uppercase() },
            enabled = !isSaverActive,
            color = color,
            hazeState = hazeState
        )

        Spacer(Modifier.height(12.dp))

        GlassDropdown(
            label = stringResource(R.string.cpu_label_max),
            options = state.validMaxOptions.sortedDescending(),
            selectedOption = state.currentMax,
            onOptionSelected = { onFreqChange(true, it) },
            itemLabelMapper = { context.getString(R.string.cpu_freq_mhz_fmt, it / 1000) },
            enabled = !isSaverActive,
            color = color,
            hazeState = hazeState
        )
        
        Spacer(Modifier.height(12.dp))

        GlassDropdown(
            label = stringResource(R.string.cpu_label_min),
            options = state.validMinOptions.sortedDescending(),
            selectedOption = state.currentMin,
            onOptionSelected = { onFreqChange(false, it) },
            itemLabelMapper = { context.getString(R.string.cpu_freq_mhz_fmt, it / 1000) },
            enabled = !isSaverActive,
            color = MaterialTheme.colorScheme.secondary,
            hazeState = hazeState
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun TechnicalFooter(isAdvanced: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (isAdvanced) stringResource(R.string.cpu_manual_override_active) else stringResource(R.string.cpu_footer_simple),
            style = MaterialTheme.typography.labelSmall,
            // Changed from 'outline' to 'onSurfaceVariant' because 'outline' (XinyaDarkDivider) 
            // is too dark (0xFF30363D) for text on a dark background.
            color = MaterialTheme.colorScheme.onSurfaceVariant 
        )
    }
}

@Composable
private fun CpuInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cpu_control_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.cpu_info_desc), style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(stringResource(R.string.cpu_info_simple_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.cpu_info_simple_desc), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.cpu_info_advanced_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.cpu_info_advanced_desc), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_got_it)) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
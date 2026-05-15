package com.rianixia.settings.overlay.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.ui.components.*
import com.rianixia.settings.overlay.ui.viewmodel.ScreenDisplayViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorCalibrationScreen(
    navController: NavController,
    viewModel: ScreenDisplayViewModel = viewModel()
) {
    val redVal by viewModel.redVal.collectAsState()
    val greenVal by viewModel.greenVal.collectAsState()
    val blueVal by viewModel.blueVal.collectAsState()
    val saturationVal by viewModel.saturationVal.collectAsState()
    val colorTemp by viewModel.colorTemperature.collectAsState()
    val presets by viewModel.presets.collectAsState()

    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    
    var showSaveDialog by remember { mutableStateOf(false) }
    var showManualInputDialog by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var presetNameInput by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importPresetFromUri(context, it) }
    }

    MaterialGlassScaffold {
        Box(Modifier.fillMaxSize()) {
            BouncyLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
                contentPadding = PaddingValues(top = 100.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Presets Card (Now at Top)
                item {
                    MaterialGlassCard(
                        header = stringResource(R.string.sd_color_presets_title),
                        headerTrailing = {
                            Row {
                                IconButton(onClick = { importLauncher.launch("application/json") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.FileOpen,
                                        contentDescription = stringResource(R.string.btn_import),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = { showSaveDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.sd_color_save_preset),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    ) {
                        if (presets.isEmpty()) {
                            Text(
                                text = stringResource(R.string.sd_color_no_presets),
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                presets.keys.forEach { name ->
                                    PresetItem(
                                        name = name,
                                        onLoad = {
                                            viewModel.loadPreset(name)
                                            Toast.makeText(context, context.getString(R.string.sd_color_preset_loaded, name), Toast.LENGTH_SHORT).show()
                                        },
                                        onDelete = {
                                            viewModel.deletePreset(name)
                                            Toast.makeText(context, context.getString(R.string.sd_color_preset_deleted), Toast.LENGTH_SHORT).show()
                                        },
                                        onExport = {
                                            viewModel.exportPresetToFile(context, name)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Color Temperature Card (Now Middle)
                item {
                    MaterialGlassCard(header = stringResource(R.string.sd_color_temp)) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.sd_color_temp_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Surface(
                                    color = if (colorTemp == 1000f) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.clickable { showManualInputDialog = "Temperature" to colorTemp }
                                ) {
                                    Text(
                                        text = colorTemp.toInt().toString(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (colorTemp == 1000f) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Slider(
                                value = colorTemp,
                                onValueChange = { viewModel.updateColorCalibration(temperature = it) },
                                onValueChangeFinished = { viewModel.finishColorCalibration() },
                                valueRange = 0f..2000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.sd_color_temp_warm), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(stringResource(R.string.sd_color_temp_cold), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                // RGB and Saturation Card (Now Bottom)
                item {
                    MaterialGlassCard(
                        header = stringResource(R.string.sd_color_calibration_title),
                        headerTrailing = {
                            IconButton(onClick = {
                                viewModel.resetColorCalibration()
                                Toast.makeText(context, context.getString(R.string.sd_color_reset_toast), Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.sd_color_reset),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColorSliderItem(
                                label = stringResource(R.string.sd_color_red),
                                summary = stringResource(R.string.sd_color_red_desc),
                                value = redVal,
                                accentColor = Color(0xFFEF5350),
                                onValueChange = { viewModel.updateColorCalibration(red = it) },
                                onFinish = { viewModel.finishColorCalibration() },
                                onBadgeClick = { showManualInputDialog = "Red" to redVal }
                            )
                            ColorSliderItem(
                                label = stringResource(R.string.sd_color_green),
                                summary = stringResource(R.string.sd_color_green_desc),
                                value = greenVal,
                                accentColor = Color(0xFF66BB6A),
                                onValueChange = { viewModel.updateColorCalibration(green = it) },
                                onFinish = { viewModel.finishColorCalibration() },
                                onBadgeClick = { showManualInputDialog = "Green" to greenVal }
                            )
                            ColorSliderItem(
                                label = stringResource(R.string.sd_color_blue),
                                summary = stringResource(R.string.sd_color_blue_desc),
                                value = blueVal,
                                accentColor = Color(0xFF42A5F5),
                                onValueChange = { viewModel.updateColorCalibration(blue = it) },
                                onFinish = { viewModel.finishColorCalibration() },
                                onBadgeClick = { showManualInputDialog = "Blue" to blueVal }
                            )
                            ColorSliderItem(
                                label = stringResource(R.string.sd_color_saturation),
                                summary = stringResource(R.string.sd_color_saturation_desc),
                                value = saturationVal,
                                accentColor = MaterialTheme.colorScheme.primary,
                                onValueChange = { viewModel.updateColorCalibration(saturation = it) },
                                onFinish = { viewModel.finishColorCalibration() },
                                onBadgeClick = { showManualInputDialog = "Saturation" to saturationVal }
                            )
                        }
                    }
                }
            }
            
            GradientBlurAppBar(
                title = stringResource(R.string.sd_color_calibration_title),
                icon = Icons.Rounded.Palette,
                onBackClick = { navController.popBackStack() },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter),
                addStatusBarPadding = true
            )
        }
    }

    // Save Preset Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.sd_color_save_preset)) },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    label = { Text(stringResource(R.string.sd_color_preset_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (presetNameInput.isNotBlank()) {
                            viewModel.savePreset(presetNameInput)
                            presetNameInput = ""
                            showSaveDialog = false
                            Toast.makeText(context, context.getString(R.string.sd_color_preset_saved), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Manual Input Dialog
    showManualInputDialog?.let { (label, currentValue) ->
        var inputValue by remember { mutableStateOf(currentValue.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { showManualInputDialog = null },
            title = { Text(stringResource(R.string.sd_color_manual_input) + ": $label") },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { if (it.all { char -> char.isDigit() }) inputValue = it },
                    label = { Text(stringResource(R.string.sd_color_enter_value)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newVal = inputValue.toFloatOrNull()?.coerceIn(0f, 2000f)
                        if (newVal != null) {
                            when (label) {
                                "Red" -> viewModel.updateColorCalibration(red = newVal)
                                "Green" -> viewModel.updateColorCalibration(green = newVal)
                                "Blue" -> viewModel.updateColorCalibration(blue = newVal)
                                "Saturation" -> viewModel.updateColorCalibration(saturation = newVal)
                                "Temperature" -> viewModel.updateColorCalibration(temperature = newVal)
                            }
                            viewModel.finishColorCalibration()
                        }
                        showManualInputDialog = null
                    }
                ) {
                    Text(stringResource(R.string.sd_apply_resolution))
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
fun PresetItem(
    name: String,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { onLoad() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.ColorLens, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onExport) {
            Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ColorSliderItem(
    label: String,
    summary: String,
    value: Float,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    onFinish: () -> Unit,
    onBadgeClick: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val animatedProgress by animateFloatAsState(
        targetValue = value / 2000f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.outline
                )
            }
            
            Surface(
                color = if (value == 1000f) colorScheme.surfaceVariant else colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { onBadgeClick() }
            ) {
                Text(
                    text = value.toInt().toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (value == 1000f) colorScheme.onSurfaceVariant else colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colorScheme.surfaceContainerHighest)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(accentColor.copy(alpha = 0.6f), accentColor)
                        )
                    )
            )

            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onFinish,
                valueRange = 0f..2000f,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.sd_min_val, 0), style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
            Text(stringResource(R.string.sd_default_val, 1000), style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
            Text(stringResource(R.string.sd_max_val, 2000), style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
        }
    }
}
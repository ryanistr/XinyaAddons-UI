package com.rianixia.settings.overlay.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.ui.components.*
import com.rianixia.settings.overlay.ui.viewmodel.ScreenDisplayViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EyeCareScreen(
    navController: NavController,
    viewModel: ScreenDisplayViewModel = viewModel()
) {
    val eyeCareSchedule by viewModel.eyeCareSchedule.collectAsState()
    val eyeCareEnabled = eyeCareSchedule > 0
    
    val eyeCareIntensity by viewModel.eyeCareIntensity.collectAsState()
    val eyeCareStartTime by viewModel.eyeCareStartTime.collectAsState()
    val eyeCareEndTime by viewModel.eyeCareEndTime.collectAsState()
    val extraDim by viewModel.extraDimEnabled.collectAsState()
    val extraDimIntensity by viewModel.extraDimIntensity.collectAsState()

    val context = LocalContext.current
    val hazeState = remember { HazeState() }

    MaterialGlassScaffold {
        Box(Modifier.fillMaxSize()) {
            BouncyLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
                contentPadding = PaddingValues(top = 100.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Eye Care Section
                item {
                    MaterialGlassCard(header = stringResource(R.string.sd_eye_care_title)) {
                        XinyaToggle(
                            title = stringResource(R.string.sd_eye_care_enable),
                            subtitle = stringResource(R.string.sd_eye_care_desc),
                            icon = Icons.Rounded.Visibility,
                            checked = eyeCareEnabled,
                            onCheckedChange = { viewModel.toggleEyeCare(it) },
                            iconTint = MaterialTheme.colorScheme.primary
                        )
                        
                        AnimatedVisibility(visible = eyeCareEnabled) {
                            Column {
                                MaterialDivider()
                                
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(R.string.sd_eye_care_schedule),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    
                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                        if (maxWidth < 360.dp) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                ScheduleChip(
                                                    label = stringResource(R.string.sd_eye_care_schedule_always),
                                                    selected = eyeCareSchedule == 1,
                                                    onClick = { viewModel.setEyeCareSchedule(1) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                ScheduleChip(
                                                    label = stringResource(R.string.sd_eye_care_schedule_custom),
                                                    selected = eyeCareSchedule == 2,
                                                    onClick = { viewModel.setEyeCareSchedule(2) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ScheduleChip(
                                                    label = stringResource(R.string.sd_eye_care_schedule_always),
                                                    selected = eyeCareSchedule == 1,
                                                    onClick = { viewModel.setEyeCareSchedule(1) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                ScheduleChip(
                                                    label = stringResource(R.string.sd_eye_care_schedule_custom),
                                                    selected = eyeCareSchedule == 2,
                                                    onClick = { viewModel.setEyeCareSchedule(2) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                    
                                    AnimatedVisibility(visible = eyeCareSchedule == 2) {
                                        Column(modifier = Modifier.padding(top = 16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                TimeSelector(
                                                    label = stringResource(R.string.sd_eye_care_start_time),
                                                    time = eyeCareStartTime,
                                                    onTimeSelected = { viewModel.setEyeCareTime(start = it) }
                                                )
                                                TimeSelector(
                                                    label = stringResource(R.string.sd_eye_care_end_time),
                                                    time = eyeCareEndTime,
                                                    onTimeSelected = { viewModel.setEyeCareTime(end = it) }
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                MaterialDivider()
                                
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(stringResource(R.string.sd_eye_care_intensity), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${(eyeCareIntensity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = eyeCareIntensity,
                                        onValueChange = { viewModel.setEyeCareIntensity(it) },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Extra Dim Section
                item {
                    MaterialGlassCard(header = stringResource(R.string.sd_extra_dim_title)) {
                        XinyaToggle(
                            title = stringResource(R.string.sd_extra_dim_title),
                            subtitle = stringResource(R.string.sd_extra_dim_desc),
                            icon = Icons.Rounded.BrightnessLow,
                            checked = extraDim,
                            onCheckedChange = { viewModel.toggleExtraDim(it) },
                            iconTint = MaterialTheme.colorScheme.secondary
                        )
                        AnimatedVisibility(visible = extraDim) {
                            Column(modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.sd_dim_intensity), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${(extraDimIntensity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = extraDimIntensity,
                                    onValueChange = { viewModel.setExtraDimIntensity(it) },
                                    valueRange = 0f..0.9f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            GradientBlurAppBar(
                title = stringResource(R.string.sd_eye_care_title),
                icon = Icons.Rounded.Visibility,
                onBackClick = { navController.popBackStack() },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter),
                addStatusBarPadding = true
            )
        }
    }
}

@Composable
fun ScheduleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun TimeSelector(
    label: String,
    time: String,
    onTimeSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val parts = time.split(":").map { it.toIntOrNull() ?: 0 }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        Spacer(Modifier.height(6.dp))
        Surface(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        onTimeSelected(String.format("%02d:%02d", hour, minute))
                    },
                    parts[0],
                    parts[1],
                    true
                ).show()
            },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            modifier = Modifier.width(110.dp)
        ) {
            Text(
                text = time,
                modifier = Modifier.padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
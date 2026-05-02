package com.rianixia.settings.overlay.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.ui.components.*
import com.rianixia.settings.overlay.ui.viewmodel.IOViewModel
import com.rianixia.settings.overlay.ui.viewmodel.IoEvent
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun IoSchedulerScreen(
    navController: NavController,
    viewModel: IOViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val hazeState = remember { HazeState() }
    var showInfoDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Theme Colors
    val primaryColor = MaterialTheme.colorScheme.secondary
    val warningColor = MaterialTheme.colorScheme.error

    // Event Listener for Toasts
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is IoEvent.SchedulerChanged -> {
                    // Toast Logic: Language-aware formatting
                    val msg = context.getString(R.string.io_scheduler_changed_success, event.schedulerName)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    MaterialGlassScaffold {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            ) {
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primaryColor)
                    }
                } else {
                    BouncyLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 100.dp, bottom = 40.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Warning Banner
                        item {
                            WarningBanner(color = warningColor)
                        }

                        // 2. Main Controller
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .frostedGlass(
                                        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                        borderColor = primaryColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(24.dp),
                                        hazeState = hazeState
                                    )
                                    .padding(20.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Tune, null, tint = primaryColor)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        stringResource(R.string.active_scheduler).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor
                                    )
                                }
                                
                                Spacer(Modifier.height(16.dp))

                                GlassDropdown(
                                    label = stringResource(R.string.io_sched_label),
                                    options = state.unifiedAvailableSchedulers,
                                    selectedOption = state.targetScheduler,
                                    onOptionSelected = { viewModel.setScheduler(it) },
                                    itemLabelMapper = { it },
                                    color = primaryColor,
                                    hazeState = hazeState
                                )

                                Spacer(Modifier.height(12.dp))

                                // Recommendation Text
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.TipsAndUpdates, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.io_sched_recommendation),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                Spacer(Modifier.height(16.dp))
                                
                                // Status Indicator
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.io_global_status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    if (state.isMixedState) {
                                        StatusChip(text = stringResource(R.string.status_mixed), color = warningColor)
                                    } else {
                                        StatusChip(text = stringResource(R.string.status_synced), color = primaryColor)
                                    }
                                }
                            }
                        }

                        // 3. Dynamic Block Device List
                        item {
                            Text(
                                stringResource(R.string.io_block_map),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp, top = 8.dp)
                            )
                        }

                        if (state.devices.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(R.string.io_no_devices),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(state.devices.size) { index ->
                                val device = state.devices[index]
                                DeviceBlockCard(
                                    name = device.name,
                                    path = device.path,
                                    currentScheduler = device.currentScheduler,
                                    color = primaryColor,
                                    hazeState = hazeState
                                )
                            }
                        }
                    }
                }
            }

            // AppBar with Info Button
            GradientBlurAppBar(
                title = stringResource(R.string.io_scheduler_title),
                icon = Icons.Rounded.Storage,
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
                            Icon(Icons.Rounded.QuestionMark, stringResource(R.string.info), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        }
    }

    if (showInfoDialog) {
        IoInfoDialog(onDismiss = { showInfoDialog = false })
    }
}

// ==========================================
// SUB-COMPONENTS
// ==========================================

@Composable
private fun WarningBanner(color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.io_warning_msg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DeviceBlockCard(
    name: String,
    path: String,
    currentScheduler: String,
    color: Color,
    hazeState: HazeState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass(
                backgroundColor = color.copy(alpha = 0.05f),
                borderColor = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
                hazeState = hazeState
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Box
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.SdStorage, null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
        
        Spacer(Modifier.width(16.dp))
        
        // Info
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        // Status
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.status_active_caps), style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.outline)
            Text(currentScheduler, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun IoInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.io_guide_title)) 
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.io_guide_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                
                Text(stringResource(R.string.io_common_types), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                
                BulletPoint(stringResource(R.string.io_type_noop), stringResource(R.string.io_desc_noop))
                BulletPoint(stringResource(R.string.io_type_mq), stringResource(R.string.io_desc_mq))
                BulletPoint(stringResource(R.string.io_type_bfq), stringResource(R.string.io_desc_bfq))
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    stringResource(R.string.io_guide_footer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_understand)) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun BulletPoint(title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) { 
        Text("•", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
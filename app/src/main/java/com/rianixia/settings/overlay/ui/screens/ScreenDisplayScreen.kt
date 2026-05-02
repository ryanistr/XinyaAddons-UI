package com.rianixia.settings.overlay.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rianixia.settings.overlay.ui.components.*
import com.rianixia.settings.overlay.ui.viewmodel.ScreenDisplayViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenDisplayScreen(
    navController: NavController,
    viewModel: ScreenDisplayViewModel = viewModel()
) {
    val vSync by viewModel.vSyncEnabled.collectAsState()
    val resState by viewModel.resState.collectAsState()
    val extraDim by viewModel.extraDimEnabled.collectAsState()
    
    val hazeState = remember { HazeState() }
    
    // Transient UI state for the selected resolution before applying
    var selectedResolution by remember { mutableStateOf<String?>(null) }

    if (resState.pendingRes != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Confirm Resolution", fontWeight = FontWeight.Bold) },
            text = { 
                Text("Do you want to keep the new screen resolution? Reverting to previous state in ${resState.countdown} seconds.") 
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmResolution() }) {
                    Text("Keep Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.revertResolution() }) {
                    Text("Revert")
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
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
                item {
                    MaterialGlassCard(header = "Display Matrix") {
                        XinyaToggle(
                            title = "VSync Synchronization",
                            subtitle = "Synchronize frame rates to prevent screen tearing",
                            icon = Icons.Rounded.Sync,
                            checked = vSync,
                            onCheckedChange = { viewModel.toggleVSync(it) }
                        )
                        MaterialDivider()
                        Column {
                            XinyaToggle(
                                title = "Extra Dim Mode",
                                subtitle = "Reduce brightness below the minimum system level",
                                icon = Icons.Rounded.BrightnessLow,
                                checked = extraDim,
                                onCheckedChange = { viewModel.toggleExtraDim(it) }
                            )
                            AnimatedVisibility(visible = extraDim) {
                                val intensity by viewModel.extraDimIntensity.collectAsState()
                                Column(modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Dim Intensity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${(intensity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = intensity,
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
                
                item {
                    MaterialGlassCard(header = "Render Resolution Target") {
                        Text(
                            text = "Current: ${resState.currentRes}", 
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        if (resState.availableResolutions.isEmpty() || resState.currentRes == "Loading...") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            ) {
                                resState.availableResolutions.forEach { res ->
                                    val title = if (res == "Reset") "Default" else "${res.substringBefore('x')}p"
                                    val sub = if (res == "Reset") "Native" else res
                                    
                                    val isSelected = if (selectedResolution != null) {
                                        selectedResolution == res
                                    } else {
                                        resState.currentRes == res || (res == "Reset" && resState.currentRes == resState.physicalRes)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable(enabled = resState.pendingRes == null) {
                                                selectedResolution = res
                                            }
                                    ) {
                                        ResoChip(
                                            modifier = Modifier.width(120.dp),
                                            title = title,
                                            sub = sub,
                                            selected = isSelected
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            GradientBlurAppBar(
                title = "Display Matrix",
                icon = Icons.Rounded.DisplaySettings,
                onBackClick = { navController.popBackStack() },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter),
                addStatusBarPadding = true
            )

            // Calculate FAB visibility
            val isDefaultRedundant = selectedResolution == "Reset" && resState.currentRes == resState.physicalRes
            val showFab = selectedResolution != null && selectedResolution != resState.currentRes && !isDefaultRedundant

            AnimatedVisibility(
                visible = showFab,
                enter = fadeIn() + slideInHorizontally { -it }, // Slide in from the left
                exit = fadeOut() + slideOutHorizontally { -it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        selectedResolution?.let { viewModel.changeResolutionImmediate(it) }
                        selectedResolution = null
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Apply Resolution")
                }
            }
        }
    }
}
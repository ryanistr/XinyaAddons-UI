// File: main/java/com/rianixia/settings/overlay/ui/screens/ScreenDisplayScreen.kt
package com.rianixia.settings.overlay.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

private const val TAG = "ScreenDisplay"

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
    
    var selectedResolution by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedResolution, resState.currentRes, resState.pendingRes) {
        val isDefaultRedundant = selectedResolution == "Reset" && resState.currentRes == resState.physicalRes
        val showFab = selectedResolution != null && selectedResolution != resState.currentRes && !isDefaultRedundant
        Log.d(TAG, "UI State Check -> selectedRes: $selectedResolution | currentRes: ${resState.currentRes} | pendingRes: ${resState.pendingRes} | showFab evaluates to: $showFab")
    }

    if (resState.pendingRes != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Confirm Resolution", fontWeight = FontWeight.Bold) },
            text = {
                Text("Do you want to keep the new screen resolution? Reverting to previous state in ${resState.countdown} seconds.")
            },
            confirmButton = {
                Button(onClick = {
                    Log.d(TAG, "Dialog: Keep Changes clicked")
                    viewModel.confirmResolution()
                }) {
                    Text("Keep Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    Log.d(TAG, "Dialog: Revert clicked")
                    viewModel.revertResolution()
                }) {
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
                            title = "VSync",
                            subtitle = "Synchronize frame rates to prevent screen tearing. Disabling may improve performance and touch responsiveness at the cost of potential visual artifacts.",
                            icon = Icons.Rounded.Sync,
                            checked = vSync,
                            onCheckedChange = { viewModel.toggleVSync(it) },
                            iconTint = MaterialTheme.colorScheme.tertiary
                        )
                        MaterialDivider()
                        Column {
                            XinyaToggle(
                                title = "Extra Dim Mode",
                                subtitle = "Reduce brightness below the minimum system level",
                                icon = Icons.Rounded.BrightnessLow,
                                checked = extraDim,
                                onCheckedChange = { viewModel.toggleExtraDim(it) },
                                iconTint = MaterialTheme.colorScheme.secondary
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        Modifier.size(6.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = resState.currentRes,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        
                        if (resState.availableResolutions.isEmpty() || resState.currentRes == "Loading...") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            val currentActiveRes = if (resState.currentRes == resState.physicalRes && resState.physicalRes != "Unknown") "Reset" else resState.currentRes
                            val displayedSelection = selectedResolution ?: currentActiveRes
                            GlassDropdown(
                                label = "Select Resolution",
                                options = resState.availableResolutions,
                                selectedOption = displayedSelection,
                                onOptionSelected = { res ->
                                    Log.d(TAG, "Resolution dropdown selected: $res")
                                    selectedResolution = res
                                },
                                itemLabelMapper = { res ->
                                    if (res == "Reset") "Default (Native)" else "${res.substringBefore('x')}p ($res)"
                                },
                                enabled = resState.pendingRes == null,
                                color = MaterialTheme.colorScheme.primary,
                                hazeState = hazeState
                            )
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

            val isDefaultRedundant = selectedResolution == "Reset" && resState.currentRes == resState.physicalRes
            val showFab = selectedResolution != null && selectedResolution != resState.currentRes && !isDefaultRedundant
            AnimatedVisibility(
                visible = showFab,
                enter = fadeIn() + slideInHorizontally { -it },
                exit = fadeOut() + slideOutHorizontally { -it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        Log.d(TAG, "FAB clicked to apply resolution: $selectedResolution")
                        selectedResolution?.let { 
                            Log.d(TAG, "Calling viewModel.changeResolutionImmediate($it)")
                            viewModel.changeResolutionImmediate(it) 
                        }
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
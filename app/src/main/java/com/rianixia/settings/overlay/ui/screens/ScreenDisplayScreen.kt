package com.rianixia.settings.overlay.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rianixia.settings.overlay.R
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

    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    
    var selectedResolution by remember { mutableStateOf<String?>(null) }
    val defaultNativeLabel = stringResource(R.string.sd_default_native)

    LaunchedEffect(selectedResolution, resState.currentRes, resState.pendingRes) {
        val isDefaultRedundant = selectedResolution == "Reset" && resState.currentRes == resState.physicalRes
        val showFab = selectedResolution != null && selectedResolution != resState.currentRes && !isDefaultRedundant
        Log.d(TAG, "UI State Check -> selectedRes: $selectedResolution | currentRes: ${resState.currentRes} | pendingRes: ${resState.pendingRes} | showFab evaluates to: $showFab")
    }

    if (resState.pendingRes != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.sd_confirm_res_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(stringResource(R.string.sd_confirm_res_desc, resState.countdown))
            },
            confirmButton = {
                Button(onClick = {
                    Log.d(TAG, "Dialog: Keep Changes clicked")
                    viewModel.confirmResolution()
                }) {
                    Text(stringResource(R.string.sd_keep_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    Log.d(TAG, "Dialog: Revert clicked")
                    viewModel.revertResolution()
                }) {
                    Text(stringResource(R.string.sd_revert))
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
                    MaterialGlassCard(header = stringResource(R.string.sd_render_res_target)) {
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
                                        text = stringResource(R.string.sd_active),
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
                                label = stringResource(R.string.sd_select_resolution),
                                options = resState.availableResolutions,
                                selectedOption = displayedSelection,
                                onOptionSelected = { res ->
                                    Log.d(TAG, "Resolution dropdown selected: $res")
                                    selectedResolution = res
                                },
                                itemLabelMapper = { res ->
                                    if (res == "Reset") defaultNativeLabel else "${res.substringBefore('x')}p ($res)"
                                },
                                enabled = resState.pendingRes == null,
                                color = MaterialTheme.colorScheme.primary,
                                hazeState = hazeState
                            )
                        }
                    }
                }

                item {
                    MaterialGlassCard(header = stringResource(R.string.sd_display_matrix)) {
                        XinyaToggle(
                            title = stringResource(R.string.sd_vsync_title),
                            subtitle = stringResource(R.string.sd_vsync_desc),
                            icon = Icons.Rounded.Sync,
                            checked = vSync,
                            onCheckedChange = { viewModel.toggleVSync(it) },
                            iconTint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                item {
                    NavRow(
                        title = stringResource(R.string.sd_eye_care_title),
                        sub = stringResource(R.string.sd_eye_care_desc),
                        icon = Icons.Rounded.Visibility,
                        onClick = { navController.navigate("eye_care") }
                    )
                }

                item {
                    NavRow(
                        title = stringResource(R.string.sd_color_calibration_title),
                        sub = stringResource(R.string.sd_color_calibration_desc),
                        icon = Icons.Rounded.Palette,
                        onClick = { navController.navigate("color_calibration") }
                    )
                }
            }
            
            GradientBlurAppBar(
                title = stringResource(R.string.sd_display_matrix),
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
                enter = fadeIn() + slideInHorizontally { it },
                exit = fadeOut() + slideOutHorizontally { it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
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
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.sd_apply_resolution))
                }
            }
        }
    }
}
package com.rianixia.settings.overlay.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rianixia.settings.overlay.data.TorchRepository
import com.rianixia.settings.overlay.ui.theme.XinyaTheme
import com.rianixia.settings.overlay.ui.components.frostedGlass
import kotlinx.coroutines.launch

class FlashlightMenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        setContent {
            XinyaTheme {
                FlashlightDialog(onDismiss = { finish() })
            }
        }
    }
}

@Composable
fun FlashlightDialog(onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    
    var frontLevel by remember { mutableStateOf(0) }
    var backLevel by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        frontLevel = TorchRepository.getTorchLevel(TorchRepository.PATH_FRONT_TORCH)
        backLevel = TorchRepository.getTorchLevel(TorchRepository.PATH_BACK_TORCH)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlass(
                        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.FlashlightOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Flashlight Control",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Back Torch Slider
                Column {
                    Text(
                        text = "Main Flashlight (Back)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = backLevel.toFloat(),
                        onValueChange = { newValue ->
                            val level = newValue.toInt()
                            backLevel = level
                            coroutineScope.launch {
                                TorchRepository.setTorchLevel(TorchRepository.PATH_BACK_TORCH, level)
                            }
                        },
                        valueRange = 0f..2f,
                        steps = 1
                    )
                }

                // Front Torch Slider
                Column {
                    Text(
                        text = "Sub Flashlight (Front)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = frontLevel.toFloat(),
                        onValueChange = { newValue ->
                            val level = newValue.toInt()
                            frontLevel = level
                            coroutineScope.launch {
                                TorchRepository.setTorchLevel(TorchRepository.PATH_FRONT_TORCH, level)
                            }
                        },
                        valueRange = 0f..2f,
                        steps = 1
                    )
                }

                // 360 Flash Toggle
                val is360 = frontLevel > 0 && backLevel > 0 && frontLevel == backLevel
                Button(
                    onClick = {
                        val newLevel = if (is360) 0 else 2
                        frontLevel = newLevel
                        backLevel = newLevel
                        coroutineScope.launch {
                            TorchRepository.setTorchLevel(TorchRepository.PATH_FRONT_TORCH, newLevel)
                            TorchRepository.setTorchLevel(TorchRepository.PATH_BACK_TORCH, newLevel)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (is360) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (is360) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(text = if (is360) "Disable 360 Flash" else "Enable 360 Flash (Max)")
                }
            }
        }
    }
}

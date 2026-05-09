package com.rianixia.settings.overlay.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.rianixia.settings.overlay.ui.components.*
import com.rianixia.settings.overlay.ui.viewmodel.HaloLightViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt

private data class EffectMeta(
    val key: String,
    val label: String,
    val accentColor: Color
)

private val effectPalette = listOf(
    Color(0xFF7C8CFF), 
    Color(0xFF5EC4FF), 
    Color(0xFFFF7875), 
    Color(0xFF68D9A4), 
    Color(0xFFFFB347)  
)

private val effectMetas = listOf(
    EffectMeta("Static",             "Static",          effectPalette[0]),
    EffectMeta("FlowingLightNotify", "Flowing\nNotify", effectPalette[1]),
    EffectMeta("BreathingNotify",    "Breathing\nNotify", effectPalette[0]),
    EffectMeta("FlowingLight",       "Flowing\nLight",  effectPalette[1]),
    EffectMeta("GTRacing",           "GT\nRacing",      effectPalette[2]),
    EffectMeta("HeliumFlash",        "Helium\nFlash",   effectPalette[2]),
    EffectMeta("AirFlow",            "Air\nFlow",       effectPalette[1]),
    EffectMeta("Charging",           "Charging",        effectPalette[3]),
    EffectMeta("Breathing",          "Breathing",       effectPalette[0]),
    EffectMeta("Meteor",             "Meteor",          effectPalette[4]),
    EffectMeta("Flow",               "Flow",            effectPalette[1]),
    EffectMeta("Startup2",           "Startup",         effectPalette[3])
)

@Composable
private fun HaloRingPreview(
    effect: String,
    brightness: Float,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val meta = effectMetas.firstOrNull { it.key == effect }
    val baseColor = if (isEnabled) (meta?.accentColor ?: Color(0xFF7C8CFF)) else Color(0xFF444455)
    val alphaMultiplier = if (isEnabled) (brightness / 100f).coerceIn(0.2f, 1f) else 0.2f

    val rotation = rememberInfiniteTransition(label = "rotate")
    val angle by rotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (effect) {
                    "GTRacing" -> 700
                    "FlowingLight", "FlowingLightNotify", "Flow" -> 1800
                    "AirFlow" -> 2400
                    "Meteor" -> 1200
                    else -> 3000
                },
                easing = LinearEasing
            )
        ),
        label = "angle"
    )

    val pulse by rotation.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val flash by rotation.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                1f at 0
                1f at 150
                0f at 200
                0f at 350
                1f at 400
            }
        ),
        label = "flash"
    )

    val chargeFill by rotation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chargeFill"
    )

    val animatedColor by animateColorAsState(
        targetValue = baseColor,
        animationSpec = tween(600),
        label = "color"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .blur(28.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            animatedColor.copy(alpha = 0.5f * alphaMultiplier),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(140.dp)
        ) {
            val strokeWidth = 10.dp.toPx()
            val radius = (size.minDimension / 2f) - strokeWidth / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val effectiveAlpha = alphaMultiplier

            when (effect) {
                "Static" -> {
                    drawCircle(
                        color = animatedColor.copy(alpha = 0.18f),
                        radius = radius,
                        center = center,
                        style = Stroke(strokeWidth * 1.8f)
                    )
                    drawCircle(
                        color = animatedColor.copy(alpha = effectiveAlpha),
                        radius = radius,
                        center = center,
                        style = Stroke(strokeWidth)
                    )
                }

                "Breathing", "BreathingNotify" -> {
                    val a = pulse * effectiveAlpha
                    drawCircle(
                        color = animatedColor.copy(alpha = a * 0.25f),
                        radius = radius * (0.9f + pulse * 0.1f),
                        center = center,
                        style = Stroke(strokeWidth * 2f)
                    )
                    drawCircle(
                        color = animatedColor.copy(alpha = a),
                        radius = radius,
                        center = center,
                        style = Stroke(strokeWidth)
                    )
                }

                "FlowingLight", "FlowingLightNotify", "Flow", "AirFlow" -> {
                    drawCircle(
                        color = animatedColor.copy(alpha = 0.15f),
                        radius = radius,
                        center = center,
                        style = Stroke(strokeWidth)
                    )
                    val sweepBrush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            animatedColor.copy(alpha = 0.4f * effectiveAlpha),
                            animatedColor.copy(alpha = effectiveAlpha),
                            Color.Transparent
                        )
                    )
                    rotate(angle, center) {
                        drawArc(
                            brush = sweepBrush,
                            startAngle = -20f,
                            sweepAngle = 120f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                "GTRacing" -> {
                    drawCircle(
                        color = animatedColor.copy(alpha = 0.12f),
                        radius = radius,
                        center = center,
                        style = Stroke(strokeWidth)
                    )
                    rotate(angle, center) {
                        drawArc(
                            color = animatedColor.copy(alpha = effectiveAlpha),
                            startAngle = -10f,
                            sweepAngle = 55f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(strokeWidth * 1.4f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = animatedColor.copy(alpha = effectiveAlpha * 0.5f),
                            startAngle = 165f,
                            sweepAngle = 35f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                "HeliumFlash" -> {
                    val a = flash * effectiveAlpha
                    drawCircle(
                        color = animatedColor.copy(alpha = a * 0.3f),
                        radius = radius * 1.1f,
                        center = center,
                        style = Stroke(strokeWidth * 2.5f)
                    )
                    drawCircle(
                        color = animatedColor.copy(alpha = a),
                        radius = radius,
                        center = center,
                        style = Stroke(strokeWidth)
                    )
                }

                "Charging" -> {
                    drawCircle(
                        color = animatedColor.copy(alpha = 0.15f),
                        radius = radius,
                        center = center,
                        style = Stroke(strokeWidth)
                    )
                    drawArc(
                        color = animatedColor.copy(alpha = effectiveAlpha),
                        startAngle = -90f,
                        sweepAngle = 360f * chargeFill,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                }

                "Meteor" -> {
                    drawCircle(
                        color = animatedColor.copy(alpha = 0.1f),
                        radius = radius,
                        center = center,
                        style = Stroke(strokeWidth)
                    )
                    rotate(angle, center) {
                        val tailBrush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                animatedColor.copy(alpha = 0.1f * effectiveAlpha),
                                animatedColor.copy(alpha = 0.5f * effectiveAlpha),
                                animatedColor.copy(alpha = effectiveAlpha),
                                Color.Transparent
                            )
                        )
                        drawArc(
                            brush = tailBrush,
                            startAngle = -60f,
                            sweepAngle = 80f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                "Startup2" -> {
                    val expandProgress = chargeFill
                    val r = radius * (0.2f + expandProgress * 0.8f)
                    val a = (1f - expandProgress) * effectiveAlpha
                    drawCircle(
                        color = animatedColor.copy(alpha = a),
                        radius = r,
                        center = center,
                        style = Stroke(strokeWidth * (1f + expandProgress))
                    )
                    drawCircle(
                        color = animatedColor.copy(alpha = a * 0.3f),
                        radius = r * 1.15f,
                        center = center,
                        style = Stroke(strokeWidth * 0.5f)
                    )
                }

                else -> {
                    drawCircle(
                        color = animatedColor.copy(alpha = 0.15f),
                        radius = radius,
                        center = center,
                        style = Stroke(strokeWidth)
                    )
                    drawCircle(
                        color = animatedColor.copy(alpha = effectiveAlpha * pulse),
                        radius = radius,
                        center = center,
                        style = Stroke(strokeWidth)
                    )
                }
            }

            drawCircle(
                color = animatedColor.copy(alpha = effectiveAlpha * 0.6f),
                radius = 6.dp.toPx(),
                center = center
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(88.dp))
            Text(
                text = if (isEnabled) (meta?.label?.replace("\n", " ") ?: effect) else "Off",
                style = MaterialTheme.typography.labelSmall,
                color = animatedColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun HaloLightScreen(
    navController: NavController,
    viewModel: HaloLightViewModel
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val prefs = context.getSharedPreferences("xinya_app_prefs", Context.MODE_PRIVATE)
    var showExperimentalDialog by remember { mutableStateOf(!prefs.getBoolean("halo_warning_shown", false)) }

    val isEnabled by viewModel.isHaloEnabled.collectAsState()
    val activeEffect by viewModel.activeEffect.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val hazeState = remember { HazeState() }

    if (showExperimentalDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Experimental Feature", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Everything on this page is experimental and might have some slight issues, but don't worry, a simple reboot should be enough to fix it.\n\nThe halolight is currently under development. If you'd like to contribute, please consider donating.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextButton(onClick = { uriHandler.openUri("https://t.me/xiaallkay/6") }) {
                        Text("Donate", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            prefs.edit().putBoolean("halo_warning_shown", true).apply()
                            showExperimentalDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Understood", fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    }

    MaterialGlassScaffold {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            ) {
                BouncyLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 108.dp,
                        bottom = 100.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        MaterialGlassCard {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                HaloRingPreview(
                                    effect = activeEffect,
                                    brightness = brightness,
                                    isEnabled = isEnabled,
                                    modifier = Modifier
                                        .size(180.dp)
                                        .padding(vertical = 8.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Live Preview",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                )
                                Spacer(Modifier.height(16.dp))
                                XinyaToggle(
                                    title = "Enable Halo Lighting",
                                    subtitle = "Master switch for rear indicator matrix",
                                    icon = Icons.Rounded.PowerSettingsNew,
                                    checked = isEnabled,
                                    onCheckedChange = { viewModel.toggleHalo(it) }
                                )
                            }
                        }
                    }

                    item {
                        MaterialGlassCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Animation Matrix",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val activeMeta = effectMetas.firstOrNull { it.key == activeEffect }
                                    if (activeMeta != null) {
                                        val badgeColor = if (isEnabled)
                                            activeMeta.accentColor
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        MaterialGlassBadge(
                                            text = activeMeta.label.replace("\n", " "),
                                            containerColor = badgeColor,
                                            contentColor = badgeColor
                                        )
                                    }
                                }

                                val gridHeight = 380.dp
                                Box(modifier = Modifier.height(gridHeight)) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        userScrollEnabled = false
                                    ) {
                                        items(effectMetas) { meta ->
                                            AnimationToggleButton(
                                                meta = meta,
                                                isSelected = activeEffect == meta.key,
                                                onClick = { viewModel.setEffect(meta.key) },
                                                enabled = isEnabled
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        MaterialGlassCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Matrix Intensity",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    BrightnessIndicator(
                                        brightness = brightness,
                                        color = effectMetas.firstOrNull { it.key == activeEffect }
                                            ?.accentColor
                                            ?: MaterialTheme.colorScheme.primary,
                                        isEnabled = isEnabled
                                    )
                                }
                                GradientBrightnessSlider(
                                    value = brightness,
                                    onValueChange = { viewModel.setBrightness(it) },
                                    accentColor = if (isEnabled)
                                        (effectMetas.firstOrNull { it.key == activeEffect }
                                            ?.accentColor ?: MaterialTheme.colorScheme.primary)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    enabled = isEnabled
                                )
                            }
                        }
                    }
                }
            }

            GradientBlurAppBar(
                title = "Halo Matrix",
                icon = Icons.Rounded.Lightbulb,
                onBackClick = { navController.popBackStack() },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter),
                addStatusBarPadding = true
            )
        }
    }
}

@Composable
private fun AnimationToggleButton(
    meta: EffectMeta,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme

    val animatedAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.35f
            isSelected -> 1f
            else -> 0.7f
        },
        animationSpec = tween(300),
        label = "alpha"
    )

    val accentColor = if (enabled) meta.accentColor else colorScheme.onSurface.copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .then(
                if (isSelected && enabled) Modifier.drawBehind {
                    drawRoundRect(
                        color = meta.accentColor.copy(alpha = 0.35f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 8.dp.toPx())
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = if (isSelected && enabled)
                    Brush.linearGradient(
                        colors = listOf(
                            meta.accentColor.copy(alpha = 0.25f),
                            meta.accentColor.copy(alpha = 0.10f)
                        )
                    )
                else
                    Brush.linearGradient(
                        colors = listOf(
                            colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
            )
            .border(
                width = if (isSelected && enabled) 1.5.dp else 0.8.dp,
                color = if (isSelected && enabled)
                    meta.accentColor.copy(alpha = 0.5f)
                else
                    colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = animatedAlpha))
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = meta.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected && enabled) accentColor else colorScheme.onSurfaceVariant.copy(alpha = animatedAlpha),
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun BrightnessIndicator(
    brightness: Float,
    color: Color,
    isEnabled: Boolean
) {
    val dotCount = 5
    val filledDots = ((brightness / 100f) * dotCount).roundToInt().coerceIn(0, dotCount)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(dotCount) { i ->
            val isFilled = i < filledDots
            Box(
                modifier = Modifier
                    .size(if (isFilled) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFilled && isEnabled) color.copy(alpha = 0.8f)
                        else color.copy(alpha = 0.2f)
                    )
            )
        }
    }
}

@Composable
private fun GradientBrightnessSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    accentColor: Color,
    enabled: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Brightness Level",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f)
            )
            Text(
                "${String.format("%.0f", value)}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) accentColor else colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = 1f..100f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        )
    }
}
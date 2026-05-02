package com.rianixia.settings.overlay.ui.components

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
// [FIXED] Updated import to AutoMirrored
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch

fun Modifier.frostedGlass(
    backgroundColor: Color,
    borderColor: Color,
    shape: RoundedCornerShape,
    hazeState: HazeState? = null
): Modifier = this
    .clip(shape)
    .then(
        if (hazeState != null) {
            Modifier.hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = backgroundColor,
                    blurRadius = 24.dp,
                    tint = HazeTint(backgroundColor),
                    noiseFactor = 0.05f
                )
            )
        } else {
            Modifier.background(backgroundColor)
        }
    )
    .border(1.dp, borderColor, shape)
    .drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.maxDimension
                ),
                blendMode = BlendMode.SrcOver
            )
        }
    }

@Composable
fun GradientBlurAppBar(
    title: String,
    icon: ImageVector,
    onBackClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    addStatusBarPadding: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val surface = MaterialTheme.colorScheme.surface
    
    // Multiple gradient layers to simulate progressive blur
    val topGradient = Brush.verticalGradient(
        colors = listOf(
            surface.copy(alpha = 0.95f),
            surface.copy(alpha = 0.7f),
            Color.Transparent
        )
    )
    
    val midGradient = Brush.verticalGradient(
        colors = listOf(
            surface.copy(alpha = 0.6f),
            surface.copy(alpha = 0.3f),
            Color.Transparent
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp) // Extended height to cover more area
    ) {
        // Layer 1: Heavy blur at top (40% of extended height)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.TopCenter)
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = Color.Transparent,
                        blurRadius = 40.dp, // Stronger blur
                        tint = HazeTint(Color.Transparent),
                        noiseFactor = 0.02f
                    )
                )
                .background(topGradient)
        )
        
        // Layer 2: Medium blur at middle (65% of extended height)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .align(Alignment.TopCenter)
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = Color.Transparent,
                        blurRadius = 24.dp, // Medium blur
                        tint = HazeTint(Color.Transparent),
                        noiseFactor = 0.02f
                    )
                )
                .background(midGradient)
        )
        
        // Layer 3: Light blur extending to bottom (full height)
        Box(
            modifier = Modifier
                .matchParentSize()
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = Color.Transparent,
                        blurRadius = 12.dp, // Light blur
                        tint = HazeTint(Color.Transparent),
                        noiseFactor = 0.02f
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            surface.copy(alpha = 0.4f),
                            surface.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Content Layer
        Row(
            modifier = Modifier
                .then(if (addStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .height(64.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBackClick,
                shape = CircleShape,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack, // [FIXED]
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            if (actions != {}) {
                Spacer(modifier = Modifier.weight(1f))
                actions()
            }
        }
    }
}

@Composable
fun MaterialGlassScaffold(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlidingPillNavBar(
    pagerState: PagerState,
    items: List<Triple<String, String, ImageVector>>, 
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    val tintColor = colorScheme.surface.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .padding(horizontal = 48.dp)
            .height(64.dp)
            .frostedGlass(
                backgroundColor = tintColor,
                borderColor = colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = shape,
                hazeState = hazeState
            ),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            val widthPerItem = maxWidth / items.size
            val indicatorOffset = widthPerItem * (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            
            Box(
                modifier = Modifier
                    .width(widthPerItem)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .offset(x = indicatorOffset)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colorScheme.primary.copy(alpha = 0.15f))
                    .border(1.dp, colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onItemClick(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.third,
                            contentDescription = item.second,
                            tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(64.dp)
            .frostedGlass(
                backgroundColor = colorScheme.surface.copy(alpha = 0.85f),
                borderColor = colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colorScheme.onSurface) // [FIXED]
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = colorScheme.onSurface)
        }
    }
}

@Composable
fun MaterialGlassCard(
    modifier: Modifier = Modifier,
    header: String? = null,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    
    // Determine effective colors with defaults
    val effectiveContainer = containerColor ?: colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val effectiveBorder = borderColor ?: colorScheme.outlineVariant.copy(alpha = 0.3f)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .frostedGlass(
                backgroundColor = effectiveContainer,
                borderColor = effectiveBorder,
                shape = shape
            )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            if (header != null) {
                Text(header, style = MaterialTheme.typography.titleMedium, color = colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

@Composable
fun MaterialGlassBadge(text: String, containerColor: Color, contentColor: Color) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .frostedGlass(
                backgroundColor = containerColor.copy(alpha = 0.5f),
                borderColor = containerColor.copy(alpha = 0.7f),
                shape = shape
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}

@Composable
fun MaterialDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun SnapshotRow(label: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LiveGraphRenderer(points: List<Float>, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val path = Path()
        val w = size.width
        val h = size.height
        val step = w / (points.size - 1).coerceAtLeast(1)
        if (points.isNotEmpty()) {
            path.moveTo(0f, h - (points[0] * h))
            for (i in 0 until points.size - 1) {
                val x1 = i * step
                val y1 = h - (points[i] * h)
                val x2 = (i + 1) * step
                val y2 = h - (points[i + 1] * h)
                path.cubicTo(x1 + step / 2, y1, x1 + step / 2, y2, x2, y2)
            }
        }
        drawPath(path, color.copy(alpha = 0.3f), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}

@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground) } // [FIXED]
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun NavRow(title: String, sub: String, icon: ImageVector, onClick: () -> Unit) {
    MaterialGlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun XinyaToggle(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit, isRisk: Boolean = false) {
    val colorScheme = MaterialTheme.colorScheme
    val activeColor = if(isRisk) colorScheme.error else colorScheme.onSurface
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = activeColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = activeColor)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun XinyaSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, label: String, suffix: String) {
    val colorScheme = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurface)
            Text("${String.format("%.0f", value)}$suffix", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
fun BoostItem(app: String, active: Boolean, onCheckedChange: (Boolean) -> Unit = {}) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!active) }
            .padding(vertical = 8.dp), 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(colorScheme.surfaceVariant))
        Spacer(Modifier.width(12.dp))
        Text(app, Modifier.weight(1f), color = colorScheme.onSurface)
        Switch(checked = active, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ResoChip(modifier: Modifier, title: String, sub: String, selected: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colorScheme.primary else colorScheme.surfaceVariant)
            .clickable { }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold, color = if (selected) colorScheme.onPrimary else colorScheme.onSurface)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant)
        }
    }
}
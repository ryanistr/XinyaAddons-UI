package com.rianixia.settings.overlay.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import dev.chrisbanes.haze.HazeState

/**
 * A refined GlassDropdown with 2-stage animation (Dot -> Width -> Height)
 * and content-driven sizing.
 * * Updated: Aligns to TopEnd (Right) for smooth expansion from the arrow.
 */
@Composable
fun <T> GlassDropdown(
    label: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    itemLabelMapper: (T) -> String = { it.toString() },
    enabled: Boolean = true,
    color: Color,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var expanded by remember { mutableStateOf(false) }

    // Master Transition State
    val transitionState = remember { MutableTransitionState(expanded) }
    transitionState.targetState = expanded
    
    // [FIXED] Replaced deprecated updateTransition with rememberTransition
    val transition = rememberTransition(transitionState, label = "GlassDropdown")

    // Arrow Rotation
    val rotation by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "arrowRotation"
    ) { if (it) 180f else 0f }

    Column(modifier = modifier) {
        // Label (Only render if provided)
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Trigger
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .frostedGlass(
                    backgroundColor = if (enabled) MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                                      else MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                    borderColor = if (expanded) color.copy(alpha = 0.6f) 
                                  else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    hazeState = hazeState
                )
                .clickable(enabled = enabled) { expanded = !expanded }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = itemLabelMapper(selectedOption),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = if (enabled) color else color.copy(alpha = 0.3f),
                    modifier = Modifier.rotate(rotation)
                )
            }
        }

        // Dropdown Popup
        if (transitionState.currentState || transitionState.targetState) {
            Popup(
                // Align TopEnd to anchor the menu to the right (arrow side)
                alignment = Alignment.TopEnd,
                onDismissRequest = { expanded = false }
            ) {
                DropdownContentAnimator(
                    options = options,
                    transition = transition,
                    hazeState = hazeState,
                    color = color,
                    itemLabelMapper = itemLabelMapper,
                    selectedOption = selectedOption,
                    onOptionSelected = {
                        onOptionSelected(it)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun <T> DropdownContentAnimator(
    options: List<T>,
    transition: Transition<Boolean>,
    hazeState: HazeState?,
    color: Color,
    itemLabelMapper: (T) -> String,
    selectedOption: T,
    onOptionSelected: (T) -> Unit
) {
    // Animation Specs
    val expandDuration = 400
    val collapseDuration = 350
    
    // Width Animation: (Dot -> Target Width)
    val widthProgress by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                // Open: Expand Width first
                keyframes {
                    durationMillis = expandDuration
                    0f at 0 using FastOutSlowInEasing
                    1f at 200
                }
            } else {
                // Close: Collapse Width last
                keyframes {
                    durationMillis = collapseDuration
                    1f at 0
                    1f at 150
                    0f at collapseDuration using FastOutSlowInEasing
                }
            }
        },
        label = "widthProgress"
    ) { if (it) 1f else 0f }

    // Height Animation: (Dot -> Target Height)
    val heightProgress by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                // Open: Expand Height second
                keyframes {
                    durationMillis = expandDuration
                    0f at 0
                    0f at 150
                    1f at expandDuration using FastOutSlowInEasing
                }
            } else {
                // Close: Collapse Height first
                keyframes {
                    durationMillis = collapseDuration
                    1f at 0
                    0f at 200 using FastOutSlowInEasing
                }
            }
        },
        label = "heightProgress"
    ) { if (it) 1f else 0f }

    val alphaProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 250) },
        label = "alpha"
    ) { if (it) 1f else 0f }

    SubcomposeLayout(
        modifier = Modifier.padding(top = 8.dp)
    ) { constraints ->
        // 1. Measure content
        val contentPlaceable = subcompose("content") {
            Box(Modifier.width(IntrinsicSize.Max)) {
                Column {
                    options.forEach { option ->
                        DropdownItem(
                            text = itemLabelMapper(option),
                            isSelected = option == selectedOption,
                            color = color,
                            onClick = {}
                        )
                    }
                }
            }
        }.first().measure(Constraints())

        val targetWidth = contentPlaceable.width.toFloat()
        val targetHeight = contentPlaceable.height.coerceAtMost(280.dp.roundToPx()).toFloat()
        
        // Initial dot size
        val startSize = 40.dp.toPx()

        // Interpolate Dimensions
        val currentWidth = startSize + (targetWidth - startSize) * widthProgress
        val currentHeight = startSize + (targetHeight - startSize) * heightProgress
        
        // 2. Measure animated container
        val animatedPlaceable = subcompose("container") {
            Box(
                modifier = Modifier
                    .size(
                        width = currentWidth.toDp(),
                        height = currentHeight.toDp()
                    )
                    .graphicsLayer {
                        alpha = alphaProgress
                        clip = true
                        shape = RoundedCornerShape(16.dp)
                    }
                    .frostedGlass(
                        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        borderColor = color.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        hazeState = hazeState
                    )
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(options) { option ->
                        DropdownItem(
                            text = itemLabelMapper(option),
                            isSelected = option == selectedOption,
                            color = color,
                            onClick = { onOptionSelected(option) }
                        )
                    }
                }
            }
        }.first().measure(Constraints.fixed(currentWidth.toInt(), currentHeight.toInt()))

        layout(currentWidth.toInt(), currentHeight.toInt()) {
            animatedPlaceable.place(0, 0)
        }
    }
}

@Composable
private fun DropdownItem(
    text: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val backgroundBrush = if (isSelected) {
        Brush.horizontalGradient(
            colors = listOf(
                color.copy(alpha = 0.15f),
                color.copy(alpha = 0.05f),
                Color.Transparent
            )
        )
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(
                if (backgroundBrush != null) Modifier.background(backgroundBrush) 
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
package com.rianixia.settings.overlay.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

// ==========================================
// BOUNCY SCROLL LOGIC
// ==========================================
class BouncyScrollState(
    val offset: MutableFloatState,
    val connection: NestedScrollConnection
)

@Composable
fun rememberBouncyScroll(): BouncyScrollState {
    val scope = rememberCoroutineScope()
    // 1. Use MutableState for instant, synchronous updates during drag
    val offsetState = remember { mutableFloatStateOf(0f) }
    
    // Configurable Physics Constants
    val maxDragOffset = 400f 
    
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Only process drag gestures
                // [FIXED] Drag replaced by UserInput
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                val delta = available.y
                val currentOffset = offsetState.floatValue

                // If not bouncing, do nothing
                if (currentOffset == 0f) return Offset.Zero

                // Logic: Are we pushing BACK towards 0?
                val isResetting = (currentOffset > 0f && delta < 0f) || (currentOffset < 0f && delta > 0f)

                if (isResetting) {
                    val newOffset = currentOffset + delta
                    
                    // Check if we will cross 0
                    val willOvershoot = (currentOffset > 0 && newOffset < 0) || (currentOffset < 0 && newOffset > 0)

                    if (willOvershoot) {
                        // Snap to 0 immediately
                        offsetState.floatValue = 0f
                        // Consume only what was needed to reach 0
                        return Offset(0f, -currentOffset) 
                    } else {
                        // Apply immediate 1:1 delta
                        offsetState.floatValue = newOffset
                        return Offset(0f, delta)
                    }
                }
                
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Only process drag gestures
                // [FIXED] Drag replaced by UserInput
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                val delta = available.y
                
                // Cubic Decay Friction
                val currentOffset = offsetState.floatValue
                val progress = (abs(currentOffset) / maxDragOffset).coerceIn(0f, 1f)
                val friction = (1f - progress).pow(3) 
                
                val dampedDelta = delta * friction
                
                // Apply immediately
                val newOffset = (currentOffset + dampedDelta).coerceIn(-maxDragOffset, maxDragOffset)
                offsetState.floatValue = newOffset
                
                return available // Report consumed
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Kill fling if bouncing
                if (offsetState.floatValue != 0f) {
                    return available 
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // 2. Hand off to Animatable for the spring back animation
                // We create a temporary Animatable starting from the current state
                Animatable(offsetState.floatValue).animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.65f,
                        stiffness = 350f
                    )
                ) {
                    // Update state on every frame of animation
                    offsetState.floatValue = value
                }
                return available
            }
        }
    }
    
    return remember(offsetState, connection) { BouncyScrollState(offsetState, connection) }
}

// ==========================================
// REUSABLE COMPONENT
// ==========================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BouncyLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    val bouncyState = rememberBouncyScroll()

    CompositionLocalProvider(
        LocalOverscrollConfiguration provides null
    ) {
        LazyColumn(
            modifier = modifier
                // Read directly from the float state
                .offset { IntOffset(0, bouncyState.offset.floatValue.roundToInt()) }
                .nestedScroll(bouncyState.connection),
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = content
        )
    }
}
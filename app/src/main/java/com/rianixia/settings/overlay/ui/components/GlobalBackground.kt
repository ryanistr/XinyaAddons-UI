package com.rianixia.settings.overlay.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GlobalBackground() {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Dynamic Blur Radius based on Android Version
        val blurRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 100.dp else 0.dp

        // 1. The Glowing Blobs
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
                .alpha(0.5f)
        ) {
            val w = size.width
            val h = size.height
            // Top-Left Primary Blob
            drawCircle(
                color = colorScheme.primaryContainer,
                radius = w * 0.8f,
                center = Offset(0f, 0f)
            )
            // Bottom-Right Tertiary Blob
            drawCircle(
                color = colorScheme.tertiaryContainer,
                radius = w * 0.8f,
                center = Offset(w, h)
            )
        }

        // 2. Static Top Gradient (Fade out header)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(colorScheme.background, Color.Transparent)
                    )
                )
        )

        // 3. Static Bottom Gradient (Fade out nav)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, colorScheme.background)
                    )
                )
        )
    }
}
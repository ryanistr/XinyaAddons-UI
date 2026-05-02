package com.rianixia.settings.overlay.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Fallback Dark Theme
private val DarkColorScheme = darkColorScheme(
    primary = XinyaDarkPrimary,
    onPrimary = XinyaDarkOnPrimary,
    secondary = XinyaDarkSecondary,
    background = XinyaDarkBackground,
    surface = XinyaDarkSurface,
    surfaceVariant = XinyaDarkSurfaceVariant,
    onSurface = XinyaDarkText,
    onSurfaceVariant = XinyaDarkSubText,
    outline = XinyaDarkDivider,
    error = XinyaDarkError
)

// Fallback Light Theme
private val LightColorScheme = lightColorScheme(
    primary = XinyaLightPrimary,
    onPrimary = XinyaLightOnPrimary,
    secondary = XinyaLightSecondary,
    background = XinyaLightBackground,
    surface = XinyaLightSurface,
    surfaceVariant = XinyaLightSurfaceVariant,
    onSurface = XinyaLightText,
    onSurfaceVariant = XinyaLightSubText,
    outline = XinyaLightDivider,
    error = XinyaLightError
)

@Composable
fun XinyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Force Dynamic Colors on Android 12+ for Material You
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            
            // [FIXED] Removed deprecated window color assignments.
            // Edge-to-edge handling in MainActivity takes care of transparency.
            
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
package com.focuslock.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CyberpunkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    secondary = VioletSecondary,
    tertiary = PlasmaAmber,
    error = DangerRed,
    background = DeepVoid,
    surface = SurfaceDark,
    surfaceVariant = SurfaceElevated,
    onPrimary = DeepVoid,
    onSecondary = TextPrimary,
    onTertiary = DeepVoid,
    onError = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun FocusLockTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                @Suppress("DEPRECATION")
                window.statusBarColor = CyberpunkColorScheme.background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = CyberpunkColorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = CyberpunkColorScheme,
        typography = FocusTypography,
        content = content
    )
}

package com.cypher.music.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CypherLavender,
    onPrimary = IosDarkTextPrimary,
    secondary = CypherLavenderLight,
    onSecondary = IosDarkTextPrimary,
    tertiary = CypherLavenderDark,
    background = CypherDarkBg,
    onBackground = IosDarkTextPrimary,
    surface = CypherDarkSurface,
    onSurface = IosDarkTextPrimary,
    surfaceVariant = IosDarkElevated,
    onSurfaceVariant = IosDarkTextSecondary,
    outline = CypherDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = CypherLavender,
    onPrimary = IosLightTextPrimary,
    secondary = CypherLavenderLight,
    onSecondary = IosLightTextPrimary,
    tertiary = CypherLavenderDark,
    background = IosLightBackground,
    onBackground = IosLightTextPrimary,
    surface = IosLightSurface,
    onSurface = IosLightTextPrimary,
    surfaceVariant = IosLightElevated,
    onSurfaceVariant = IosLightTextSecondary,
    outline = IosLightBorder
)

@Composable
fun CypherTheme(
    darkTheme: Boolean = true, // Cypher defaults to deep minimalist dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppleTypography,
        content = content
    )
}

/**
 * Backward compatibility alias for CypherTheme.
 */
@Composable
fun AppleMusicTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    CypherTheme(darkTheme = darkTheme, content = content)
}

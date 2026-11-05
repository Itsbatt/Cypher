package com.cypher.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cypher.music.data.ThemePreferences

/**
 * Solid Surface Modifier - Completely eliminates AGSL runtime shaders,
 * optical refractions, and translucent blurs in favor of crisp, solid,
 * high-performance OLED surfaces with discrete 1dp borders.
 */
fun Modifier.realLiquidGlass(
    themePrefs: ThemePreferences,
    cornerRadiusDp: Dp? = null,
    bevelWidthDp: Dp = 10.dp,
    tintColor: Color = Color(0xFF18181B),
    borderColor: Color? = null
): Modifier = composed {
    val effectiveRadius = cornerRadiusDp ?: themePrefs.cornerRadius.dp
    val shape = RoundedCornerShape(effectiveRadius)
    val resolvedBg = if (tintColor.alpha < 1.0f) Color(0xFF18181B) else tintColor
    val resolvedBorder = borderColor ?: Color(0xFF27272A)

    this
        .clip(shape)
        .background(resolvedBg)
        .border(
            width = 1.dp,
            color = resolvedBorder,
            shape = shape
        )
}

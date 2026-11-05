package com.cypher.music.ui.theme

import androidx.compose.ui.graphics.Color
import com.cypher.music.data.SurfaceStyle
import com.cypher.music.data.ThemePreferences

data class ResolvedThemeColors(
    val background: Color,
    val cardBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val primary: Color,
    val separator: Color,
    val isDark: Boolean
) {
    val card: Color get() = cardBackground
}

object ThemeResolver {
    fun resolve(
        prefs: ThemePreferences,
        artworkPalette: AmbientArtworkColors? = null
    ): ResolvedThemeColors {
        val accent = Color(prefs.accentColor)
        val isDark = prefs.surfaceStyle != SurfaceStyle.LIGHT_CLEAN

        val background = Color(prefs.customBackground)
        val cardBackground = Color(prefs.customCard)
        val border = if (prefs.hasBorder) Color.White.copy(alpha = 0.12f) else Color.Transparent

        val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF09090B)
        val textSecondary = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)

        return ResolvedThemeColors(
            background = background,
            cardBackground = cardBackground,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accent = accent,
            primary = accent,
            separator = border,
            isDark = isDark
        )
    }
}

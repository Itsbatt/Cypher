package com.cypher.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cypher.desktop.core.ThemeColors

/**
 * The nine colours the interface is built from. Keeping them in one value object means a
 * change is a single assignment and the whole window repaints, instead of patching each
 * screen.
 */
@Immutable
data class CypherPalette(
    val background: Color = Color(0xFF261B26),
    val surface: Color = Color(0xFF322332),
    val card: Color = Color(0xFF413041),
    val pill: Color = Color(0xFF4B3A4B),
    val border: Color = Color(0xFF5D4A5E),
    val separator: Color = Color(0xFF3A2C3A),
    val textPrimary: Color = Color(0xFFEDE7ED),
    val textSecondary: Color = Color(0xFFB8A9B8),
    val textTertiary: Color = Color(0xFF9E8B9C)
)

/** The colours chosen in Ajustes, ready for the painter. */
fun ThemeColors.toPalette(): CypherPalette = CypherPalette(
    background = Color(background),
    surface = Color(surface),
    card = Color(card),
    pill = Color(pill),
    border = Color(border),
    separator = Color(separator),
    textPrimary = Color(textPrimary),
    textSecondary = Color(textSecondary),
    textTertiary = Color(textTertiary)
)

/**
 * Cypher's palette, straight from the Android theme: pastel lavender on near-black,
 * with raised cards and very low-contrast borders.
 *
 * The values live in snapshot state, so every read in composition (`CypherColors.Card`,
 * `CypherColors.TextTertiary`, …) repaints the moment the user picks a different colour.
 * The defaults are the reference SVG palette; [apply] swaps in the user's own.
 */
object CypherColors {
    private val defaults = CypherPalette()

    private var current by mutableStateOf(defaults)

    /** What is painted right now. */
    val palette: CypherPalette get() = current

    /** The original palette, for "restablecer". */
    val Default: CypherPalette get() = defaults

    /** Assigns a palette; Equal values are ignored so composition never loops. */
    fun apply(next: CypherPalette) {
        if (next != current) current = next
    }

    fun reset() = apply(defaults)

    val Lavender = Color(0xFFB8A9B8)
    val LavenderLight = Color(0xFFD8BEE0)
    val LavenderDark = Color(0xFF826E84)

    val Background: Color get() = current.background
    val Surface: Color get() = current.surface
    val Card: Color get() = current.card
    val Pill: Color get() = current.pill
    val Border: Color get() = current.border
    val Separator: Color get() = current.separator

    val TextPrimary: Color get() = current.textPrimary
    val TextSecondary: Color get() = current.textSecondary
    val TextTertiary: Color get() = current.textTertiary

    val Danger = Color(0xFFE06A6A)
    val Success = Color(0xFF7FC8A9)
}

val CypherTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 0.37.sp
    ),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.8.sp
    )
)

@Composable
fun CypherTheme(accent: Color = CypherColors.Lavender, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF14101A),
        secondary = CypherColors.LavenderLight,
        background = CypherColors.Background,
        onBackground = CypherColors.TextPrimary,
        surface = CypherColors.Surface,
        onSurface = CypherColors.TextPrimary,
        surfaceVariant = CypherColors.Card,
        onSurfaceVariant = CypherColors.TextSecondary,
        outline = CypherColors.Border,
        error = CypherColors.Danger
    )
    MaterialTheme(colorScheme = scheme, typography = CypherTypography, content = content)
}

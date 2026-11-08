package com.cypher.music.ui.share

import androidx.compose.ui.graphics.Color

/**
 * Shared visual language for everything CYPHER exports as an image.
 * Lyrics cards use it today; the annual recap share (December) reuses the same
 * palette and spacing so both feel like the same product.
 */
object SharePalette {
    val CanvasTop = Color(0xFF17131C)
    val CanvasBottom = Color(0xFF07060A)
    val TextPrimary = Color(0xFFF8F6FB)
    val TextSecondary = Color(0xFFA9A3B8)
    val Hairline = Color(0x1FFFFFFF)
}

enum class ShareFormat(val label: String, val width: Int, val height: Int) {
    VERTICAL("Historia", 1080, 1920),
    SQUARE("Cuadrado", 1080, 1080)
}

enum class ShareDesign(val label: String, val hint: String) {
    MINIMAL("Minimal", "Limpia y centrada en la letra"),
    ARTWORK("Portada", "La carátula como fondo"),
    TYPOGRAPHY("Tipografía", "Solo texto, muy editorial")
}

data class LyricsCardData(
    val trackTitle: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val lines: List<String>,
    val accent: Color
)

/**
 * A fragment, not the song: keeping a hard cap protects the lyric providers and
 * keeps the card legible. Six lines fill a story without reproducing a full lyric.
 */
const val MAX_SHARE_LINES = 6

package com.cypher.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Colours taken from the cover, so the lyrics screen can be tinted by the record instead
 * of by a fixed purple. The extraction is deliberately small: a 56-pixel thumbnail, one
 * histogram pass and three picks, all cached per bitmap, because this runs while the
 * user is opening the expanded player.
 */
fun coverPalette(bitmap: ImageBitmap): List<Color> {
    val pixels = bitmap.toPixelMap()
    val stepX = max(1, pixels.width / 56)
    val stepY = max(1, pixels.height / 56)

    // Bucket key: 4 bits per channel, so gradients collapse into one entry.
    val weight = HashMap<Int, Double>()
    val sumR = HashMap<Int, Double>()
    val sumG = HashMap<Int, Double>()
    val sumB = HashMap<Int, Double>()

    var y = 0
    while (y < pixels.height) {
        var x = 0
        while (x < pixels.width) {
            val color = pixels[x, y]
            val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
            val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
            val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
            val high = maxOf(r, g, b)
            val low = minOf(r, g, b)
            val lightness = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            val saturated = if (high == 0) 0f else (high - low).toFloat() / high
            // Near-black and near-white pixels only widen the field without colouring it;
            // mid, saturated tones are what reads as "the colour of the record".
            if (lightness in 0.07f..0.96f) {
                val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                val w = 0.25 + saturated * 2.4 + (1f - abs(lightness - 0.55f)) * 0.8
                weight[key] = (weight[key] ?: 0.0) + w
                sumR[key] = (sumR[key] ?: 0.0) + r * w
                sumG[key] = (sumG[key] ?: 0.0) + g * w
                sumB[key] = (sumB[key] ?: 0.0) + b * w
            }
            x += stepX
        }
        y += stepY
    }

    val ranked = weight.entries.sortedByDescending { it.value }.map { (key, w) ->
        Color(
            red = (sumR[key]!! / w / 255.0).toFloat(),
            green = (sumG[key]!! / w / 255.0).toFloat(),
            blue = (sumB[key]!! / w / 255.0).toFloat()
        )
    }

    // Three visibly different tones: a field of three near-identical purples still looks
    // like one colour once the shader mixes them.
    val picked = ArrayList<Color>(3)
    ranked.forEach { candidate ->
        if (picked.size >= 3) return@forEach
        if (picked.all { hueDistance(it, candidate) > 22f || lightnessDistance(it, candidate) > 0.22f }) {
            picked += candidate
        }
    }
    val fallback = picked.firstOrNull() ?: Color(0xFF9D7BB0)
    while (picked.size < 3) {
        picked += when (picked.size) {
            1 -> brighten(fallback, 1.45f)
            else -> darken(fallback, 0.55f)
        }
    }
    // A cover can be almost black (a night photo, a dark sleeve). Keeping the literal
    // pixels would paint an invisible backdrop, so very dark tones are lifted until the
    // hue shows; bright covers are left exactly as they are.
    return picked.map { lift(it) }
}

/** Raises a nearly black colour to something the shader can tint with, keeping its hue. */
private fun lift(color: Color): Color {
    val peak = maxOf(color.red, color.green, color.blue)
    if (peak >= 0.42f) return color
    val factor = (0.42f / peak.coerceAtLeast(0.02f)).coerceIn(1f, 7f)
    return Color(
        red = (color.red * factor).coerceIn(0f, 1f),
        green = (color.green * factor).coerceIn(0f, 1f),
        blue = (color.blue * factor).coerceIn(0f, 1f)
    )
}

private fun hueOf(color: Color): Float {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val delta = max - min
    if (delta <= 0.0001f) return 0f
    val hue = when (max) {
        color.red -> ((color.green - color.blue) / delta) % 6f
        color.green -> (color.blue - color.red) / delta + 2f
        else -> (color.red - color.green) / delta + 4f
    } * 60f
    return if (hue < 0f) hue + 360f else hue
}

private fun hueDistance(a: Color, b: Color): Float {
    val diff = abs(hueOf(a) - hueOf(b))
    return if (diff > 180f) 360f - diff else diff
}

private fun lightness(color: Color): Float = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

private fun lightnessDistance(a: Color, b: Color): Float = abs(lightness(a) - lightness(b))

private fun brighten(color: Color, factor: Float) = Color(
    red = (color.red * factor).pow(0.75f).coerceIn(0f, 1f),
    green = (color.green * factor).pow(0.75f).coerceIn(0f, 1f),
    blue = (color.blue * factor).pow(0.75f).coerceIn(0f, 1f)
)

private fun darken(color: Color, factor: Float) = Color(
    red = (color.red * factor).coerceIn(0f, 1f),
    green = (color.green * factor).coerceIn(0f, 1f),
    blue = (color.blue * factor).coerceIn(0f, 1f)
)

/** Palette for the playing cover, recomputed only when the picture changes. */
@Composable
fun rememberCoverPalette(bitmap: ImageBitmap?, accent: Color): List<Color> =
    remember(bitmap, accent) {
        val extracted = bitmap?.let { runCatching { coverPalette(it) }.getOrNull() }
        extracted?.takeIf { it.size >= 3 } ?: listOf(accent, darken(accent, 0.55f), brighten(accent, 1.3f))
    }

package com.cypher.music.data

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure HSV colour model. Implemented by hand (instead of android.graphics.Color)
 * so the conversion can be unit tested on the JVM and reused by the picker.
 */
data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float
) {
    companion object {
        fun fromArgb(argb: Long): HsvColor {
            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            val hue = when {
                delta == 0f -> 0f
                max == r -> 60f * (((g - b) / delta) % 6f)
                max == g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }.let { if (it < 0f) it + 360f else it }
            val saturation = if (max == 0f) 0f else delta / max
            return HsvColor(hue, saturation.coerceIn(0f, 1f), max.coerceIn(0f, 1f))
        }
    }

    fun toArgb(alpha: Int = 255): Long {
        val h = ((hue % 360f) + 360f) % 360f
        val c = value.coerceIn(0f, 1f) * saturation.coerceIn(0f, 1f)
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = value.coerceIn(0f, 1f) - c
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return (alpha.coerceIn(0, 255).toLong() shl 24) or
            (r.toLong() shl 16) or
            (g.toLong() shl 8) or
            b.toLong()
    }
}

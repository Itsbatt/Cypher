package com.cypher.music.data

/**
 * Parses user-typed hex colours into Android ARGB longs.
 *
 * Accepts `#RRGGBB`, `RRGGBB`, `#AARRGGBB` and `AARRGGBB` (case-insensitive).
 * A six-digit value becomes fully opaque.
 */
object HexColor {

    fun parse(raw: String): Long? {
        val cleaned = raw.trim().removePrefix("#").removePrefix("#")
        if (cleaned.length != 6 && cleaned.length != 8) return null
        if (cleaned.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
        val value = cleaned.toLongOrNull(16) ?: return null
        return if (cleaned.length == 6) 0xFF000000L or value else value
    }

    /** `#RRGGBB`, or `#AARRGGBB` when the colour is translucent. */
    fun toHex(argb: Long, forceAlpha: Boolean = false): String {
        val alpha = (argb ushr 24) and 0xFF
        val rgb = argb and 0xFFFFFFL
        return if (forceAlpha || alpha != 0xFFL) {
            "#%02X%02X%02X%02X".format(alpha, (argb ushr 16) and 0xFF, (argb ushr 8) and 0xFF, rgb and 0xFF)
        } else {
            "#%06X".format(rgb)
        }
    }
}

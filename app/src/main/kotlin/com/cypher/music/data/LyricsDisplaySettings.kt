package com.cypher.music.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LyricFontSize(val label: String, val activeSp: TextUnit, val inactiveSp: TextUnit) {
    SMALL("Pequeño", 20.sp, 16.sp),
    MEDIUM("Mediano", 26.sp, 20.sp),
    LARGE("Grande", 32.sp, 24.sp),
    EXTRA_LARGE("Extra Grande", 38.sp, 28.sp)
}

enum class LyricAlignment(val label: String, val textAlign: TextAlign) {
    START("Izquierda", TextAlign.Start),
    CENTER("Centro", TextAlign.Center)
}

enum class LyricColorMode(val label: String) {
    THEME_ACCENT("Acento del Tema"),
    ARTWORK_PALETTE("Color de Carátula")
}

data class LyricsDisplaySettings(
    val fontSize: LyricFontSize = LyricFontSize.MEDIUM,
    val alignment: LyricAlignment = LyricAlignment.START,
    val inactiveOpacity: Float = 0.45f,
    val inactiveBlurDp: Float = 0f,
    val colorMode: LyricColorMode = LyricColorMode.THEME_ACCENT
)

class LyricsSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lyrics_display_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<LyricsDisplaySettings> = _settings.asStateFlow()

    private fun loadSettings(): LyricsDisplaySettings {
        val sizeName = prefs.getString("font_size", LyricFontSize.MEDIUM.name) ?: LyricFontSize.MEDIUM.name
        val alignName = prefs.getString("alignment", LyricAlignment.START.name) ?: LyricAlignment.START.name
        val colorName = prefs.getString("color_mode", LyricColorMode.THEME_ACCENT.name) ?: LyricColorMode.THEME_ACCENT.name
        val opacity = prefs.getFloat("inactive_opacity", 0.45f)
        val blur = prefs.getFloat("inactive_blur", 0f)

        return LyricsDisplaySettings(
            fontSize = runCatching { LyricFontSize.valueOf(sizeName) }.getOrDefault(LyricFontSize.MEDIUM),
            alignment = runCatching { LyricAlignment.valueOf(alignName) }.getOrDefault(LyricAlignment.START),
            inactiveOpacity = opacity,
            inactiveBlurDp = blur,
            colorMode = runCatching { LyricColorMode.valueOf(colorName) }.getOrDefault(LyricColorMode.THEME_ACCENT)
        )
    }

    /** Current lyric preferences as JSON, embedded in the backup file. */
    fun exportJson(): String {
        val current = _settings.value
        return JSONObject().apply {
            put("font_size", current.fontSize.name)
            put("alignment", current.alignment.name)
            put("color_mode", current.colorMode.name)
            put("inactive_opacity", current.inactiveOpacity.toDouble())
            put("inactive_blur", current.inactiveBlurDp.toDouble())
        }.toString()
    }

    fun importJson(json: String): Boolean {
        val parsed = runCatching { JSONObject(json) }.getOrNull() ?: return false
        if (parsed.length() == 0) return false
        prefs.edit()
            .putString("font_size", parsed.optString("font_size", LyricFontSize.MEDIUM.name))
            .putString("alignment", parsed.optString("alignment", LyricAlignment.START.name))
            .putString("color_mode", parsed.optString("color_mode", LyricColorMode.THEME_ACCENT.name))
            .putFloat("inactive_opacity", parsed.optDouble("inactive_opacity", 0.45).toFloat())
            .putFloat("inactive_blur", parsed.optDouble("inactive_blur", 0.0).toFloat())
            .apply()
        _settings.value = loadSettings()
        return true
    }

    fun updateSettings(newSettings: LyricsDisplaySettings) {
        prefs.edit()
            .putString("font_size", newSettings.fontSize.name)
            .putString("alignment", newSettings.alignment.name)
            .putString("color_mode", newSettings.colorMode.name)
            .putFloat("inactive_opacity", newSettings.inactiveOpacity)
            .putFloat("inactive_blur", newSettings.inactiveBlurDp)
            .apply()
        _settings.value = newSettings
    }
}

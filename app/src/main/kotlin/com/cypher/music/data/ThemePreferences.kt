package com.cypher.music.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class SurfaceStyle(val title: String) {
    OLED_PURE("OLED Puro"),
    DEEP_CHARCOAL("Carbón"),
    GRAPHITE("Grafito"),
    LIGHT_CLEAN("Claro")
}

data class ThemePreferences(
    val accentColor: Long = 0xFF9D7BB0L, // Lavanda Cypher (#9D7BB0)
    val surfaceStyle: SurfaceStyle = SurfaceStyle.OLED_PURE,
    val customBackground: Long = 0xFF0D0B11L, // Minimalist deep OLED
    val customCard: Long = 0xFF16141DL,
    val hasBorder: Boolean = true,
    val cornerRadius: Float = 20f,
    val hapticsEnabled: Boolean = true,
    val artworkSpringPhysics: Boolean = true,
    val ambientGlowEnabled: Boolean = true,
    val ambientGlowIntensity: Float = 0.85f,
    val glassTintAlpha: Float = 0.05f,
    val crossfadeSeconds: Int = 0, // 0 = Desactivado, 2, 4, 6, 8
    val liquidGlassConfig: LiquidGlassConfig = LiquidGlassConfig(
        customColor1 = 0xFF9D7BB0L,
        customColor2 = 0xFF7A588EL,
        customColor3 = 0xFF352244L
    )
)

// Curated vibrant accent palette for Settings
val AccentPalette = listOf(
    0xFF9D7BB0L to "Lavanda Cypher",
    0xFFB89BC9L to "Morado Pastel",
    0xFF7A588EL to "Lavanda Oscuro",
    0xFF651FFFL to "Índigo",
    0xFF00E5FFL to "Cian Eléctrico",
    0xFF10B981L to "Esmeralda",
    0xFFFFB300L to "Oro Ámbar",
    0xFFFF334BL to "Coral",
    0xFFFA243CL to "Rojo Carmín",
    0xFFFFFFFFL to "Blanco Puro"
)

// Base background tone options (OLED / Dark / Tints)
val BackgroundTones = listOf(
    0xFF0D0B11L to "Cypher Dark",
    0xFF000000L to "OLED Puro",
    0xFF0E0E10L to "Carbón",
    0xFF120B18L to "Púrpura Profundo",
    0xFF18181BL to "Grafito"
)

// Card surface container options
val CardTones = listOf(
    0xFF16141DL to "Cypher Card",
    0xFF18181CL to "Carbón",
    0xFF222228L to "Elevado",
    0xFF2A2A32L to "Translúcido",
    0xFF141824L to "Azulado"
)

val CrossfadeOptions = listOf(
    0 to "Desactivado",
    2 to "2 segundos",
    4 to "4 segundos",
    6 to "6 segundos",
    8 to "8 segundos"
)

class ThemePreferencesManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cypher_theme_prefs", Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<ThemePreferences> = _preferences.asStateFlow()

    fun setUserId(userId: String) {
        // Single user personal app
    }

    private fun loadPreferences(): ThemePreferences {
        val jsonStr = prefs.getString("theme_config", null) ?: return ThemePreferences()
        return try {
            val json = JSONObject(jsonStr)
            ThemePreferences(
                accentColor = json.optLong("accentColor", 0xFF9D7BB0L),
                surfaceStyle = SurfaceStyle.valueOf(json.optString("surfaceStyle", SurfaceStyle.OLED_PURE.name)),
                customBackground = json.optLong("customBackground", 0xFF0D0B11L),
                customCard = json.optLong("customCard", 0xFF16141DL),
                hasBorder = json.optBoolean("hasBorder", true),
                cornerRadius = json.optDouble("cornerRadius", 20.0).toFloat(),
                hapticsEnabled = json.optBoolean("hapticsEnabled", true),
                artworkSpringPhysics = json.optBoolean("artworkSpringPhysics", true),
                ambientGlowEnabled = json.optBoolean("ambientGlowEnabled", true),
                ambientGlowIntensity = json.optDouble("ambientGlowIntensity", 0.85).toFloat(),
                glassTintAlpha = json.optDouble("glassTintAlpha", 0.05).toFloat(),
                crossfadeSeconds = json.optInt("crossfadeSeconds", 0),
                liquidGlassConfig = LiquidGlassConfig(
                    colorMode = runCatching {
                        ShaderColorMode.valueOf(json.optString("shaderColorMode", ShaderColorMode.DYNAMIC.name))
                    }.getOrDefault(ShaderColorMode.DYNAMIC),
                    customColor1 = json.optLong("shaderCustomColor1", 0xFF9D7BB0L),
                    customColor2 = json.optLong("shaderCustomColor2", 0xFF7A588EL),
                    customColor3 = json.optLong("shaderCustomColor3", 0xFF352244L),
                    animationSpeed = json.optDouble("shaderSpeed", 0.60).toFloat(),
                    distortionScale = json.optDouble("shaderDistortion", 0.70).toFloat(),
                    overallOpacity = json.optDouble("shaderOpacity", 0.65).toFloat(),
                    isEnabled = json.optBoolean("shaderEnabled", true)
                )
            )
        } catch (_: Exception) {
            ThemePreferences()
        }
    }

    fun updatePreferences(update: (ThemePreferences) -> ThemePreferences) {
        val newPrefs = update(_preferences.value)
        _preferences.value = newPrefs
        savePreferences(newPrefs)
    }

    fun resetToDefaults() {
        val defaultPrefs = ThemePreferences()
        _preferences.value = defaultPrefs
        savePreferences(defaultPrefs)
    }

    private fun savePreferences(themePrefs: ThemePreferences) {
        val json = JSONObject().apply {
            put("accentColor", themePrefs.accentColor)
            put("surfaceStyle", themePrefs.surfaceStyle.name)
            put("customBackground", themePrefs.customBackground)
            put("customCard", themePrefs.customCard)
            put("hasBorder", themePrefs.hasBorder)
            put("cornerRadius", themePrefs.cornerRadius)
            put("hapticsEnabled", themePrefs.hapticsEnabled)
            put("artworkSpringPhysics", themePrefs.artworkSpringPhysics)
            put("ambientGlowEnabled", themePrefs.ambientGlowEnabled)
            put("ambientGlowIntensity", themePrefs.ambientGlowIntensity)
            put("glassTintAlpha", themePrefs.glassTintAlpha)
            put("crossfadeSeconds", themePrefs.crossfadeSeconds)
            put("shaderColorMode", themePrefs.liquidGlassConfig.colorMode.name)
            put("shaderCustomColor1", themePrefs.liquidGlassConfig.customColor1)
            put("shaderCustomColor2", themePrefs.liquidGlassConfig.customColor2)
            put("shaderCustomColor3", themePrefs.liquidGlassConfig.customColor3)
            put("shaderSpeed", themePrefs.liquidGlassConfig.animationSpeed)
            put("shaderDistortion", themePrefs.liquidGlassConfig.distortionScale)
            put("shaderOpacity", themePrefs.liquidGlassConfig.overallOpacity)
            put("shaderEnabled", themePrefs.liquidGlassConfig.isEnabled)
        }
        prefs.edit().putString("theme_config", json.toString()).apply()
    }
}

package com.cypher.music.data

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * "Movimiento reducido": apaga los shaders AGSL y las animaciones ambientales de la
 * interfaz. Hasta que el usuario toca el ajuste, el valor sigue la preferencia del
 * sistema (Ajustes > Accesibilidad > Quitar animaciones), así que quien ya la tiene
 * activada no ve ni un shader al abrir Cypher por primera vez.
 */
class MotionPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // The screen that draws the shaders and this store are different objects, so the
    // state is process-wide: toggling in Settings must repaint Now Playing at once.
    private val _reducedMotion = sharedReducedMotion
    val reducedMotion: StateFlow<Boolean> = _reducedMotion.asStateFlow()

    init {
        if (!initialized) {
            sharedReducedMotion.value = readReducedMotion()
            initialized = true
        }
    }

    fun isReducedMotion(): Boolean = _reducedMotion.value

    fun setReducedMotion(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCED_MOTION, enabled).apply()
        _reducedMotion.value = enabled
    }

    /** Whether the stored value came from the user; false while following the system. */
    fun isExplicitlySet(): Boolean = prefs.contains(KEY_REDUCED_MOTION)

    fun exportJson(): String = JSONObject().apply {
        put("reducedMotion", isReducedMotion())
        put("explicit", isExplicitlySet())
    }.toString()

    fun importJson(json: String) {
        runCatching {
            val root = JSONObject(json)
            if (root.optBoolean("explicit", true)) {
                setReducedMotion(root.optBoolean("reducedMotion", false))
            } else {
                prefs.edit().remove(KEY_REDUCED_MOTION).apply()
                _reducedMotion.value = systemReducedMotion(appContext)
            }
        }
    }

    private fun readReducedMotion(): Boolean =
        if (prefs.contains(KEY_REDUCED_MOTION)) {
            prefs.getBoolean(KEY_REDUCED_MOTION, false)
        } else {
            systemReducedMotion(appContext)
        }

    companion object {
        const val PREFS = "cypher_motion_prefs"
        private const val KEY_REDUCED_MOTION = "reduced_motion"

        private val sharedReducedMotion = MutableStateFlow(false)

        @Volatile
        private var initialized = false

        /**
         * Android exposes the accessibility setting as the global animation scale.
         * A scale of 0 means the user asked the system to remove animations.
         */
        fun systemReducedMotion(context: Context): Boolean = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

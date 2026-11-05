package com.cypher.music.ui.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic Engine for iOS 26 tactile feel:
 * Provides micro-ticks, light taps, and boundary clicks tuned for Snapdragon 778G / A52s vibrator.
 */
object AppHaptics {
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        if (vibrator != null) return
        val appCtx = context.applicationContext
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Micro-click when switching tabs on the floating dock.
     */
    fun tabClick(enabled: Boolean = true) {
        if (!enabled) return
        performMicroTick()
    }

    /**
     * Crisp tactile click when pressing quick action buttons (Play/Pause, Shuffle, options menu).
     */
    fun actionClick(enabled: Boolean = true) {
        if (!enabled) return
        performClick()
    }

    /**
     * Subtle tick when scrubbing progress or dragging sliders.
     */
    fun scrubberTick(enabled: Boolean = true) {
        if (!enabled) return
        performMicroTick()
    }

    /**
     * Tactile pulse when reordering playlist items up or down.
     */
    fun reorderTick(enabled: Boolean = true) {
        if (!enabled) return
        performClick()
    }

    private fun performMicroTick() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(10, 45))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(10)
            }
        } catch (_: Exception) {}
    }

    private fun performClick() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(16, 85))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(16)
            }
        } catch (_: Exception) {}
    }
}

package com.cypher.music.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioVolumeManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _volumeFraction = MutableStateFlow(getCurrentVolumeFraction())
    val volumeFraction: StateFlow<Float> = _volumeFraction.asStateFlow()

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                _volumeFraction.value = getCurrentVolumeFraction()
            }
        }
    }

    init {
        try {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            context.registerReceiver(volumeReceiver, filter)
        } catch (e: Exception) {
            // Ignore if permission or restricted
        }
    }

    fun getCurrentVolumeFraction(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        if (max <= 0f) return 0.5f
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        return (current / max).coerceIn(0f, 1f)
    }

    fun setVolumeFraction(fraction: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (fraction.coerceIn(0f, 1f) * max).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        _volumeFraction.value = fraction.coerceIn(0f, 1f)
    }

    fun release() {
        try {
            context.unregisterReceiver(volumeReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }
}

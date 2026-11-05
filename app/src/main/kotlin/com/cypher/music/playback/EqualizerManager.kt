package com.cypher.music.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val levelMb: Short,
    val minLevelMb: Short,
    val maxLevelMb: Short
) {
    val levelDb: Float
        get() = levelMb / 100f

    val formattedFreq: String
        get() = if (centerFreqHz >= 1000) "${centerFreqHz / 1000}kHz" else "${centerFreqHz}Hz"
}

data class EqualizerState(
    val isSupported: Boolean = false,
    val isEnabled: Boolean = false,
    val presets: List<String> = emptyList(),
    val currentPresetIndex: Short = -1, // -1 means Custom / Personalizado
    val bands: List<EqualizerBand> = emptyList(),
    val isBassBoostSupported: Boolean = false,
    val isBassBoostEnabled: Boolean = false,
    val bassBoostStrength: Short = 0 // 0 to 1000
)

class EqualizerManager(private val context: Context) {
    companion object {
        private const val TAG = "EqualizerManager"
        private const val PREFS_NAME = "cypher_equalizer_prefs"
        private const val KEY_ENABLED = "eq_enabled"
        private const val KEY_PRESET = "eq_preset"
        private const val KEY_BASS_ENABLED = "bass_enabled"
        private const val KEY_BASS_STRENGTH = "bass_strength"
        private const val KEY_BAND_PREFIX = "eq_band_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var currentSessionId: Int = 0

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        currentSessionId = audioSessionId

        releaseFx()

        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq

            val minLevel = eq.bandLevelRange?.getOrNull(0) ?: -1500
            val maxLevel = eq.bandLevelRange?.getOrNull(1) ?: 1500
            val numBands = eq.numberOfBands

            // Read Presets
            val numPresets = eq.numberOfPresets
            val presetList = mutableListOf<String>()
            for (p in 0 until numPresets) {
                presetList.add(eq.getPresetName(p.toShort()))
            }

            // Read BassBoost
            var bbSupported = false
            try {
                val bb = BassBoost(0, audioSessionId)
                bassBoost = bb
                bbSupported = bb.strengthSupported
                val savedBbEnabled = prefs.getBoolean(KEY_BASS_ENABLED, false)
                val savedBbStrength = prefs.getInt(KEY_BASS_STRENGTH, 0).toShort()
                bb.enabled = savedBbEnabled
                if (bbSupported) {
                    bb.setStrength(savedBbStrength)
                }
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost init skipped or unsupported: ${e.message}")
            }

            // Restore Equalizer Saved State
            val savedEnabled = prefs.getBoolean(KEY_ENABLED, false)
            val savedPreset = prefs.getInt(KEY_PRESET, -1).toShort()
            eq.enabled = savedEnabled

            if (savedPreset >= 0 && savedPreset < numPresets) {
                eq.usePreset(savedPreset)
            } else {
                for (b in 0 until numBands) {
                    val bandIdx = b.toShort()
                    val savedLevel = prefs.getInt("$KEY_BAND_PREFIX$b", 0).toShort()
                    eq.setBandLevel(bandIdx, savedLevel.coerceIn(minLevel, maxLevel))
                }
            }

            // Build Bands state
            val bands = mutableListOf<EqualizerBand>()
            for (b in 0 until numBands) {
                val bandIdx = b.toShort()
                val freq = eq.getCenterFreq(bandIdx) / 1000 // mHz to Hz
                val level = eq.getBandLevel(bandIdx)
                bands.add(
                    EqualizerBand(
                        index = bandIdx,
                        centerFreqHz = freq,
                        levelMb = level,
                        minLevelMb = minLevel,
                        maxLevelMb = maxLevel
                    )
                )
            }

            _state.value = EqualizerState(
                isSupported = true,
                isEnabled = eq.enabled,
                presets = presetList,
                currentPresetIndex = if (savedPreset in 0 until numPresets) savedPreset else -1,
                bands = bands,
                isBassBoostSupported = bbSupported,
                isBassBoostEnabled = bassBoost?.enabled ?: false,
                bassBoostStrength = bassBoost?.roundedStrength ?: 0
            )

            Log.d(TAG, "Equalizer successfully attached to session $audioSessionId (${numBands} bands)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Equalizer to session $audioSessionId: ${e.message}")
            _state.value = _state.value.copy(isSupported = false)
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
            _state.update { it.copy(isEnabled = enabled) }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting eq enabled: ${e.message}")
        }
    }

    fun applyPreset(presetIndex: Short) {
        val eq = equalizer ?: return
        try {
            if (presetIndex in 0 until eq.numberOfPresets) {
                eq.usePreset(presetIndex)
                prefs.edit().putInt(KEY_PRESET, presetIndex.toInt()).apply()

                // Update band levels from preset
                val updatedBands = _state.value.bands.map { band ->
                    val newLevel = eq.getBandLevel(band.index)
                    prefs.edit().putInt("$KEY_BAND_PREFIX${band.index}", newLevel.toInt()).apply()
                    band.copy(levelMb = newLevel)
                }

                _state.update {
                    it.copy(
                        currentPresetIndex = presetIndex,
                        bands = updatedBands
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying preset $presetIndex: ${e.message}")
        }
    }

    fun setBandLevel(bandIndex: Short, levelMb: Short) {
        val eq = equalizer ?: return
        try {
            val clamped = levelMb.coerceIn(
                _state.value.bands.firstOrNull()?.minLevelMb ?: -1500,
                _state.value.bands.firstOrNull()?.maxLevelMb ?: 1500
            )
            eq.setBandLevel(bandIndex, clamped)
            prefs.edit()
                .putInt(KEY_PRESET, -1) // Marked as custom
                .putInt("$KEY_BAND_PREFIX$bandIndex", clamped.toInt())
                .apply()

            val updatedBands = _state.value.bands.map {
                if (it.index == bandIndex) it.copy(levelMb = clamped) else it
            }

            _state.update {
                it.copy(
                    currentPresetIndex = -1, // Custom
                    bands = updatedBands
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting band level: ${e.message}")
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        try {
            bassBoost?.enabled = enabled
            prefs.edit().putBoolean(KEY_BASS_ENABLED, enabled).apply()
            _state.update { it.copy(isBassBoostEnabled = enabled) }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting bass boost enabled: ${e.message}")
        }
    }

    fun setBassBoostStrength(strength: Short) {
        try {
            val clamped = strength.coerceIn(0, 1000)
            bassBoost?.setStrength(clamped)
            prefs.edit().putInt(KEY_BASS_STRENGTH, clamped.toInt()).apply()
            _state.update { it.copy(bassBoostStrength = clamped) }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting bass boost strength: ${e.message}")
        }
    }

    private fun releaseFx() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null

        try {
            bassBoost?.release()
        } catch (_: Exception) {}
        bassBoost = null
    }

    fun release() {
        releaseFx()
        currentSessionId = 0
    }
}

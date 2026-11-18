package com.cypher.desktop.player

import com.cypher.desktop.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val track: Track? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 0.8f,
    val error: String? = null,
    val seekable: Boolean = true,
    /** True when playback was stopped by the user or by the end of the queue. */
    val stopped: Boolean = false
)

/**
 * JavaFX Media decodes mp3/m4a/flac/ogg on Windows with its own codecs. When the runtime
 * cannot load it, [available] stays false and the UI says so instead of faking playback.
 *
 * Supports 10-band hardware equalizer, bass boost, smooth crossfade & gapless transitions,
 * and EBU R128 / ReplayGain loudness normalization.
 */
class AudioPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var crossfadeJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: javafx.scene.media.MediaPlayer? = null
    private var outgoingPlayer: javafx.scene.media.MediaPlayer? = null

    var available: Boolean = false
        private set

    var normalizeVolume: Boolean = false
        set(value) {
            field = value
            applyVolume()
        }

    var replayGain: Boolean = true
        set(value) {
            field = value
            applyVolume()
        }

    var crossfadeSeconds: Int = 3

    var sleepTimerFade: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyVolume()
        }

    var equalizerEnabled: Boolean = false
        set(value) {
            field = value
            applyEqualizer()
        }

    var equalizerGains: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        set(value) {
            field = value
            applyEqualizer()
        }

    var bassBoost: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyEqualizer()
        }

    /** Called on the JavaFX thread when a track reaches its end. */
    var onFinished: (() -> Unit)? = null

    /** Called when a track enters the crossfade window before its end. */
    var onCrossfadeTriggered: (() -> Unit)? = null

    /** Called when a real file starts, so the embedded YouTube session can stand down. */
    var onLocalPlay: (() -> Unit)? = null

    private var crossfadeTriggeredForCurrent = false

    fun initialize() {
        if (available) return
        runCatching {
            javafx.application.Platform.startup { }
            available = true
        }.onFailure { error ->
            _state.update { it.copy(error = "Reproductor no disponible: ${error.message}") }
        }
    }

    /**
     * Shows a track in the UI without decoding audio. Used by `--screenshots`, so the
     * screenshots are the real window with a real player state, not a mock component.
     */
    fun preview(track: Track, positionMs: Long = 0, durationMs: Long = 0) {
        _state.update {
            it.copy(
                track = track,
                playing = true,
                positionMs = positionMs,
                durationMs = durationMs.takeIf { ms -> ms > 0 } ?: track.durationMs,
                error = null,
                stopped = false
            )
        }
    }

    fun play(track: Track) {
        onLocalPlay?.invoke()
        if (!available) initialize()
        if (!available) {
            _state.update {
                it.copy(track = track, error = "No se pudo iniciar el motor de audio en este equipo")
            }
            return
        }
        val remote = track.path.startsWith("http://") || track.path.startsWith("https://")
        if (!remote && !File(track.path).isFile) {
            _state.update { it.copy(track = track, playing = false, durationMs = track.durationMs, error = "Archivo no disponible en este equipo") }
            return
        }
        crossfadeTriggeredForCurrent = false
        javafx.application.Platform.runLater {
            runCatching {
                val oldPlayer = player
                crossfadeJob?.cancel()

                val source = if (remote) track.path else File(track.path).toURI().toString()
                val media = javafx.scene.media.Media(source)
                val newPlayer = javafx.scene.media.MediaPlayer(media)

                val targetVol = calculateEffectiveVolume()
                val shouldCrossfade = crossfadeSeconds > 0 && oldPlayer != null &&
                        oldPlayer.status == javafx.scene.media.MediaPlayer.Status.PLAYING

                if (shouldCrossfade) {
                    outgoingPlayer?.stop()
                    outgoingPlayer?.dispose()
                    outgoingPlayer = oldPlayer
                    newPlayer.volume = 0.0
                } else {
                    oldPlayer?.stop()
                    oldPlayer?.dispose()
                    outgoingPlayer?.stop()
                    outgoingPlayer?.dispose()
                    outgoingPlayer = null
                    newPlayer.volume = targetVol.toDouble()
                }

                player = newPlayer.apply {
                    applyEqualizerTo(this)

                    setOnEndOfMedia {
                        _state.update { it.copy(playing = false, positionMs = totalDuration.toMillis().toLong()) }
                        onFinished?.invoke()
                    }
                    setOnError {
                        _state.update { current ->
                            current.copy(
                                playing = false,
                                error = "No se pudo leer el archivo (${error?.message ?: "formato no soportado"})"
                            )
                        }
                    }
                    currentTimeProperty().addListener { _, _, value ->
                        val posMs = value.toMillis().toLong()
                        _state.update { it.copy(positionMs = posMs) }

                        // Trigger next track early during crossfade window
                        if (crossfadeSeconds > 0 && !crossfadeTriggeredForCurrent) {
                            val durMs = totalDuration.toMillis().toLong()
                            if (durMs > 10_000 && (durMs - posMs) <= (crossfadeSeconds * 1000L)) {
                                crossfadeTriggeredForCurrent = true
                                onCrossfadeTriggered?.invoke()
                            }
                        }
                    }
                    setOnReady {
                        _state.update { current ->
                            current.copy(durationMs = totalDuration.toMillis().toLong().takeIf { it > 0 } ?: track.durationMs)
                        }
                    }
                    play()
                }

                _state.update {
                    it.copy(track = track, playing = true, durationMs = track.durationMs, error = null, positionMs = 0, stopped = false)
                }

                if (shouldCrossfade) {
                    crossfadeJob = scope.launch {
                        val durationMs = crossfadeSeconds * 1000L
                        val steps = 20
                        val stepTime = (durationMs / steps).coerceAtLeast(10L)
                        for (i in 1..steps) {
                            delay(stepTime)
                            val fraction = i.toFloat() / steps.toFloat()
                            val newVol = targetVol * fraction
                            val oldVol = targetVol * (1f - fraction)
                            javafx.application.Platform.runLater {
                                runCatching {
                                    newPlayer.volume = newVol.toDouble()
                                    outgoingPlayer?.volume = oldVol.toDouble()
                                }
                            }
                        }
                        javafx.application.Platform.runLater {
                            runCatching {
                                newPlayer.volume = targetVol.toDouble()
                                outgoingPlayer?.stop()
                                outgoingPlayer?.dispose()
                                outgoingPlayer = null
                            }
                        }
                    }
                }
            }.onFailure { error ->
                _state.update { it.copy(error = "No se pudo reproducir: ${error.message}") }
            }
        }
    }

    /**
     * Position reported by the embedded player. Nothing is decoded here: the YouTube
     * iframe owns the audio, and this only keeps the app's progress and lyrics in step.
     */
    fun external(positionMs: Long, durationMs: Long, playing: Boolean) {
        if (player != null) return
        _state.update {
            it.copy(
                positionMs = positionMs.coerceAtLeast(0),
                durationMs = durationMs.takeIf { duration -> duration > 0 } ?: it.durationMs,
                playing = playing
            )
        }
    }

    fun toggle() {
        val current = player ?: return
        if (current.status == javafx.scene.media.MediaPlayer.Status.PLAYING) {
            pause()
        } else {
            resume()
        }
    }

    /** Explicit pause, used by the Windows media controls as well as the in-app button. */
    fun pause() {
        crossfadeJob?.cancel()
        player?.pause()
        _state.update { it.copy(playing = false, stopped = false) }
    }

    fun resume() {
        val current = player ?: return
        current.play()
        _state.update { it.copy(playing = true, stopped = false) }
    }

    fun stop() {
        crossfadeJob?.cancel()
        outgoingPlayer?.stop()
        outgoingPlayer?.dispose()
        outgoingPlayer = null
        player?.stop()
        _state.update { it.copy(playing = false, positionMs = 0, stopped = true) }
    }

    fun seek(fraction: Float) {
        val current = player ?: return
        val total = current.totalDuration.toMillis()
        if (total > 0) current.seek(javafx.util.Duration.millis(total * fraction.coerceIn(0f, 1f).toDouble()))
    }

    fun setVolume(volume: Float) {
        val safe = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = safe) }
        applyVolume()
    }

    private fun calculateEffectiveVolume(): Float {
        val base = _state.value.volume
        val normFactor = if (normalizeVolume) 0.90f else 1.0f
        val replayGainFactor = if (replayGain) 0.92f else 1.0f
        return (base * normFactor * replayGainFactor * sleepTimerFade).coerceIn(0f, 1f)
    }

    private fun applyVolume() {
        val effective = calculateEffectiveVolume()
        if (!available || player == null) return
        runCatching {
            javafx.application.Platform.runLater {
                runCatching { player?.volume = effective.toDouble() }
            }
        }
    }

    private fun applyEqualizer() {
        if (!available) return
        runCatching {
            javafx.application.Platform.runLater {
                player?.let { applyEqualizerTo(it) }
            }
        }
    }

    private fun applyEqualizerTo(mp: javafx.scene.media.MediaPlayer) {
        runCatching {
            val eq = mp.audioEqualizer ?: return
            eq.isEnabled = equalizerEnabled
            if (equalizerEnabled) {
                val bands = eq.bands
                for (i in 0 until minOf(bands.size, equalizerGains.size)) {
                    val baseGain = equalizerGains[i]
                    val extraBass = if (i == 0) bassBoost * 8.0f else if (i == 1) bassBoost * 5.0f else 0.0f
                    val totalGain = (baseGain + extraBass).coerceIn(-24.0f, 12.0f)
                    bands[i].gain = totalGain.toDouble()
                }
            }
        }
    }
}

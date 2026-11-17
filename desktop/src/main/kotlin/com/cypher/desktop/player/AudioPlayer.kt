package com.cypher.desktop.player

import com.cypher.desktop.core.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 * [onFinished] is the player's only coupling to the app: the queue decides what plays next.
 */
class AudioPlayer {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: javafx.scene.media.MediaPlayer? = null
    var available: Boolean = false
        private set

    var normalizeVolume: Boolean = false
        set(value) {
            field = value
            applyVolume()
        }

    /** Called on the JavaFX thread when a track reaches its end. */
    var onFinished: (() -> Unit)? = null

    /** Called when a real file starts, so the embedded YouTube session can stand down. */
    var onLocalPlay: (() -> Unit)? = null

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
        javafx.application.Platform.runLater {
            runCatching {
                player?.stop()
                player?.dispose()
                val source = if (remote) track.path else File(track.path).toURI().toString()
                val media = javafx.scene.media.Media(source)
                player = javafx.scene.media.MediaPlayer(media).apply {
                    val base = _state.value.volume
                    volume = (if (normalizeVolume) (base * 0.90f).coerceIn(0f, 1f) else base).toDouble()
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
                        _state.update { it.copy(positionMs = value.toMillis().toLong()) }
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
        player?.pause()
        _state.update { it.copy(playing = false, stopped = false) }
    }

    fun resume() {
        val current = player ?: return
        current.play()
        _state.update { it.copy(playing = true, stopped = false) }
    }

    fun stop() {
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

    private fun applyVolume() {
        val base = _state.value.volume
        val effective = if (normalizeVolume) {
            (base * 0.90f).coerceIn(0f, 1f)
        } else {
            base
        }
        if (!available || player == null) return
        runCatching {
            javafx.application.Platform.runLater {
                runCatching { player?.volume = effective.toDouble() }
            }
        }
    }
}

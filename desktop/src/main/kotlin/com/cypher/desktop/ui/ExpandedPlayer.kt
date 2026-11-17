@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.cypher.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.UiState
import com.cypher.desktop.core.Lyrics
import com.cypher.desktop.core.LyricsProvider
import com.cypher.desktop.ui.glass.cypherGlass
import com.cypher.desktop.player.PlaybackState
import androidx.compose.ui.draw.shadow

/**
 * The expanded screen: the cover and the controls on the left, the lyrics filling the
 * right half, over a blurred copy of the artwork. Playback is never interrupted by
 * opening or closing it — it is the same player, only shown larger.
 */
@Composable
fun ExpandedPlayer(playback: PlaybackState, ui: UiState, accent: Color, state: AppState) {
    val track = playback.track
    val view = track?.let { playing ->
        viewOf(ui.data, playing.id)
            ?: TrackView(playing.id, playing.title, playing.artist, playing.album, playing.durationMs, playing.artwork)
    }
    val bitmap = rememberArtwork(view?.artwork)
    val lyricsState = rememberSongLyrics(ui.data, view, track?.path)
    val lines = lyricsState.lines
    val repeat = ui.data.preferences.repeat

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0910))) {
        if (bitmap != null) {
            Image(
                bitmap, null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.25f
                        scaleY = 1.25f
                        // The reference lets the record colour own the screen; the veil
                        // below is what keeps the lyrics readable, not a black wall.
                        alpha = 0.85f
                    }
                    .blur(70.dp)
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Color.Black.copy(alpha = 0.38f)
            )
        )

        // Geometry from "Musica Full Screen.svg": cover starts at 11.3% of the width,
        // lyrics at 47.7%. Both scale with the window instead of being fixed pixels.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth * 0.1133f
        val coverSize = minOf(maxWidth * 0.311f, maxHeight * 0.55f)
        val lyricsStart = maxWidth * 0.4766f
        Row(
            Modifier.fillMaxSize().padding(start = side, end = 24.dp, top = 24.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ---------------------------------------------------------- cover + controls
            Column(Modifier.width(coverSize)) {
                Box(
                    Modifier.size(coverSize)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    if (view != null) {
                        Artwork(view, 392, accent)
                    } else {
                        Box(Modifier.fillMaxSize().background(CypherColors.Card), contentAlignment = Alignment.Center) {
                            Icon(
                                androidx.compose.material.icons.Icons.Rounded.PlayArrow, null,
                                tint = accent.copy(alpha = 0.6f), modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(playback.positionMs), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Slider(
                            value = if (playback.durationMs > 0) (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f) else 0f,
                            onValueChange = state::seek,
                            enabled = playback.track != null,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White, activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.26f),
                                disabledThumbColor = Color.White.copy(alpha = 0.3f),
                                disabledActiveTrackColor = Color.White.copy(alpha = 0.3f),
                                disabledInactiveTrackColor = Color.White.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                    }
                    Text(formatTime(playback.durationMs), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    view?.title ?: "Nada en reproducción",
                    color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    view?.artist ?: "Elige una canción de tu biblioteca",
                    color = Color.White.copy(alpha = 0.66f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = state::toggleShuffle) {
                        Icon(
                            Icons.Rounded.Shuffle, "Aleatorio",
                            tint = if (ui.data.preferences.shuffle) accent else Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = state::playPrevious) {
                        Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    IconButton(onClick = state::togglePlayback) {
                        Box(Modifier.size(54.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(
                                if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                "Reproducir o pausar", tint = Color(0xFF14101A), modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    IconButton(onClick = { state.playNext() }) {
                        Icon(Icons.Rounded.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    IconButton(onClick = state::cycleRepeat) {
                        Icon(
                            if (repeat == "ONE") Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            if (repeat == "OFF") "Repetición desactivada" else "Repetición activa",
                            tint = if (repeat == "OFF") Color.White.copy(alpha = 0.55f) else accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width((lyricsStart - side - coverSize).coerceAtLeast(24.dp)))

            // ------------------------------------------------------------------- lyrics
            // No panel at all: the lyrics are drawn straight on the blurred cover, as in the
            // reference drawing. A boxed pane read as a separate purple card.
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "LETRAS", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp,
                        letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = state::collapsePlayer) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Volver a la ventana", tint = Color.White.copy(alpha = 0.8f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (lines.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                track == null -> "Elige una canción para ver sus letras."
                                lyricsState.status == LyricsProvider.Status.LOADING -> "Buscando la letra…"
                                lyricsState.status == LyricsProvider.Status.OFFLINE -> "No hay conexión para buscar la letra."
                                else -> "Sin letras para esta canción.\nPuedes añadir un archivo .lrc desde el panel derecho."
                            },
                            color = Color.White.copy(alpha = 0.55f), fontSize = 17.sp, textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Same painter as the panel, at full size: the active line carries
                    // the runtime shader and the karaoke wash instead of a plain Text.
                    LyricsStage(
                        lines = lines,
                        positionMs = playback.positionMs,
                        prefs = ui.data.preferences,
                        accent = accent,
                        fullscreen = true,
                        playing = playback.playing,
                        modifier = Modifier.fillMaxSize().padding(end = 56.dp)
                    )
                }
            }
        }
    }
        }
}

@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.cypher.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.UiState
import com.cypher.desktop.core.LyricsProvider
import com.cypher.desktop.player.PlaybackState
import kotlinx.coroutines.delay

/**
 * Modo Pantalla Completa Inmersivo / Karaoke de Salón (F11).
 * Totalmente limpio, con fondo líquido ambiental reactivo a la carátula,
 * letras gigantes sincronizadas en tiempo real y controles discretos que se ocultan.
 */
@Composable
fun FullscreenKaraokeView(
    playback: PlaybackState,
    ui: UiState,
    accent: Color,
    state: AppState
) {
    val track = playback.track
    val view = track?.let { playing ->
        viewOf(ui.data, playing.id)
            ?: TrackView(playing.id, playing.title, playing.artist, playing.album, playing.durationMs, playing.artwork)
    }
    val artworkSource = view?.artwork?.takeIf { it.isNotBlank() } ?: view?.artworkUrl
    val bitmap = rememberArtwork(artworkSource)
    val lyricsState = rememberSongLyrics(ui.data, view, track?.path)
    val lines = lyricsState.lines
    val prefs = ui.data.preferences
    val coverColors = rememberCoverPalette(bitmap, accent)

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastInteraction) {
        delay(3500L)
        controlsVisible = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF08060B))
            .onPointerEvent(PointerEventType.Move) {
                controlsVisible = true
                lastInteraction = System.currentTimeMillis()
            }
    ) {
        // Ambient reactive liquid backdrop
        LiquidBackdrop(colors = coverColors, prefs = prefs)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))

        // Central synchronized lyrics display
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 96.dp, vertical = 72.dp),
            contentAlignment = Alignment.Center
        ) {
            if (lines.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        view?.title ?: "Cypher Karaoke",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        view?.artist ?: "Pulsa F11 para salir",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))
                    Text(
                        when {
                            track == null -> "Elige una canción para comenzar el karaoke."
                            lyricsState.status == LyricsProvider.Status.LOADING -> "Buscando letras sincronizadas…"
                            lyricsState.status == LyricsProvider.Status.OFFLINE -> "Modo sin conexión"
                            else -> "Sin letras disponibles para esta canción."
                        },
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 16.sp
                    )
                }
            } else {
                LyricsStage(
                    lines = lines,
                    positionMs = playback.positionMs,
                    prefs = prefs,
                    accent = accent,
                    artworkColors = coverColors,
                    fullscreen = true,
                    playing = playback.playing,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top bar overlay (Title, Artist, Close button)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        view?.title ?: "Cypher",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        view?.artist ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (track != null) {
                    IconButton(
                        onClick = { state.openLyricCardDialog(track) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = "Compartir tarjeta de letra",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }

                IconButton(
                    onClick = { state.setFullscreenKaraoke(false) },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(
                        Icons.Rounded.FullscreenExit,
                        contentDescription = "Salir de pantalla completa (F11)",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom playback control overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress Slider
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTime(playback.positionMs),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Slider(
                        value = if (playback.durationMs > 0) {
                            (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
                        } else 0f,
                        onValueChange = state::seek,
                        enabled = playback.track != null,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    )
                    Text(
                        formatTime(playback.durationMs),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Playback Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = state::playPrevious) {
                        Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { state.togglePlayback() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            "Reproducir/Pausar",
                            tint = Color(0xFF14101A),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(onClick = { state.playNext() }) {
                        Icon(Icons.Rounded.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

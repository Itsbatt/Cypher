@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.cypher.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.UiState
import com.cypher.desktop.core.LyricsProvider
import com.cypher.desktop.player.PlaybackState
import kotlin.math.roundToInt

/**
 * The expanded screen: the record on the left with its controls over the cover on hover,
 * the lyrics filling the right half, and a liquid-glass backdrop tinted by the cover.
 * Playback is never interrupted by opening or closing it — it is the same player, only
 * shown larger.
 */
@Composable
fun ExpandedPlayer(playback: PlaybackState, ui: UiState, accent: Color, state: AppState) {
    val track = playback.track
    val view = track?.let { playing ->
        viewOf(ui.data, playing.id)
            ?: TrackView(playing.id, playing.title, playing.artist, playing.album, playing.durationMs, playing.artwork)
    }
    // Same source the cover widget uses: a file's own art, or the URL the service gave.
    val artworkSource = view?.artwork?.takeIf { it.isNotBlank() } ?: view?.artworkUrl
    val bitmap = rememberArtwork(artworkSource)
    val lyricsState = rememberSongLyrics(ui.data, view, track?.path)
    val lines = lyricsState.lines
    val repeat = ui.data.preferences.repeat
    val prefs = ui.data.preferences

    // The screen is coloured by the record: the three dominant tones of the cover feed a
    // liquid-glass shader, the desktop reading of the phone's AGSL backdrop.
    val coverColors = rememberCoverPalette(bitmap, accent)
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0910))) {
        LiquidBackdrop(colors = coverColors, prefs = prefs)
        // A thin veil on top keeps white lyrics readable over a bright cover without
        // turning the glass back into a black wall.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))

        // Geometry from "Musica Full Screen.svg": cover starts at 11.3% of the width,
        // lyrics at 47.7%. Both scale with the window instead of being fixed pixels.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = maxWidth * 0.1133f
            val coverSize = minOf(maxWidth * 0.40f, maxHeight * 0.68f)
            val lyricsStart = maxWidth * 0.4766f
            Row(
                Modifier.fillMaxSize().padding(start = side, end = 24.dp, top = 24.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ------------------------------------------------------ cover + controls
                Column(Modifier.width(coverSize)) {
                    var hovered by remember { mutableStateOf(false) }
                    Box(
                        Modifier.size(coverSize)
                            .clip(RoundedCornerShape(22.dp))
                            .onPointerEvent(PointerEventType.Enter) { hovered = true }
                            .onPointerEvent(PointerEventType.Exit) { hovered = false }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = view != null
                            ) { state.togglePlayback() }
                    ) {
                        if (view != null) {
                            Artwork(view, coverSize.value.roundToInt(), accent)
                        } else {
                            Box(
                                Modifier.fillMaxSize().background(CypherColors.Card),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow, null,
                                    tint = accent.copy(alpha = 0.6f), modifier = Modifier.size(64.dp)
                                )
                            }
                        }

                        // The controls live on the cover and only when the pointer is over
                        // it, like the phone's player: they stay out of the record's way.
                        val overlayAlpha by animateFloatAsState(
                            targetValue = if (hovered) 1f else 0f,
                            animationSpec = tween(170),
                            label = "controles"
                        )
                        if (overlayAlpha > 0.01f) {
                            Box(Modifier.fillMaxSize().graphicsLayer { alpha = overlayAlpha }) {
                                Box(
                                    Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.34f),
                                                Color.Black.copy(alpha = 0.10f),
                                                Color.Black.copy(alpha = 0.72f)
                                            )
                                        )
                                    )
                                )
                                Row(
                                    Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    IconButton(onClick = state::playPrevious) {
                                        Icon(
                                            Icons.Rounded.SkipPrevious, "Anterior",
                                            tint = Color.White, modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Box(
                                        Modifier.size(62.dp)
                                            .background(Color.White.copy(alpha = 0.94f), CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { state.togglePlayback() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            "Reproducir o pausar", tint = Color(0xFF14101A),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    IconButton(onClick = { state.playNext() }) {
                                        Icon(
                                            Icons.Rounded.SkipNext, "Siguiente",
                                            tint = Color.White, modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Column(
                                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Slider(
                                        value = if (playback.durationMs > 0) {
                                            (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
                                        } else 0f,
                                        onValueChange = state::seek,
                                        enabled = playback.track != null,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White, activeTrackColor = Color.White,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.30f),
                                            disabledThumbColor = Color.White.copy(alpha = 0.3f),
                                            disabledActiveTrackColor = Color.White.copy(alpha = 0.3f),
                                            disabledInactiveTrackColor = Color.White.copy(alpha = 0.18f)
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(22.dp)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            formatTime(playback.positionMs),
                                            color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp
                                        )
                                        Spacer(Modifier.weight(1f))
                                        IconButton(onClick = state::toggleShuffle, modifier = Modifier.size(26.dp)) {
                                            Icon(
                                                Icons.Rounded.Shuffle, "Aleatorio",
                                                tint = if (prefs.shuffle) Color.White else Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        IconButton(onClick = state::cycleRepeat, modifier = Modifier.size(26.dp)) {
                                            Icon(
                                                if (repeat == "ONE") Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                                if (repeat == "OFF") "Repetición desactivada" else "Repetición activa",
                                                tint = if (repeat == "OFF") Color.White.copy(alpha = 0.6f) else Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            formatTime(playback.durationMs),
                                            color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        view?.title ?: "Nada en reproducción",
                        color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        view?.artist ?: "Elige una canción de tu biblioteca",
                        color = Color.White.copy(alpha = 0.66f), fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width((lyricsStart - side - coverSize).coerceAtLeast(24.dp)))

                // ----------------------------------------------------------------- lyrics
                // No panel at all: the lyrics are drawn straight on the backdrop, as in the
                // reference drawing. A boxed pane read as a separate purple card.
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    var sleepTimerMenuOpen by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "LETRAS", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp,
                            letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))

                        if (track != null) {
                            // Start Radio button
                            IconButton(onClick = { state.startRadio(track) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Lucide.Radio,
                                    contentDescription = "Iniciar Radio infinita basada en esta canción",
                                    tint = if (ui.radioLoading) accent else Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))

                            // Share Lyric Card button
                            IconButton(onClick = { state.openLyricCardDialog(track) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Lucide.Share2,
                                    contentDescription = "Exportar tarjeta estética de letra",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }

                        // Sleep Timer button + Dropdown
                        Box {
                            IconButton(onClick = { sleepTimerMenuOpen = true }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Lucide.Timer,
                                    contentDescription = "Temporizador de apagado",
                                    tint = if (ui.sleepTimerRemainingSeconds != null || ui.sleepTimerMinutes != null) accent else Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = sleepTimerMenuOpen,
                                onDismissRequest = { sleepTimerMenuOpen = false },
                                modifier = Modifier.background(Color(0xFF1A1424))
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("15 minutos", color = Color.White, fontSize = 12.sp) },
                                    onClick = { state.setSleepTimer(15); sleepTimerMenuOpen = false }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("30 minutos", color = Color.White, fontSize = 12.sp) },
                                    onClick = { state.setSleepTimer(30); sleepTimerMenuOpen = false }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("45 minutos", color = Color.White, fontSize = 12.sp) },
                                    onClick = { state.setSleepTimer(45); sleepTimerMenuOpen = false }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("60 minutos", color = Color.White, fontSize = 12.sp) },
                                    onClick = { state.setSleepTimer(60); sleepTimerMenuOpen = false }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Al terminar la canción", color = Color.White, fontSize = 12.sp) },
                                    onClick = { state.setSleepTimer(0); sleepTimerMenuOpen = false }
                                )
                                if (ui.sleepTimerRemainingSeconds != null || ui.sleepTimerMinutes != null) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Desactivar temporizador", color = CypherColors.Danger, fontSize = 12.sp) },
                                        onClick = { state.setSleepTimer(null); sleepTimerMenuOpen = false }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))

                        // Fullscreen Karaoke F11
                        IconButton(onClick = { state.setFullscreenKaraoke(true) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Lucide.Maximize,
                                contentDescription = "Modo Karaoke F11",
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))

                        IconButton(onClick = state::collapsePlayer, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown, "Volver a la ventana",
                                tint = Color.White.copy(alpha = 0.8f)
                            )
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
                                color = Color.White.copy(alpha = 0.55f), fontSize = 17.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Same painter as the panel, at full size: the active line carries
                        // the runtime shader and the karaoke wash instead of a plain Text.
                        LyricsStage(
                            lines = lines,
                            positionMs = playback.positionMs,
                            prefs = prefs,
                            accent = accent,
                            artworkColors = coverColors,
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

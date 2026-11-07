package com.cypher.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cypher.music.data.ThemePreferences
import com.cypher.music.playback.PlayerState
import com.cypher.music.ui.theme.IosDarkBorder
import com.cypher.music.ui.theme.IosDarkPlayerGlass
import com.cypher.music.ui.theme.IosDarkTextSecondary
import com.cypher.music.ui.utils.AppHaptics

@Composable
fun CypherMiniPlayer(
    playerState: PlayerState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onExpand: () -> Unit,
    themePrefs: ThemePreferences = ThemePreferences(),
    accentColor: Color = Color(0xFFFA243C),
    currentPositionProvider: (() -> Float)? = null,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack ?: return
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    // 56dp keeps a deliberate swipe from being confused with a tap on the play button.
    val swipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    val latestOnSkipNext by rememberUpdatedState(onSkipNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)
    val miniPlayerShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(
                elevation = 8.dp,
                shape = miniPlayerShape,
                clip = false
            )
            .clip(miniPlayerShape)
            .background(IosDarkPlayerGlass)
            .border(
                width = 1.dp,
                color = IosDarkBorder,
                shape = miniPlayerShape
            )
            .clickable { onExpand() }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragCancel = { dragAccumulator = 0f },
                    onDragEnd = {
                        val distance = dragAccumulator
                        dragAccumulator = 0f
                        if (distance <= -swipeThresholdPx) {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            latestOnSkipNext()
                        } else if (distance >= swipeThresholdPx && latestOnPrevious != null) {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            latestOnPrevious?.invoke()
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    }
                )
            }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Proportional Album Artwork with rounded corners
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ArtworkPlaceholder(modifier = Modifier.fillMaxSize())

                    if (!track.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // High-legibility Title & Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = track.artist,
                        color = IosDarkTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls
                if (playerState.isBuffering) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .size(36.dp)
                            .padding(8.dp)
                    )
                } else {
                    IconButton(
                        onClick = {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onTogglePlayPause()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pausar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        AppHaptics.actionClick(themePrefs.hapticsEnabled)
                        onSkipNext()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Siguiente pista",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Sleek progress bar rendered directly in Draw phase
            val progressLambda = currentPositionProvider ?: { playerState.progress }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .drawWithContent {
                        drawRect(color = Color(0xFF27272A))
                        val fraction = progressLambda().coerceIn(0f, 1f)
                        if (fraction > 0f) {
                            drawRect(
                                color = accentColor,
                                size = size.copy(width = size.width * fraction)
                            )
                        }
                    }
            )
        }
    }
}

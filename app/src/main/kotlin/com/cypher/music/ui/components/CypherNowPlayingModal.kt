package com.cypher.music.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cypher.music.data.LiquidGlassConfig
import com.cypher.music.data.ThemePreferences
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import com.cypher.music.playback.MediaCacheManager
import com.cypher.music.playback.PlayerState
import com.cypher.music.playback.RepeatMode
import com.cypher.music.ui.theme.AmbientArtworkColors
import com.cypher.music.ui.theme.IosDarkCard
import com.cypher.music.ui.theme.IosDarkTextPrimary
import com.cypher.music.ui.theme.IosDarkTextSecondary
import com.cypher.music.ui.utils.AppHaptics
import com.cypher.music.ui.theme.PaletteExtractor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CypherNowPlayingModal(
    playerState: PlayerState,
    isFavorite: Boolean,
    volumeFraction: Float,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onSetVolume: (Float) -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onMoveQueueIndex: (Int, Int) -> Unit = { _, _ -> },
    onViewArtist: (String) -> Unit,
    onViewAlbum: (String) -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    onOpenSleepTimer: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onDownload: ((Track) -> Unit)? = null,
    onDeleteDownload: ((Track) -> Unit)? = null,
    themePrefs: ThemePreferences = ThemePreferences(),
    liquidGlassConfig: LiquidGlassConfig = themePrefs.liquidGlassConfig,
    accentColor: Color = Color(0xFFFA243C),
    playbackProgress: Float = playerState.progress,
    currentPositionMs: Long = playerState.currentPositionMs,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack ?: return
    val context = LocalContext.current

    // Extract dynamic ambient color palette for Liquid Glass background
    var ambientColors by remember { mutableStateOf(AmbientArtworkColors()) }
    LaunchedEffect(track.artworkUrl) {
        ambientColors = PaletteExtractor.extractColors(context, track.artworkUrl)
    }

    // Spring scale physics on album artwork
    val artworkScale by animateFloatAsState(
        targetValue = if (themePrefs.artworkSpringPhysics) (if (playerState.isPlaying) 1.0f else 0.88f) else 1.0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 280f),
        label = "ArtworkScale"
    )

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(playerState.progress) }

    // Horizontal swipe on the artwork changes track without touching the vertical dismissal gesture.
    val artworkSwipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    var artworkSwipeAccumulator by remember { mutableFloatStateOf(0f) }
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)
    // 1 = incoming artwork slides from the right (next), -1 = from the left (previous).
    var artworkSlideDirection by remember { mutableIntStateOf(1) }
    // The artwork follows the finger a fraction of the drag distance, then springs back.
    var artworkDragging by remember { mutableStateOf(false) }
    var artworkDragOffsetPx by remember { mutableFloatStateOf(0f) }
    val maxArtworkFollowPx = with(LocalDensity.current) { 56.dp.toPx() }
    val artworkFollowOffset by animateFloatAsState(
        targetValue = if (artworkDragging) artworkDragOffsetPx else 0f,
        animationSpec = if (artworkDragging) tween(0) else spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "ArtworkFollow"
    )

    // Sheets toggles
    var showLyrics by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Ambient AGSL Liquid Glass Background
            val effectiveOpacity = if (themePrefs.ambientGlowEnabled) liquidGlassConfig.overallOpacity else 0f
            LiquidGlassBackground(
                config = liquidGlassConfig.copy(overallOpacity = effectiveOpacity),
                dynamicColors = ambientColors
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Drag Pull Handle Indicator with generous touch area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDismiss() }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 10f) {
                                    onDismiss()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Center Area: Crossfade between Hero Artwork & Live Lyrics
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(targetState = showLyrics, label = "LyricsOrArt") { lyricsActive ->
                        if (lyricsActive) {
                            CypherLyricsSheet(
                                track = track,
                                currentPositionMs = currentPositionMs,
                                durationMs = playerState.durationMs,
                                onSeekTo = onSeek,
                                onClose = { showLyrics = false },
                                accentColor = accentColor,
                                liquidGlassConfig = liquidGlassConfig,
                                ambientColors = ambientColors,
                                showBackground = false
                            )
                        } else {
                            // Giant Squircle Artwork with Apple Music Liquid Drop Shadow
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .aspectRatio(1f)
                                    .scale(artworkScale)
                                    .clickable { showLyrics = true }
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                artworkSwipeAccumulator = 0f
                                                artworkDragOffsetPx = 0f
                                                artworkDragging = true
                                            },
                                            onDragCancel = {
                                                artworkSwipeAccumulator = 0f
                                                artworkDragging = false
                                            },
                                            onDragEnd = {
                                                val distance = artworkSwipeAccumulator
                                                artworkSwipeAccumulator = 0f
                                                artworkDragging = false
                                                artworkDragOffsetPx = 0f
                                                if (distance <= -artworkSwipeThresholdPx) {
                                                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                                    artworkSlideDirection = 1
                                                    latestOnNext()
                                                } else if (distance >= artworkSwipeThresholdPx) {
                                                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                                    artworkSlideDirection = -1
                                                    latestOnPrevious()
                                                }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                artworkSwipeAccumulator += dragAmount
                                                artworkDragOffsetPx =
                                                    (artworkDragOffsetPx + dragAmount * 0.32f).coerceIn(-maxArtworkFollowPx, maxArtworkFollowPx)
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = track,
                                    contentKey = { it.id },
                                    transitionSpec = {
                                        val direction = artworkSlideDirection
                                        (slideInHorizontally(animationSpec = tween(260)) { width -> direction * width / 4 } +
                                            fadeIn(animationSpec = tween(180)))
                                            .togetherWith(
                                                slideOutHorizontally(animationSpec = tween(260)) { width -> -direction * width / 4 } +
                                                    fadeOut(animationSpec = tween(180))
                                            )
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { translationX = artworkFollowOffset },
                                    label = "ArtworkSlide"
                                ) { shownTrack ->
                                    AsyncImage(
                                        model = shownTrack.artworkUrl,
                                        contentDescription = shownTrack.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .shadow(
                                                elevation = if (playerState.isPlaying) 32.dp else 14.dp,
                                                shape = RoundedCornerShape(24.dp),
                                                ambientColor = ambientColors.accent.copy(alpha = 0.5f),
                                                spotColor = ambientColors.dominant
                                            )
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(IosDarkCard)
                                            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Track Title, Artist, Quality Badge & Heart/Options
                val downloadProgressMap by MediaCacheManager.downloadProgressMap.collectAsState()
                val downloadedTrackIds by MediaCacheManager.downloadedTrackIds.collectAsState()
                val isDownloading = downloadProgressMap.containsKey(track.id)
                val downloadPct = ((downloadProgressMap[track.id] ?: 0f) * 100).toInt()
                val isDownloaded = track.isDownloaded || downloadedTrackIds.contains(track.id) || MediaCacheManager.isTrackDownloaded(context, track.id)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = IosDarkTextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                color = IosDarkTextSecondary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onViewArtist(track.artist) }
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onToggleFavorite(track) }) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "Favorito",
                                    tint = if (isFavorite) accentColor else IosDarkTextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(onClick = { showContextMenu = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreHoriz,
                                    contentDescription = "Más opciones",
                                    tint = IosDarkTextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Minimalist Unified Format & Offline Capsule with clean dot indicator
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (track.sourceType == AudioSourceType.LOCAL) "Lossless ALAC" else "Spatial Audio",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )

                            if (isDownloaded) {
                                Spacer(modifier = Modifier.width(6.dp))
                                // Subtle, clean green dot indicator
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF30D158))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Offline",
                                    color = Color(0xFF30D158),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            } else if (isDownloading) {
                                Spacer(modifier = Modifier.width(6.dp))
                                CircularProgressIndicator(
                                    strokeWidth = 1.2.dp,
                                    color = accentColor,
                                    modifier = Modifier.size(8.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$downloadPct%",
                                    color = accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Scrubber Progress Bar
                CypherScrubber(
                    progress = playbackProgress,
                    durationMs = playerState.durationMs,
                    onSeek = onSeek,
                    hapticsEnabled = themePrefs.hapticsEnabled,
                    modifier = Modifier.fillMaxWidth()
                )

                // Playback Transport Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onPrevious()
                        },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = IosDarkTextPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onTogglePlayPause()
                        },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                            contentDescription = if (playerState.isPlaying) "Pausar" else "Reproducir",
                            tint = IosDarkTextPrimary,
                            modifier = Modifier.size(76.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onNext()
                        },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Siguiente",
                            tint = IosDarkTextPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Interactive iOS Volume Slider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = "Volumen bajo",
                        tint = IosDarkTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )

                    CypherVolumeSlider(
                        volume = volumeFraction,
                        onVolumeChange = onSetVolume,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volumen alto",
                        tint = IosDarkTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Bottom Tool Bar: Encased in Authentic Liquid Glass Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onToggleShuffle()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = "Aleatorio",
                                tint = if (playerState.isShuffle) accentColor else IosDarkTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(onClick = {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            showLyrics = !showLyrics
                        }) {
                            Icon(
                                imageVector = Icons.Filled.FormatQuote,
                                contentDescription = "Letras",
                                tint = if (showLyrics) accentColor else IosDarkTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(onClick = { openSoundSettings(context) }) {
                            Icon(
                                imageVector = Icons.Filled.Airplay,
                                contentDescription = "Dispositivos de audio",
                                tint = IosDarkTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onToggleRepeat()
                        }) {
                            Icon(
                                imageVector = if (playerState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                contentDescription = "Repetir",
                                tint = if (playerState.repeatMode != RepeatMode.OFF) accentColor else IosDarkTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(onClick = { showQueueSheet = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "A continuación",
                                tint = if (showQueueSheet) accentColor else IosDarkTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Context Menu Action Sheet
    if (showContextMenu) {
        CypherContextMenu(
            track = track,
            isFavorite = isFavorite,
            onDismiss = { showContextMenu = false },
            onPlayNext = {
                // Play next in queue
            },
            onAddToQueue = {
                // Add to queue
            },
            onAddToPlaylist = {
                showContextMenu = false
                onAddToPlaylist?.invoke(track)
            },
            onToggleFavorite = { onToggleFavorite(track) },
            onViewArtist = {
                showContextMenu = false
                onDismiss()
                onViewArtist(track.artist)
            },
            onViewAlbum = {
                showContextMenu = false
                onDismiss()
                onViewAlbum(track.album)
            },
            onOpenSleepTimer = {
                showContextMenu = false
                onOpenSleepTimer()
            },
            onOpenEqualizer = {
                showContextMenu = false
                onOpenEqualizer()
            },
            onDownload = {
                showContextMenu = false
                onDownload?.invoke(track)
            },
            onDeleteDownload = {
                showContextMenu = false
                onDeleteDownload?.invoke(track)
            }
        )
    }

    // Up Next Queue Sheet
    if (showQueueSheet) {
        CypherQueueSheet(
            queue = playerState.queue,
            queueIndex = playerState.queueIndex,
            currentTrack = playerState.currentTrack,
            isShuffle = playerState.isShuffle,
            repeatMode = playerState.repeatMode,
            onToggleShuffle = onToggleShuffle,
            onToggleRepeat = onToggleRepeat,
            onSelectIndex = onSelectQueueIndex,
            onRemoveIndex = onRemoveQueueIndex,
            onMoveIndex = onMoveQueueIndex,
            onDismiss = { showQueueSheet = false }
        )
    }
}

private fun openSoundSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

/**
 * Authentic Apple Music iOS Scrubber.
 * Slim 4dp continuous track without gaps or stop-dots, with a sleek expanding circular thumb.
 */
@Composable
private fun CypherScrubber(
    progress: Float,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
    enabled: Boolean = true
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var lastHapticTimestamp by remember { mutableLongStateOf(0L) }
    val hapticThrottleMs = 45L // Debouncer/limiter to prevent saturating main thread and vibration hardware

    val activeProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)

    val thumbRadius by animateDpAsState(
        targetValue = if (isDragging) 7.dp else 4.5.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ScrubberThumbSize"
    )

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ScrubberTrackHeight"
    )

    fun fireThrottledHaptic() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastHapticTimestamp >= hapticThrottleMs) {
            AppHaptics.scrubberTick(hapticsEnabled)
            lastHapticTimestamp = now
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = { offset ->
                            isDragging = true
                            val newProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragProgress = newProgress
                            fireThrottledHaptic()

                            val success = tryAwaitRelease()
                            isDragging = false
                            if (success) {
                                onSeek(dragProgress)
                            }
                        }
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            fireThrottledHaptic()
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeek(dragProgress)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val newProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            if ((newProgress * 100).toInt() != (dragProgress * 100).toInt()) {
                                fireThrottledHaptic()
                            }
                            dragProgress = newProgress
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
            val usableWidth = (totalWidthPx - thumbRadiusPx * 2).coerceAtLeast(0f)
            val thumbLeftPx = activeProgress * usableWidth
            val activeTrackWidthPx = thumbLeftPx + thumbRadiusPx

            // Inactive Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
            )

            // Active Track
            Box(
                modifier = Modifier
                    .width(with(LocalDensity.current) { activeTrackWidthPx.toDp() })
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.85f),
                                Color.White
                            )
                        )
                    )
            )

            // Sleek Round Thumb
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbLeftPx.toInt(), 0) }
                    .size(thumbRadius * 2)
                    .shadow(elevation = if (isDragging) 6.dp else 2.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        // Time Labels Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val currentMs = (activeProgress * durationMs).toLong()
            val remainingMs = (durationMs - currentMs).coerceAtLeast(0L)

            Text(
                text = formatTime(currentMs),
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "-${formatTime(remainingMs)}",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Authentic Apple Music iOS Volume Slider.
 * Continuous rounded 7dp capsule track that dynamically expands to 9dp when touched,
 * without thumb handles, gaps or stop-indicators.
 */
@Composable
private fun CypherVolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragVolume by remember { mutableFloatStateOf(0f) }

    val activeVolume = if (isDragging) dragVolume else volume.coerceIn(0f, 1f)

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 9.dp else 7.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "VolumeTrackHeight"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        val newVol = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragVolume = newVol
                        onVolumeChange(newVol)

                        val success = tryAwaitRelease()
                        isDragging = false
                        if (success) {
                            onVolumeChange(dragVolume)
                        }
                    }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val newVol = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragVolume = newVol
                        onVolumeChange(newVol)
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val newVol = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragVolume = newVol
                        onVolumeChange(newVol)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val activeWidthPx = (totalWidthPx * activeVolume).coerceIn(0f, totalWidthPx)

        // Inactive Capsule Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(Color.White.copy(alpha = 0.22f))
        )

        // Active Capsule Track
        if (activeWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .width(with(LocalDensity.current) { activeWidthPx.toDp() })
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(Color.White.copy(alpha = 0.90f))
            )
        }
    }
}

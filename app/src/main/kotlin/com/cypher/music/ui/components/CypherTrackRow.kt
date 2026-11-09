package com.cypher.music.ui.components

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import com.cypher.music.playback.MediaCacheManager
import com.cypher.music.playback.MusicPlayerManager
import com.cypher.music.playback.TrackRowPlayback
import com.cypher.music.ui.theme.IosDarkCard
import com.cypher.music.ui.theme.IosDarkSeparator
import com.cypher.music.ui.theme.IosDarkTextPrimary
import com.cypher.music.ui.theme.IosDarkTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CypherTrackRow(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onViewArtist: (() -> Unit)? = null,
    onViewAlbum: (() -> Unit)? = null,
    showDivider: Boolean = true,
    accentColor: Color = Color(0xFFFA243C),
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The real playback state lives in MusicPlayerManager; screens pass whether this
    // row is the selected track, not whether audio is actually running.
    val playerState by MusicPlayerManager.getInstance(context).state.collectAsState()
    val isCurrentTrack = isPlaying || TrackRowPlayback.isCurrent(track.id, playerState.currentTrack?.id)
    val isActivelyPlaying = TrackRowPlayback.isActivelyPlaying(
        rowTrackId = track.id,
        currentTrackId = playerState.currentTrack?.id,
        isPlaying = playerState.isPlaying
    )

    val downloadProgressMap by MediaCacheManager.downloadProgressMap.collectAsState()
    val downloadedTrackIds by MediaCacheManager.downloadedTrackIds.collectAsState()

    val isDownloading = downloadProgressMap.containsKey(track.id)
    val downloadProgress = downloadProgressMap[track.id] ?: 0f
    // Driven by the reactive set (synced at app start) so drawing a row never touches disk.
    val isDownloaded = track.isDownloaded || downloadedTrackIds.contains(track.id)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                )
                .padding(vertical = 7.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Squircle Artwork with placeholder & Apple-style bottom-corner download badge
            Box(
                modifier = Modifier.size(46.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ArtworkPlaceholder(modifier = Modifier.fillMaxSize())
                    if (!track.displayArtworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track.displayArtworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Apple Music-style Discrete Download Badge overlaid on bottom-end corner of artwork
                if (isDownloading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 3.dp, y = 3.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xE61C1C1E))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 1.8.dp,
                            color = accentColor,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (isDownloaded) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 3.dp, y = 3.dp)
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(Color(0xE61C1C1E))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DownloadDone,
                            contentDescription = "Descargada",
                            tint = Color(0xFF30D158),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isCurrentTrack) accentColor else IosDarkTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!track.isAvailable) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = "Archivo no disponible",
                            tint = Color(0xFFFFB800),
                            modifier = Modifier
                                .size(13.dp)
                                .padding(end = 4.dp)
                        )
                        Text(
                            text = "Archivo no encontrado",
                            color = Color(0xFFFFB800),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        if (track.sourceType == AudioSourceType.LOCAL) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = "Local",
                                tint = IosDarkTextSecondary.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(end = 4.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.SmartDisplay,
                                contentDescription = "YouTube",
                                tint = accentColor.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(end = 4.dp)
                            )
                        }

                        Text(
                            text = "${track.artist} • ${track.album}",
                            color = IosDarkTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Animated bars only for the track that is actually producing audio;
            // every other row keeps its plain duration.
            if (isActivelyPlaying) {
                PlaybackBars(accentColor = accentColor)
            } else if (track.formattedDuration.isNotBlank()) {
                Text(
                    text = track.formattedDuration,
                    color = IosDarkTextSecondary.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // More Options Icon
            IconButton(
                onClick = { showContextMenu = true },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = "Opciones",
                    tint = IosDarkTextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                color = IosDarkSeparator,
                thickness = 0.5.dp,
                modifier = Modifier.padding(start = 76.dp)
            )
        }
    }

    if (showContextMenu) {
        CypherContextMenu(
            track = track,
            isFavorite = isFavorite,
            onDismiss = { showContextMenu = false },
            onPlayNext = { onPlayNext?.invoke() },
            onAddToQueue = { onAddToQueue?.invoke() },
            onAddToPlaylist = { onAddToPlaylist?.invoke() },
            onToggleFavorite = { onToggleFavorite?.invoke() },
            onViewArtist = { onViewArtist?.invoke() },
            onViewAlbum = { onViewAlbum?.invoke() },
            onDownload = {
                Toast.makeText(context, "Descargando \"${track.title}\" con yt-dlp...", Toast.LENGTH_SHORT).show()
                scope.launch {
                    val file = MediaCacheManager.downloadTrackOffline(context, track)
                    if (file != null) {
                        Toast.makeText(context, "Descargado: \"${track.title}\"", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Error al descargar \"${track.title}\"", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDeleteDownload = {
                scope.launch {
                    val deleted = MediaCacheManager.deleteOfflineTrack(context, track.id)
                    val message = if (deleted) {
                        "Descarga eliminada: \"${track.title}\""
                    } else {
                        "No se pudo eliminar la descarga de \"${track.title}\""
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

/** Three animated bars shown only while this row's track is the one playing. */
@Composable
private fun PlaybackBars(accentColor: Color) {
    val transition = rememberInfiniteTransition(label = "EQ")
    val bar1 by transition.animateFloat(
        initialValue = 4f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 12f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(360), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 6f, targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse),
        label = "b3"
    )
    Row(
        modifier = Modifier
            .height(18.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
    ) {
        Box(modifier = Modifier.width(3.dp).height(bar1.dp).background(accentColor, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(3.dp).height(bar2.dp).background(accentColor, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(3.dp).height(bar3.dp).background(accentColor, RoundedCornerShape(1.dp)))
    }
}

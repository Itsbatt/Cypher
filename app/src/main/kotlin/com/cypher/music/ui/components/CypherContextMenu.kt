package com.cypher.music.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.cypher.music.ui.theme.AppleMusicRed
import com.cypher.music.ui.theme.IosDarkSeparator
import com.cypher.music.ui.theme.IosDarkTextPrimary
import com.cypher.music.ui.theme.IosDarkTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CypherContextMenu(
    track: Track,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
    onOpenSleepTimer: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onDownload: ((Track) -> Unit)? = null,
    onDeleteDownload: ((Track) -> Unit)? = null
) {
    val context = LocalContext.current
    val downloadProgressMap by MediaCacheManager.downloadProgressMap.collectAsState()
    val downloadedTrackIds by MediaCacheManager.downloadedTrackIds.collectAsState()

    val isDownloading = downloadProgressMap.containsKey(track.id)
    val progressFraction = downloadProgressMap[track.id] ?: 0f
    val isDownloaded = track.isDownloaded || downloadedTrackIds.contains(track.id) || MediaCacheManager.isTrackDownloaded(context, track.id)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF21C1C1E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Track Hero Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    if (!track.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ArtworkPlaceholder(
                            modifier = Modifier.fillMaxSize(),
                            iconSize = 22.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${track.artist} · ${if (track.sourceType == AudioSourceType.LOCAL) "Local" else "YouTube"}",
                        color = IosDarkTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = IosDarkSeparator, thickness = 0.5.dp)

            // Playback & Queue Actions Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                ContextMenuItem(
                    icon = when {
                        isDownloading -> Icons.Filled.Download
                        isDownloaded -> Icons.Filled.FileDownloadDone
                        else -> Icons.Filled.Download
                    },
                    title = when {
                        isDownloading -> "Descargando... ${(progressFraction * 100).toInt()}%"
                        isDownloaded -> "Eliminar de descargas"
                        else -> "Descargar canción (Escuchar offline)"
                    },
                    iconTint = when {
                        isDownloading -> AppleMusicRed
                        isDownloaded -> Color(0xFF30D158)
                        else -> Color.White
                    },
                    onClick = {
                        onDismiss()
                        if (isDownloading) {
                            // Already downloading
                        } else if (isDownloaded) {
                            onDeleteDownload?.invoke(track)
                        } else {
                            onDownload?.invoke(track)
                        }
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))

                ContextMenuItem(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    title = "Añadir a playlist...",
                    iconTint = AppleMusicRed,
                    onClick = {
                        onDismiss()
                        onAddToPlaylist()
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))

                ContextMenuItem(
                    icon = Icons.Filled.QueuePlayNext,
                    title = "Reproducir a continuación",
                    onClick = {
                        onPlayNext()
                        onDismiss()
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))

                ContextMenuItem(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = "Añadir a la cola",
                    onClick = {
                        onAddToQueue()
                        onDismiss()
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))

                ContextMenuItem(
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    title = if (isFavorite) "Eliminar de favoritos" else "Añadir a favoritos",
                    iconTint = if (isFavorite) AppleMusicRed else IosDarkTextPrimary,
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    }
                )
            }

            // AudioFX & Timer Actions Group
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                ContextMenuItem(
                    icon = Icons.Filled.Bedtime,
                    title = "Temporizador de apagado...",
                    onClick = {
                        onDismiss()
                        onOpenSleepTimer()
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))

                ContextMenuItem(
                    icon = Icons.Filled.Equalizer,
                    title = "Ecualizador DSP y Audio FX...",
                    onClick = {
                        onDismiss()
                        onOpenEqualizer()
                    }
                )
            }

            // Secondary Group (Navigation & Share)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                ContextMenuItem(
                    icon = Icons.Filled.Person,
                    title = "Ver artista: ${track.artist}",
                    onClick = {
                        onViewArtist()
                        onDismiss()
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))

                ContextMenuItem(
                    icon = Icons.Filled.Album,
                    title = "Ver álbum: ${track.album.ifEmpty { "Desconocido" }}",
                    onClick = {
                        onViewAlbum()
                        onDismiss()
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))

                ContextMenuItem(
                    icon = Icons.Filled.Share,
                    title = "Compartir canción",
                    onClick = {
                        shareTrack(context, track)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    title: String,
    iconTint: Color = IosDarkTextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun shareTrack(context: Context, track: Track) {
    val shareUrl = if (track.sourceType == AudioSourceType.YOUTUBE) {
        "https://www.youtube.com/watch?v=${track.id.removePrefix("yt_")}"
    } else {
        "Cypher Player - ${track.title} de ${track.artist}"
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${track.title} - ${track.artist}")
        putExtra(Intent.EXTRA_TEXT, "Escuchando '${track.title}' de ${track.artist} en Cypher:\n$shareUrl")
    }
    context.startActivity(Intent.createChooser(intent, "Compartir con"))
}

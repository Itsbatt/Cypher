package com.cypher.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.data.DownloadedPlaylist
import com.cypher.music.data.Playlist
import com.cypher.music.model.Track
import com.cypher.music.playback.MediaCacheManager
import com.cypher.music.ui.components.CypherTrackRow
import com.cypher.music.ui.components.PlaylistMosaicCover
import com.cypher.music.ui.theme.ResolvedThemeColors
import kotlinx.coroutines.launch

/**
 * Downloads screen.
 *
 * Songs that belong to a downloaded playlist are grouped inside that playlist card, and
 * only the songs downloaded on their own are listed as individual downloads. Failed
 * downloads keep their real reason and can be retried; nothing is deleted from disk here.
 */
@Composable
fun DownloadsSectionView(
    downloadedTracks: List<Track>,
    downloadedPlaylists: List<DownloadedPlaylist>,
    failedDownloads: Map<String, String>,
    knownTracks: List<Track>,
    currentTrackId: String?,
    favoriteTrackIds: Set<String>,
    themeColors: ResolvedThemeColors,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onRetryDownload: (Track) -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val trackProgress by MediaCacheManager.downloadProgressMap.collectAsState()
    val playlistProgress by MediaCacheManager.playlistProgressMap.collectAsState()

    val groupedIds = remember(downloadedPlaylists) {
        downloadedPlaylists.flatMap { entry -> entry.playlist.tracks.map { it.id } }.toSet()
    }
    val individualTracks = remember(downloadedTracks, groupedIds) {
        downloadedTracks.filter { it.id !in groupedIds }
    }
    val failedItems = remember(failedDownloads, knownTracks) {
        failedDownloads.map { (id, reason) -> knownTracks.firstOrNull { it.id == id } to reason }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.background),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBackIosNew,
                            contentDescription = "Atrás",
                            tint = themeColors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Música descargada",
                        color = themeColors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = {
                    scope.launch {
                        var exported = 0
                        downloadedTracks.forEach { track ->
                            if (MediaCacheManager.exportToMediaStore(context, track)) exported++
                        }
                        Toast.makeText(
                            context,
                            "Exportadas $exported de ${downloadedTracks.size} canciones a Música",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.IosShare,
                        contentDescription = "Exportar a Música",
                        tint = themeColors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(themeColors.cardBackground)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = (if (downloadedTracks.size == 1) "1 canción disponible sin conexión" else "${downloadedTracks.size} canciones disponibles sin conexión") +
                        if (downloadedPlaylists.isNotEmpty()) " · ${downloadedPlaylists.size} playlists" else "",
                    color = themeColors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        if (downloadedPlaylists.isNotEmpty()) {
            item {
                Text(
                    text = "PLAYLISTS DESCARGADAS",
                    color = themeColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
                )
            }
            items(downloadedPlaylists, key = { it.playlist.id }) { entry ->
                val progress = playlistProgress[entry.playlist.id]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPlaylist(entry.playlist) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaylistMosaicCover(
                        artworks = entry.playlist.mosaicArtworks,
                        customCoverUri = entry.playlist.coverUri,
                        size = 56.dp,
                        cornerRadius = 10.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.playlist.name,
                            color = themeColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (progress != null) {
                                "Descargando ${progress.current + 1}/${progress.total}"
                            } else {
                                entry.statusLabel
                            },
                            color = themeColors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (progress != null && progress.total > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (progress.current.toFloat() / progress.total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = themeColors.accent,
                                trackColor = themeColors.separator
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.DownloadDone,
                        contentDescription = null,
                        tint = if (entry.isComplete) themeColors.accent else themeColors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (failedItems.isNotEmpty()) {
            item {
                Text(
                    text = "DESCARGAS CON ERROR",
                    color = themeColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            items(failedItems, key = { it.first?.id ?: "desconocida" }) { entry ->
                val track = entry.first
                val reason = entry.second
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.title ?: "Canción no disponible",
                            color = themeColors.textPrimary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = reason,
                            color = themeColors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (track != null) {
                        IconButton(onClick = { onRetryDownload(track) }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Reintentar descarga",
                                tint = themeColors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "CANCIONES INDIVIDUALES",
                color = themeColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 2.dp)
            )
        }

        if (individualTracks.isEmpty()) {
            item {
                Text(
                    text = "No hay canciones descargadas fuera de tus playlists.",
                    color = themeColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }
        } else {
            items(individualTracks, key = { it.id }) { track ->
                CypherTrackRow(
                    track = track,
                    isPlaying = track.id == currentTrackId,
                    isFavorite = favoriteTrackIds.contains(track.id),
                    accentColor = themeColors.accent,
                    onClick = { onTrackClick(track, individualTracks) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onToggleFavorite = { onToggleFavorite(track) }
                )
            }
        }

        item {
            val downloading = trackProgress.size
            if (downloading > 0) {
                Text(
                    text = "$downloading descargas en curso",
                    color = themeColors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

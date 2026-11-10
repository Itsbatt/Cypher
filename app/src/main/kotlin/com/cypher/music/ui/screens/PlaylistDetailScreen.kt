package com.cypher.music.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.data.Playlist
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import com.cypher.music.playback.MediaCacheManager
import com.cypher.music.ui.components.CypherTrackRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.cypher.music.ui.components.ArtworkPlaceholder
import com.cypher.music.ui.components.PlaylistMosaicCover
import com.cypher.music.ui.theme.IosDarkBorder
import com.cypher.music.ui.theme.IosDarkCard
import com.cypher.music.ui.theme.IosDarkPill
import com.cypher.music.ui.theme.ResolvedThemeColors
import com.cypher.music.ui.utils.AppHaptics

@Composable
fun PlaylistDetailScreen(
        playlist: Playlist,
        currentTrackId: String?,
        favoriteTrackIds: Set<String>,
        themeColors: ResolvedThemeColors,
        bottomPadding: androidx.compose.ui.unit.Dp = 150.dp,
        onBack: () -> Unit,
        onPlayTrack: (Track, List<Track>) -> Unit,
        onPlayAll: (List<Track>) -> Unit,
        onShuffleAll: (List<Track>) -> Unit,
        onPlayNext: (Track) -> Unit,
        onAddToQueue: (Track) -> Unit,
        onAddToPlaylist: (Track) -> Unit,
        onToggleFavorite: (Track) -> Unit,
        onRemoveTrack: (playlistId: String, trackId: String) -> Unit,
        onReorderTracks: (playlistId: String, reorderedTracks: List<Track>) -> Unit,
        onUpdateDetails: (playlistId: String, name: String, description: String) -> Unit,
        onChangeCover: (playlistId: String, uri: android.net.Uri?) -> Unit = { _, _ -> },
        onDeletePlaylist: (playlistId: String) -> Unit,
        onViewArtist: (String) -> Unit,
        onViewAlbum: (String) -> Unit,
        onSharePlaylist: (Playlist) -> Unit = {},
        onDownloadPlaylist: (Playlist) -> Unit = {},
        onDeletePlaylistDownloads: (Playlist) -> Unit = {},
        onRetrySync: () -> Unit = {},
        modifier: Modifier = Modifier
) {
        val context = LocalContext.current
        // The picker hands back temporary read access only, so the view model copies the
        // image into app storage before saving it on the playlist.
        val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { picked -> picked?.let { onChangeCover(playlist.id, it) } }
        val playlistProgressMap by MediaCacheManager.playlistProgressMap.collectAsState()
        val downloadedTrackIds by MediaCacheManager.downloadedTrackIds.collectAsState()
        val isDownloadingPlaylist = playlistProgressMap.containsKey(playlist.id)
        val playlistProgress = playlistProgressMap[playlist.id]
        val isFullyDownloaded = remember(playlist.tracks, downloadedTrackIds) {
            MediaCacheManager.isPlaylistFullyDownloaded(context, playlist)
        }

        var isEditing by remember { mutableStateOf(false) }
        var showMenu by remember { mutableStateOf(false) }

        // Edit fields
        var editName by remember(playlist) { mutableStateOf(playlist.name) }
        var editDesc by remember(playlist) { mutableStateOf(playlist.description) }

        // Working copy for reordering in edit mode
        var workingTracks by remember(playlist.tracks) { mutableStateOf(playlist.tracks) }

        LazyColumn(
    modifier = modifier
            .fillMaxSize()
            .background(themeColors.background),
    contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
    // Top Navigation Bar
    item {
            Row(
        modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
            ) {
        IconButton(onClick = onBack) {
                Icon(
            imageVector = Icons.Filled.ArrowBackIosNew,
            contentDescription = "Volver",
            tint = themeColors.accent,
            modifier = Modifier.size(20.dp)
                )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditing) {
            TextButton(
              onClick = {
                isEditing = false
                if (editName != playlist.name || editDesc != playlist.description) {
                        onUpdateDetails(playlist.id, editName, editDesc)
                }
                if (workingTracks != playlist.tracks) {
                        onReorderTracks(playlist.id, workingTracks)
                }
              }
            ) {
              Text(
                text = "Listo",
                color = themeColors.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
              )
            }
                } else {
            IconButton(onClick = { onSharePlaylist(playlist) }) {
              Icon(
                imageVector = Icons.Filled.QrCode,
                contentDescription = "Compartir con QR o Código",
                tint = themeColors.accent,
                modifier = Modifier.size(22.dp)
              )
            }
            IconButton(onClick = { showMenu = true }) {
              Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = "Opciones",
                tint = themeColors.textPrimary,
                modifier = Modifier.size(22.dp)
              )
            }

            DropdownMenu(
              expanded = showMenu,
              onDismissRequest = { showMenu = false },
              modifier = Modifier
                .background(IosDarkCard)
                .border(1.dp, IosDarkBorder, RoundedCornerShape(12.dp))
            ) {
              DropdownMenuItem(
                text = { Text("Editar playlist", color = Color.White) },
                leadingIcon = {
                        Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = Color.White
                        )
                },
                onClick = {
                        showMenu = false
                        isEditing = true
                }
              )

              DropdownMenuItem(
                text = { Text("Compartir con QR / Código", color = Color.White) },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.QrCode,
                    contentDescription = null,
                    tint = themeColors.accent
                  )
                },
                onClick = {
                  showMenu = false
                  onSharePlaylist(playlist)
                }
              )

              DropdownMenuItem(
                text = { Text("Compartir enlace", color = Color.White) },
                leadingIcon = {
                        Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    tint = Color.White
                        )
                },
                onClick = {
                        showMenu = false
                        sharePlaylist(context, playlist)
                }
              )

              DropdownMenuItem(
                text = { Text(if (isFullyDownloaded) "Eliminar descargas de playlist" else "Descargar toda la playlist", color = Color.White) },
                leadingIcon = {
                        Icon(
                    imageVector = if (isFullyDownloaded) Icons.Filled.FileDownloadDone else Icons.Filled.Download,
                    contentDescription = null,
                    tint = if (isFullyDownloaded) Color(0xFF30D158) else themeColors.accent
                        )
                },
                onClick = {
                        showMenu = false
                        if (isFullyDownloaded) {
                            onDeletePlaylistDownloads(playlist)
                        } else {
                            onDownloadPlaylist(playlist)
                        }
                }
              )

              HorizontalDivider(color = IosDarkBorder)

              DropdownMenuItem(
                text = { Text("Eliminar playlist", color = Color(0xFFFF453A)) },
                leadingIcon = {
                        Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = Color(0xFFFF453A)
                        )
                },
                onClick = {
                        showMenu = false
                        onDeletePlaylist(playlist.id)
                        onBack()
                }
              )
            }
                }
        }
            }
    }

    // Header: Proportional Artwork + Title + Stats
    item {
            Column(
        modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
            ) {
        // Cover art with subtle shadow
        Box(
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(16.dp))
        ) {
                PlaylistMosaicCover(
            artworks = playlist.mosaicArtworks,
            customCoverUri = playlist.coverUri,
            size = 180.dp,
            cornerRadius = 16.dp
                )
        }

        if (isEditing) {
                Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
            TextButton(
              onClick = {
                AppHaptics.actionClick()
                coverPicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
              },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = themeColors.accent,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (playlist.coverUri.isNullOrBlank()) "Añadir portada" else "Cambiar portada",
                color = themeColors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
            }

            if (!playlist.coverUri.isNullOrBlank()) {
                TextButton(
                  onClick = {
                        AppHaptics.actionClick()
                        onChangeCover(playlist.id, null)
                  },
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                  Text(
                        text = "Quitar portada",
                        color = themeColors.textSecondary,
                        fontSize = 13.sp
                  )
                }
            }
                }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (isEditing) {
                OutlinedTextField(
            value = editName,
            onValueChange = { editName = it },
            label = { Text("Nombre") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedBorderColor = themeColors.accent,
              unfocusedBorderColor = IosDarkBorder,
              focusedLabelColor = themeColors.accent,
              unfocusedLabelColor = themeColors.textSecondary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
            value = editDesc,
            onValueChange = { editDesc = it },
            label = { Text("Descripción") },
            maxLines = 2,
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedBorderColor = themeColors.accent,
              unfocusedBorderColor = IosDarkBorder,
              focusedLabelColor = themeColors.accent,
              unfocusedLabelColor = themeColors.textSecondary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
                )
        } else {
                Text(
            text = playlist.name,
            color = themeColors.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
                )

                if (playlist.description.isNotBlank() && playlist.description != playlist.name) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = playlist.description,
              color = themeColors.textSecondary,
              fontSize = 13.sp,
              textAlign = TextAlign.Center
            )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
            text = "${playlist.formattedTrackCount} • ${playlist.formattedDuration}",
            color = themeColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
                )

                if (playlist.isSynced) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(themeColors.card)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (playlist.isSyncIncomplete) Color(0xFFFFB300) else Color(0xFF30D158)
                                )
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = if (playlist.isSyncIncomplete) "Sincronización incompleta"
                            else "Sincronizada con YouTube Music",
                            color = themeColors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (playlist.isSyncIncomplete) {
                            TextButton(
                                onClick = onRetrySync,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Reintentar",
                                    color = themeColors.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "Sincronización híbrida: las canciones que añadas aquí se conservan",
                        color = themeColors.textSecondary,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (isDownloadingPlaylist && playlistProgress != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Descargando ${playlistProgress.current} de ${playlistProgress.total} canciones...",
                                color = themeColors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val pct = if (playlistProgress.total > 0) (playlistProgress.current.toFloat() / playlistProgress.total.toFloat() * 100).toInt() else 0
                            Text(
                                text = "$pct%",
                                color = themeColors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (playlistProgress.currentTrackTitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = playlistProgress.currentTrackTitle,
                                color = themeColors.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val frac = if (playlistProgress.total > 0) playlistProgress.current.toFloat() / playlistProgress.total.toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = themeColors.accent,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                    }
                } else if (isFullyDownloaded && playlist.tracks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF30D158).copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DownloadDone,
                            contentDescription = null,
                            tint = Color(0xFF30D158),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Playlist descargada para escuchar sin conexión",
                            color = Color(0xFF30D158),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Action Bar: Solid Pill Play Button + Circular Shuffle & Add Buttons
        if (playlist.tracks.isNotEmpty() && !isEditing) {
                Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
                ) {
            // Play Button: Solid Pill Shape
            Button(
              onClick = {
                AppHaptics.actionClick()
                onPlayAll(playlist.tracks)
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = themeColors.accent,
                contentColor = Color.White
              ),
              shape = RoundedCornerShape(50),
              contentPadding = PaddingValues(horizontal = 8.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Reproducir",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
              )
            }

            // Shuffle Button: Solid Circular Button
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(IosDarkPill)
                .border(1.dp, IosDarkBorder, CircleShape)
                .clickable {
                        AppHaptics.actionClick()
                        onShuffleAll(playlist.tracks)
                },
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = "Aleatorio",
                tint = themeColors.accent,
                modifier = Modifier.size(20.dp)
              )
            }

            // Add Button: Solid Circular Button
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(IosDarkPill)
                .border(1.dp, IosDarkBorder, CircleShape)
                .clickable {
                        AppHaptics.actionClick()
                        isEditing = true
                },
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Añadir / Editar",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
              )
            }

            // Download Playlist Button: Solid Circular Button
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isFullyDownloaded) Color(0xFF30D158).copy(alpha = 0.15f)
                    else if (isDownloadingPlaylist) themeColors.accent.copy(alpha = 0.15f)
                    else IosDarkPill
                )
                .border(
                    1.dp,
                    if (isFullyDownloaded) Color(0xFF30D158).copy(alpha = 0.4f)
                    else if (isDownloadingPlaylist) themeColors.accent.copy(alpha = 0.5f)
                    else IosDarkBorder,
                    CircleShape
                )
                .clickable {
                    AppHaptics.actionClick()
                    if (isFullyDownloaded) {
                        onDeletePlaylistDownloads(playlist)
                    } else if (!isDownloadingPlaylist) {
                        onDownloadPlaylist(playlist)
                    }
                },
              contentAlignment = Alignment.Center
            ) {
              if (isDownloadingPlaylist) {
                  CircularProgressIndicator(
                      strokeWidth = 2.5.dp,
                      color = themeColors.accent,
                      modifier = Modifier.size(22.dp)
                  )
              } else {
                  Icon(
                      imageVector = if (isFullyDownloaded) Icons.Filled.FileDownloadDone else Icons.Filled.Download,
                      contentDescription = if (isFullyDownloaded) "Playlist descargada" else "Descargar playlist",
                      tint = if (isFullyDownloaded) Color(0xFF30D158) else Color.White,
                      modifier = Modifier.size(20.dp)
                  )
              }
            }
                }
        }

        Spacer(modifier = Modifier.height(16.dp))
            }
    }

    // Empty Playlist State
    if (playlist.tracks.isEmpty()) {
            item {
        Box(
                modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
                contentAlignment = Alignment.Center
        ) {
                Text(
            text = "Esta playlist está vacía.\nExplora o busca canciones y pulsa «Añadir a playlist» en su menú de opciones.",
            color = themeColors.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
                )
        }
            }
    }

    // Song List or Edit Mode
    if (isEditing) {
            itemsIndexed(workingTracks) { index, track ->
        Row(
                modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(IosDarkPill)
            .border(1.dp, IosDarkBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                // Delete button
                IconButton(
            onClick = {
              val updated = workingTracks.toMutableList().apply { removeAt(index) }
              workingTracks = updated
              onRemoveTrack(playlist.id, track.id)
            },
            modifier = Modifier.size(32.dp)
                ) {
            Icon(
              imageVector = Icons.Filled.Delete,
              contentDescription = "Eliminar de playlist",
              tint = Color(0xFFFF453A),
              modifier = Modifier.size(20.dp)
            )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(46.dp)
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

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = themeColors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Move Up
                if (index > 0) {
            IconButton(
              onClick = {
                AppHaptics.reorderTick()
                val list = workingTracks.toMutableList()
                val item = list.removeAt(index)
                list.add(index - 1, item)
                workingTracks = list
              },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = "Subir",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
            }
                }

                // Move Down
                if (index < workingTracks.size - 1) {
            IconButton(
              onClick = {
                AppHaptics.reorderTick()
                val list = workingTracks.toMutableList()
                val item = list.removeAt(index)
                list.add(index + 1, item)
                workingTracks = list
              },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.ArrowDownward,
                contentDescription = "Bajar",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
            }
                }
        }
            }
    } else {
            itemsIndexed(playlist.tracks) { index, track ->
        CypherTrackRow(
                track = track,
                isPlaying = track.id == currentTrackId,
                isFavorite = favoriteTrackIds.contains(track.id),
                accentColor = themeColors.accent,
                onClick = {
            val queue = playlist.tracks.drop(index) + playlist.tracks.take(index)
            onPlayTrack(track, queue)
                },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onToggleFavorite = { onToggleFavorite(track) },
                onViewArtist = { onViewArtist(track.artist) },
                onViewAlbum = { onViewAlbum(track.album) }
        )
            }
    }
        }
}

private fun sharePlaylist(context: Context, playlist: Playlist) {
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine("🎵 Playlist: ${playlist.name}")
        if (playlist.description.isNotBlank()) {
    stringBuilder.appendLine(playlist.description)
        }
        stringBuilder.appendLine("${playlist.formattedTrackCount}:")
        stringBuilder.appendLine()

        playlist.tracks.forEachIndexed { i, t ->
    if (t.sourceType == AudioSourceType.YOUTUBE) {
            stringBuilder.appendLine("${i + 1}. ${t.title} - ${t.artist} (https://www.youtube.com/watch?v=${t.streamUrl})")
    } else {
            stringBuilder.appendLine("${i + 1}. ${t.title} - ${t.artist}")
    }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_SUBJECT, playlist.name)
    putExtra(Intent.EXTRA_TEXT, stringBuilder.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Compartir playlist"))
}

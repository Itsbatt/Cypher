package com.cypher.music.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.cypher.music.data.ArtistNormalizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cypher.music.data.Playlist
import com.cypher.music.data.ThemePreferences
import com.cypher.music.data.toTrackCountString
import com.cypher.music.model.Track
import com.cypher.music.playback.MediaCacheManager
import com.cypher.music.ui.components.CypherSectionHeader
import com.cypher.music.ui.components.CypherTrackRow
import com.cypher.music.ui.components.ArtworkPlaceholder
import com.cypher.music.ui.components.PlaylistMosaicCover
import com.cypher.music.ui.theme.ResolvedThemeColors
import kotlinx.coroutines.launch

data class LibraryMenuItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val count: Int? = null
)

@Composable
fun LibraryScreen(
    localTracks: List<Track>,
    favoriteTracks: List<Track>,
    favoriteTrackIds: Set<String>,
    downloadedTracks: List<Track> = emptyList(),
    playlists: List<Playlist>,
    currentTrackId: String?,
    activeSection: String?,
    selectedArtist: String?,
    selectedAlbum: String?,
    selectedPlaylist: Playlist?,
    themePrefs: ThemePreferences = ThemePreferences(),
    themeColors: ResolvedThemeColors,
    bottomPadding: Dp = 150.dp,
    onSectionChange: (String?) -> Unit,
    onArtistSelect: (String?) -> Unit,
    onAlbumSelect: (String?) -> Unit,
    onPlaylistSelect: (Playlist?) -> Unit,
    onCreatePlaylist: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onRemoveTrackFromPlaylist: (playlistId: String, trackId: String) -> Unit,
    onReorderPlaylistTracks: (playlistId: String, reorderedTracks: List<Track>) -> Unit,
    onUpdatePlaylistDetails: (playlistId: String, name: String, description: String) -> Unit,
    onDeletePlaylist: (playlistId: String) -> Unit,
    onRefreshStorage: () -> Unit,
    onOpenImportScanner: () -> Unit = {},
    onSharePlaylist: (Playlist) -> Unit = {},
    onDownloadPlaylist: (Playlist) -> Unit = {},
    onDeletePlaylistDownloads: (Playlist) -> Unit = {},
    onRetryPlaylistSync: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allLibraryTracks = remember(localTracks, favoriteTracks) {
        (favoriteTracks + localTracks).distinctBy { it.id }
    }

    AnimatedContent(
        targetState = Triple(activeSection, selectedPlaylist != null, selectedArtist to selectedAlbum),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "LibraryNavigation"
    ) { (section, hasPlaylist, artistAndAlbum) ->
        val (artist, album) = artistAndAlbum

        when {
            // Drill-down into Playlist Detail
            selectedPlaylist != null -> {
                PlaylistDetailScreen(
                    playlist = selectedPlaylist,
                    currentTrackId = currentTrackId,
                    favoriteTrackIds = favoriteTrackIds,
                    themeColors = themeColors,
                    bottomPadding = bottomPadding,
                    onBack = { onPlaylistSelect(null) },
                    onSharePlaylist = onSharePlaylist,
                    onPlayTrack = onTrackClick,
                    onPlayAll = { tracks -> if (tracks.isNotEmpty()) onTrackClick(tracks.first(), tracks) },
                    onShuffleAll = { tracks ->
                        val shuffled = tracks.shuffled()
                        if (shuffled.isNotEmpty()) onTrackClick(shuffled.first(), shuffled)
                    },
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleFavorite = onToggleFavorite,
                    onRemoveTrack = onRemoveTrackFromPlaylist,
                    onReorderTracks = onReorderPlaylistTracks,
                    onUpdateDetails = onUpdatePlaylistDetails,
                    onDeletePlaylist = onDeletePlaylist,
                    onViewArtist = { artistName ->
                        onPlaylistSelect(null)
                        onSectionChange("artistas")
                        onArtistSelect(artistName)
                    },
                    onViewAlbum = { albumName ->
                        onPlaylistSelect(null)
                        onSectionChange("albumes")
                        onAlbumSelect(albumName)
                    },
                    onDownloadPlaylist = onDownloadPlaylist,
                    onDeletePlaylistDownloads = onDeletePlaylistDownloads,
                    onRetrySync = { onRetryPlaylistSync(selectedPlaylist.id) }
                )
            }

            // Drill-down into specific Artist
            artist != null -> {
                val artistTracks = allLibraryTracks.filter { ArtistNormalizer.areSameArtist(it.artist, artist) }
                ArtistDetailView(
                    artistName = artist,
                    tracks = artistTracks,
                    currentTrackId = currentTrackId,
                    favoriteTrackIds = favoriteTrackIds,
                    themeColors = themeColors,
                    bottomPadding = bottomPadding,
                    onBack = { onArtistSelect(null) },
                    onTrackClick = onTrackClick,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleFavorite = onToggleFavorite
                )
            }

            // Drill-down into specific Album
            album != null -> {
                val albumTracks = allLibraryTracks.filter { it.album.equals(album, ignoreCase = true) }
                AlbumDetailView(
                    albumName = album,
                    tracks = albumTracks,
                    currentTrackId = currentTrackId,
                    favoriteTrackIds = favoriteTrackIds,
                    themeColors = themeColors,
                    bottomPadding = bottomPadding,
                    onBack = { onAlbumSelect(null) },
                    onTrackClick = onTrackClick,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleFavorite = onToggleFavorite
                )
            }

            // Sub-view: Playlists
            section == "playlists" -> {
                PlaylistsFullListView(
                    playlists = playlists,
                    themeColors = themeColors,
                    bottomPadding = bottomPadding,
                    onBack = { onSectionChange(null) },
                    onCreatePlaylist = onCreatePlaylist,
                    onOpenImportScanner = onOpenImportScanner,
                    onSelectPlaylist = onPlaylistSelect
                )
            }

            // Sub-view: Artistas
            section == "artistas" -> {
                ArtistsListView(
                    tracks = allLibraryTracks,
                    themeColors = themeColors,
                    bottomPadding = bottomPadding,
                    onBack = { onSectionChange(null) },
                    onSelectArtist = { onArtistSelect(it) }
                )
            }

            // Sub-view: Álbumes
            section == "albumes" -> {
                AlbumsGridView(
                    tracks = allLibraryTracks,
                    themeColors = themeColors,
                    bottomPadding = bottomPadding,
                    onBack = { onSectionChange(null) },
                    onSelectAlbum = { onAlbumSelect(it) }
                )
            }

            // Sub-view: Canciones
            section == "canciones" -> {
                TracksFullListView(
                    title = "Canciones",
                    tracks = allLibraryTracks,
                    currentTrackId = currentTrackId,
                    favoriteTrackIds = favoriteTrackIds,
                    themeColors = themeColors,
                    bottomPadding = bottomPadding,
                    onBack = { onSectionChange(null) },
                    onTrackClick = onTrackClick,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleFavorite = onToggleFavorite
                )
            }

            // Sub-view: Favoritos
            section == "favoritos" -> {
                TracksFullListView(
                    title = "Favoritos",
                    tracks = favoriteTracks,
                    currentTrackId = currentTrackId,
                    favoriteTrackIds = favoriteTrackIds,
                    themeColors = themeColors,
                    bottomPadding = bottomPadding,
                    emptyMessage = "No tienes canciones marcadas como favoritas todavía. Toca el icono de corazón en cualquier canción para añadirla.",
                    onBack = { onSectionChange(null) },
                    onTrackClick = onTrackClick,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleFavorite = onToggleFavorite
                )
            }

            // Sub-view: Música descargada (Offline vault)
            section == "descargas" -> {
                val exportContext = LocalContext.current
                val exportScope = rememberCoroutineScope()
                TracksFullListView(
                    title = "Música descargada",
                    tracks = downloadedTracks,
                    currentTrackId = currentTrackId,
                    favoriteTrackIds = favoriteTrackIds,
                    themeColors = themeColors,
                    bottomPadding = bottomPadding,
                    emptyMessage = "No tienes música descargada aún. Usa 'Descargar canción' en el menú (•••) de cualquier canción para escucharla sin conexión.",
                    onExport = {
                        exportScope.launch {
                            val total = downloadedTracks.count {
                                MediaCacheManager.exportToMediaStore(exportContext, it)
                            }
                            Toast.makeText(
                                exportContext,
                                "Exportadas $total de ${downloadedTracks.size} canciones a Música",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onBack = { onSectionChange(null) },
                    onTrackClick = onTrackClick,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleFavorite = onToggleFavorite
                )
            }

            // Sub-view: Archivos locales (Música local)
            section == "archivos_locales" -> {
                TracksFullListView(
                    title = "Música local",
                    tracks = localTracks,
                    currentTrackId = currentTrackId,
                    favoriteTrackIds = favoriteTrackIds,
                    themeColors = themeColors,
                    bottomPadding = bottomPadding,
                    emptyMessage = "No se encontraron archivos de audio en el almacenamiento local.",
                    onBack = { onSectionChange(null) },
                    onTrackClick = onTrackClick,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleFavorite = onToggleFavorite
                )
            }

            // Default Main Library Screen
            else -> {
                MainLibraryView(
                    localTracks = localTracks,
                    favoriteTracks = favoriteTracks,
                    favoriteTrackIds = favoriteTrackIds,
                    downloadedTracks = downloadedTracks,
                    playlists = playlists,
                    currentTrackId = currentTrackId,
                    themeColors = themeColors,
                    bottomPadding = bottomPadding,
                    onSectionClick = { onSectionChange(it) },
                    onPlaylistClick = onPlaylistSelect,
                    onCreatePlaylist = onCreatePlaylist,
                    onTrackClick = onTrackClick,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleFavorite = onToggleFavorite,
                    onRefreshStorage = onRefreshStorage,
                    onOpenImportScanner = onOpenImportScanner,
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
private fun MainLibraryView(
    localTracks: List<Track>,
    favoriteTracks: List<Track>,
    favoriteTrackIds: Set<String>,
    downloadedTracks: List<Track>,
    playlists: List<Playlist>,
    currentTrackId: String?,
    themeColors: ResolvedThemeColors,
    bottomPadding: Dp,
    onSectionClick: (String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onRefreshStorage: () -> Unit,
    onOpenImportScanner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allTracks = remember(localTracks, favoriteTracks) { (favoriteTracks + localTracks).distinctBy { it.id } }

    val menuItems = listOf(
        LibraryMenuItem("playlists", "Playlists", Icons.AutoMirrored.Filled.QueueMusic, playlists.size),
        LibraryMenuItem("albumes", "Álbumes", Icons.Filled.Album, allTracks.map { it.album }.filter { it.isNotBlank() }.distinct().size),
        LibraryMenuItem("artistas", "Artistas", Icons.Filled.Person, allTracks.map { ArtistNormalizer.normalize(it.artist) }.distinct().size),
        LibraryMenuItem("canciones", "Canciones", Icons.Filled.MusicNote, allTracks.size),
        LibraryMenuItem("favoritos", "Favoritos", Icons.Filled.Favorite, favoriteTracks.size),
        LibraryMenuItem("descargas", "Música descargada", Icons.Filled.DownloadDone, downloadedTracks.size),
        LibraryMenuItem("archivos_locales", "Música local", Icons.Filled.Folder, localTracks.size)
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        // iOS Large Title Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Biblioteca",
                    color = themeColors.textPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenImportScanner) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = "Escanear QR de playlist",
                            tint = themeColors.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onRefreshStorage) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Actualizar almacenamiento",
                            tint = themeColors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Library Navigation List
        items(menuItems) { item ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSectionClick(item.id) }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = themeColors.accent,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = item.title,
                        color = themeColors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )

                    if (item.count != null && item.count > 0) {
                        Text(
                            text = item.count.toString(),
                            color = themeColors.textSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = themeColors.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                HorizontalDivider(
                    color = themeColors.separator,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 60.dp)
                )
            }
        }

        // Section: Tus Playlists (Quick access carousel)
        if (playlists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tus playlists",
                        color = themeColors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Escanear QR",
                            color = themeColors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onOpenImportScanner() }
                        )
                        Text(
                            text = "+ Nueva",
                            color = themeColors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onCreatePlaylist() }
                        )
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(playlists) { playlist ->
                        Column(
                            modifier = Modifier
                                .width(115.dp)
                                .clickable { onPlaylistClick(playlist) }
                        ) {
                            PlaylistMosaicCover(
                                artworks = playlist.mosaicArtworks,
                                customCoverUri = playlist.coverUri,
                                size = 115.dp,
                                cornerRadius = 12.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = playlist.name,
                                color = themeColors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = playlist.formattedTrackCount,
                                color = themeColors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Section: Añadido recientemente
        item {
            Spacer(modifier = Modifier.height(20.dp))
            CypherSectionHeader(
                title = "Añadido recientemente",
                subtitle = "Tu música",
                showDivider = false
            )
        }

        val recentTracks = allTracks.take(15)
        items(recentTracks) { track ->
            CypherTrackRow(
                track = track,
                isPlaying = track.id == currentTrackId,
                isFavorite = favoriteTrackIds.contains(track.id),
                accentColor = themeColors.accent,
                onClick = { onTrackClick(track, recentTracks) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onToggleFavorite = { onToggleFavorite(track) }
            )
        }
    }
}

@Composable
private fun PlaylistsFullListView(
    playlists: List<Playlist>,
    themeColors: ResolvedThemeColors,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenImportScanner: () -> Unit = {},
    onSelectPlaylist: (Playlist) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("updated") } // "updated", "name", "count"
    var showSortMenu by remember { mutableStateOf(false) }

    val filtered = remember(playlists, searchQuery, sortBy) {
        var list = playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }
        list = when (sortBy) {
            "name" -> list.sortedBy { it.name.lowercase() }
            "count" -> list.sortedByDescending { it.trackCount }
            else -> list.sortedByDescending { it.updatedAt }
        }
        list
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
                        text = "Playlists",
                        color = themeColors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Ordenar",
                            tint = themeColors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(Color(0xFF222226))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Recientes", color = Color.White) },
                            onClick = { sortBy = "updated"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Título (A-Z)", color = Color.White) },
                            onClick = { sortBy = "name"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Número de canciones", color = Color.White) },
                            onClick = { sortBy = "count"; showSortMenu = false }
                        )
                    }

                    IconButton(onClick = onOpenImportScanner) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = "Escanear QR",
                            tint = themeColors.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onCreatePlaylist) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Nueva playlist",
                            tint = themeColors.accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Search bar
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filtrar playlists...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = themeColors.accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = themeColors.accent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No se encontraron playlists para «$searchQuery»." else "No tienes playlists todavía.\nPulsa el icono «+» para crear una.",
                        color = themeColors.textSecondary,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(filtered) { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPlaylist(playlist) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaylistMosaicCover(
                        artworks = playlist.mosaicArtworks,
                        customCoverUri = playlist.coverUri,
                        size = 56.dp,
                        cornerRadius = 10.dp
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            color = themeColors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${playlist.formattedTrackCount} • ${playlist.formattedDuration}",
                            color = themeColors.textSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = themeColors.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                HorizontalDivider(
                    color = themeColors.separator,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 86.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtistsListView(
    tracks: List<Track>,
    themeColors: ResolvedThemeColors,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onSelectArtist: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortByCount by remember { mutableStateOf(false) }

    val artists = remember(tracks, searchQuery, sortByCount) {
        val grouped = tracks.groupBy { ArtistNormalizer.normalize(it.artist) }.toList()
        val filtered = if (searchQuery.isBlank()) grouped else grouped.filter { it.first.contains(searchQuery, ignoreCase = true) }
        if (sortByCount) {
            filtered.sortedByDescending { it.second.size }
        } else {
            filtered.sortedBy { it.first.lowercase() }
        }
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
                        text = "Artistas",
                        color = themeColors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (sortByCount) "Ordenar A-Z" else "Por canciones",
                    color = themeColors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { sortByCount = !sortByCount }
                        .padding(end = 12.dp)
                )
            }
        }

        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filtrar artistas...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = themeColors.accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = themeColors.accent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                )
            }
        }

        items(artists) { (artist, artistTracks) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectArtist(artist) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = artistTracks.firstOrNull()?.displayArtworkUrl,
                    contentDescription = artist,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(themeColors.card)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist,
                        color = themeColors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artistTracks.size.toTrackCountString(),
                        color = themeColors.textSecondary,
                        fontSize = 13.sp
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = themeColors.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
            HorizontalDivider(
                color = themeColors.separator,
                thickness = 0.5.dp,
                modifier = Modifier.padding(start = 88.dp)
            )
        }
    }
}

@Composable
private fun AlbumsGridView(
    tracks: List<Track>,
    themeColors: ResolvedThemeColors,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onSelectAlbum: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val albums = remember(tracks, searchQuery) {
        val grouped = tracks.filter { it.album.isNotBlank() }
            .groupBy { it.album }
            .toList()
        if (searchQuery.isBlank()) grouped else grouped.filter { it.first.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBackIosNew,
                    contentDescription = "Atrás",
                    tint = themeColors.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "Álbumes",
                color = themeColors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filtrar álbumes...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = themeColors.accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    cursorColor = themeColors.accent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            )
        }

        LazyVerticalGrid(
            // Two columns on a phone, four or more on a tablet.
            columns = GridCells.Adaptive(minSize = 168.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(albums) { (album, albumTracks) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectAlbum(album) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        ArtworkPlaceholder(modifier = Modifier.fillMaxSize())
                        if (!albumTracks.firstOrNull()?.displayArtworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = albumTracks.firstOrNull()?.displayArtworkUrl,
                                contentDescription = album,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = album,
                        color = themeColors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = albumTracks.firstOrNull()?.artist ?: "",
                        color = themeColors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TracksFullListView(
    title: String,
    tracks: List<Track>,
    currentTrackId: String?,
    favoriteTrackIds: Set<String>,
    themeColors: ResolvedThemeColors,
    bottomPadding: Dp,
    emptyMessage: String = "No hay canciones disponibles.",
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onExport: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("title") } // "title", "artist", "duration"
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredTracks = remember(tracks, searchQuery, sortBy) {
        var list = if (searchQuery.isBlank()) tracks else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true)
        }
        list = when (sortBy) {
            "artist" -> list.sortedBy { it.artist.lowercase() }
            "duration" -> list.sortedByDescending { it.durationMs }
            else -> list.sortedBy { it.title.lowercase() }
        }
        list
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
                        text = title,
                        color = themeColors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (onExport != null) {
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Filled.IosShare,
                            contentDescription = "Exportar a Música",
                            tint = themeColors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Ordenar",
                        tint = themeColors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(Color(0xFF222226))
                ) {
                    DropdownMenuItem(
                        text = { Text("Por título (A-Z)", color = Color.White) },
                        onClick = { sortBy = "title"; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Por artista", color = Color.White) },
                        onClick = { sortBy = "artist"; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Por duración", color = Color.White) },
                        onClick = { sortBy = "duration"; showSortMenu = false }
                    )
                }
            }
        }

        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filtrar canciones...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = themeColors.accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = themeColors.accent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                )
            }
        }

        if (filteredTracks.isNotEmpty()) {
            item {
                PlayShuffleHeroButtons(
                    themeColors = themeColors,
                    onPlayAll = { onTrackClick(filteredTracks.first(), filteredTracks) },
                    onShuffle = { onTrackClick(filteredTracks.shuffled().first(), filteredTracks.shuffled()) }
                )
            }

            items(filteredTracks) { track ->
                CypherTrackRow(
                    track = track,
                    isPlaying = track.id == currentTrackId,
                    isFavorite = favoriteTrackIds.contains(track.id),
                    accentColor = themeColors.accent,
                    onClick = { onTrackClick(track, filteredTracks) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onToggleFavorite = { onToggleFavorite(track) }
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No se encontraron canciones para «$searchQuery»." else emptyMessage,
                        color = themeColors.textSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistDetailView(
    artistName: String,
    tracks: List<Track>,
    currentTrackId: String?,
    favoriteTrackIds: Set<String>,
    themeColors: ResolvedThemeColors,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.background),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        item {
            SubHeader(title = artistName, themeColors = themeColors, onBack = onBack)
        }

        // Hero Artist Avatar
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = tracks.firstOrNull()?.displayArtworkUrl,
                    contentDescription = artistName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(themeColors.card)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${tracks.size.toTrackCountString()} en biblioteca",
                    color = themeColors.textSecondary,
                    fontSize = 14.sp
                )
            }

            PlayShuffleHeroButtons(
                themeColors = themeColors,
                onPlayAll = { if (tracks.isNotEmpty()) onTrackClick(tracks.first(), tracks) },
                onShuffle = { if (tracks.isNotEmpty()) onTrackClick(tracks.shuffled().first(), tracks.shuffled()) }
            )
        }

        items(tracks) { track ->
            CypherTrackRow(
                track = track,
                isPlaying = track.id == currentTrackId,
                isFavorite = favoriteTrackIds.contains(track.id),
                accentColor = themeColors.accent,
                onClick = { onTrackClick(track, tracks) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onToggleFavorite = { onToggleFavorite(track) }
            )
        }
    }
}

@Composable
private fun AlbumDetailView(
    albumName: String,
    tracks: List<Track>,
    currentTrackId: String?,
    favoriteTrackIds: Set<String>,
    themeColors: ResolvedThemeColors,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit
) {
    val artist = tracks.firstOrNull()?.artist ?: ""

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.background),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        item {
            SubHeader(title = albumName, themeColors = themeColors, onBack = onBack)
        }

        // Hero Album Artwork
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ArtworkPlaceholder(modifier = Modifier.fillMaxSize())
                    if (!tracks.firstOrNull()?.displayArtworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = tracks.firstOrNull()?.displayArtworkUrl,
                            contentDescription = albumName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = artist,
                    color = themeColors.accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = tracks.size.toTrackCountString(),
                    color = themeColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            PlayShuffleHeroButtons(
                themeColors = themeColors,
                onPlayAll = { if (tracks.isNotEmpty()) onTrackClick(tracks.first(), tracks) },
                onShuffle = { if (tracks.isNotEmpty()) onTrackClick(tracks.shuffled().first(), tracks.shuffled()) }
            )
        }

        items(tracks) { track ->
            CypherTrackRow(
                track = track,
                isPlaying = track.id == currentTrackId,
                isFavorite = favoriteTrackIds.contains(track.id),
                accentColor = themeColors.accent,
                onClick = { onTrackClick(track, tracks) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onToggleFavorite = { onToggleFavorite(track) }
            )
        }
    }
}

@Composable
private fun SubHeader(
    title: String,
    themeColors: ResolvedThemeColors,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Filled.ArrowBackIosNew,
                contentDescription = "Atrás",
                tint = themeColors.accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = title,
            color = themeColors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun PlayShuffleHeroButtons(
    themeColors: ResolvedThemeColors,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Button(
            onClick = onPlayAll,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeColors.card),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = themeColors.accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Reproducir",
                color = themeColors.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }

        Button(
            onClick = onShuffle,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeColors.card),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = null,
                tint = themeColors.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Aleatorio",
                color = themeColors.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

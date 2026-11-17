package com.cypher.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.LibrarySection
import com.cypher.desktop.app.Screen
import com.cypher.desktop.app.SortMode
import com.cypher.desktop.app.UiState
import com.cypher.desktop.core.AlbumGroup
import com.cypher.desktop.core.ArtistGroup
import com.cypher.desktop.core.LibraryData
import com.cypher.desktop.core.Playlist
import com.cypher.desktop.core.Track

/* ------------------------------------------------------------------- inicio */

@Composable
private fun HomeViewLegacy(ui: UiState, accent: Color, state: AppState) {
    val data = ui.data
    if (data.tracks.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.LibraryMusic,
            title = "Tu biblioteca empieza aquí",
            body = "Elige una carpeta con música y Cypher leerá las etiquetas de cada archivo. " +
                "Todo se queda en este equipo.",
            accent = accent,
            actionLabel = "Elegir carpeta",
            onAction = { pickFolder(state) }
        )
        return
    }
    val totalMs = data.stats.sumOf { it.listenDurationMs }
    val plays = data.stats.sumOf { it.playCount }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth()
                    .background(CypherColors.Card, RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Hola, ${data.profile.displayName}", color = CypherColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${data.tracks.size} pistas · ${data.albums.size} álbumes · ${data.artists.size} artistas",
                        color = CypherColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    if (totalMs > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatDuration(totalMs)} escuchados · $plays reproducciones",
                            color = CypherColors.TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Reproducir todo", accent) {
                            state.playQueue(idsFor(data, data.tracks))
                        }
                        TextButton(onClick = { state.select(Screen.LIBRARY) }) {
                            Text("Abrir biblioteca", color = accent, fontSize = 13.sp)
                        }
                        TextButton(onClick = { state.select(Screen.ONLINE) }) {
                            Icon(Icons.Rounded.Public, null, tint = accent, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("YouTube Music", color = accent, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        item { SectionHeader("RECIENTE", accent) }
        val recent = data.recent.mapNotNull { viewOf(data, it) }.take(4)
            .ifEmpty { data.tracks.take(4).mapNotNull { viewOf(data, it.id) } }
        items(recent, key = { "recent-${it.id}" }) { view ->
            TrackRow(view, data, accent, state, ui.currentTrackId)
        }
        item {
            SectionHeader("TUS PLAYLISTS", accent) {
                TextButton(onClick = { state.select(Screen.PLAYLISTS) }) {
                    Text("Ver todas", color = accent, fontSize = 11.sp)
                }
            }
        }
        if (data.playlists.isEmpty()) {
            item { Hint("Todavía no hay playlists. Crea la primera desde Playlists.") }
        } else {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(data.playlists.take(8), key = { "pl-${it.id}" }) { playlist ->
                        PlaylistShelfCard(playlist, data, accent) { state.openPlaylist(playlist.id) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(26.dp)) }
    }
}

/* --------------------------------------------------------------- biblioteca */

@Composable
fun LibraryView(ui: UiState, accent: Color, state: AppState) {
    val data = ui.data
    if (data.tracks.isEmpty()) {
        EmptyState(
            Icons.Rounded.Search,
            "Biblioteca vacía",
            "Elige la carpeta donde guardas tu música. Después podrás ordenar por álbum, artista o duración.",
            accent,
            "Elegir carpeta"
        ) { pickFolder(state) }
        return
    }
    val album = ui.albumFilter
    val artist = ui.artistFilter
    if (album != null) {
        val tracks = data.tracks.filter { it.album.ifBlank { "Sin álbum" } == album }
        CollectionHeader("Álbum", album, tracks, accent, state) { state.clearFilter() }
        return
    }
    if (artist != null) {
        val tracks = data.tracks.filter { it.artist == artist }
        CollectionHeader("Artista", artist, tracks, accent, state) { state.clearFilter() }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Álbumes", ui.librarySection == LibrarySection.ALBUMS, accent) { state.setLibrarySection(LibrarySection.ALBUMS) }
                Pill("Artistas", ui.librarySection == LibrarySection.ARTISTS, accent) { state.setLibrarySection(LibrarySection.ARTISTS) }
                Pill("Canciones", ui.librarySection == LibrarySection.SONGS, accent) { state.setLibrarySection(LibrarySection.SONGS) }
            }
            Spacer(Modifier.weight(1f))
            SortMenu(ui.sort, accent, state::setSort)
            TextButton(onClick = { pickFolder(state) }) {
                Icon(Icons.Rounded.FolderOpen, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Carpeta", color = accent, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f)) {
            when (ui.librarySection) {
                LibrarySection.ALBUMS -> AlbumGrid(data, ui.query, accent, state)
                LibrarySection.ARTISTS -> ArtistList(data, ui.query, accent, state)
                LibrarySection.SONGS -> SongList(data, ui, accent, state)
            }
        }
    }
}

@Composable
private fun SongList(data: LibraryData, ui: UiState, accent: Color, state: AppState) {
    val tracks = sortTracks(data.search(ui.query), ui.sort)
    Column(Modifier.fillMaxSize()) {
        Text("${tracks.size} canciones", color = CypherColors.TextTertiary, fontSize = 11.sp)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(tracks, key = { it.id }) { track ->
                viewOf(data, track.id)?.let { TrackRow(it, data, accent, state, ui.currentTrackId) }
            }
        }
    }
}

@Composable
private fun AlbumGrid(data: LibraryData, query: String, accent: Color, state: AppState) {
    val albums = data.albums.filter {
        query.isBlank() || it.name.lowercase().contains(query.lowercase()) || it.artist.lowercase().contains(query.lowercase())
    }
    if (albums.isEmpty()) {
        EmptyState(Icons.Rounded.LibraryMusic, "Sin álbumes", "La búsqueda no coincide con ningún álbum.", accent)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItems(albums, key = { it.name }) { group ->
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(CypherColors.Card)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                    .hoverable(interaction)
                    .clickable { state.openAlbum(group.name) }
                    .padding(12.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(132.dp)
                        .background(CypherColors.Card, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    viewOf(data, group.tracks.first().id)?.let { Artwork(it, 78, accent) }
                    if (hovered) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.size(44.dp).background(accent, CircleShape)
                                    .clickable { state.playQueue(idsFor(data, group.tracks)) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.PlayArrow, "Reproducir álbum", tint = Color(0xFF14101A), modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(group.name, color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("${group.artist} · ${group.tracks.size} pistas", color = CypherColors.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistList(data: LibraryData, query: String, accent: Color, state: AppState) {
    val artists = data.artists.filter { query.isBlank() || it.name.lowercase().contains(query.lowercase()) }
    if (artists.isEmpty()) {
        EmptyState(Icons.Rounded.Person, "Sin artistas", "La búsqueda no coincide con ningún artista.", accent)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(artists, key = { it.name }) { group ->
            Row(
                Modifier.fillMaxWidth().height(58.dp).clickable { state.openArtist(group.name) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.18f), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Text(initials(group.name), color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.name, color = CypherColors.TextPrimary, fontSize = 13.sp)
                    Text("${group.tracks.size} canciones · ${group.tracks.map { it.album }.distinct().size} álbumes", color = CypherColors.TextTertiary, fontSize = 11.sp)
                }
                Icon(Icons.Rounded.PlayArrow, null, tint = accent.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CollectionHeader(kind: String, name: String, tracks: List<Track>, accent: Color, state: AppState, onBack: () -> Unit) {
    val data = state.snapshot()
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Volver", tint = CypherColors.TextSecondary) }
            Column {
                Text(kind, color = CypherColors.TextTertiary, fontSize = 10.sp, letterSpacing = 1.2.sp)
                Text(name, color = CypherColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton("Reproducir", accent, tracks.isNotEmpty()) { state.playQueue(tracks.map { it.id }) }
        }
        Spacer(Modifier.height(10.dp))
        Text("${tracks.size} pistas · ${formatDuration(tracks.sumOf { it.durationMs })}", color = CypherColors.TextTertiary, fontSize = 11.sp)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(tracks, key = { it.id }) { track ->
                viewOf(data, track.id)?.let { TrackRow(it, data, accent, state, state.state.value.currentTrackId) }
            }
        }
    }
}

/* ---------------------------------------------------------------- favoritos */

@Composable
fun FavoritesView(ui: UiState, accent: Color, state: AppState) {
    val data = ui.data
    val views = viewsOf(data, data.favorites)
    if (views.isEmpty()) {
        EmptyState(
            Icons.Rounded.Favorite,
            "Canciones favoritas",
            "Marca una canción con el corazón y aparecerá aquí, también si la marcaste en el móvil.",
            accent
        )
        return
    }
    val playable = views.filter { it.playable }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${views.size} favoritas", color = CypherColors.TextTertiary, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            PrimaryButton("Reproducir favoritos", accent, playable.isNotEmpty()) {
                state.playQueue(playable.map { it.id })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(views, key = { it.id }) { view ->
                TrackRow(view, data, accent, state, ui.currentTrackId)
            }
        }
    }
}

/* ---------------------------------------------------------------- playlists */

@Composable
fun PlaylistsView(ui: UiState, accent: Color, state: AppState) {
    val selected = ui.selectedPlaylistId?.let { ui.data.playlist(it) }
    if (selected != null) {
        PlaylistDetail(selected, ui, accent, state)
        return
    }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Text(
            if (ui.data.playlists.isEmpty()) "Crea tu primera playlist" else "${ui.data.playlists.size} playlists",
            color = CypherColors.TextTertiary,
            fontSize = 11.sp
        )
        var sharedCode by remember { mutableStateOf("") }
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Text(
                "Añadir desde un código",
                color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Pega el código o el enlace cypher://p2p que te hayan compartido.",
                color = CypherColors.TextTertiary, fontSize = 10.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CypherField(
                    sharedCode, { sharedCode = it },
                    "cypher://p2p?d=…", accent, Modifier.weight(1f)
                )
                AddButton("Añadir", accent) {
                    state.importSharedPlaylist(sharedCode)
                    sharedCode = ""
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Text("Nueva playlist", color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CypherField(name, { name = it }, "Nombre", accent, Modifier.width(240.dp))
                CypherField(description, { description = it }, "Descripción (opcional)", accent, Modifier.weight(1f))
                AddButton("Crear", accent) {
                    val id = state.createPlaylist(name, description)
                    if (id != null) {
                        name = ""
                        description = ""
                        state.openPlaylist(id)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (ui.data.playlists.isEmpty()) {
            // No early return: the enclosing Column is still composing, so bailing out
            // here corrupted the composer stack as soon as the first playlist existed.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EmptyState(Icons.Rounded.PlaylistPlay, "Sin playlists", "Ponle nombre arriba y crea la primera.", accent)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                gridItems(ui.data.playlists, key = { it.id }) { playlist ->
                    PlaylistCard(playlist, ui.data, accent, onOpen = { state.openPlaylist(playlist.id) })
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, data: LibraryData, accent: Color, onOpen: () -> Unit) {
    val covers = playlist.trackIds.take(4).mapNotNull { viewOf(data, it) }
    val cover = rememberArtwork(playlist.artwork)
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(LocalShellCard.current)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(120.dp)
                .background(CypherColors.Card, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                androidx.compose.foundation.Image(
                    cover,
                    null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                )
            } else if (covers.isEmpty()) {
                Icon(Icons.Rounded.PlaylistPlay, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(30.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    covers.take(2).forEach { Artwork(it, 52, accent) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(playlist.name, color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${playlist.trackIds.size} pistas", color = CypherColors.TextTertiary, fontSize = 11.sp)
    }
}

@Composable
private fun PlaylistDetail(playlist: Playlist, ui: UiState, accent: Color, state: AppState) {
    val data = ui.data
    val views = viewsOf(data, playlist.trackIds)
    var editing by remember(playlist.id) { mutableStateOf(false) }
    var confirmDelete by remember(playlist.id) { mutableStateOf(false) }
    var name by remember(playlist.id, editing) { mutableStateOf(playlist.name) }
    var description by remember(playlist.id, editing) { mutableStateOf(playlist.description) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.select(Screen.PLAYLISTS) }) {
                Icon(Icons.Rounded.ArrowBack, "Volver", tint = CypherColors.TextSecondary)
            }
            Text(
                "PLAYLIST", color = CypherColors.TextTertiary, fontSize = 9.sp,
                letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { editing = !editing }) {
                Icon(Icons.Rounded.Edit, null, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Editar", color = accent, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column {
                Box(
                    Modifier.size(196.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(CypherColors.Pill)
                        .clickable {
                            state.openFilePicker(
                                FilePickerConfig(
                                    title = "Portada de «${playlist.name}»",
                                    subtitle = "PNG, JPG o WEBP. Se recorta al cuadro de la playlist.",
                                    extensions = setOf("png", "jpg", "jpeg", "webp"),
                                    startDirectory = java.io.File(System.getProperty("user.home"), "Pictures"),
                                    confirmLabel = "Usar portada",
                                    onPick = { files -> files.firstOrNull()?.let { state.setPlaylistCover(playlist.id, it) } }
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val cover = playlist.artwork
                    if (cover != null) {
                        Artwork(
                            TrackView(playlist.id, playlist.name, "", "", 0, cover),
                            196, accent
                        )
                    } else {
                        Icon(
                            Lucide.ListMusic, null,
                            tint = accent.copy(alpha = 0.6f), modifier = Modifier.size(44.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (playlist.artwork == null) "Toca para añadir portada" else "Toca para cambiarla",
                    color = CypherColors.TextTertiary, fontSize = 10.sp,
                    modifier = Modifier.width(196.dp)
                )
                if (playlist.artwork != null) {
                    TextButton(onClick = { state.setPlaylistCover(playlist.id, null) }) {
                        Text("Quitar portada", color = CypherColors.TextTertiary, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name, color = CypherColors.TextPrimary,
                    fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (playlist.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        playlist.description, color = CypherColors.TextSecondary,
                        fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${views.size} pistas · ${formatDuration(views.sumOf { it.durationMs })}",
                    color = CypherColors.TextTertiary, fontSize = 11.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton("Reproducir", accent, views.any { it.playable }) {
                        state.playPlaylist(playlist.id)
                    }
                    TextButton(onClick = { state.playPlaylist(playlist.id, shuffled = true) }) {
                        Icon(Lucide.Shuffle, null, tint = CypherColors.TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aleatorio", color = CypherColors.TextSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = { state.downloadPlaylist(playlist.id) }) {
                        Icon(Lucide.FileArchive, null, tint = CypherColors.TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Descargar", color = CypherColors.TextSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = { state.openPlaylistShare(playlist.id) }) {
                        Icon(Lucide.FileArchive, null, tint = CypherColors.TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir", color = CypherColors.TextSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = { confirmDelete = true }) { Text("Eliminar", color = CypherColors.Danger, fontSize = 12.sp) }
                }
                if (ui.onlineSaving && ui.onlineTotal > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Descargando ${ui.onlineSaved} de ${ui.onlineTotal}…",
                        color = accent, fontSize = 11.sp
                    )
                }
                val statusLine = ui.onlineStatus
                if (ui.onlineSaving && statusLine != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(statusLine, color = CypherColors.TextTertiary, fontSize = 10.sp)
                }
            }
        }
        if (editing) {
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                CypherField(name, { name = it }, "Nombre", accent, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                CypherField(description, { description = it }, "Descripción", accent, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Guardar", accent) {
                        state.updatePlaylist(playlist.id, name, description)
                        editing = false
                    }
                    TextButton(onClick = { editing = false }) { Text("Cancelar", color = CypherColors.TextSecondary, fontSize = 12.sp) }
                }
            }
        }
        if (confirmDelete) {
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Text(
                    "¿Eliminar «${playlist.name}»? La playlist desaparecerá también de tus otros dispositivos. " +
                        "Las canciones siguen en tu biblioteca.",
                    color = CypherColors.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Sí, eliminar", CypherColors.Danger) { state.deletePlaylist(playlist.id) }
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = CypherColors.TextSecondary, fontSize = 12.sp) }
                }
            }
        }
    // Share sheet: QR plus the same code as text, and a field to take one in.
    val share = ui.playlistShare
    if (share != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { state.closePlaylistShare() }) {
            Column(
                Modifier.width(420.dp)
                    .background(CypherColors.Surface, RoundedCornerShape(22.dp))
                    .border(1.dp, CypherColors.Border, RoundedCornerShape(22.dp))
                    .padding(22.dp)
            ) {
                Text(
                    "Compartir «${ui.playlistShareName.orEmpty()}»",
                    color = CypherColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Escanea el código desde el móvil o pégalo en otra copia de Cypher. " +
                        "Solo viajan los títulos y enlaces: el audio nunca se comparte.",
                    color = CypherColors.TextTertiary, fontSize = 11.sp
                )
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    QrCode(share, 220.dp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (share.length > 120) share.take(112) + "…" else share,
                    color = CypherColors.TextSecondary, fontSize = 9.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Copiar código", accent) {
                        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        clipboard.setContents(java.awt.datatransfer.StringSelection(share), null)
                        state.closePlaylistShare()
                    }
                    TextButton(onClick = { state.closePlaylistShare() }) {
                        Text("Cerrar", color = CypherColors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }

        Spacer(Modifier.height(10.dp))
        // No early return here: bailing out of a composable in the middle of its group
        // leaves the composer stack unbalanced and crashes as soon as an empty playlist
        // becomes non-empty (or the screen is reopened).
        if (views.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EmptyState(
                    Icons.Rounded.MusicNote,
                    "Playlist vacía",
                    "Añade canciones desde la biblioteca con el menú de cada fila.",
                    accent
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(views, key = { it.id }) { view ->
                    val index = playlist.trackIds.indexOf(view.id)
                    TrackRow(
                        view,
                        data,
                        accent,
                        state,
                        ui.currentTrackId,
                        onRemove = { state.removeFromPlaylist(playlist.id, view.id) },
                        onMoveUp = if (index > 0) ({ state.moveInPlaylist(playlist.id, index, index - 1) }) else null,
                        onMoveDown = if (index in 0 until playlist.trackIds.lastIndex) ({ state.moveInPlaylist(playlist.id, index, index + 1) }) else null
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------- estadísticas */

@Composable
fun StatsView(ui: UiState, accent: Color, state: AppState) {
    val data = ui.data
    val stats = data.stats
    if (stats.isEmpty()) {
        EmptyState(
            Icons.Rounded.BarChart,
            "Aún no hay estadísticas",
            "Cypher cuenta las reproducciones y el tiempo escuchado en este equipo. " +
                "Cuando sincronices, se suman las del móvil sin duplicar minutos.",
            accent
        )
        return
    }
    val totalMs = stats.sumOf { it.listenDurationMs }
    val totalPlays = stats.sumOf { it.playCount }
    val topArtists = stats.groupBy { it.artist.ifBlank { "Desconocido" } }
        .map { (artist, rows) -> artist to rows.sumOf { it.listenDurationMs } }
        .sortedByDescending { it.second }
        .take(6)
    val topTracks = stats.groupBy { it.trackId }
        .map { (id, rows) -> Triple(id, rows.sumOf { it.playCount }, rows.sumOf { it.listenDurationMs }) }
        .sortedByDescending { it.second }
        .take(8)
    val days = stats.groupBy { it.day }.mapValues { it.value.sumOf { row -> row.listenDurationMs } }
        .toList().sortedByDescending { it.first }.take(7)

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Tiempo escuchado", formatDuration(totalMs), accent, Modifier.weight(1f))
                MetricCard("Reproducciones", totalPlays.toString(), accent, Modifier.weight(1f))
                MetricCard("Canciones contadas", stats.map { it.trackId }.distinct().size.toString(), accent, Modifier.weight(1f))
            }
        }
        item { SectionHeader("ARTISTAS MÁS ESCUCHADOS", accent) }
        items(topArtists, key = { "ar-${it.first}" }) { (artist, ms) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(accent.copy(alpha = 0.16f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Text(initials(artist), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(artist, color = CypherColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(formatDuration(ms), color = CypherColors.TextTertiary, fontSize = 11.sp)
            }
        }
        item { SectionHeader("CANCIONES MÁS REPRODUCIDAS", accent) }
        items(topTracks, key = { "tr-${it.first}" }) { (id, plays, ms) ->
            val view = viewOf(data, id)
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(view, 34, accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(view?.title ?: stats.firstOrNull { it.trackId == id }?.title ?: id, color = CypherColors.TextPrimary, fontSize = 12.sp)
                    Text(view?.artist ?: stats.firstOrNull { it.trackId == id }?.artist.orEmpty(), color = CypherColors.TextTertiary, fontSize = 10.sp)
                }
                Text("$plays rep · ${formatDuration(ms)}", color = CypherColors.TextTertiary, fontSize = 10.sp)
            }
        }
        item { SectionHeader("ÚLTIMOS DÍAS", accent) }
        items(days, key = { "day-${it.first}" }) { (day, ms) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(day, color = CypherColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(formatDuration(ms), color = CypherColors.TextTertiary, fontSize = 11.sp)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(
        modifier.background(CypherColors.Card, RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(label, color = CypherColors.TextTertiary, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/* ------------------------------------------------------------------ helpers */

@Composable
private fun Hint(text: String) {
    Text(text, color = CypherColors.TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
fun CypherField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onEnter: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = CypherColors.TextTertiary, fontSize = 12.sp) },
        shape = RoundedCornerShape(11.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onEnter() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = CypherColors.Border,
            focusedContainerColor = CypherColors.Surface,
            unfocusedContainerColor = CypherColors.Surface,
            focusedTextColor = CypherColors.TextPrimary,
            unfocusedTextColor = CypherColors.TextPrimary
        ),
        modifier = modifier
    )
}

fun sortTracks(tracks: List<Track>, sort: SortMode): List<Track> = when (sort) {
    SortMode.TITLE -> tracks.sortedBy { it.title.lowercase() }
    SortMode.ARTIST -> tracks.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() }))
    SortMode.ALBUM -> tracks.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
    SortMode.ADDED -> tracks.sortedByDescending { it.addedAt }
    SortMode.DURATION -> tracks.sortedByDescending { it.durationMs }
}

fun idsFor(data: LibraryData, tracks: List<Track>): List<String> = tracks.map { it.id }

fun pickFolder(state: AppState) {
    state.openFilePicker(
        FilePickerConfig(
            title = "Tu carpeta de música",
            subtitle = "Entra en la carpeta y confirma: se añade todo lo que haya dentro.",
            mode = PickerMode.FOLDER,
            confirmLabel = "Usar esta carpeta",
            startDirectory = java.io.File(System.getProperty("user.home"), "Music"),
            onPick = { files -> files.firstOrNull()?.let(state::scanFolder) }
        )
    )
}

@Composable
private fun PlaylistShelfCard(playlist: Playlist, data: LibraryData, accent: Color, onOpen: () -> Unit) {
    val cover = rememberArtwork(playlist.artwork)
    Column(
        Modifier.width(172.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalShellCard.current)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(106.dp)
                .background(CypherColors.Card, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            val first = playlist.trackIds.firstOrNull()?.let { viewOf(data, it) }
            if (cover != null) {
                androidx.compose.foundation.Image(
                    cover,
                    null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                )
            } else if (first != null) Artwork(first, 58, accent)
            else Icon(Icons.Rounded.PlaylistPlay, null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${playlist.trackIds.size} pistas", color = CypherColors.TextTertiary, fontSize = 10.sp)
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, accent: Color, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onOpen).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).background(accent.copy(alpha = 0.18f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlaylistPlay, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(playlist.name, color = CypherColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("${playlist.trackIds.size} pistas", color = CypherColors.TextTertiary, fontSize = 11.sp)
    }
}

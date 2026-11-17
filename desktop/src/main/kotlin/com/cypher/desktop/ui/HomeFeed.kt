package com.cypher.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.LibrarySection
import com.cypher.desktop.app.Screen
import com.cypher.desktop.app.UiState
import com.cypher.desktop.core.AlbumGroup
import com.cypher.desktop.core.ArtistGroup
import com.cypher.desktop.core.LibraryData
import com.cypher.desktop.core.Playlist
import com.cypher.desktop.core.Track

/**
 * Inicio: playlists recientes compactas arriba y secciones horizontales debajo.
 * Solo se pinta contenido que existe en la biblioteca; no hay recomendaciones
 * inventadas.
 */
@Composable
fun HomeView(ui: UiState, accent: Color, state: AppState) {
    val data = ui.data
    if (data.tracks.isEmpty() && data.playlists.isEmpty() && data.remoteTracks.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.LibraryMusic,
            title = "Tu biblioteca empieza aquí",
            body = "Busca una canción arriba y Cypher la trae de YouTube Music. " +
                "La música local sigue disponible en Ajustes.",
            accent = accent,
            actionLabel = "Buscar en YouTube Music",
            onAction = { state.select(Screen.ONLINE) }
        )
        return
    }

    val favorites = data.favorites.mapNotNull { id -> viewOf(data, id) }
    val listened = (data.recent + data.stats.sortedByDescending { it.lastListenedAt }.map { it.trackId })
        .distinct()
        .mapNotNull { viewOf(data, it) }
        .take(12)
    val youtubeLocal = data.tracks.filter { it.id.startsWith("yt:") }.take(12)
    val recentPlaylists = data.playlists.sortedByDescending { it.updatedAt }.take(9)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth < 700.dp -> 1
            maxWidth < 960.dp -> 2
            else -> 3
        }

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                SectionHeader("PLAYLISTS RECIENTES", accent) {
                    TextButton(onClick = { state.select(Screen.PLAYLISTS) }) {
                        Text("Ver todas", color = accent, fontSize = 11.sp)
                    }
                }
            }
            if (recentPlaylists.isEmpty()) {
                item { Text("Todavía no hay listas. Crea la primera desde Playlists.", color = CypherColors.TextTertiary, fontSize = 11.sp) }
            } else {
                recentPlaylists.take(6).chunked(columns).forEach { row ->
                    item {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { playlist ->
                                RecentPlaylistCard(playlist, data, accent, Modifier.weight(1f)) { state.openPlaylist(playlist.id) }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            if (favorites.isNotEmpty()) {
                mediaSection(
                    title = "TUS FAVORITOS",
                    subtitle = "Lo que marcaste con el corazón, aquí y en el móvil",
                    items = favorites.take(12),
                    accent = accent,
                    onAll = { state.select(Screen.FAVORITES) }
                ) { view, onPlay -> MediaCard(view.title, view.artist, view.artwork, view.artworkUrl, null, accent, onPlay, onPlay) }
            }

            if (listened.isNotEmpty()) {
                item { SectionHeader("ESCUCHADO RECIENTEMENTE", accent) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(listened, key = { "recent-${it.id}" }) { view ->
                            MediaCard(
                                view.title, view.artist, view.artwork, view.artworkUrl, null, accent,
                                onOpen = { state.playQueue(listOf(view.id)) },
                                onPlay = { state.playQueue(listOf(view.id)) }
                            )
                        }
                    }
                }
            }

            if (data.albums.isNotEmpty()) {
                item {
                    SectionHeader("ÁLBUMES", accent) {
                        TextButton(onClick = { state.select(Screen.LIBRARY); state.setLibrarySection(LibrarySection.ALBUMS) }) {
                            Text("Mostrar todos", color = accent, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(data.albums, key = { "album-${it.name}" }) { album: AlbumGroup ->
                            MediaCard(
                                album.name,
                                album.artist,
                                album.tracks.firstOrNull()?.artwork,
                                null,
                                "${album.tracks.size} pistas",
                                accent,
                                onOpen = {
                                    state.select(Screen.LIBRARY)
                                    state.setLibrarySection(LibrarySection.ALBUMS)
                                    state.openAlbum(album.name)
                                },
                                onPlay = { state.playQueue(album.tracks.map { it.id }) }
                            )
                        }
                    }
                }
            }

            if (data.artists.isNotEmpty()) {
                item {
                    SectionHeader("ARTISTAS", accent) {
                        TextButton(onClick = { state.select(Screen.LIBRARY); state.setLibrarySection(LibrarySection.ARTISTS) }) {
                            Text("Mostrar todos", color = accent, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(data.artists, key = { "artist-${it.name}" }) { artist: ArtistGroup ->
                            ArtistCard(artist, accent) {
                                state.select(Screen.LIBRARY)
                                state.setLibrarySection(LibrarySection.ARTISTS)
                                state.openArtist(artist.name)
                            }
                        }
                    }
                }
            }

            if (youtubeLocal.isNotEmpty()) {
                item {
                    SectionHeader("YOUTUBE MUSIC", accent) {
                        TextButton(onClick = { state.select(Screen.ONLINE) }) {
                            Text("Buscar más", color = accent, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(youtubeLocal, key = { "yt-${it.id}" }) { track: Track ->
                            MediaCard(
                                track.title, track.artist, track.artwork, null, "YouTube", accent,
                                onOpen = { state.playQueue(listOf(track.id)) },
                                onPlay = { state.playQueue(listOf(track.id)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Section + horizontal rail, so the home reads as one strip per idea. */
private fun androidx.compose.foundation.lazy.LazyListScope.mediaSection(
    title: String,
    subtitle: String,
    items: List<TrackView>,
    accent: Color,
    onAll: () -> Unit,
    card: @Composable (TrackView, () -> Unit) -> Unit
) {
    item {
        SectionHeader(title, accent) {
            TextButton(onClick = onAll) { Text("Mostrar todos", color = accent, fontSize = 11.sp) }
        }
        Text(subtitle, color = CypherColors.TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
    }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(items, key = { "$title-${it.id}" }) { view ->
                card(view) { }
            }
        }
    }
}

private fun plural(count: Int, one: String, many: String): String =
    if (count == 1) "1 $one" else "$count $many"

/* ------------------------------------------------------------- components */

@Composable
private fun RecentPlaylistCard(playlist: Playlist, data: LibraryData, accent: Color, modifier: Modifier, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val cover = rememberArtwork(playlist.artwork ?: playlist.trackIds.firstOrNull()?.let { data.track(it)?.artwork })
    Row(
        modifier
            // Reference: 272 x 58 rows with a compact cover.
            .height(58.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (hovered) LocalShellCard.current else LocalShellCard.current.copy(alpha = LocalShellCard.current.alpha * 0.82f))
            .border(1.dp, Color.White.copy(alpha = if (hovered) 0.14f else 0.06f), RoundedCornerShape(15.dp))
            .hoverable(interaction)
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(CypherColors.Pill), contentAlignment = Alignment.Center) {
            if (cover != null) {
                Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(coverBrush(playlist.id)), contentAlignment = Alignment.Center) {
                    Text(
                        coverInitials(playlist.name),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, color = CypherColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Lista · ${plural(playlist.trackIds.size, "pista", "pistas")}", color = CypherColors.TextTertiary, fontSize = 10.sp, maxLines = 1)
        }
        Box(
            Modifier.size(32.dp)
                .clip(CircleShape)
                .background(if (hovered) accent else Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PlayArrow, "Abrir ${playlist.name}",
                tint = if (hovered) Color(0xFF14101A) else CypherColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MediaCard(
    title: String,
    subtitle: String,
    artwork: String?,
    artworkUrl: String?,
    badge: String?,
    accent: Color,
    onOpen: () -> Unit,
    onPlay: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bitmap = rememberArtwork(artwork?.takeIf { it.isNotBlank() } ?: artworkUrl)
    val scale by animateFloatAsState(if (hovered) 1.03f else 1f, label = "card-scale")
    // Reference: 134 wide by 166 tall, cover square and two lines underneath.
    Column(
        Modifier
            .width(134.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(14.dp))
            .background(LocalShellCard.current.copy(alpha = LocalShellCard.current.alpha * 0.85f))
            .border(1.dp, Color.White.copy(alpha = if (hovered) 0.15f else 0.05f), RoundedCornerShape(14.dp))
            .hoverable(interaction)
            .clickable(onClick = onOpen)
    ) {
        Box(Modifier.fillMaxWidth().height(134.dp).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(CypherColors.Pill), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(coverBrush(title)), contentAlignment = Alignment.Center) {
                    Text(
                        coverInitials(title),
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (hovered) {
                Box(
                    Modifier.size(44.dp).background(accent, CircleShape).clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Reproducir $title", tint = Color(0xFF14101A), modifier = Modifier.size(24.dp))
                }
            }
            if (badge != null) {
                Text(
                    badge,
                    color = CypherColors.TextPrimary,
                    fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
            Text(title, color = CypherColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(1.dp))
            Text(subtitle, color = CypherColors.TextTertiary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ArtistCard(artist: ArtistGroup, accent: Color, onClick: () -> Unit) {
    val bitmap = rememberArtwork(artist.tracks.firstOrNull { it.artwork != null }?.artwork)
    Column(
        Modifier.width(132.dp).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(116.dp).clip(CircleShape).background(CypherColors.Pill),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(coverBrush(artist.name)), contentAlignment = Alignment.Center) {
                    Text(
                        coverInitials(artist.name),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(artist.name, color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${artist.tracks.size} pistas", color = CypherColors.TextTertiary, fontSize = 10.sp)
    }
}

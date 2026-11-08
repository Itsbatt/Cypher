package com.cypher.music.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cypher.music.data.Playlist
import com.cypher.music.data.RecentlyPlayedItem
import com.cypher.music.data.ThemePreferences
import com.cypher.music.model.Track
import com.cypher.music.ui.components.ArtworkPlaceholder
import com.cypher.music.ui.components.PlaylistMosaicCover
import com.cypher.music.ui.theme.IosDarkBorder
import com.cypher.music.ui.theme.IosDarkCard
import com.cypher.music.ui.theme.IosDarkPill
import com.cypher.music.ui.theme.ResolvedThemeColors
import com.cypher.music.ui.utils.AppHaptics

@Composable
private fun SectionTitle(text: String, themeColors: ResolvedThemeColors, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = themeColors.textPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun EmptySection(text: String, themeColors: ResolvedThemeColors) {
    Text(
        text = text,
        color = themeColors.textSecondary,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
fun HomeRecommendationsSection(
    topTracks: List<Track>,
    favoriteTrackIds: Set<String>,
    themePrefs: ThemePreferences,
    themeColors: ResolvedThemeColors,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    val gradientPalettes = listOf(
        listOf(themeColors.accent, Color(0xFF9F1239)),
        listOf(Color(0xFF10B981), Color(0xFF047857)),
        listOf(Color(0xFF06B6D4), Color(0xFF1E40AF)),
        listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95)),
        listOf(Color(0xFFF97316), Color(0xFF991B1B)),
        listOf(Color(0xFFEAB308), Color(0xFFBE123C))
    )

    Column(modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)) {
        SectionTitle("Elegido para ti", themeColors)
        if (topTracks.isEmpty()) {
            EmptySection(
                "Aún no hay recomendaciones. Escucha algunas canciones y CYPHER empezará a sugerir música de tu biblioteca.",
                themeColors
            )
            return@Column
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(topTracks) { index, track ->
                val cardShape = RoundedCornerShape(22.dp)
                val colors = gradientPalettes[index % gradientPalettes.size]
                Box(
                    modifier = Modifier
                        .width(170.dp)
                        .height(230.dp)
                        .shadow(elevation = 6.dp, shape = cardShape)
                        .clip(cardShape)
                        .background(Brush.verticalGradient(colors))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), cardShape)
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onPlayTrack(track, topTracks)
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.25f),
                                        Color.Black.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (favoriteTrackIds.contains(track.id)) "FAVORITO" else "RECOMENDADO",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Column {
                            Text(
                                text = track.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                color = Color.White.copy(alpha = 0.80f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Reproducir",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeRecentsSection(
    recentlyPlayed: List<RecentlyPlayedItem>,
    themePrefs: ThemePreferences,
    themeColors: ResolvedThemeColors,
    onPlayItem: (RecentlyPlayedItem) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 22.dp)) {
        SectionTitle("Escuchado recientemente", themeColors)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(recentlyPlayed, key = { it.id + it.type.name }) { item ->
                Column(
                    modifier = Modifier
                        .width(115.dp)
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onPlayItem(item)
                        }
                ) {
                    ArtworkBox(
                        artworkUrl = item.displayArtworkUrl,
                        contentDescription = item.title
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.title,
                        color = themeColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        color = themeColors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkBox(artworkUrl: String?, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(115.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(IosDarkCard),
        contentAlignment = Alignment.Center
    ) {
        ArtworkPlaceholder(modifier = Modifier.fillMaxSize())
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun HomeFavoriteTracksSection(
    tracks: List<Track>,
    themePrefs: ThemePreferences,
    themeColors: ResolvedThemeColors,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 22.dp)) {
        SectionTitle("Canciones favoritas", themeColors)
        if (tracks.isEmpty()) {
            EmptySection("Marca una canción con el corazón y aparecerá aquí.", themeColors)
            return@Column
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                Column(
                    modifier = Modifier
                        .width(115.dp)
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onPlayTrack(track, tracks)
                        }
                ) {
                    ArtworkBox(artworkUrl = track.displayArtworkUrl, contentDescription = track.title)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = track.title,
                        color = themeColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = themeColors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun HomePlaylistsSection(
    playlists: List<Playlist>,
    themePrefs: ThemePreferences,
    themeColors: ResolvedThemeColors,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playlists",
                color = themeColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            Text(
                text = "Nueva",
                color = themeColors.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                    onCreatePlaylist()
                }
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .width(115.dp)
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onCreatePlaylist()
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(115.dp)
                            .shadow(4.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(IosDarkPill)
                            .border(1.dp, IosDarkBorder, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Crear playlist",
                                tint = themeColors.accent,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Crear",
                                color = themeColors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Nueva playlist",
                        color = themeColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Toca para crear",
                        color = themeColors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                Column(
                    modifier = Modifier
                        .width(115.dp)
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onSelectPlaylist(playlist)
                        }
                ) {
                    PlaylistMosaicCover(
                        artworks = playlist.mosaicArtworks,
                        customCoverUri = playlist.coverUri,
                        size = 115.dp,
                        cornerRadius = 14.dp
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
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun HomeFavoritePlaylistsSection(
    playlists: List<Playlist>,
    themePrefs: ThemePreferences,
    themeColors: ResolvedThemeColors,
    onSelectPlaylist: (Playlist) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 22.dp)) {
        SectionTitle("Playlists favoritas", themeColors)
        if (playlists.isEmpty()) {
            EmptySection(
                "Marca playlists como favoritas en Ajustes → Inicio para tenerlas a mano.",
                themeColors
            )
            return@Column
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(playlists, key = { it.id }) { playlist ->
                Column(
                    modifier = Modifier
                        .width(115.dp)
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onSelectPlaylist(playlist)
                        }
                ) {
                    PlaylistMosaicCover(
                        artworks = playlist.mosaicArtworks,
                        customCoverUri = playlist.coverUri,
                        size = 115.dp,
                        cornerRadius = 14.dp
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
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Artists and songs the user touches most, one tap away from the top of the home screen. */
@Composable
fun HomeQuickAccessSection(
    artists: List<String>,
    tracks: List<Track>,
    themePrefs: ThemePreferences,
    themeColors: ResolvedThemeColors,
    onViewArtist: (String) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    if (artists.isEmpty() && tracks.isEmpty()) return
    Column(modifier = Modifier.padding(bottom = 22.dp)) {
        SectionTitle("Accesos rápidos", themeColors)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(artists, key = { "artist_" + it }) { artist ->
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onViewArtist(artist)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val artwork = tracks.firstOrNull { it.artist == artist }?.displayArtworkUrl
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(IosDarkCard),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!artwork.isNullOrBlank()) {
                            AsyncImage(
                                model = artwork,
                                contentDescription = artist,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ArtworkPlaceholder(modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = artist,
                        color = themeColors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            items(tracks, key = { "track_" + it.id }) { track ->
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onPlayTrack(track, tracks)
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(IosDarkCard)
                    ) {
                        if (!track.displayArtworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = track.displayArtworkUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ArtworkPlaceholder(modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = track.title,
                        color = themeColors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

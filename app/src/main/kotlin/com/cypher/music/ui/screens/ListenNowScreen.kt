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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cypher.music.data.Playlist
import com.cypher.music.data.RecentlyPlayedItem
import com.cypher.music.data.RecommendationSection
import com.cypher.music.data.ThemePreferences
import com.cypher.music.data.User
import com.cypher.music.data.UserListeningStats
import com.cypher.music.model.Track
import com.cypher.music.ui.components.ArtworkPlaceholder
import com.cypher.music.ui.components.PlaylistMosaicCover
import com.cypher.music.ui.theme.IosDarkBorder
import com.cypher.music.ui.theme.IosDarkCard
import com.cypher.music.ui.theme.IosDarkPill
import com.cypher.music.ui.theme.ResolvedThemeColors
import com.cypher.music.ui.utils.AppHaptics

@Composable
fun ListenNowScreen(
    user: User?,
    dynamicSections: List<RecommendationSection>,
    localTracks: List<Track>,
    playlists: List<Playlist>,
    recentlyPlayed: List<RecentlyPlayedItem>,
    currentTrackId: String?,
    favoriteTrackIds: Set<String> = emptySet(),
    listeningStats: UserListeningStats = UserListeningStats(),
    themePrefs: ThemePreferences = ThemePreferences(),
    themeColors: ResolvedThemeColors,
    bottomPadding: Dp = 150.dp,
    onOpenSettings: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayRecentlyPlayed: (RecentlyPlayedItem) -> Unit,
    onToggleFavorite: (Track) -> Unit = {},
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
    onViewArtist: (String) -> Unit = {},
    onViewAlbum: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val topTracks = dynamicSections.flatMap { it.tracks }.distinctBy { it.id }.take(6)

    val gradientPalettes = listOf(
        listOf(themeColors.accent, Color(0xFF9F1239)),
        listOf(Color(0xFF10B981), Color(0xFF047857)),
        listOf(Color(0xFF06B6D4), Color(0xFF1E40AF)),
        listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95)),
        listOf(Color(0xFFF97316), Color(0xFF991B1B)),
        listOf(Color(0xFFEAB308), Color(0xFFBE123C))
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        // -------------------------------------------------------------------------
        // 1. HEADER: "Home" + User Profile Avatar
        // -------------------------------------------------------------------------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Inicio",
                        color = themeColors.textPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    if (user != null) {
                        Text(
                            text = "Hola, ${user.displayName}",
                            color = themeColors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Profile Avatar
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(elevation = 4.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(if (user != null) Color(user.avatarColor) else IosDarkPill)
                        .border(1.dp, IosDarkBorder, CircleShape)
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onOpenSettings()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Ajustes y Perfil",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------------------
        // 1.5. CÁPSULA COMPACTA DE ESTADÍSTICAS (Pill horizontal)
        // -------------------------------------------------------------------------
        if (listeningStats.totalListeningMs > 0 || listeningStats.topArtists.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(themeColors.cardBackground)
                        .border(1.dp, themeColors.separator, RoundedCornerShape(14.dp))
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onOpenSettings()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(themeColors.accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BarChart,
                                    contentDescription = null,
                                    tint = themeColors.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${listeningStats.formattedTotalTime} escuchadas · ${listeningStats.totalPlays} reproducciones",
                                color = themeColors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "Ver detalles →",
                            color = themeColors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // -------------------------------------------------------------------------
        // 2. TOP PICKS FOR YOU: Basado en las recomendaciones dinámicas reales
        // -------------------------------------------------------------------------
        if (topTracks.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)) {
                    Text(
                        text = "Elegido para ti",
                        color = themeColors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )

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
                                    // Top Badge
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

                                    // Bottom Track Details
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
        }

        

        // Empty state: recommendations need real history, never invented filler.
        if (dynamicSections.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Aún no hay recomendaciones",
                        color = themeColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Escucha algunas canciones y CYPHER empezará a sugerir música de tu biblioteca y de los artistas que más suenan.",
                        color = themeColors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // -------------------------------------------------------------------------
        // 3. RECENTLY PLAYED: Historial real de reproducción
        // -------------------------------------------------------------------------
        if (recentlyPlayed.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(bottom = 22.dp)) {
                    Text(
                        text = "Escuchado recientemente",
                        color = themeColors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(recentlyPlayed) { item ->
                            Column(
                                modifier = Modifier
                                    .width(115.dp)
                                    .clickable {
                                        AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                        onPlayRecentlyPlayed(item)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(115.dp)
                                        .shadow(4.dp, RoundedCornerShape(14.dp))
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(IosDarkCard),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ArtworkPlaceholder(modifier = Modifier.fillMaxSize())
                                    if (!item.artworkUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = item.artworkUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

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
        }

        

        // -------------------------------------------------------------------------
        // 4. DYNAMIC RECOMMENDATION SECTIONS ("Porque escuchaste a...", etc.)
        // -------------------------------------------------------------------------
        items(dynamicSections) { section ->
            Column(modifier = Modifier.padding(bottom = 22.dp)) {
                Text(
                    text = section.title,
                    color = themeColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )

                if (!section.subtitle.isNullOrBlank()) {
                    Text(
                        text = section.subtitle,
                        color = themeColors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(section.tracks) { track ->
                        Column(
                            modifier = Modifier
                                .width(115.dp)
                                .clickable {
                                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                    onPlayTrack(track, section.tracks)
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(115.dp)
                                    .shadow(4.dp, RoundedCornerShape(14.dp))
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(IosDarkCard),
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

        

        // -------------------------------------------------------------------------
        // 5. PLAYLISTS: Playlists de la cuenta actual
        // -------------------------------------------------------------------------
        item {
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
                    // Create Playlist Solid Card
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

                    // Existing Playlists for current user
                    items(playlists) { playlist ->
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

        
    }
}

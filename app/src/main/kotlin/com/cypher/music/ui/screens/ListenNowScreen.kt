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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.data.HomeSection
import com.cypher.music.data.HomeSectionState
import com.cypher.music.data.Playlist
import com.cypher.music.data.RecentlyPlayedItem
import com.cypher.music.data.RecommendationSection
import com.cypher.music.data.ThemePreferences
import com.cypher.music.data.User
import com.cypher.music.data.UserListeningStats
import com.cypher.music.data.defaultHomeSections
import com.cypher.music.model.Track
import com.cypher.music.ui.theme.IosDarkBorder
import com.cypher.music.ui.theme.IosDarkPill
import com.cypher.music.ui.theme.ResolvedThemeColors
import com.cypher.music.ui.utils.AppHaptics

/**
 * Home. The header and the listening capsule are fixed; every other block is rendered in
 * the order and with the visibility the user chose in Settings, so the screen is the same
 * as before by default but fully rearrangeable.
 */
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
    homeSections: List<HomeSectionState> = defaultHomeSections(),
    favoriteTracks: List<Track> = emptyList(),
    favoritePlaylists: List<Playlist> = emptyList(),
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
    val quickArtists = localTracks
        .groupBy { it.artist }
        .filterKeys { it.isNotBlank() }
        .entries
        .sortedByDescending { it.value.size }
        .map { it.key }
        .take(6)
    val quickTracks = (favoriteTracks.ifEmpty { localTracks }).distinctBy { it.id }.take(4)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
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

        homeSections.filter { it.enabled }.forEach { entry ->
            when (entry.section) {
                HomeSection.RECOMMENDATIONS -> item(key = "home_recommendations") {
                    HomeRecommendationsSection(
                        topTracks = topTracks,
                        favoriteTrackIds = favoriteTrackIds,
                        themePrefs = themePrefs,
                        themeColors = themeColors,
                        onPlayTrack = onPlayTrack
                    )
                }

                HomeSection.RECENTS -> if (recentlyPlayed.isNotEmpty()) {
                    item(key = "home_recents") {
                        HomeRecentsSection(
                            recentlyPlayed = recentlyPlayed,
                            themePrefs = themePrefs,
                            themeColors = themeColors,
                            onPlayItem = onPlayRecentlyPlayed
                        )
                    }
                }

                HomeSection.FAVORITES -> item(key = "home_favorites") {
                    HomeFavoriteTracksSection(
                        tracks = favoriteTracks,
                        themePrefs = themePrefs,
                        themeColors = themeColors,
                        onPlayTrack = onPlayTrack
                    )
                }

                HomeSection.PLAYLISTS -> item(key = "home_playlists") {
                    HomePlaylistsSection(
                        playlists = playlists,
                        themePrefs = themePrefs,
                        themeColors = themeColors,
                        onCreatePlaylist = onCreatePlaylist,
                        onSelectPlaylist = onSelectPlaylist
                    )
                }

                HomeSection.FAVORITE_PLAYLISTS -> if (favoritePlaylists.isNotEmpty()) {
                    item(key = "home_favorite_playlists") {
                        HomeFavoritePlaylistsSection(
                            playlists = favoritePlaylists,
                            themePrefs = themePrefs,
                            themeColors = themeColors,
                            onSelectPlaylist = onSelectPlaylist
                        )
                    }
                }

                HomeSection.QUICK_ACCESS -> item(key = "home_quick_access") {
                    HomeQuickAccessSection(
                        artists = quickArtists,
                        tracks = quickTracks,
                        themePrefs = themePrefs,
                        themeColors = themeColors,
                        onViewArtist = onViewArtist,
                        onPlayTrack = onPlayTrack
                    )
                }
            }
        }

        if (homeSections.none { it.enabled }) {
            item {
                Text(
                    text = "Todas las secciones están ocultas. Activa las que quieras en Ajustes → Inicio.",
                    color = themeColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
            }
        }
    }
}

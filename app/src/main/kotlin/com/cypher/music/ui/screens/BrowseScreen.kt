package com.cypher.music.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cypher.music.data.MediaCollection
import com.cypher.music.data.SearchCategory
import com.cypher.music.data.ThemePreferences
import com.cypher.music.model.Track
import com.cypher.music.ui.components.CypherTrackRow
import com.cypher.music.ui.theme.ResolvedThemeColors

@Composable
fun BrowseScreen(
    collections: List<MediaCollection>,
    categories: List<SearchCategory> = emptyList(),
    popularTracks: List<Track>,
    currentTrackId: String?,
    favoriteTrackIds: Set<String> = emptySet(),
    themePrefs: ThemePreferences = ThemePreferences(),
    themeColors: ResolvedThemeColors,
    bottomPadding: Dp = 150.dp,
    onTrackClick: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit = {},
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
    onViewArtist: (String) -> Unit = {},
    onViewAlbum: (String) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.background),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        item {
            Text(
                text = "Explorar",
                color = themeColors.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        // Minimalist Editorial Cards Carousel
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(collections) { col ->
                    Box(
                        modifier = Modifier
                            .width(250.dp)
                            .height(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF222226))
                            .clickable {
                                if (col.tracks.isNotEmpty()) {
                                    onTrackClick(col.tracks.first(), col.tracks)
                                }
                            }
                    ) {
                        AsyncImage(
                            model = col.artworkUrl,
                            contentDescription = col.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                        startY = 40f
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = col.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = col.subtitle,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Top Tracks Section
        item {
            Text(
                text = "Canciones destacadas",
                color = themeColors.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        items(popularTracks.take(8)) { track ->
            CypherTrackRow(
                track = track,
                isPlaying = track.id == currentTrackId,
                isFavorite = favoriteTrackIds.contains(track.id),
                accentColor = themeColors.accent,
                onClick = { onTrackClick(track, popularTracks) },
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

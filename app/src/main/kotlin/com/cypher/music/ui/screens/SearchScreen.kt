package com.cypher.music.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.core.content.ContextCompat
import com.cypher.music.ui.components.CypherVoiceSearchModal
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cypher.music.data.Playlist
import com.cypher.music.data.SearchCategory
import com.cypher.music.data.ThemePreferences
import com.cypher.music.data.UnifiedSearchResults
import com.cypher.music.model.Track
import com.cypher.music.ui.components.CypherSectionHeader
import com.cypher.music.ui.components.CypherTrackRow
import com.cypher.music.ui.components.PlaylistMosaicCover
import com.cypher.music.ui.theme.IosDarkSurface
import com.cypher.music.ui.theme.ResolvedThemeColors
import com.cypher.music.ui.utils.AppHaptics
import java.util.Locale

@Composable
fun SearchScreen(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<Track>,
    unifiedResults: UnifiedSearchResults = UnifiedSearchResults(),
    isSearching: Boolean,
    categories: List<SearchCategory> = emptyList(),
    searchCategories: List<SearchCategory> = emptyList(),
    recentSearches: List<String> = emptyList(),
    onDeleteHistoryItem: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onExecuteSearch: ((String) -> Unit)? = null,
    currentTrackId: String?,
    favoriteTrackIds: Set<String> = emptySet(),
    themePrefs: ThemePreferences = ThemePreferences(),
    themeColors: ResolvedThemeColors,
    bottomPadding: Dp = 150.dp,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlaylistClick: ((Playlist) -> Unit)? = null,
    onCategoryClick: ((SearchCategory) -> Unit)? = null,
    onSearchCategoryClick: ((SearchCategory) -> Unit)? = null,
    onToggleFavorite: (Track) -> Unit = {},
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
    onViewArtist: (String) -> Unit = {},
    onViewAlbum: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    // Consistent floating capsule aesthetics (matching CypherNavBar)
    val pillShape = RoundedCornerShape(32.dp)
    val lavenderAccent = Color(0xFF9D7BB0)

    var showVoiceModal by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showVoiceModal = true
        } else {
            Toast.makeText(
                context,
                "Se requiere permiso de micrófono para la búsqueda por voz",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val startVoiceSearch = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            showVoiceModal = true
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Resolve categories with native SearchCategory model
    val resolvedCategories: List<SearchCategory> = remember(categories, searchCategories) {
        if (searchCategories.isNotEmpty()) {
            searchCategories
        } else if (categories.isNotEmpty()) {
            categories
        } else {
            SearchCategory.defaultCategories()
        }
    }

    val handleCategorySelect: (SearchCategory) -> Unit = { cat ->
        AppHaptics.actionClick(themePrefs.hapticsEnabled)
        onQueryChange(cat.title)
        onExecuteSearch?.invoke(cat.title)
        onSearchCategoryClick?.invoke(cat)
        onCategoryClick?.invoke(cat)
        focusManager.clearFocus()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.background),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        // Main Screen Header
        item {
            Text(
                text = "Buscar",
                color = themeColors.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }

        // Floating Capsule Search Bar (sharing CypherNavBar visual language)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = pillShape,
                        clip = false
                    )
                    .clip(pillShape)
                    .background(IosDarkSurface.copy(alpha = 0.85f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = pillShape
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Buscar",
                        tint = lavenderAccent,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Artistas, canciones, letras y más",
                                color = themeColors.textSecondary.copy(alpha = 0.7f),
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onQueryChange,
                            textStyle = TextStyle(
                                color = themeColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(lavenderAccent),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        onExecuteSearch?.invoke(searchQuery)
                                    }
                                    focusManager.clearFocus()
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isSearchFocused = it.isFocused }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                color = lavenderAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                    onQueryChange("")
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Borrar",
                                    tint = lavenderAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                startVoiceSearch()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Búsqueda por voz",
                                tint = lavenderAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section: "Búsquedas recientes" (LIFO, deduplicated, removable chips)
        val showRecentSearches = recentSearches.isNotEmpty() && (searchQuery.isBlank() || isSearchFocused)
        if (showRecentSearches) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recientes",
                        color = themeColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    TextButton(
                        onClick = {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onClearHistory()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Borrar todo",
                            color = lavenderAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    items(recentSearches, key = { it }) { query ->
                        RecentSearchChip(
                            query = query,
                            accentColor = lavenderAccent,
                            textColor = themeColors.textPrimary,
                            secondaryColor = themeColors.textSecondary,
                            hapticsEnabled = themePrefs.hapticsEnabled,
                            onClick = {
                                onQueryChange(query)
                                onExecuteSearch?.invoke(query)
                                focusManager.clearFocus()
                            },
                            onDelete = {
                                onDeleteHistoryItem(query)
                            }
                        )
                    }
                }
            }
        }

        // Active Unified Search Results
        if (searchQuery.isNotBlank()) {
            val hasResults = !unifiedResults.isEmpty || searchResults.isNotEmpty()

            if (!hasResults && !isSearching) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, start = 20.dp, end = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No se encontraron resultados para «$searchQuery»",
                            color = themeColors.textSecondary,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                // Section 1: "En tu biblioteca" (Local Playlists + Local Tracks)
                if (unifiedResults.hasLocalMatches) {
                    item {
                        CypherSectionHeader(
                            title = "En tu biblioteca",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    // Matching local playlists carousel
                    if (unifiedResults.localPlaylists.isNotEmpty()) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                items(unifiedResults.localPlaylists) { pl ->
                                    Column(
                                        modifier = Modifier
                                            .width(115.dp)
                                            .clickable {
                                                AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                                onPlaylistClick?.invoke(pl)
                                            }
                                    ) {
                                        PlaylistMosaicCover(
                                            artworks = pl.mosaicArtworks,
                                            customCoverUri = pl.coverUri,
                                            size = 115.dp,
                                            cornerRadius = 14.dp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = pl.name,
                                            color = themeColors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = pl.formattedTrackCount,
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

                    // Matching local tracks
                    if (unifiedResults.localTracks.isNotEmpty()) {
                        items(unifiedResults.localTracks) { track ->
                            CypherTrackRow(
                                track = track,
                                isPlaying = track.id == currentTrackId,
                                isFavorite = favoriteTrackIds.contains(track.id),
                                accentColor = themeColors.accent,
                                onClick = { onTrackClick(track, unifiedResults.localTracks) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onViewArtist = { onViewArtist(track.artist) },
                                onViewAlbum = { onViewAlbum(track.album) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Section 2: "Resultados globales" (YouTube Music / Online catalog)
                val globalTracks = if (unifiedResults.globalTracks.isNotEmpty()) {
                    unifiedResults.globalTracks
                } else {
                    searchResults.filter { it.sourceType != com.cypher.music.model.AudioSourceType.LOCAL }
                }

                if (globalTracks.isNotEmpty()) {
                    item {
                        CypherSectionHeader(
                            title = "Resultados globales",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    items(globalTracks) { track ->
                        CypherTrackRow(
                            track = track,
                            isPlaying = track.id == currentTrackId,
                            isFavorite = favoriteTrackIds.contains(track.id),
                            accentColor = themeColors.accent,
                            onClick = { onTrackClick(track, globalTracks) },
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
        } else {
            // Default: Browse Categories Grid
            item {
                Text(
                    text = "Explorar categorías",
                    color = themeColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            items(resolvedCategories.chunked(2)) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    pair.forEach { cat ->
                        val catShape = RoundedCornerShape(16.dp)
                        val catBorderColor = cat.customBorderColor ?: Color.White.copy(alpha = 0.12f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(96.dp)
                                .shadow(elevation = 4.dp, shape = catShape)
                                .clip(catShape)
                                .background(Brush.linearGradient(cat.gradientColors))
                                .border(1.dp, catBorderColor, catShape)
                                .clickable {
                                    handleCategorySelect(cat)
                                }
                        ) {
                            if (!cat.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = cat.coverUrl,
                                    contentDescription = cat.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(0.32f)
                                )
                            }

                            if (cat.iconRes != null) {
                                Icon(
                                    painter = painterResource(id = cat.iconRes),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .size(32.dp)
                                )
                            }

                            Text(
                                text = cat.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp)
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showVoiceModal) {
        CypherVoiceSearchModal(
            onDismiss = { showVoiceModal = false },
            onSpeechResult = { recognizedQuery ->
                onQueryChange(recognizedQuery)
                onExecuteSearch?.invoke(recognizedQuery)
                focusManager.clearFocus()
            },
            hapticsEnabled = themePrefs.hapticsEnabled
        )
    }
}

/**
 * Capsule Chip representing an individual recent search item with an icon and quick removal button.
 */
@Composable
private fun RecentSearchChip(
    query: String,
    accentColor: Color,
    textColor: Color,
    secondaryColor: Color,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val chipShape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(chipShape)
            .background(IosDarkSurface.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), chipShape)
            .clickable {
                AppHaptics.actionClick(hapticsEnabled)
                onClick()
            }
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = query,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable {
                    AppHaptics.actionClick(hapticsEnabled)
                    onDelete()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Eliminar",
                tint = secondaryColor.copy(alpha = 0.7f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

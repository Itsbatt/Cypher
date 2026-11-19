package com.cypher.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.app.AppState
import com.cypher.desktop.core.LibraryData
import com.cypher.desktop.core.Playlist

/**
 * Modern context menu for songs with a cascading playlist submenu,
 * real-time playlist search, and sleek rounded aesthetic matching Cypher's FilePicker.
 */
@Composable
fun TrackContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    view: TrackView,
    data: LibraryData,
    accent: Color,
    state: AppState,
    onRemove: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    var showPlaylists by remember { mutableStateOf(false) }
    var playlistQuery by remember { mutableStateOf("") }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            showPlaylists = false
            playlistQuery = ""
            onDismissRequest()
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = CypherColors.Surface,
        border = BorderStroke(1.dp, CypherColors.Border.copy(alpha = 0.65f)),
        shadowElevation = 14.dp
    ) {
        TrackContextMenuContent(
            view = view,
            data = data,
            accent = accent,
            state = state,
            showPlaylists = showPlaylists,
            playlistQuery = playlistQuery,
            onQueryChange = { playlistQuery = it },
            onTogglePlaylists = { showPlaylists = !showPlaylists },
            onHoverPlaylists = { showPlaylists = true },
            onRemove = onRemove,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onClose = {
                showPlaylists = false
                playlistQuery = ""
                onDismissRequest()
            }
        )
    }
}

@Composable
fun TrackContextMenuContent(
    view: TrackView,
    data: LibraryData,
    accent: Color,
    state: AppState,
    showPlaylists: Boolean,
    playlistQuery: String,
    onQueryChange: (String) -> Unit,
    onTogglePlaylists: () -> Unit,
    onHoverPlaylists: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(CypherColors.Surface, RoundedCornerShape(16.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left Submenu: Playlist selector with search and "+ Nueva lista"
        if (showPlaylists) {
            PlaylistSubmenuPanel(
                trackId = view.id,
                playlists = data.playlists,
                query = playlistQuery,
                onQueryChange = onQueryChange,
                accent = accent,
                state = state,
                onClose = onClose
            )

            // Vertical Divider between panels
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(280.dp)
                    .background(CypherColors.Border.copy(alpha = 0.45f))
            )
            Spacer(Modifier.width(6.dp))
        }

        // Right Panel: Main song options
        TrackMainActionsPanel(
            view = view,
            isFavorite = data.isFavorite(view.id),
            showPlaylists = showPlaylists,
            onTogglePlaylists = onTogglePlaylists,
            onHoverPlaylists = onHoverPlaylists,
            accent = accent,
            state = state,
            data = data,
            onRemove = onRemove,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onClose = onClose
        )
    }
}

@Composable
private fun PlaylistSubmenuPanel(
    trackId: String,
    playlists: List<Playlist>,
    query: String,
    onQueryChange: (String) -> Unit,
    accent: Color,
    state: AppState,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .padding(vertical = 2.dp)
    ) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CypherColors.Card.copy(alpha = 0.75f))
                .border(1.dp, CypherColors.Border.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Lucide.Search, "Buscar lista", tint = CypherColors.TextTertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = CypherColors.TextPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(accent),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text("Busca una lista", color = CypherColors.TextTertiary, fontSize = 12.sp)
                    }
                    innerTextField()
                }
            )
            if (query.isNotEmpty()) {
                Icon(
                    Lucide.X,
                    "Limpiar",
                    tint = CypherColors.TextTertiary,
                    modifier = Modifier.size(12.dp).clickable { onQueryChange("") }
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Create new playlist option
        TrackMenuItem(
            icon = Lucide.Plus,
            text = "Nueva lista",
            tint = accent,
            iconTint = accent,
            onClick = {
                val newId = state.createPlaylist("Nueva lista")
                if (newId != null) {
                    state.addToPlaylist(newId, trackId)
                }
                onClose()
            }
        )

        HorizontalDivider(
            color = CypherColors.Border.copy(alpha = 0.4f),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Scrollable list of playlists
        val filtered = remember(playlists, query) {
            if (query.isBlank()) playlists
            else playlists.filter { it.name.contains(query, ignoreCase = true) }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (playlists.isEmpty()) "No hay playlists creadas" else "No se encontraron listas",
                        color = CypherColors.TextTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(filtered, key = { it.id }) { playlist ->
                    val isInPlaylist = playlist.trackIds.contains(trackId)
                    val interaction = remember { MutableInteractionSource() }
                    val hovered by interaction.collectIsHoveredAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    hovered -> CypherColors.Card.copy(alpha = 0.85f)
                                    else -> Color.Transparent
                                }
                            )
                            .hoverable(interaction)
                            .clickable {
                                state.addToPlaylist(playlist.id, trackId)
                                onClose()
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Lucide.ListMusic,
                            null,
                            tint = if (isInPlaylist) accent else CypherColors.TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            playlist.name,
                            color = if (isInPlaylist) accent else CypherColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isInPlaylist) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isInPlaylist) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Lucide.Check, "Ya en la playlist", tint = accent, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackMainActionsPanel(
    view: TrackView,
    isFavorite: Boolean,
    showPlaylists: Boolean,
    onTogglePlaylists: () -> Unit,
    onHoverPlaylists: () -> Unit,
    accent: Color,
    state: AppState,
    data: LibraryData,
    onRemove: (() -> Unit)?,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(235.dp)
            .padding(vertical = 2.dp)
    ) {
        // Añadir a lista (toggles or opens left submenu)
        TrackMenuItem(
            icon = Lucide.ListPlus,
            text = "Añadir a lista",
            tint = if (showPlaylists) accent else CypherColors.TextPrimary,
            iconTint = if (showPlaylists) accent else CypherColors.TextSecondary,
            active = showPlaylists,
            trailing = {
                Icon(
                    Lucide.ChevronLeft,
                    null,
                    tint = if (showPlaylists) accent else CypherColors.TextTertiary,
                    modifier = Modifier.size(14.dp)
                )
            },
            onHover = onHoverPlaylists,
            onClick = onTogglePlaylists
        )

        // Añadir a Canciones que te gustan / Quitar
        TrackMenuItem(
            icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            text = if (isFavorite) "Quitar de tus favoritas" else "Añadir a canciones que te gustan",
            tint = if (isFavorite) accent else CypherColors.TextPrimary,
            iconTint = if (isFavorite) accent else CypherColors.TextSecondary,
            onClick = {
                state.toggleFavorite(view.id)
                onClose()
            }
        )

        // Añadir a la cola
        TrackMenuItem(
            icon = Lucide.List,
            text = "Añadir a la cola",
            onClick = {
                state.addToQueue(view.id)
                onClose()
            }
        )

        // Reproducir a continuación
        TrackMenuItem(
            icon = Lucide.Play,
            text = "Reproducir a continuación",
            onClick = {
                state.playNext(view.id)
                onClose()
            }
        )

        val resolvedTrack = remember(view, data) {
            data.track(view.id) ?: com.cypher.desktop.core.Track(
                id = view.id,
                title = view.title,
                artist = view.artist,
                album = view.album,
                durationMs = view.durationMs,
                path = "",
                artwork = view.artwork ?: view.artworkUrl
            )
        }

        // Ir a radio de la canción
        TrackMenuItem(
            icon = Lucide.Radio,
            text = "Ir a radio de la canción",
            onClick = {
                state.startRadio(resolvedTrack)
                onClose()
            }
        )

        // Crear tarjeta de letras
        TrackMenuItem(
            icon = Lucide.Quote,
            text = "Crear tarjeta de letras",
            onClick = {
                state.openLyricCardDialog(resolvedTrack)
                onClose()
            }
        )

        // Playlist context actions (reordering & removal)
        if (onMoveUp != null || onMoveDown != null || onRemove != null) {
            HorizontalDivider(
                color = CypherColors.Border.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            if (onMoveUp != null) {
                TrackMenuItem(
                    icon = Lucide.ArrowUp,
                    text = "Subir en la playlist",
                    onClick = {
                        onMoveUp()
                        onClose()
                    }
                )
            }

            if (onMoveDown != null) {
                TrackMenuItem(
                    icon = Lucide.ArrowDown,
                    text = "Bajar en la playlist",
                    onClick = {
                        onMoveDown()
                        onClose()
                    }
                )
            }

            if (onRemove != null) {
                TrackMenuItem(
                    icon = Lucide.Trash2,
                    text = "Quitar de la playlist",
                    tint = CypherColors.Danger,
                    iconTint = CypherColors.Danger,
                    onClick = {
                        onRemove()
                        onClose()
                    }
                )
            }
        }
    }
}

@Composable
private fun TrackMenuItem(
    icon: ImageVector,
    text: String,
    tint: Color = CypherColors.TextPrimary,
    iconTint: Color = CypherColors.TextSecondary,
    active: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onHover: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) {
        if (hovered && onHover != null) onHover()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    active -> CypherColors.Pill.copy(alpha = 0.8f)
                    hovered -> CypherColors.Card.copy(alpha = 0.85f)
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

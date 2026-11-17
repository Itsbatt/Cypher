@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.cypher.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInFull
import com.cypher.desktop.ui.glass.GlassBackdropProvider
import com.cypher.desktop.ui.glass.cypherGlass
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.Screen
import com.cypher.desktop.app.UiState
import com.cypher.desktop.core.Lyrics
import com.cypher.desktop.core.RecentSearch
import com.cypher.desktop.player.PlaybackState

/**
 * Surface colours the shell should use. With a custom background they turn translucent
 * so the picture or the glass glows show through, while the text keeps its contrast.
 */
val LocalShellSurface = staticCompositionLocalOf { CypherColors.Surface }
val LocalShellCard = staticCompositionLocalOf { CypherColors.Card }

@Composable
fun CypherApp(state: AppState) {
    val ui by state.state.collectAsState()
    val playback by state.playback.collectAsState()
    val accent = Color(ui.data.preferences.accent)
    // The palette is applied during composition on purpose: the very first frame has to
    // carry the saved colours, otherwise the window flashes the defaults while opening.
    // `apply` ignores equal values, so this never loops.
    CypherColors.apply(ui.data.preferences.colors.toPalette())
    val background = ui.data.preferences.backgroundSettings()
    // Real translucency: the glass and the picture must show through the panels, not
    // just behind them. Glass is lighter because its own glow already adds contrast.
    val surface = when (background.mode) {
        "GLASS" -> CypherColors.Surface.copy(alpha = 0.58f)
        "IMAGE" -> CypherColors.Surface.copy(alpha = 0.70f)
        else -> CypherColors.Surface
    }
    val card = when (background.mode) {
        "GLASS" -> CypherColors.Card.copy(alpha = 0.62f)
        "IMAGE" -> CypherColors.Card.copy(alpha = 0.74f)
        else -> CypherColors.Card
    }

    // The lyrics panel (and every other glass surface) refracts the cover of what is
    // playing. Until a cover exists it falls back to the chosen background.
    val playingView = playback.track?.let { track ->
        viewOf(ui.data, track.id) ?: TrackView(track.id, track.title, track.artist, track.album, track.durationMs, track.artwork)
    }
    val coverSource = playingView?.artwork?.takeIf { it.isNotBlank() } ?: playingView?.artworkUrl
    val coverLoad = rememberArtworkState(coverSource)
    var coverFile by remember(coverSource) { mutableStateOf(com.cypher.desktop.core.ArtworkStore.cachedFile(coverSource)) }
    LaunchedEffect(coverLoad.status, coverSource) {
        if (coverLoad.status == com.cypher.desktop.core.ArtworkStore.Status.AVAILABLE) {
            coverFile = com.cypher.desktop.core.ArtworkStore.cachedFile(coverSource)
        }
    }

    CompositionLocalProviderCompat(surface, card) {
        CypherBackground(background, accent) {
            // One backdrop texture per window; every glass panel samples this same one.
            GlassBackdropProvider(
                mode = background.mode,
                imagePath = background.image,
                accent = accent
            ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wideRight = maxWidth >= 1080.dp
                val searchFocus = remember { FocusRequester() }
                // The rail itself grows (54 -> 212) and pushes the content, exactly as the
                // two menu references: the names and counts never cover the library.
                var railHover by remember { mutableStateOf(false) }
                val railWidth by animateDpAsState(
                    if (railHover || ui.libraryPanelOpen) 220.dp else 62.dp,
                    androidx.compose.animation.core.tween(200)
                )
                // Fixed: the reference panel never grows when the pointer enters it.
                val panelWidth = 301.dp

                Column(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.K) {
                        searchFocus.requestFocus()
                        state.openSearch()
                        true
                    } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        // Esc leaves the expanded screen first, then the search dropdown.
                        if (ui.playerExpanded) {
                            state.collapsePlayer()
                            true
                        } else {
                            state.closeSearch()
                            false
                        }
                    } else false
                }) {
                    AppTopBar(ui, accent, state, searchFocus)
                    Row(
                        // The dock floats over the content, so the content is not cut
                        // short: pages scroll behind it instead of stopping at a strip.
                        Modifier.weight(1f).fillMaxWidth()
                            // A press anywhere outside the dropdown dismisses it, the same way
                            // the field's focus loss does.
                            .onPointerEvent(PointerEventType.Press) { state.closeSearch() }
                    ) {
                        CompactRail(
                            ui, accent, state,
                            width = railWidth,
                            expanded = railWidth > 120.dp,
                            onHover = { railHover = it }
                        )
                        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 22.dp)) {
                            when (ui.screen) {
                                Screen.HOME -> HomeView(ui, accent, state)
                                Screen.LIBRARY -> LibraryView(ui, accent, state)
                                Screen.FAVORITES -> FavoritesView(ui, accent, state)
                                Screen.PLAYLISTS -> PlaylistsView(ui, accent, state)
                                Screen.STATS -> StatsView(ui, accent, state)
                                Screen.WRAPPED -> WrappedView(ui, accent, state)
                                Screen.DOWNLOADS -> DownloadsView(ui, accent, state)
                                Screen.ONLINE -> YouTubeView(ui, accent, state)
                                Screen.IMPORT -> ImportView(ui, accent, state)
                                Screen.SETTINGS -> SettingsView(ui, accent, state)
                            }
                        }
                        if (wideRight) {
                            if (ui.rightPanelOpen) {
                                // Hovering the panel widens it and the centre shrinks by the
                                // same amount, so the layout keeps its proportions.
                                RightInfoPanel(
                                    ui, playback, accent, state,
                                    Modifier.width(panelWidth)
                                )
                            } else {
                                CollapsedRightRail(accent, state)
                            }
                        }
                    }
                }


                FloatingPlayer(
                    playback, ui, accent, state,
                    Modifier
                        .align(Alignment.BottomCenter)
                        // Like the PDF: the dock runs from the rail to the right panel
                        // instead of floating in the middle as a small pill.
                        // Geometry from the reference: 26 dp above the bottom edge and
                        // flush with the content column, not a floating pill.
                        .padding(start = 12.dp, end = 12.dp, bottom = 26.dp)
                )

                // The expanded screen sits above the shell: same player, more room.
                if (ui.playerExpanded) {
                    ExpandedPlayer(playback, ui, accent, state)
                }

                // First run: the local name, straight away. Also reachable later from
                // the profile menu, so there is no second, different rename screen.
                if (ui.profileSetup || !ui.data.profileSetupDone) {
                    ProfileSetupDialog(
                        initialName = ui.data.profile.displayName,
                        accent = accent,
                        firstRun = !ui.data.profileSetupDone,
                        onDismiss = { state.closeProfileSetup() },
                        onSave = { state.saveProfileName(it) }
                    )
                }

                // One picker for the whole app, configured by whoever opened it.
                ui.filePicker?.let { picker ->
                    FilePickerDialog(picker, accent, onDismiss = { state.closeFilePicker() })
                }

                if (ui.searchOpen) {
                    Popup(
                        alignment = Alignment.TopCenter,
                        offset = IntOffset(0, 84),
                        onDismissRequest = { state.closeSearch() },
                        properties = PopupProperties(focusable = false)
                    ) {
                        SearchPanel(ui, accent, state)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun CompositionLocalProviderCompat(surface: Color, card: Color, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalShellSurface provides surface,
        LocalShellCard provides card,
        content = content
    )
}

/* ------------------------------------------------------------- icon rail */

@Composable
private fun CompactRail(
    ui: UiState,
    accent: Color,
    state: AppState,
    width: androidx.compose.ui.unit.Dp,
    expanded: Boolean,
    onHover: (Boolean) -> Unit
) {
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .padding(start = 8.dp)
            .background(CypherColors.Surface, RoundedCornerShape(18.dp))
            .border(1.dp, CypherColors.Border, RoundedCornerShape(18.dp))
            .onPointerEvent(PointerEventType.Enter) { onHover(true) }
            .onPointerEvent(PointerEventType.Exit) { onHover(false) }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(36.dp)
                .background(accent, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, "Cypher", tint = Color(0xFF14101A), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(14.dp))
        RailButton(Lucide.Home, "Inicio", ui.screen == Screen.HOME, accent, expanded) { state.select(Screen.HOME) }
        Spacer(Modifier.height(12.dp))
        RailButton(Lucide.ListMusic, "Playlists", ui.screen == Screen.PLAYLISTS, accent, expanded) { state.setLibraryPanel(true) }
        Spacer(Modifier.height(12.dp))
        RailButton(Lucide.Heart, "Favoritos", ui.screen == Screen.FAVORITES, accent, expanded) { state.select(Screen.FAVORITES) }

        if (expanded) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().padding(start = 11.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PLAYLISTS", color = CypherColors.TextTertiary, fontSize = 8.sp,
                    letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(accent.copy(alpha = 0.20f))
                        .clickable { state.createPlaylist("Nueva lista")?.let { id -> state.openPlaylist(id) } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Lucide.Plus, "Crear playlist", tint = accent, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(ui.data.playlists, key = { it.id }) { playlist ->
                    RailPlaylistRow(playlist, ui, accent, state)
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** One playlist in the expanded rail: cover, name and the song count underneath. */
@Composable
private fun RailPlaylistRow(playlist: com.cypher.desktop.core.Playlist, ui: UiState, accent: Color, state: AppState) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp))
            .background(if (hovered) CypherColors.Pill.copy(alpha = 0.5f) else Color.Transparent)
            .hoverable(interaction)
            .clickable { state.openPlaylist(playlist.id) }
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistArtwork(ui.data, playlist, 36, accent)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playlist.name, color = CypherColors.TextPrimary, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${playlist.trackIds.size} canciones", color = CypherColors.TextTertiary,
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    showLabel: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.height(42.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> accent.copy(alpha = 0.18f)
                    hovered -> CypherColors.Pill.copy(alpha = 0.6f)
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, label,
            tint = if (selected) accent else CypherColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        // Collapsed the rail is icons only, like the reference; expanded it names itself.
        if (showLabel) {
            Spacer(Modifier.width(12.dp))
            Text(
                label, color = if (selected) CypherColors.TextPrimary else CypherColors.TextSecondary,
                fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* --------------------------------------------------------- library flyout */

@Composable
private fun LibraryFlyout(
    ui: UiState,
    accent: Color,
    state: AppState,
    onHover: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .width(304.dp)
            .fillMaxHeight()
            // Flat like the reference drawing: no glass, no shadow, just a surface.
            .background(CypherColors.Surface, RoundedCornerShape(20.dp))
            .border(1.dp, CypherColors.Border, RoundedCornerShape(20.dp))
            .onPointerEvent(PointerEventType.Enter) { onHover(true) }
            .onPointerEvent(PointerEventType.Exit) { onHover(false) }
            .padding(top = 14.dp)
    ) {
        // Only playlists live here: no favorites, no stats, no downloads, no history.
        // One header, one "+", then the list.
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Playlists", color = CypherColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(accent.copy(alpha = 0.20f))
                    .clickable { state.createPlaylist("Nueva lista")?.let { id -> state.openPlaylist(id) }; onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Lucide.Plus, "Crear playlist", tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        if (ui.data.playlists.isEmpty()) {
            Text(
                "Todavía no hay playlists. Crea la primera con el botón de arriba.",
                color = CypherColors.TextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(ui.data.playlists, key = { it.id }) { playlist ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (hovered) CypherColors.Pill.copy(alpha = 0.5f) else Color.Transparent)
                        .hoverable(interaction)
                        .clickable { state.openPlaylist(playlist.id); onClose() }
                        .height(56.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaylistArtwork(ui.data, playlist, 42, accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            playlist.name,
                            color = CypherColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${playlist.trackIds.size} canciones",
                            color = CypherColors.TextTertiary,
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

/**
 * A full-height hover sensor. Without it the panel could only be opened with the
 * keyboard shortcut, because an empty list has no pointer target.
 */
@Composable
private fun ScaffoldHoverArea(onHover: (Boolean) -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Enter) { onHover(true) }
            .onPointerEvent(PointerEventType.Exit) { onHover(false) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

/* --------------------------------------------------------------- top bar */

@Composable
private fun AppTopBar(ui: UiState, accent: Color, state: AppState, searchFocus: FocusRequester) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left zone: one circular button, and it means Settings. It is not a home button,
        // it is not the library, and it never changes its meaning with the screen.
        Box(Modifier.width(236.dp), contentAlignment = Alignment.CenterStart) {
                            Box(
                    Modifier.size(40.dp).clip(CircleShape)
                    .background(
                        if (ui.screen == Screen.SETTINGS) accent.copy(alpha = 0.22f)
                        else CypherColors.Pill.copy(alpha = 0.55f)
                    )
                    .clickable { state.select(Screen.SETTINGS) },
                contentAlignment = Alignment.Center
            ) {
                // The three dots are the settings button, as in the reference layout.
                Icon(
                    Lucide.MoreHorizontal, "Ajustes",
                    tint = if (ui.screen == Screen.SETTINGS) accent else CypherColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // One circle and one pill, both 42 dp tall: at different heights the pair
            // looked misaligned, and the duplicated magnifier read as two controls.
            Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(if (ui.query.isNotBlank()) accent.copy(alpha = 0.22f) else CypherColors.Pill.copy(alpha = 0.75f))
                    .clickable { searchFocus.requestFocus(); state.openSearch() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Lucide.Search, "Buscar", tint = CypherColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = ui.query,
                onValueChange = { state.setQuery(it); state.openSearch() },
                singleLine = true,
                placeholder = { Text("¿Qué quieres reproducir?", color = CypherColors.TextTertiary, fontSize = 13.sp) },
                trailingIcon = {
                    if (ui.query.isNotBlank()) {
                        IconButton(onClick = { state.setQuery("") }) {
                            Icon(Icons.Rounded.Close, "Limpiar", tint = CypherColors.TextTertiary, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Box(Modifier.padding(end = 10.dp).background(CypherColors.Pill, RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                            Text("Ctrl K", color = CypherColors.TextTertiary, fontSize = 10.sp)
                        }
                    }
                },
                shape = RoundedCornerShape(21.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    state.recordQuerySearch(ui.query)
                    state.openSearch()
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent.copy(alpha = 0.55f),
                    unfocusedBorderColor = CypherColors.Border,
                    focusedContainerColor = CypherColors.Pill.copy(alpha = 0.45f),
                    unfocusedContainerColor = CypherColors.Pill.copy(alpha = 0.45f),
                    focusedTextColor = CypherColors.TextPrimary,
                    unfocusedTextColor = CypherColors.TextPrimary
                ),
                modifier = Modifier
                    .width(456.dp)
                    .height(42.dp)
                    .focusRequester(searchFocus)
                    .onFocusChanged { if (it.isFocused) state.openSearch() }
            )
            }
        }
        // Right zone: the local profile. Nothing here talks to a server.
        Box(Modifier.width(236.dp), contentAlignment = Alignment.CenterEnd) {
            var profileMenu by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .hoverable(remember { MutableInteractionSource() })
                    .clickable { profileMenu = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Box(
                    Modifier.size(34.dp).background(accent.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials(ui.data.profile.displayName), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(
                        ui.data.profile.displayName.ifBlank { "Tu perfil" },
                        color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Perfil local",
                        color = CypherColors.TextTertiary, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(Lucide.ChevronDown, "Opciones del perfil", tint = CypherColors.TextTertiary, modifier = Modifier.size(16.dp))
            }
            androidx.compose.material3.DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Cambiar nombre", fontSize = 13.sp) },
                    onClick = { profileMenu = false; state.openProfileSetup() }
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Ajustes", fontSize = 13.sp) },
                    onClick = { profileMenu = false; state.select(Screen.SETTINGS) }
                )
            }
        }
    }
}

/* ------------------------------------------------------------ search menu */

private data class SearchEntry(
    val kind: String,
    val title: String,
    val subtitle: String,
    val artwork: String?,
    val onClick: () -> Unit
)

@Composable
private fun SearchPanel(ui: UiState, accent: Color, state: AppState) {
    val data = ui.data
    val query = ui.query.trim()
    // YouTube Music is part of the search, not a separate screen: a short pause after
    // typing is enough, so the field never fires a request per keystroke.
    LaunchedEffect(query) {
        if (query.length >= 2) {
            kotlinx.coroutines.delay(450)
            if (state.state.value.query.trim() == query && !ui.onlineBusy) {
                state.setOnlineQuery(query)
                state.searchOnline()
            }
        }
    }
    val entries = remember(query, data) {
        if (query.isBlank()) emptyList() else buildList {
            val q = query.lowercase()
            data.search(query).take(6).forEach { track ->
                add(
                    SearchEntry("Canción", track.title, track.artist.ifBlank { "Sin artista" }, track.artwork) {
                        state.recordEntitySearch("TRACK", track.id)
                        state.playQueue(listOf(track.id))
                        state.closeSearch()
                    }
                )
            }
            data.remoteTracks.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }.take(3).forEach { remote ->
                add(
                    SearchEntry("Canción · YouTube", remote.title, remote.artist, remote.artworkUrl) {
                        state.recordEntitySearch("REMOTE", remote.id)
                        state.playQueue(listOf(remote.id))
                        state.closeSearch()
                    }
                )
            }
            data.albums.filter { it.name.lowercase().contains(q) || it.artist.lowercase().contains(q) }
                .take(3).forEach { album ->
                    add(SearchEntry("Álbum", album.name, album.artist, null) {
                        state.recordEntitySearch("ALBUM", album.name)
                        state.select(Screen.LIBRARY)
                        state.setLibrarySection(com.cypher.desktop.app.LibrarySection.ALBUMS)
                        state.openAlbum(album.name)
                        state.closeSearch()
                    })
                }
            data.artists.filter { it.name.lowercase().contains(q) }.take(3).forEach { artist ->
                add(SearchEntry("Artista", artist.name, "${artist.tracks.size} pistas", null) {
                    state.recordEntitySearch("ARTIST", artist.name)
                    state.select(Screen.LIBRARY)
                    state.setLibrarySection(com.cypher.desktop.app.LibrarySection.ARTISTS)
                    state.openArtist(artist.name)
                    state.closeSearch()
                })
            }
            data.playlists.filter { it.name.lowercase().contains(q) }.take(3).forEach { playlist ->
                add(SearchEntry("Lista", playlist.name, data.profile.displayName, playlist.artwork) {
                    state.recordEntitySearch("PLAYLIST", playlist.id)
                    state.openPlaylist(playlist.id)
                    state.closeSearch()
                })
            }
            // Results from the YouTube Music search, when the user already ran it for
            // this same query. Nothing is fetched just by typing.
            if (ui.onlineQuery.equals(query, ignoreCase = true)) {
                ui.onlineHits.take(4).forEach { hit ->
                    add(SearchEntry("YouTube Music", hit.title, hit.artist, hit.thumbnail) {
                        state.recordQuerySearch(query)
                        state.playOnline(hit)
                        state.closeSearch()
                    })
                }
            }
        }
    }

    Column(
        Modifier
            .width(560.dp)
            .background(CypherColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(vertical = 10.dp)
    ) {
        if (query.isBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Búsquedas recientes", color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (data.recentSearches.isNotEmpty()) {
                    TextButton(onClick = { state.clearRecentSearches() }) {
                        Text("Limpiar", color = accent, fontSize = 11.sp)
                    }
                }
            }
            if (data.recentSearches.isEmpty()) {
                Text(
                    "Busca canciones, álbumes, artistas o listas. Lo que abras quedará aquí.",
                    color = CypherColors.TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(data.recentSearches, key = { it.key }) { entry ->
                    RecentRow(entry, accent, state)
                }
            }
        } else if (entries.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Sin resultados para «$query»", color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Tu biblioteca no tiene coincidencias.", color = CypherColors.TextTertiary, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                if (ui.onlineBusy) {
                    Text("Buscando en YouTube Music…", color = accent, fontSize = 12.sp)
                } else {
                    TextButton(onClick = { state.setOnlineQuery(query); state.searchOnline() }) {
                        Text("Buscar «$query» en YouTube Music", color = accent, fontSize = 12.sp)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                item {
                    if (ui.onlineBusy) {
                        Text(
                            "Buscando también en YouTube Music…",
                            color = CypherColors.TextTertiary, fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    } else if (!ui.onlineQuery.equals(query, ignoreCase = true)) {
                        TextButton(
                            onClick = { state.setOnlineQuery(query); state.searchOnline() },
                            modifier = Modifier.padding(start = 6.dp)
                        ) { Text("Añadir resultados de YouTube Music", color = accent, fontSize = 11.sp) }
                    }
                }
                items(entries) { entry ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { entry.onClick() }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp))) {
                            if (entry.artwork != null) {
                                Artwork(TrackView(entry.title, entry.title, "", "", 0, entry.artwork), 40, accent)
                            } else {
                                Box(Modifier.fillMaxSize().background(CypherColors.Pill), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Album, null, tint = accent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, color = CypherColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${entry.kind} · ${entry.subtitle}", color = CypherColors.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Lucide.Play, null, tint = CypherColors.TextTertiary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(entry: RecentSearch, accent: Color, state: AppState) {
    val label = when (entry.kind) {
        "QUERY" -> "Búsqueda"
        "PLAYLIST" -> "Lista"
        "ALBUM" -> "Álbum"
        "ARTIST" -> "Artista"
        else -> "Canción"
    }
    Row(
        Modifier.fillMaxWidth()
            .clickable { state.openRecentSearch(entry) }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp))) {
            if (entry.artwork != null) {
                Artwork(TrackView(entry.key, entry.title, "", "", 0, entry.artwork), 40, accent)
            } else {
                Box(Modifier.fillMaxSize().background(CypherColors.Pill), contentAlignment = Alignment.Center) {
                    Icon(
                        if (entry.kind == "QUERY") Lucide.Search else Icons.Rounded.Album,
                        null, tint = accent, modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = CypherColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(label, entry.subtitle).filter { it.isNotBlank() && it != label }.distinct().joinToString(" · ").ifBlank { label },
                color = CypherColors.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { state.removeRecentSearch(entry.key) }) {
            Icon(Icons.Rounded.Delete, "Quitar de recientes", tint = CypherColors.TextTertiary, modifier = Modifier.size(15.dp))
        }
    }
}

/* ------------------------------------------------------------ right panel */

@Composable
private fun RightInfoPanel(ui: UiState, playback: PlaybackState, accent: Color, state: AppState, modifier: Modifier) {
    val track = playback.track
    val view = track?.let { viewOf(ui.data, it.id) ?: TrackView(it.id, it.title, it.artist, it.album, it.durationMs, it.artwork) }
    val storedLyrics = view?.let { ui.data.lyric(it.id) }
    val lyricsState = rememberSongLyrics(ui.data, view, track?.path)
    val lyrics = lyricsState.lines
    // The panel paints with the record's own tones when the lyrics ask for them.
    val coverBitmap = rememberArtwork(view?.artwork?.takeIf { it.isNotBlank() } ?: view?.artworkUrl)
    val coverColors = rememberCoverPalette(coverBitmap, accent)
    Column(
        modifier
            .fillMaxHeight()
            .padding(end = 12.dp)
            .background(CypherColors.Surface, RoundedCornerShape(20.dp))
            .border(1.dp, CypherColors.Border, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EN REPRODUCCIÓN", color = CypherColors.TextSecondary, fontSize = 9.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = state::expandPlayer) {
                Icon(Icons.Rounded.OpenInFull, "Vista ampliada", tint = CypherColors.TextTertiary, modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = state::toggleRightPanel) {
                Icon(Lucide.ChevronRight, "Contraer panel", tint = CypherColors.TextTertiary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        // Square by construction: the panel is narrower than 292 dp, so a fixed height
        // left a portrait rectangle. The cover now follows the panel width.
        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(CypherColors.Pill),
            contentAlignment = Alignment.Center
        ) {
            if (view != null) {
                Artwork(view, maxWidth.value.toInt(), accent)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Album, null, tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(56.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    view?.title ?: "Nada en reproducción",
                    color = CypherColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    view?.artist?.ifBlank { "Elige una canción" } ?: "Elige una canción de tu biblioteca",
                    color = CypherColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (view != null) {
                IconButton(onClick = { state.toggleFavorite(view.id) }) {
                    val fav = ui.data.isFavorite(view.id)
                    Icon(
                        if (fav) Lucide.Heart else Lucide.Heart,
                        if (fav) "Quitar de favoritos" else "Añadir a favoritos",
                        tint = if (fav) accent else CypherColors.TextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        if (view?.album?.isNotBlank() == true) {
            Spacer(Modifier.height(6.dp))
            Text(
                view.album,
                color = accent, fontSize = 11.sp,
                modifier = Modifier.clickable {
                    state.select(Screen.LIBRARY)
                    state.setLibrarySection(com.cypher.desktop.app.LibrarySection.ALBUMS)
                    state.openAlbum(view.album)
                }
            )
        }
        Spacer(Modifier.height(14.dp))
        RightPanelTabs(ui, accent, state)
        Spacer(Modifier.height(10.dp))
        if (ui.rightPanelTab == "COLA") {
            QueueTab(ui, playback, accent, state, Modifier.fillMaxWidth().weight(1f))
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LETRAS", color = CypherColors.TextSecondary, fontSize = 9.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (storedLyrics != null) {
                Text(
                    when (storedLyrics.source) {
                        "IMPORTED" -> "Importada · ${if (storedLyrics.synced) "LRC" else "sin tiempos"}"
                        "SYNCED" -> "Sincronizada"
                        else -> "Guardada · ${if (storedLyrics.synced) "LRC" else "sin tiempos"}"
                    },
                    color = accent.copy(alpha = 0.85f), fontSize = 9.sp
                )
            } else if (track != null) {
                // The cached YouTube audio lives in a local file, so the path no longer
                // tells the source; the id does.
                val fromYouTube = track.id.startsWith("yt:") || track.path.contains("stream-cache")
                Text(
                    if (fromYouTube) "YouTube Music" else "Local",
                    color = CypherColors.TextTertiary, fontSize = 9.sp
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (lyrics.isEmpty()) {
            Box(
                // No filled card: an empty panel with a big grey block reads as a broken
                // component, a quiet centred note reads as "there is nothing here yet".
                Modifier.fillMaxWidth().weight(1f).padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Lucide.ListMusic, null, tint = CypherColors.TextTertiary.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Sin letras disponibles", color = CypherColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Cypher no ha encontrado letras para esta canción.",
                        color = CypherColors.TextTertiary, fontSize = 10.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (view != null) {
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = {
                            state.openFilePicker(
                                FilePickerConfig(
                                    title = "Elige la letra",
                                    subtitle = "Archivos .lrc o .txt con la letra sincronizada.",
                                    extensions = setOf("lrc", "txt"),
                                    onPick = { files ->
                                        files.firstOrNull()?.let { state.notify(state.importLyricsFile(it, view.id)) }
                                    }
                                )
                            )
                        }) {
                            Text("Añadir letra .lrc", color = accent, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            LyricsStage(
                lines = lyrics,
                positionMs = playback.positionMs,
                prefs = ui.data.preferences,
                accent = accent,
                artworkColors = coverColors,
                fullscreen = false,
                playing = playback.playing,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

/** LETRAS / COLA: the two things the info panel shows without leaving the song. */
@Composable
private fun RightPanelTabs(ui: UiState, accent: Color, state: AppState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("LETRAS", "COLA").forEach { id ->
            val selected = ui.rightPanelTab == id
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { state.setRightPanelTab(id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    id,
                    color = if (selected) accent else CypherColors.TextTertiary,
                    fontSize = 9.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** The queue as a plain list: tapping a row jumps to it, so nothing is hidden. */
@Composable
private fun QueueTab(ui: UiState, playback: PlaybackState, accent: Color, state: AppState, modifier: Modifier) {
    if (ui.queue.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("La cola está vacía", color = CypherColors.TextSecondary, fontSize = 12.sp)
        }
        return
    }
    val current = ui.queue.getOrNull(ui.queueIndex)
    LazyColumn(modifier) {
        items(ui.queue.size) { index ->
            val id = ui.queue[index]
            val view = viewOf(ui.data, id) ?: return@items
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { state.playQueue(ui.queue, index) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(view, 34, accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        view.title,
                        color = if (id == playback.track?.id) accent else CypherColors.TextPrimary,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        view.artist, color = CypherColors.TextTertiary, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (id == current && id == playback.track?.id) {
                    Icon(Icons.Rounded.Equalizer, null, tint = accent, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun CollapsedRightRail(accent: Color, state: AppState) {
    Column(
        Modifier.width(44.dp).fillMaxHeight().padding(end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = state::toggleRightPanel) {
            Icon(Icons.Rounded.ChevronLeft, "Expandir panel", tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

/* -------------------------------------------------------- floating player */

@Composable
private fun FloatingPlayer(playback: PlaybackState, ui: UiState, accent: Color, state: AppState, modifier: Modifier) {
    val repeat = ui.data.preferences.repeat
    var queueOpen by remember { mutableStateOf(false) }
    val currentView = playback.track?.let { track ->
        viewOf(ui.data, track.id) ?: TrackView(track.id, track.title, track.artist, track.album, track.durationMs, track.artwork)
    }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                // The shell is flat, so the dock needs an opaque plate: at 0.55 the cards
                // behind showed through and looked like a broken bright band. The glass
                // shader still runs at full density, which keeps the paint path alive.
                .background(CypherColors.Surface, RoundedCornerShape(22.dp))
                .cypherGlass(accent, cornerRadiusDp = 22f, opacity = 1f, density = 1f, strength = 0f)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.width(250.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(currentView, 50, accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        playback.track?.title ?: "Nada en reproducción",
                        color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        playback.track?.artist ?: "Elige una pista de tu biblioteca",
                        color = CypherColors.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    // The row under the title is the app's only place for "what is it doing":
                    // without it, a YouTube hit looks like it does nothing while it downloads.
                    val status = playback.error ?: if (ui.onlineBusy) ui.onlineStatus else null
                    status?.let {
                        Text(
                            it,
                            color = if (playback.error != null) CypherColors.Danger else CypherColors.TextTertiary,
                            fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = state::toggleShuffle) {
                        Icon(
                            Lucide.Shuffle, "Aleatorio",
                            tint = if (ui.data.preferences.shuffle) accent else CypherColors.TextTertiary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = state::playPrevious) {
                        Icon(Lucide.SkipBack, "Anterior", tint = CypherColors.TextSecondary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = state::togglePlayback) {
                        Box(Modifier.size(38.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(
                                if (playback.playing) Lucide.Pause else Lucide.Play,
                                "Reproducir o pausar", tint = Color(0xFF14101A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(onClick = { state.playNext() }) {
                        Icon(Lucide.SkipForward, "Siguiente", tint = CypherColors.TextSecondary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = state::cycleRepeat) {
                        Icon(
                            if (repeat == "ONE") Lucide.RepeatOne else Lucide.Repeat,
                            if (repeat == "OFF") "Repetición desactivada" else "Repetición ${if (repeat == "ONE") "una" else "toda la cola"}",
                            tint = if (repeat == "OFF") CypherColors.TextTertiary else accent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatTime(playback.positionMs), color = CypherColors.TextTertiary, fontSize = 10.sp)
                    Box(Modifier.width(360.dp).padding(horizontal = 10.dp)) {
                        SlimSlider(
                            value = if (playback.durationMs > 0) (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f) else 0f,
                            onValueChange = state::seek,
                            color = accent,
                            enabled = playback.track != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(formatTime(playback.durationMs), color = CypherColors.TextTertiary, fontSize = 10.sp)
                }
            }
            Row(Modifier.width(200.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { queueOpen = !queueOpen }) {
                    Icon(
                        Lucide.ListMusic,
                        if (queueOpen) "Cerrar la cola" else "Cola de reproducción",
                        tint = if (queueOpen) accent else CypherColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Icon(Lucide.Volume, null, tint = CypherColors.TextTertiary, modifier = Modifier.size(16.dp))
                Box(Modifier.width(104.dp).padding(start = 8.dp, end = 6.dp)) {
                    SlimSlider(
                        value = playback.volume,
                        onValueChange = state::setVolume,
                        color = accent
                    )
                }
            }
        }

        // In-window queue instead of a DropdownMenu: at the bottom edge the popup was
        // clipped/placed off-screen, so the button looked dead. This panel is part of the
        // layout and always visible above the player.
        if (queueOpen) {
            val pending = ui.queue.drop(ui.queueIndex.coerceAtLeast(0)).take(12)
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    // Sits left of the lyrics panel instead of covering it.
                    .padding(end = 336.dp, bottom = 104.dp)
                    .width(320.dp)
                    .background(CypherColors.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("COLA · ${pending.size}", color = CypherColors.TextTertiary, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { queueOpen = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Close, "Cerrar la cola", tint = CypherColors.TextTertiary, modifier = Modifier.size(14.dp))
                    }
                }
                if (pending.isEmpty()) {
                    Text("La cola está vacía", color = CypherColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                } else {
                    pending.forEach { id ->
                        val view = viewOf(ui.data, id)
                        if (view != null) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        state.playQueue(ui.queue, ui.queue.indexOf(id))
                                        queueOpen = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Artwork(view, 28, accent)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(view.title, color = CypherColors.TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(view.artist, color = CypherColors.TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (id == playback.track?.id) {
                                    Icon(Icons.Rounded.Equalizer, null, tint = accent, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.cypher.desktop.ui

import com.cypher.desktop.core.videoKey

import androidx.compose.ui.unit.Dp

import androidx.compose.ui.layout.onSizeChanged

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isSecondaryPressed

import androidx.compose.ui.geometry.Size

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.geometry.CornerRadius

import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.foundation.gestures.detectHorizontalDragGestures

import androidx.compose.foundation.Canvas

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.app.AppState
import com.cypher.desktop.core.ArtworkStore
import com.cypher.desktop.core.LibraryData
import com.cypher.desktop.core.Playlist
import com.cypher.desktop.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.Locale

/** A track as the UI sees it: local file, or a favorite that only exists in the account. */
data class TrackView(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artwork: String?,
    val artworkUrl: String? = null,
    val playable: Boolean = true
)

fun viewOf(data: LibraryData, id: String): TrackView? {
    data.track(id)?.let {
        // No embedded art? The album folder usually carries cover.jpg / folder.jpg, and
        // that is a real cover, not an invented one.
        val art = it.artwork?.takeIf { value -> value.isNotBlank() }
            ?: com.cypher.desktop.core.ArtworkStore.folderArtworkFor(it.path)
        return TrackView(it.id, it.title, it.artist, it.album, it.durationMs, art)
    }
    data.remoteTrack(id)?.let {
        return TrackView(
            it.id,
            it.title.ifBlank { "Canción importada" },
            it.artist.ifBlank { "Artista desconocido" },
            it.album,
            it.durationMs,
            null,
            it.artworkUrl,
            playable = it.source == "YOUTUBE" && it.available &&
                it.videoKey().isNotBlank()
        )
    }
    return null
}

fun viewsOf(data: LibraryData, ids: List<String>): List<TrackView> = ids.mapNotNull { viewOf(data, it) }

@Composable
fun SectionHeader(label: String, accent: Color, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(accent, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, color = CypherColors.TextSecondary, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(LocalShellCard.current, RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) { content() }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    accent: Color,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(64.dp).background(accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, color = CypherColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = CypherColors.TextTertiary, fontSize = 12.sp, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF14101A))
            ) {
                Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Deterministic cover for tracks and playlists with no embedded art: a gradient from
 * the id plus the initials. It is obvious placeholder art, not invented metadata.
 */
fun coverBrush(seed: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFF2B1B4D), Color(0xFF9D7BB0)),
        listOf(Color(0xFF14343B), Color(0xFF7FC8A9)),
        listOf(Color(0xFF3B1F2B), Color(0xFFE0A96D)),
        listOf(Color(0xFF1B2440), Color(0xFF6FA8DC)),
        listOf(Color(0xFF331B1B), Color(0xFFE06A6A)),
        listOf(Color(0xFF241F14), Color(0xFFC9B458))
    )
    val index = (seed.hashCode().toLong() and 0x7FFFFFFF).mod(palettes.size.toLong()).toInt()
    return Brush.linearGradient(palettes[index])
}

fun coverInitials(title: String): String {
    val words = title.trim().split(" ").filter { it.isNotBlank() }
    if (words.isEmpty()) return "♪"
    return if (words.size == 1) words.first().take(2).uppercase()
    else (words[0].take(1) + words[1].take(1)).uppercase()
}

@Composable
fun Artwork(track: TrackView?, size: Int, accent: Color) {
    // Small rows want a tight radius; a 292 px cover with size/4 would look like a circle.
    val radius = when {
        size >= 140 -> 18
        size >= 60 -> size / 6
        else -> size / 4
    }
    val shape = RoundedCornerShape(radius.dp)
    // Local art, then the imported cover, then the cached copy: the same picture on every
    // screen because they all read from one store.
    val load = rememberArtworkState(track?.artwork?.takeIf { it.isNotBlank() } ?: track?.artworkUrl)
    val bitmap = load.bitmap
    Box(
        Modifier.size(size.dp).clip(shape).background(coverBrush(track?.id ?: track?.title ?: "cypher")),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (size >= 34) {
            // A placeholder, and it has to look like one: a small disc with the initials,
            // not giant letters that pass for a real cover.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.MusicNote, null,
                    tint = Color.White.copy(alpha = 0.42f),
                    modifier = Modifier.size((size / 7).dp.coerceAtLeast(10.dp))
                )
                Spacer(Modifier.height((size / 30).dp.coerceAtLeast(2.dp)))
                Text(
                    coverInitials(track?.title ?: ""),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = (size / 6).coerceAtLeast(10).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Icon(Icons.Rounded.Album, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size((size / 2).dp))
        }
    }
}

/**
 * The state of one cover: loading, available, missing, corrupt or offline. Screens use
 * it to tell "todavía no" apart from "no hay", instead of drawing an empty hole.
 */
@Composable
fun rememberArtworkState(source: String?): ArtworkStore.Load {
    var load by remember(source) {
        mutableStateOf(ArtworkStore.peek(source) ?: ArtworkStore.Load(null, ArtworkStore.Status.LOADING, source))
    }
    LaunchedEffect(source) {
        load = ArtworkStore.load(source)
    }
    return load
}

/** Convenience for the call sites that only need the picture. */
@Composable
fun rememberArtwork(source: String?): ImageBitmap? = rememberArtworkState(source).bitmap

/**
 * A playlist cover: the one the user chose, or a mosaic of its songs' covers. Only when
 * there is nothing real does it fall back to the placeholder, so a playlist never shows
 * an invented album cover.
 */
@Composable
fun PlaylistArtwork(
    data: LibraryData,
    playlist: Playlist,
    size: Int,
    accent: Color
) {
    val radius = when {
        size >= 140 -> 18
        size >= 60 -> size / 6
        else -> size / 4
    }
    val shape = RoundedCornerShape(radius.dp)
    if (!playlist.artwork.isNullOrBlank()) {
        Artwork(TrackView(playlist.id, playlist.name, "", "", 0, playlist.artwork), size, accent)
        return
    }
    val sources = remember(data.tracks, playlist.trackIds) {
        playlist.trackIds
            .mapNotNull { id -> data.track(id)?.artwork?.takeIf { it.isNotBlank() } }
            .distinct()
            .take(4)
    }
    Box(
        Modifier.size(size.dp).clip(shape).background(coverBrush(playlist.id)),
        contentAlignment = Alignment.Center
    ) {
        when (sources.size) {
            0 -> Text(
                coverInitials(playlist.name),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = (size / 4).sp,
                fontWeight = FontWeight.Bold
            )
            1 -> Artwork(TrackView(playlist.id, playlist.name, "", "", 0, sources.first()), size, accent)
            else -> {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        sources.take(2).forEach { source ->
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                Artwork(TrackView(source, playlist.name, "", "", 0, source), size / 2, accent)
                            }
                        }
                    }
                    if (sources.size > 2) {
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            sources.drop(2).forEach { source ->
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    Artwork(TrackView(source, playlist.name, "", "", 0, source), size / 2, accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TrackRow(
    view: TrackView,
    data: LibraryData,
    accent: Color,
    state: AppState,
    currentId: String? = null,
    showFavorite: Boolean = true,
    onRemove: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }
    val active = currentId == view.id

    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    active -> accent.copy(alpha = 0.14f)
                    hovered -> CypherColors.Card.copy(alpha = 0.8f)
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    menu = true
                }
            }
            .clickable(enabled = view.playable) { state.playQueue(listOf(view.id)) }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(view, 38, accent)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                view.title,
                color = if (active) accent else CypherColors.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(view.artist.takeIf { it.isNotBlank() }, view.album.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
                    .ifBlank { if (view.playable) "" else "No disponible" },
                color = CypherColors.TextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Downloaded (a file on this computer) or just a reference to stream.
        val isDownloaded = data.track(view.id) != null
        Icon(
            if (isDownloaded) Lucide.Download else Lucide.Cloud,
            if (isDownloaded) "Descargada" else "Solo referencia",
            tint = if (isDownloaded) accent.copy(alpha = 0.9f) else CypherColors.TextTertiary.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(formatTime(view.durationMs), color = CypherColors.TextTertiary, fontSize = 11.sp)
        if (showFavorite) {
            IconButton(onClick = { state.toggleFavorite(view.id) }) {
                Icon(
                    if (data.isFavorite(view.id)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (data.isFavorite(view.id)) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (data.isFavorite(view.id)) accent else CypherColors.TextTertiary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "Más opciones", tint = CypherColors.TextTertiary, modifier = Modifier.size(18.dp))
            }
            TrackContextMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                view = view,
                data = data,
                accent = accent,
                state = state,
                onRemove = onRemove,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown
            )
        }
    }
}

@Composable
fun Pill(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else CypherColors.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) accent else CypherColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun PrimaryButton(label: String, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF14101A))
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AddButton(label: String, accent: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF14101A))
    ) {
        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SyncBadge(text: String, accent: Color, warning: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Cloud, null, tint = if (warning) CypherColors.Danger else accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (warning) CypherColors.Danger else CypherColors.TextTertiary, fontSize = 11.sp)
    }
}

@Composable
fun SortMenu(current: com.cypher.desktop.app.SortMode, accent: Color, onSelect: (com.cypher.desktop.app.SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("Orden: ${current.label}", color = accent, fontSize = 12.sp)
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = accent, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            com.cypher.desktop.app.SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label, fontSize = 12.sp) },
                    onClick = { onSelect(mode); open = false }
                )
            }
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}

fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 60 -> "${minutes} min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

fun initials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/**
 * A slim, flat slider: 4 dp track and a small round thumb, no Material elevation, no
 * overlay ripple. The stock Slider is 48 dp tall and its thumb is much heavier than the
 * rest of this interface, which is why the progress and volume rows looked off.
 */
@Composable
fun SlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 5.dp
) {
    var widthPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }
    val fraction = value.coerceIn(0f, 1f)

    fun update(x: Float) {
        if (enabled) onValueChange((x / widthPx).coerceIn(0f, 1f))
    }

    Box(
        modifier
            .height(18.dp)
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled, widthPx) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> update(offset.x) }
            }
            .pointerInput(enabled, widthPx) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ -> update(change.position.x) }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val track = trackHeight.toPx()
            val radius = track / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(0f, centerY - radius),
                size = Size(size.width, track),
                cornerRadius = CornerRadius(radius)
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = if (enabled) color else color.copy(alpha = 0.4f),
                    topLeft = Offset(0f, centerY - radius),
                    size = Size(size.width * fraction, track),
                    cornerRadius = CornerRadius(radius)
                )
            }
            drawCircle(
                color = if (enabled) color else color.copy(alpha = 0.4f),
                radius = thumbRadius.toPx(),
                center = Offset((size.width * fraction).coerceIn(thumbRadius.toPx(), size.width - thumbRadius.toPx()), centerY)
            )
        }
    }
}

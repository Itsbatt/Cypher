package com.cypher.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.cypher.music.model.Track
import com.cypher.music.playback.RepeatMode
import com.cypher.music.ui.theme.AppleMusicRed
import com.cypher.music.ui.theme.IosDarkCard
import com.cypher.music.ui.theme.IosDarkSeparator
import com.cypher.music.ui.theme.IosDarkTextPrimary
import com.cypher.music.ui.theme.IosDarkTextSecondary
import com.cypher.music.ui.utils.AppHaptics
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CypherQueueSheet(
    queue: List<Track>,
    queueIndex: Int,
    currentTrack: Track?,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSelectIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onMoveIndex: (Int, Int) -> Unit = { _, _ -> },
    onClearQueue: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 58.dp.toPx() }
    var showClearConfirm by remember { mutableStateOf(false) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val latestQueue by rememberUpdatedState(queue)
    val latestQueueIndex by rememberUpdatedState(queueIndex)
    val latestOnMove by rememberUpdatedState(onMoveIndex)

    val visible = remember(queue, queueIndex, query) {
        queue.withIndex().filter { (index, track) ->
            index != queueIndex && (query.isBlank() || track.matches(query))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF21C1C1E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "A continuación",
                    color = IosDarkTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        enabled = queue.size > 1
                    ) {
                        Text(
                            text = "Vaciar",
                            color = if (queue.size > 1) IosDarkTextSecondary else IosDarkTextSecondary.copy(alpha = 0.35f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Aleatorio",
                            tint = if (isShuffle) AppleMusicRed else IosDarkTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(onClick = onToggleRepeat) {
                        Icon(
                            imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Repetir",
                            tint = if (repeatMode != RepeatMode.OFF) AppleMusicRed else IosDarkTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Current Playing Track Card
            if (currentTrack != null) {
                Text(
                    text = "EN REPRODUCCIÓN",
                    color = AppleMusicRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = currentTrack.displayArtworkUrl,
                        contentDescription = currentTrack.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(IosDarkCard)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack.title,
                            color = AppleMusicRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack.artist,
                            color = IosDarkTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = { onRemoveIndex(queueIndex); onDismiss() }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Quitar la canción actual de la cola",
                            tint = IosDarkTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Reproduciendo ahora",
                        tint = AppleMusicRed,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Search inside the queue
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = {
                    Text("Buscar en la cola", color = IosDarkTextSecondary, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = IosDarkTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Borrar búsqueda",
                                tint = IosDarkTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.06f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = AppleMusicRed
                ),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(
                color = IosDarkSeparator,
                thickness = 0.5.dp,
                modifier = Modifier.padding(top = 12.dp)
            )

            Text(
                text = if (query.isBlank()) {
                    "SIGUIENTES (${(queue.size - 1).coerceAtLeast(0)})"
                } else {
                    "RESULTADOS (${visible.size})"
                },
                color = IosDarkTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
            )

            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isBlank()) {
                            "No hay canciones siguientes en la cola"
                        } else {
                            "Ninguna canción coincide con «$query»"
                        },
                        color = IosDarkTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    itemsIndexed(visible) { position, entry ->
                        val index = entry.index
                        val track = entry.value
                        val latestRowIndex by rememberUpdatedState(index)
                        val isDragging = index == draggingIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffset else 0f
                                    shadowElevation = if (isDragging) with(density) { 10.dp.toPx() } else 0f
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDragging) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable {
                                    onSelectIndex(index)
                                    onDismiss()
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = track.displayArtworkUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(IosDarkCard)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = IosDarkTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    color = IosDarkTextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Dragging only makes sense on an unfiltered, complete list.
                            if (query.isBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        // Keyed by Unit: a reorder must not restart the gesture.
                                        .pointerInput(Unit) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggingIndex = latestRowIndex
                                                    dragOffset = 0f
                                                    AppHaptics.reorderTick()
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffset += dragAmount.y
                                                    val shift = (dragOffset / rowHeightPx).roundToInt()
                                                    if (shift != 0) {
                                                        val currentPos = latestQueue.indices
                                                            .filter { it != latestQueueIndex }
                                                            .indexOf(draggingIndex)
                                                        val maxPos = latestQueue.indices.count { it != latestQueueIndex } - 1
                                                        val targetPos = if (currentPos >= 0 && maxPos >= 0) {
                                                            (currentPos + shift).coerceIn(0, maxPos)
                                                        } else {
                                                            currentPos
                                                        }
                                                        if (currentPos >= 0 && targetPos != currentPos) {
                                                            val targetIndex = latestQueue.indices
                                                                .filter { it != latestQueueIndex }[targetPos]
                                                            latestOnMove(draggingIndex, targetIndex)
                                                            draggingIndex = targetIndex
                                                            dragOffset -= (targetPos - currentPos) * rowHeightPx
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggingIndex = -1
                                                    dragOffset = 0f
                                                },
                                                onDragCancel = {
                                                    draggingIndex = -1
                                                    dragOffset = 0f
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DragHandle,
                                        contentDescription = "Reordenar ${track.title}",
                                        tint = IosDarkTextSecondary.copy(alpha = 0.55f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(onClick = { onRemoveIndex(index) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Quitar",
                                    tint = IosDarkTextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (position != visible.lastIndex) {
                            HorizontalDivider(
                                color = IosDarkSeparator.copy(alpha = 0.4f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 54.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Vaciar la cola", color = IosDarkTextPrimary) },
            text = {
                Text(
                    "Se quitarán todas las canciones de la cola excepto la que está sonando. " +
                        "Tu biblioteca, tus playlists y tus descargas no se tocan.",
                    color = IosDarkTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearQueue()
                    AppHaptics.actionClick(true)
                }) { Text("Vaciar", color = AppleMusicRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancelar", color = IosDarkTextSecondary)
                }
            },
            containerColor = Color(0xFF222226)
        )
    }
}

private fun Track.matches(query: String): Boolean {
    val q = query.trim()
    return title.contains(q, ignoreCase = true) ||
        artist.contains(q, ignoreCase = true)
}

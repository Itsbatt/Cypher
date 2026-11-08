package com.cypher.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.data.LiquidGlassConfig
import com.cypher.music.data.LyricsSettingsManager
import com.cypher.music.model.Track
import com.cypher.music.network.LrcLibService
import com.cypher.music.network.TrackLyrics
import com.cypher.music.ui.share.LyricsShareSheet
import com.cypher.music.ui.share.MAX_SHARE_LINES
import com.cypher.music.ui.theme.AmbientArtworkColors
import com.cypher.music.ui.theme.IosDarkTextSecondary
import kotlin.math.roundToInt

@Composable
fun CypherLyricsSheet(
    track: Track,
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Float) -> Unit,
    onClose: () -> Unit,
    accentColor: Color = Color(0xFFFA243C),
    liquidGlassConfig: LiquidGlassConfig = LiquidGlassConfig(),
    ambientColors: AmbientArtworkColors = AmbientArtworkColors(accentColor, accentColor, accentColor),
    showBackground: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember { LyricsSettingsManager(context) }
    val lyricsSettings by settingsManager.settings.collectAsState()

    var lyricsState by remember(track.id) { mutableStateOf<TrackLyrics?>(null) }
    var isLoading by remember(track.id) { mutableStateOf(true) }

    LaunchedEffect(track.id) {
        isLoading = true
        lyricsState = LrcLibService.getLyrics(track)
        isLoading = false
    }

    val lines = lyricsState?.lines ?: emptyList()
    val isSynced = lyricsState?.isSynced == true && lines.isNotEmpty()

    val plainLines = remember(lyricsState?.plainLyrics) {
        lyricsState?.plainLyrics
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    // Fragment sharing: the first tap sets the start of the fragment and the next
    // one its end, so the shared block is always a run of consecutive lines.
    var selectionMode by remember(track.id) { mutableStateOf(false) }
    var selectionAnchor by remember(track.id) { mutableStateOf<Int?>(null) }
    var selectionEnd by remember(track.id) { mutableStateOf<Int?>(null) }
    var shareLines by remember(track.id) { mutableStateOf<List<String>?>(null) }
    val hasLyrics = lines.isNotEmpty() || plainLines.isNotEmpty()

    val selectedIndices: Set<Int> = remember(selectionAnchor, selectionEnd) {
        val anchor = selectionAnchor
        val end = selectionEnd
        if (anchor == null || end == null) emptySet()
        else (minOf(anchor, end)..maxOf(anchor, end)).toSet()
    }

    fun clearSelection() {
        selectionAnchor = null
        selectionEnd = null
    }

    fun beginSelection(index: Int) {
        selectionAnchor = index
        selectionEnd = index
    }

    fun extendSelection(index: Int, lineCount: Int) {
        val anchor = selectionAnchor ?: run {
            beginSelection(index)
            return
        }
        // Grow towards the touched line but never past MAX lines, so the fragment
        // stays short and contiguous from the chosen start.
        val capped = if (index >= anchor) {
            index.coerceAtMost(anchor + MAX_SHARE_LINES - 1)
        } else {
            index.coerceAtLeast(anchor - MAX_SHARE_LINES + 1)
        }
        selectionEnd = capped.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
    }

    fun selectLine(index: Int, lineCount: Int) {
        val anchor = selectionAnchor
        when {
            anchor == null -> beginSelection(index)
            // Tapping the only selected line again clears the fragment.
            anchor == index && selectionEnd == index -> clearSelection()
            else -> extendSelection(index, lineCount)
        }
    }

    val activeIndex = remember(currentPositionMs, lines, isSynced) {
        if (!isSynced) 0
        else {
            val idx = lines.indexOfLast { it.timeMs <= currentPositionMs }
            if (idx >= 0) idx else 0
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex, isSynced, selectionMode) {
        if (isSynced && activeIndex in lines.indices && !listState.isScrollInProgress && !selectionMode) {
            listState.animateScrollToItem(
                (activeIndex - 1).coerceAtLeast(0)
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (showBackground) {
            LiquidGlassBackground(
                config = liquidGlassConfig,
                dynamicColors = ambientColors
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Sleek Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSynced) Icons.Filled.Mic else Icons.Filled.GraphicEq,
                            contentDescription = "Letras en tiempo real",
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = when {
                            selectionMode -> "FRAGMENTO SEGUIDO"
                            isSynced -> "LETRAS EN VIVO"
                            else -> "LETRAS"
                        },
                        color = if (selectionMode) accentColor else Color.White.copy(alpha = 0.90f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasLyrics) {
                        IconButton(
                            onClick = {
                                clearSelection()
                                selectionMode = !selectionMode
                            },
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = if (selectionMode) "Cancelar selección" else "Compartir letra",
                                tint = if (selectionMode) accentColor else Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cerrar letras",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = accentColor,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Buscando letras...",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (isSynced) {
                    // Synced lyrics view with smooth scrolling, edge fade, and tap-to-seek
                    val textAlign = lyricsSettings.alignment.textAlign
                    val activeFontSize = lyricsSettings.fontSize.activeSp
                    val inactiveFontSize = lyricsSettings.fontSize.inactiveSp

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        0.08f to Color.Black,
                                        0.90f to Color.Black,
                                        1.0f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(lines) { index, line ->
                                val isActive = index == activeIndex
                                val isSelected = selectedIndices.contains(index)
                                val lineAlpha by animateFloatAsState(
                                    targetValue = if (isActive || isSelected) 1.0f else 0.40f,
                                    animationSpec = tween(300),
                                    label = "lineAlpha"
                                )
                                val lineScale by animateFloatAsState(
                                    targetValue = if (isActive) 1.03f else 0.98f,
                                    animationSpec = tween(300),
                                    label = "lineScale"
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) accentColor.copy(alpha = 0.14f)
                                            else Color.Transparent
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            if (selectionMode) {
                                                selectLine(index, lines.size)
                                            } else if (durationMs > 0) {
                                                val targetProgress = (line.timeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                                onSeekTo(targetProgress)
                                            }
                                        }
                                        .pointerInput(index, lines.size) {
                                            var origin = 0f
                                            var travelled = 0f
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    origin = index.toFloat()
                                                    travelled = 0f
                                                    selectionMode = true
                                                    beginSelection(index)
                                                },
                                                onDrag = { change, amount ->
                                                    travelled += amount.y
                                                    val rowPx = 58.dp.toPx().coerceAtLeast(1f)
                                                    val target = (origin + travelled / rowPx).roundToInt()
                                                    extendSelection(
                                                        target.coerceIn(0, lines.lastIndex.coerceAtLeast(0)),
                                                        lines.size
                                                    )
                                                    change.consume()
                                                }
                                            )
                                        }
                                        .padding(vertical = 6.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectionMode) {
                                        SelectionDot(selected = isSelected, accentColor = accentColor)
                                        Spacer(modifier = Modifier.size(10.dp))
                                    }
                                    Text(
                                        text = line.text.ifEmpty { "♪" },
                                        color = if (isActive || isSelected) Color.White else Color.White.copy(alpha = 0.45f),
                                        fontSize = if (isActive) activeFontSize else inactiveFontSize,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        lineHeight = if (isActive) 34.sp else 28.sp,
                                        textAlign = textAlign,
                                        style = TextStyle(
                                            shadow = if (isActive) Shadow(
                                                color = Color.Black.copy(alpha = 0.6f),
                                                blurRadius = 8f
                                            ) else null
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .scale(lineScale)
                                            .alpha(lineAlpha)
                                    )
                                }
                            }
                        }
                    }
                } else if (plainLines.isNotEmpty()) {
                    // Plain lyrics: one row per line so a fragment can be selected.
                    val textAlign = lyricsSettings.alignment.textAlign
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                    ) {
                        itemsIndexed(plainLines) { index, line ->
                            val isSelected = selectedIndices.contains(index)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) accentColor.copy(alpha = 0.14f)
                                        else Color.Transparent
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (selectionMode) selectLine(index, plainLines.size)
                                    }
                                    .pointerInput(index, plainLines.size) {
                                        var origin = 0f
                                        var travelled = 0f
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                origin = index.toFloat()
                                                travelled = 0f
                                                selectionMode = true
                                                beginSelection(index)
                                            },
                                            onDrag = { change, amount ->
                                                travelled += amount.y
                                                val rowPx = 58.dp.toPx().coerceAtLeast(1f)
                                                val target = (origin + travelled / rowPx).roundToInt()
                                                extendSelection(
                                                    target.coerceIn(0, plainLines.lastIndex.coerceAtLeast(0)),
                                                    plainLines.size
                                                )
                                                change.consume()
                                            }
                                        )
                                    }
                                    .padding(vertical = 5.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectionMode) {
                                    SelectionDot(selected = isSelected, accentColor = accentColor)
                                    Spacer(modifier = Modifier.size(10.dp))
                                }
                                Text(
                                    text = line,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                    fontSize = lyricsSettings.fontSize.inactiveSp,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 30.sp,
                                    textAlign = textAlign,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                } else {
                    // No lyrics available fallback
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Letras no disponibles",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "No se encontraron letras sincronizadas para esta pista.",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selectionMode && !isLoading) {
                SelectionBar(
                    count = selectedIndices.size,
                    accentColor = accentColor,
                    onClear = { clearSelection() },
                    onCreate = {
                        val chosen = if (isSynced) {
                            selectedIndices.sorted().mapNotNull { lines.getOrNull(it)?.text?.trim() }
                        } else {
                            selectedIndices.sorted().mapNotNull { plainLines.getOrNull(it)?.trim() }
                        }.filter { it.isNotEmpty() }
                        if (chosen.isNotEmpty()) shareLines = chosen
                    }
                )
            }
        }

        shareLines?.let { fragment ->
            LyricsShareSheet(
                track = track,
                lines = fragment,
                accentColor = accentColor,
                onDismiss = { shareLines = null }
            )
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean, accentColor: Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(if (selected) accentColor else Color.White.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = if (selected) accentColor else Color.White.copy(alpha = 0.28f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF160F1D),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    accentColor: Color,
    onClear: () -> Unit,
    onCreate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (count == 0) "Toca la primera línea" else "$count/$MAX_SHARE_LINES líneas seguidas",
            color = IosDarkTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onClear, enabled = count > 0) {
            Text(
                text = "Limpiar",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 13.sp
            )
        }
        Button(
            onClick = onCreate,
            enabled = count > 0,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color(0xFF160F1D),
                disabledContainerColor = accentColor.copy(alpha = 0.30f),
                disabledContentColor = Color(0xFF160F1D).copy(alpha = 0.55f)
            )
        ) {
            Text(
                text = "Crear tarjeta",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

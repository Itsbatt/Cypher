package com.cypher.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.cypher.music.ui.theme.AmbientArtworkColors
import com.cypher.music.ui.theme.IosDarkTextSecondary

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

    val activeIndex = remember(currentPositionMs, lines, isSynced) {
        if (!isSynced) 0
        else {
            val idx = lines.indexOfLast { it.timeMs <= currentPositionMs }
            if (idx >= 0) idx else 0
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex, isSynced) {
        if (isSynced && activeIndex in lines.indices && !listState.isScrollInProgress) {
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
                        text = if (isSynced) "LETRAS EN VIVO" else "LETRAS",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
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
                            val lineAlpha by animateFloatAsState(
                                targetValue = if (isActive) 1.0f else 0.40f,
                                animationSpec = tween(300),
                                label = "lineAlpha"
                            )
                            val lineScale by animateFloatAsState(
                                targetValue = if (isActive) 1.03f else 0.98f,
                                animationSpec = tween(300),
                                label = "lineScale"
                            )

                            Text(
                                text = line.text.ifEmpty { "♪" },
                                color = if (isActive) Color.White else Color.White.copy(alpha = 0.45f),
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
                                    .fillMaxWidth()
                                    .scale(lineScale)
                                    .alpha(lineAlpha)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (durationMs > 0) {
                                            val targetProgress = (line.timeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                            onSeekTo(targetProgress)
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
            } else if (!lyricsState?.plainLyrics.isNullOrBlank()) {
                // Plain unsynced lyrics view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = 24.dp)
                ) {
                    Text(
                        text = lyricsState?.plainLyrics ?: "",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = lyricsSettings.fontSize.inactiveSp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 30.sp,
                        textAlign = lyricsSettings.alignment.textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
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
    }
}

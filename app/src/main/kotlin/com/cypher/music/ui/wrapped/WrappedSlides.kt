package com.cypher.music.ui.wrapped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cypher.music.data.WrappedArtistStat
import com.cypher.music.data.WrappedPeakDay
import com.cypher.music.data.WrappedStats
import com.cypher.music.data.WrappedTrackStat
import com.cypher.music.ui.theme.CypherLavender
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TextPrimary = Color(0xFFF8F6FB)
private val TextSecondary = Color(0xFFA9A3B8)
private val CardBg = Color(0x12FFFFFF)
private val CardBorder = Color(0x1FFFFFFF)
private val Accent = CypherLavender

/** Small shared entrance: fades and lifts its content, optionally staggered. */
@Composable
private fun Reveal(
    delayMs: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(460),
        label = "revealAlpha"
    )
    val rise by animateDpAsState(
        targetValue = if (shown) 0.dp else 20.dp,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "revealRise"
    )
    Box(modifier = modifier.alpha(alpha).offset(y = rise)) { content() }
}

@Composable
fun WrappedMinutesSlide(
    stats: WrappedStats,
    year: Int,
    modifier: Modifier = Modifier
) {
    val target = stats.totalMinutes.toFloat().coerceAtLeast(0f)
    val counter = remember { Animatable(0f) }
    LaunchedEffect(target) {
        counter.animateTo(target, animationSpec = tween(1900, easing = FastOutSlowInEasing))
    }
    val scale = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 120f))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Reveal(0) {
            Text(
                text = "CYPHER WRAPPED · $year",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
        }

        Spacer(Modifier.height(30.dp))

        Reveal(80) {
            Text(
                text = "Has escuchado",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .padding(top = 2.dp)
                .scale(scale.value)
        ) {
            Text(
                text = "${counter.value.toLong()}",
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Color.White, Accent)),
                    fontSize = if (target >= 1000f) 74.sp else 104.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-3).sp
                )
            )
            Text(
                text = "min",
                color = TextSecondary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 22.dp)
            )
        }

        Reveal(150) {
            Text(
                text = "de música en $year",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(32.dp))

        Reveal(240) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill(value = "${stats.totalPlays}", label = "reproducciones")
                stats.topArtist?.let { artist ->
                    StatPill(value = artist.name, label = "artista del año")
                }
            }
        }

        if (!stats.hasData) {
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Aún no hay escucha registrada este año.\nPon algo de música y vuelve.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp)
        )
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun WrappedTopTracksSlide(
    stats: WrappedStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Reveal(0) {
            Text(
                text = "TOP 5 CANCIONES",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
        }
        Reveal(60) {
            Text(
                text = "Las que más sonaron",
                color = TextPrimary,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
            )
        }

        if (stats.topTracks.isEmpty()) {
            Text(
                text = "Aún no hay reproducciones este año.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        } else {
            stats.topTracks.reversed().forEach { track ->
                Reveal(
                    delayMs = (5 - track.rank).coerceAtLeast(0) * 110,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TrackRow(track = track, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: WrappedTrackStat, modifier: Modifier = Modifier) {
    val highlight = track.rank == 1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) Accent.copy(alpha = 0.12f) else CardBg)
            .border(
                width = 1.dp,
                color = if (highlight) Accent.copy(alpha = 0.35f) else CardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${track.rank}",
            color = if (highlight) Accent else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(18.dp)
        )
        TrackArtwork(url = track.artworkUrl, title = track.title, size = 42.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                text = track.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "${track.playCount}×",
            color = if (highlight) Accent else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun TrackArtwork(url: String?, title: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(size / 2.4f)
            )
        }
    }
}

@Composable
fun WrappedArtistsSlide(
    stats: WrappedStats,
    modifier: Modifier = Modifier
) {
    val top = stats.topArtist
    val zoom = remember { Animatable(0.9f) }
    LaunchedEffect(top?.name) {
        zoom.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 130f))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Reveal(0) {
            Text(
                text = "TU ARTISTA DEL AÑO",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
        }

        if (top == null) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Todavía no hay un artista que destacar.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            return@Column
        }

        Text(
            text = top.name,
            style = TextStyle(
                brush = Brush.linearGradient(listOf(TextPrimary, Accent)),
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 6.dp)
                .scale(zoom.value)
        )
        Reveal(80) {
            Text(
                text = "${top.playCount} reproducciones · ${formatListeningTime(top.totalTimeMs)}",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        if (stats.topArtists.size > 1) {
            Spacer(Modifier.height(24.dp))
            stats.topArtists.drop(1).forEach { artist ->
                ArtistBar(
                    artist = artist,
                    maxPlayCount = top.playCount,
                    delayMs = 140 * artist.rank
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Reveal(delayMs = 320, modifier = Modifier.fillMaxWidth()) {
            PeakDayCard(peak = stats.peakDay)
        }
    }
}

@Composable
private fun ArtistBar(artist: WrappedArtistStat, maxPlayCount: Int, delayMs: Int) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        shown = true
    }
    val fraction = if (maxPlayCount > 0) {
        (artist.playCount.toFloat() / maxPlayCount).coerceIn(0f, 1f)
    } else {
        0f
    }
    val fill by animateFloatAsState(
        targetValue = if (shown) fraction else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "artistBar${artist.rank}"
    )

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "#${artist.rank}",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(26.dp)
            )
            Text(
                text = artist.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${artist.playCount}×",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.85f))
            )
        }
    }
}

@Composable
private fun PeakDayCard(peak: WrappedPeakDay?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(CircleShape)
                .background(Accent)
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "TU PICO DE ESCUCHA",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Text(
                text = formatPeakDay(peak),
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
            peak?.let { day ->
                Text(
                    text = formatListeningTime(day.totalTimeMs),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun WrappedSummarySlide(
    stats: WrappedStats,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topTrack = stats.topTracks.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Reveal(0, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(28.dp))
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CYPHER WRAPPED · ${stats.year}",
                        color = Accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "cypher",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text(
                        text = "${stats.totalMinutes}",
                        style = TextStyle(
                            brush = Brush.linearGradient(listOf(Color.White, Accent)),
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-2).sp
                        )
                    )
                    Text(
                        text = " minutos",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
                    )
                }
                Text(
                    text = "${stats.totalPlays} reproducciones en ${stats.year}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.10f))
                )

                SummaryLine(
                    label = "ARTISTA DEL AÑO",
                    value = stats.topArtist?.name ?: "Sin datos todavía",
                    detail = stats.topArtist?.let { "${it.playCount} reproducciones" }
                )
                SummaryLine(
                    label = "CANCIÓN DEL AÑO",
                    value = topTrack?.title ?: "Sin datos todavía",
                    detail = topTrack?.artist
                )
                SummaryLine(
                    label = "DÍA CON MÁS MÚSICA",
                    value = formatPeakDay(stats.peakDay),
                    detail = stats.peakDay?.let { formatListeningTime(it.totalTimeMs) }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Reveal(200, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color(0xFF160F1D)
                )
            ) {
                Text(
                    text = "Cerrar el Wrapped",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "hecho con cypher",
            color = TextSecondary.copy(alpha = 0.6f),
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String, detail: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        detail?.let {
            Text(
                text = it,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatListeningTime(ms: Long): String {
    val minutes = ms / 60_000L
    val hours = minutes / 60L
    return if (hours > 0) "${hours} h ${minutes % 60L} min" else "${minutes} min"
}

private fun formatPeakDay(peak: WrappedPeakDay?): String {
    val day = peak?.day ?: return "Sin datos todavía"
    return day.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-ES")))
}

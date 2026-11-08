package com.cypher.music.ui.wrapped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import com.cypher.music.data.WrappedAlbumStat
import com.cypher.music.data.WrappedArtistStat
import com.cypher.music.data.WrappedGenreStat
import com.cypher.music.data.WrappedPeakDay
import com.cypher.music.data.WrappedStats
import com.cypher.music.data.WrappedTrackStat
import com.cypher.music.ui.theme.CypherLavender
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val TextPrimary = Color(0xFFF8F6FB)
private val TextSecondary = Color(0xFFA9A3B8)
private val Hairline = Color(0x1FFFFFFF)
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
        targetValue = if (shown) 0.dp else 18.dp,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "revealRise"
    )
    Box(modifier = modifier.alpha(alpha).offset(y = rise)) { content() }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.6.sp,
        modifier = modifier
    )
}

@Composable
private fun Headline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
        modifier = modifier
    )
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        lineHeight = 19.sp
    )
}

@Composable
private fun HairlineRow(startIndent: Dp = 76.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(Hairline)
    )
}

// ---------------------------------------------------------------------- minutes

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
    val scale = remember { Animatable(0.94f) }
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
            SectionLabel("CYPHER WRAPPED · $year")
        }

        Spacer(Modifier.height(34.dp))

        Reveal(70) {
            Text(
                text = "Has escuchado",
                color = TextSecondary,
                fontSize = 15.sp,
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
                    fontSize = if (target >= 1000f) 72.sp else 104.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-3).sp
                )
            )
            Text(
                text = "min",
                color = TextSecondary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 22.dp)
            )
        }

        Reveal(140) {
            Text(
                text = "de música en $year",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(30.dp))

        Reveal(220) {
            Text(
                text = buildString {
                    append("${stats.totalPlays} reproducciones")
                    stats.topArtist?.let { append(" · ${it.name}") }
                },
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 300.dp)
            )
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

// ----------------------------------------------------------------------- tracks

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
        Reveal(0) { SectionLabel("TOP 5 CANCIONES") }
        Reveal(50) {
            Headline(
                text = "Las que más sonaron",
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
            )
        }

        if (stats.topTracks.isEmpty()) {
            EmptyNote("Aún no hay reproducciones este año.")
        } else {
            stats.topTracks.forEachIndexed { index, track ->
                Reveal(delayMs = index * 90, modifier = Modifier.fillMaxWidth()) {
                    TrackRow(track = track)
                }
                if (index != stats.topTracks.lastIndex) HairlineRow()
            }
        }
    }
}

@Composable
private fun TrackRow(track: WrappedTrackStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${track.rank}",
            color = if (track.rank == 1) Accent else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp)
        )
        Artwork(url = track.artworkUrl, title = track.title, size = 44.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
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
        // Ranked by listening time, so the trailing figure is time too. Showing
        // plays here made the order look wrong (e.g. #1 with fewer plays than #5).
        Text(
            text = formatListeningTime(track.totalTimeMs),
            color = if (track.rank == 1) Accent else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun Artwork(url: String?, title: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4.5f))
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

// ---------------------------------------------------------------------- artists

@Composable
fun WrappedArtistsSlide(
    stats: WrappedStats,
    modifier: Modifier = Modifier
) {
    val top = stats.topArtist
    val zoom = remember { Animatable(0.94f) }
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
        Reveal(0) { SectionLabel("TU ARTISTA DEL AÑO") }

        if (top == null) {
            Spacer(Modifier.height(18.dp))
            EmptyNote("Todavía no hay un artista que destacar.")
            return@Column
        }

        Text(
            text = top.name,
            style = TextStyle(
                brush = Brush.linearGradient(listOf(TextPrimary, Accent)),
                fontSize = 36.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 14.dp, bottom = 6.dp)
                .scale(zoom.value)
        )
        Reveal(80) {
            Text(
                text = "${top.playCount} reproducciones · ${formatListeningTime(top.totalTimeMs)}",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(22.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            stats.topArtists.forEachIndexed { index, artist ->
                ArtistShareRow(artist = artist, delayMs = 120 * index)
            }
        }

        Spacer(Modifier.height(14.dp))
        Reveal(delayMs = 260) {
            Text(
                text = "El ${(top.share * 100).roundToInt()}% de tu tiempo fue para ${top.name}.",
                color = TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(16.dp))
        Reveal(delayMs = 320, modifier = Modifier.fillMaxWidth()) {
            PeakDayRow(peak = stats.peakDay)
        }
    }
}

@Composable
private fun ArtistShareRow(artist: WrappedArtistStat, delayMs: Int) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        shown = true
    }
    val fill by animateFloatAsState(
        targetValue = if (shown) artist.share.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "artistShare${artist.rank}"
    )

    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = artist.name,
                color = if (artist.rank == 1) TextPrimary else TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (artist.rank == 1) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(artist.share * 100).roundToInt()}%",
                color = if (artist.rank == 1) Accent else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(if (artist.rank == 1) Accent else Accent.copy(alpha = 0.55f))
            )
        }
    }
}

// ----------------------------------------------------------------------- albums

@Composable
fun WrappedAlbumsSlide(
    stats: WrappedStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Reveal(0) { SectionLabel("TOP 5 ÁLBUMES") }
        Reveal(50) {
            Headline(
                text = "Los discos del año",
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
            )
        }

        if (stats.topAlbums.isEmpty()) {
            EmptyNote("Tus pistas no traen álbum identificado, así que aún no podemos ordenarlos.")
        } else {
            stats.topAlbums.forEachIndexed { index, album ->
                Reveal(delayMs = index * 90, modifier = Modifier.fillMaxWidth()) {
                    AlbumRow(album = album)
                }
                if (index != stats.topAlbums.lastIndex) HairlineRow(startIndent = 80.dp)
            }
        }
    }
}

@Composable
private fun AlbumRow(album: WrappedAlbumStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${album.rank}",
            color = if (album.rank == 1) Accent else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp)
        )
        Artwork(url = album.artworkUrl, title = album.name, size = 48.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = album.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.playCount} reproducciones · ${formatListeningTime(album.totalTimeMs)}",
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ----------------------------------------------------------------------- genres

@Composable
fun WrappedGenresSlide(
    stats: WrappedStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Reveal(0) { SectionLabel("TU AÑO EN SONIDO") }
        Reveal(50) {
            Headline(
                text = "Géneros que te definieron",
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
        }

        if (stats.topGenres.isEmpty()) {
            EmptyNote("Tu biblioteca no expone etiquetas de género, así que no podemos calcularlos sin inventarlos.")
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                stats.topGenres.forEachIndexed { index, genre ->
                    GenreRow(genre = genre, delayMs = index * 90)
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Reveal(delayMs = 200) { SectionLabel("CANCIÓN MÁS REPETIDA") }
        val repeated = stats.mostRepeatedTrack
        if (repeated == null) {
            Spacer(Modifier.height(8.dp))
            EmptyNote("Todavía no hay una canción repetida este año.")
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(url = repeated.artworkUrl, title = repeated.title, size = 44.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = repeated.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = repeated.artist,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${repeated.playCount}×",
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Reveal(delayMs = 300, modifier = Modifier.fillMaxWidth()) {
            PeakDayRow(peak = stats.peakDay)
        }
    }
}

@Composable
private fun GenreRow(genre: WrappedGenreStat, delayMs: Int) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        shown = true
    }
    val fill by animateFloatAsState(
        targetValue = if (shown) genre.share.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "genreShare${genre.rank}"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = genre.name,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(112.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.85f))
            )
        }
        Text(
            text = "${(genre.share * 100).roundToInt()}%",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun PeakDayRow(peak: WrappedPeakDay?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(32.dp)
                .clip(CircleShape)
                .background(Accent)
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "TU PICO DE ESCUCHA",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Text(
                text = formatPeakDay(peak),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 1.dp)
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

// ---------------------------------------------------------------------- summary

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
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Reveal(0, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("CYPHER WRAPPED · ${stats.year}")
                    Text(
                        text = "cypher",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "${stats.totalMinutes}",
                        style = TextStyle(
                            brush = Brush.linearGradient(listOf(Color.White, Accent)),
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-2).sp
                        )
                    )
                    Text(
                        text = " minutos",
                        color = TextPrimary,
                        fontSize = 14.sp,
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
                        .padding(vertical = 14.dp)
                        .height(1.dp)
                        .background(Hairline)
                )

                SummaryLine(
                    label = "ARTISTA DEL AÑO",
                    value = stats.topArtist?.name ?: "Sin datos todavía",
                    detail = stats.topArtist?.let {
                        "${it.playCount} reproducciones · ${(it.share * 100).roundToInt()}% de tu tiempo"
                    }
                )
                SummaryLine(
                    label = "CANCIÓN DEL AÑO",
                    value = topTrack?.title ?: "Sin datos todavía",
                    detail = topTrack?.artist
                )
                SummaryLine(
                    label = "ÁLBUM DEL AÑO",
                    value = stats.topAlbum?.name ?: "Sin álbum identificado",
                    detail = stats.topAlbum?.let { "${it.playCount} reproducciones" }
                )
                SummaryLine(
                    label = "GÉNERO PRINCIPAL",
                    value = stats.topGenres.firstOrNull()?.name ?: "Sin datos de género",
                    detail = stats.topGenres.firstOrNull()?.let {
                        "${(it.share * 100).roundToInt()}% de tu tiempo"
                    }
                )
                SummaryLine(
                    label = "DÍA CON MÁS MÚSICA",
                    value = formatPeakDay(stats.peakDay),
                    detail = stats.peakDay?.let { formatListeningTime(it.totalTimeMs) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Reveal(200, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
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
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String, detail: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp)
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

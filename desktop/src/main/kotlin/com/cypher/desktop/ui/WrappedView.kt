package com.cypher.desktop.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.UiState
import com.cypher.desktop.core.WrappedBuilder
import com.cypher.desktop.core.WrappedSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WrappedView(ui: UiState, accent: Color, state: AppState) {
    val years = remember(ui.data.stats) {
        WrappedBuilder.availableYears(ui.data.stats).ifEmpty { listOf(LocalDate.now().year) }
    }
    var year by remember { mutableIntStateOf(years.first()) }
    val summary = remember(year, ui.data.stats) { WrappedBuilder.build(ui.data, year) }

    if (!summary.hasData) {
        EmptyState(
            Icons.Rounded.AutoAwesome,
            "Tu Wrapped aparecerá aquí",
            "Cypher guarda los minutos y las reproducciones de cada día. Cuando escuches algo —aquí o en el móvil— " +
                "podrás ver tu año en música, sin cifras inventadas.",
            accent
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            years.forEach { candidate ->
                Pill(candidate.toString(), candidate == year, accent) { year = candidate }
            }
            Spacer(Modifier.weight(1f))
            Text("${summary.uniqueTracks} canciones distintas", color = CypherColors.TextTertiary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { WrappedHero(summary, accent) }
            item { SectionHeader("TU ARTISTA DEL AÑO", accent) }
            item { TopArtistCard(summary, accent) }
            item { SectionHeader("TUS CANCIONES", accent) }
            items(summary.topTracks.take(5), key = { "wt-${it.trackId}" }) { track ->
                WrappedTrackRow(track, ui, accent, state)
            }
            item { SectionHeader("MOMENTOS", accent) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    summary.mostRepeated?.let { repeated ->
                        MomentCard(
                            icon = Icons.Rounded.Repeat,
                            label = "MÁS REPETIDA",
                            title = repeated.title,
                            detail = "${repeated.playCount} reproducciones",
                            accent = accent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    summary.peakDay?.let { peak ->
                        MomentCard(
                            icon = Icons.Rounded.CalendarMonth,
                            label = "DÍA MÁS MUSICAL",
                            title = formatDay(peak.day),
                            detail = formatDuration(peak.totalTimeMs),
                            accent = accent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun WrappedHero(summary: WrappedSummary, accent: Color) {
    Column(
        Modifier.fillMaxWidth()
            .background(CypherColors.Card, RoundedCornerShape(24.dp))
            .border(1.dp, CypherColors.Border, RoundedCornerShape(24.dp))
            .padding(28.dp)
    ) {
        Text("TU ${summary.year} EN CYPHER", color = CypherColors.TextSecondary, fontSize = 10.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        // Under an hour, "0 horas" reads like a bug; show the minutes instead.
        val hours = summary.totalListeningMs / 3_600_000
        val minutes = summary.totalListeningMs / 60_000
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (hours > 0) "$hours" else "$minutes",
                color = CypherColors.TextPrimary,
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (hours > 0) "horas" else "min",
                color = CypherColors.TextPrimary.copy(alpha = 0.75f), fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Text(
            "${summary.totalPlays} reproducciones · ${summary.uniqueTracks} canciones distintas",
            color = CypherColors.TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun TopArtistCard(summary: WrappedSummary, accent: Color) {
    val artist = summary.topArtist ?: return
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(accent.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Text(initials(artist.name), color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(artist.name, color = CypherColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatDuration(artist.totalTimeMs)} · ${artist.playCount} reproducciones",
                    color = CypherColors.TextTertiary,
                    fontSize = 11.sp
                )
            }
            Text(
                "${(artist.share * 100).toInt()} %",
                color = accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(CypherColors.Pill, CircleShape)) {
            Box(
                Modifier.fillMaxWidth(artist.share.coerceIn(0f, 1f)).height(8.dp)
                    .background(accent, CircleShape)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("de tu tiempo escuchado este año", color = CypherColors.TextTertiary, fontSize = 10.sp)
    }
}

@Composable
private fun WrappedTrackRow(
    track: com.cypher.desktop.core.WrappedTrack,
    ui: UiState,
    accent: Color,
    state: AppState
) {
    val view = viewOf(ui.data, track.trackId)
    Row(
        Modifier.fillMaxWidth()
            .background(CypherColors.Card.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${track.rank}",
            color = accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )
        Artwork(view, 36, accent)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = CypherColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = CypherColors.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "×${track.playCount} · ${formatDuration(track.totalTimeMs)}",
            color = CypherColors.TextTertiary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MomentCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    title: String,
    detail: String,
    accent: Color,
    modifier: Modifier
) {
    Column(
        modifier
            .background(CypherColors.Card, RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = CypherColors.TextTertiary, fontSize = 9.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(title, color = CypherColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, color = CypherColors.TextTertiary, fontSize = 11.sp)
    }
}

private fun formatDay(day: String): String = runCatching {
    LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es")))
}.getOrDefault(day)

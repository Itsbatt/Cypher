package com.cypher.desktop.ui

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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.UiState
import com.cypher.desktop.core.MusicHit

/**
 * YouTube Music: search, stream, download and playlists. The heavy lifting is yt-dlp,
 * which the user installs from this same screen; nothing is fetched until a button is pressed.
 */
@Composable
fun YouTubeView(ui: UiState, accent: Color, state: AppState) {
    val playback by state.playback.collectAsState()
    val currentId = playback.track?.id

    Column(Modifier.fillMaxSize()) {
        Hero(ui, accent, state)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = ui.onlineQuery,
                onValueChange = state::setOnlineQuery,
                singleLine = true,
                placeholder = { Text("Canciones, artistas o álbumes", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, null, tint = CypherColors.TextTertiary, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent.copy(alpha = 0.6f),
                    unfocusedBorderColor = CypherColors.Separator,
                    focusedTextColor = CypherColors.TextPrimary,
                    unfocusedTextColor = CypherColors.TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = state::searchOnline,
                enabled = !ui.onlineBusy && ui.onlineQuery.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF14101A),
                    disabledContainerColor = CypherColors.Pill,
                    disabledContentColor = CypherColors.TextTertiary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Buscar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = ui.onlineLink,
                onValueChange = state::setOnlineLink,
                singleLine = true,
                placeholder = { Text("Pega el enlace de una playlist o álbum de YouTube Music", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(Icons.Rounded.Link, null, tint = CypherColors.TextTertiary, modifier = Modifier.size(15.dp))
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent.copy(alpha = 0.6f),
                    unfocusedBorderColor = CypherColors.Separator,
                    focusedTextColor = CypherColors.TextPrimary,
                    unfocusedTextColor = CypherColors.TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = state::loadOnlinePlaylist, enabled = !ui.onlineBusy && ui.onlineLink.isNotBlank()) {
                Text("Cargar playlist", color = accent, fontSize = 12.sp)
            }
        }

        val plainCount = ui.onlineStatus?.matches(Regex("\\d+ resultados")) == true
        if (!plainCount) ui.onlineStatus?.let { status ->
            Spacer(Modifier.height(8.dp))
            Text(
                status,
                color = if (status.startsWith("No se pudo") || status.startsWith("Falta")) CypherColors.Danger else CypherColors.TextTertiary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))
        when {
            !ui.ytDlpAvailable -> SetupCard(ui, accent, state)
            ui.onlineHits.isEmpty() && !ui.onlineBusy -> EmptyState(
                Icons.Rounded.MusicNote,
                "YouTube Music",
                "Busca arriba y elige una canción, o pega el enlace de una playlist para escucharla entera. " +
                    "Las descargas van a la carpeta elegida en Descargas.",
                accent
            )
            else -> {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ui.onlineListName?.let { "PLAYLIST · ${it.uppercase()}" } ?: "RESULTADOS",
                        color = CypherColors.TextTertiary,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${ui.onlineHits.size} canciones", color = CypherColors.TextTertiary, fontSize = 10.sp)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(ui.onlineHits, key = { it.videoId }) { hit ->
                        HitRow(
                            hit = hit,
                            accent = accent,
                            busy = ui.onlineBusy,
                            active = currentId == "yt:${hit.videoId}",
                            onPlay = { state.playOnline(hit) },
                            onDownload = { state.downloadOnline(hit) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(ui: UiState, accent: Color, state: AppState) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CypherColors.Card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).background(accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("YouTube Music", color = CypherColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                if (ui.onlineQueue.isEmpty()) "Escucha en streaming o descarga a tu biblioteca"
                else "Sonando ${ui.onlineIndex + 1} de ${ui.onlineQueue.size} de esta lista",
                color = CypherColors.TextSecondary,
                fontSize = 11.sp
            )
        }
        if (ui.onlineSaving) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Guardando ${ui.onlineSaved}/${ui.onlineTotal}",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = state::stopOnlineSave) {
                    Text("Detener", color = CypherColors.Danger, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
        } else if (ui.onlineListName != null && ui.onlineHits.isNotEmpty()) {
            TextButton(onClick = { state.saveOnlinePlaylist() }, enabled = !ui.onlineBusy) {
                Icon(Icons.Rounded.PlaylistAdd, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Guardar en Cypher", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(6.dp))
        }
        if (ui.onlineHits.isNotEmpty()) {
            Button(
                onClick = state::playOnlineAll,
                enabled = !ui.onlineBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF14101A),
                    disabledContainerColor = CypherColors.Pill,
                    disabledContentColor = CypherColors.TextTertiary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reproducir todo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SetupCard(ui: UiState, accent: Color, state: AppState) {
    Column(
        Modifier.fillMaxWidth()
            .background(CypherColors.Card, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Text("Falta yt-dlp", color = CypherColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Cypher usa yt-dlp para resolver y descargar el audio de YouTube Music. " +
                "Puedes instalarlo aquí mismo (se guarda en tu carpeta de datos, no en la biblioteca) " +
                "o indicar uno que ya tengas.",
            color = CypherColors.TextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = state::installYtDlp,
                enabled = !ui.onlineBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF14101A),
                    disabledContainerColor = CypherColors.Pill,
                    disabledContentColor = CypherColors.TextTertiary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (ui.onlineBusy) "Descargando…" else "Instalar yt-dlp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { pickYtDlp(state) }) {
                Text("Ya lo tengo: elegir archivo", color = accent, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "También se acepta la variable CYPHER_YTDLP. Sin yt-dlp no se busca ni se descarga nada.",
            color = CypherColors.TextTertiary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun HitRow(
    hit: MusicHit,
    accent: Color,
    busy: Boolean,
    active: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(62.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    active -> accent.copy(alpha = 0.16f)
                    hovered -> CypherColors.Card
                    else -> CypherColors.Card.copy(alpha = 0.45f)
                }
            )
            .hoverable(interaction)
            .clickable(enabled = !busy, onClick = onPlay)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            val artwork = rememberArtwork(hit.thumbnail)
            if (artwork != null) {
                androidx.compose.foundation.Image(
                    artwork,
                    null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hit.title,
                color = if (active) accent else CypherColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(hit.artist.takeIf { it.isNotBlank() }, "YouTube Music").joinToString(" · "),
                color = CypherColors.TextTertiary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(formatTime(hit.durationMs), color = CypherColors.TextTertiary, fontSize = 11.sp)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(34.dp).background(if (active) accent else accent.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (active) Icons.Rounded.MusicNote else Icons.Rounded.PlayArrow,
                null,
                tint = if (active) Color(0xFF14101A) else accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onDownload, enabled = !busy, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.Download,
                "Descargar",
                tint = CypherColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun pickYtDlp(state: AppState) {
    state.openFilePicker(
        FilePickerConfig(
            title = "Elige yt-dlp",
            subtitle = "El ejecutable yt-dlp.exe que ya tienes descargado.",
            extensions = setOf("exe"),
            startDirectory = java.io.File(System.getProperty("user.home"), "Downloads"),
            onPick = { files -> files.firstOrNull()?.let { state.setYtDlpPath(it.absolutePath) } }
        )
    )
}

package com.cypher.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.UiState
import com.cypher.desktop.core.PlaylistPlan
import java.io.File

/**
 * One entry point for the three import sources. Every flow is: choose, analyse, preview,
 * confirm. Nothing touches the library before the confirm button.
 */
@Composable
fun ImportView(ui: UiState, accent: Color, state: AppState) {
    Column(Modifier.fillMaxSize()) {
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
                Icon(Icons.Rounded.FileDownload, null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Importar", color = CypherColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Trae listas de YouTube Music o de Spotify sin añadir las canciones una a una.",
                    color = CypherColors.TextSecondary, fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tab("YouTube Music", ui.importTab == "YOUTUBE", accent) { state.setImportTab("YOUTUBE") }
            Tab("Spotify · CSV o ZIP", ui.importTab == "SPOTIFY", accent) { state.setImportTab("SPOTIFY") }
        }
        Spacer(Modifier.height(12.dp))

        if (ui.importBusy) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().height(3.dp),
                color = accent, trackColor = CypherColors.Pill
            )
            Spacer(Modifier.height(8.dp))
        }
        ui.importStatus?.let { status ->
            Text(status, color = CypherColors.TextSecondary, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
        }

        when (ui.importTab) {
            "SPOTIFY" -> SpotifyPane(ui, accent, state)
            else -> YouTubePane(ui, accent, state)
        }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.20f) else CypherColors.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) accent else CypherColors.TextSecondary,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun YouTubePane(ui: UiState, accent: Color, state: AppState) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = ui.onlineLink,
                onValueChange = state::setOnlineLink,
                singleLine = true,
                placeholder = { Text("music.youtube.com/playlist?list=… o watch?v=…&list=…", fontSize = 11.sp) },
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
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = state::loadOnlinePlaylist,
                enabled = !ui.importBusy && ui.onlineLink.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent, contentColor = Color(0xFF14101A),
                    disabledContainerColor = CypherColors.Pill, disabledContentColor = CypherColors.TextTertiary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Analizar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CypherColors.Card)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Person, null,
                tint = if (ui.youtubeConnected) accent else CypherColors.TextTertiary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (ui.youtubeConnected) "Cuenta de Google conectada" else "Conecta tu cuenta de Google",
                    color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    ui.youtubeEmail
                        ?: "Inicia sesión para ver tus playlists de YouTube Music, como en la app de Android.",
                    color = CypherColors.TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (ui.youtubeConnected) {
                TextButton(onClick = state::loadYouTubeLibrary, enabled = !ui.youtubeLibraryBusy) {
                    Text("Cargar mis playlists", color = accent, fontSize = 11.sp)
                }
                TextButton(onClick = state::disconnectYouTube) {
                    Text("Cerrar sesión", color = CypherColors.TextTertiary, fontSize = 11.sp)
                }
            } else {
                Button(
                    onClick = state::connectYouTube,
                    enabled = !ui.youtubeConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent, contentColor = Color(0xFF14101A),
                        disabledContainerColor = CypherColors.Pill, disabledContentColor = CypherColors.TextTertiary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (ui.youtubeConnecting) "Conectando…" else "Iniciar sesión con Google", fontSize = 11.sp)
                }
            }
        }
        if (ui.youtubeConnected && ui.youtubeLibraryBusy) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = accent, trackColor = CypherColors.Pill)
        }
        if (ui.youtubeLibrary.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "TUS PLAYLISTS · ${ui.youtubeLibrary.size}",
                color = CypherColors.TextTertiary, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.heightIn(max = 236.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(ui.youtubeLibrary, key = { it.id }) { summary ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CypherColors.Pill.copy(alpha = 0.35f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))) {
                            Artwork(
                                com.cypher.desktop.ui.TrackView(summary.id, summary.title, "", "", 0, summary.cover),
                                38, accent
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(summary.title, color = CypherColors.TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    summary.itemCount.takeIf { it > 0 }?.let { "$it canciones" },
                                    summary.channel.takeIf { it.isNotBlank() }
                                ).joinToString(" · "),
                                color = CypherColors.TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { state.previewYouTubeAccountPlaylist(summary) },
                            enabled = !ui.importBusy
                        ) {
                            Text("Importar", color = accent, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val plan = ui.youtubePlan
        if (plan == null) {
            Text(
                "Se aceptan enlaces de playlist, enlaces compartidos e identificadores. " +
                    "La importación guarda la lista y sus metadatos; el audio solo se descarga cuando pulsas play.",
                color = CypherColors.TextTertiary, fontSize = 11.sp
            )
        } else {
        // Branches instead of an early return: leaving a Column scope mid-composition
        // unbalances the Compose group stack and crashes when the preview appears.
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CypherColors.Card)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(12.dp))) {
                Artwork(
                    com.cypher.desktop.ui.TrackView(plan.sourceKey, plan.name, "", "", 0, plan.cover),
                    76, accent
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.name, color = CypherColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${plan.total} canciones · ${plan.total - plan.unavailable} disponibles" +
                        if (plan.unavailable > 0) " · ${plan.unavailable} no disponibles" else "",
                    color = CypherColors.TextSecondary, fontSize = 11.sp
                )
                Text(
                    "Procedencia YouTube Music · no se descarga audio al importar",
                    color = CypherColors.TextTertiary, fontSize = 10.sp
                )
            }
            if (ui.youtubeExisting) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Ya importada", color = accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { state.confirmYoutubeImport(updateExisting = true) }) {
                        Text("Actualizar", color = accent, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Button(
                onClick = { state.confirmYoutubeImport(updateExisting = false) },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF14101A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (ui.youtubeExisting) "Importar como nueva" else "Añadir a mi biblioteca",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = state::cancelImport) { Text("Cancelar", color = CypherColors.TextSecondary, fontSize = 12.sp) }
        }
        }
    }
}

@Composable
private fun SpotifyPane(ui: UiState, accent: Color, state: AppState) {
    val plan = ui.spotifyPlan
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    state.openFilePicker(
                        FilePickerConfig(
                            title = "Elige los archivos de Spotify",
                            subtitle = "Puedes marcar varios CSV y el ZIP de la cuenta a la vez.",
                            mode = PickerMode.MULTIPLE,
                            extensions = setOf("csv", "zip"),
                            startDirectory = java.io.File(System.getProperty("user.home"), "Downloads"),
                            confirmLabel = "Analizar",
                            onPick = { files -> state.analyzeSpotifyFiles(files) }
                        )
                    )
                },
                enabled = !ui.importBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent, contentColor = Color(0xFF14101A),
                    disabledContainerColor = CypherColors.Pill, disabledContentColor = CypherColors.TextTertiary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.FileDownload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Elegir CSV o ZIP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Sin iniciar sesión en Spotify. Nada se crea hasta que confirmes.",
                color = CypherColors.TextTertiary, fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(12.dp))

        if (plan == null) {
            Text(
                "Se detectan las columnas habituales (nombre, artista, álbum, duración, ISRC, URI) " +
                    "y se aceptan comas, puntos y coma o tabuladores, con o sin BOM.",
                color = CypherColors.TextTertiary, fontSize = 11.sp
            )
        } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("${plan.playlists.size} listas", accent)
            Chip("${plan.songs} canciones", accent)
            Chip("${plan.recognized} reconocidas", Color(0xFF7BC49A))
            if (plan.doubtful > 0) Chip("${plan.doubtful} dudosas", Color(0xFFE0B36A))
            if (plan.unmatched > 0) Chip("${plan.unmatched} sin coincidencia", CypherColors.TextSecondary)
        }
        plan.issues.take(3).forEach { issue ->
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = Color(0xFFE0B36A), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("${issue.file}: ${issue.message}", color = CypherColors.TextTertiary, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        val pending = plan.pendingYoutube().size
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pending > 0 && !ui.importBusy) {
                TextButton(onClick = state::resolveSpotifyWithYouTube) {
                    Text("Buscar $pending sin coincidencia en YouTube Music", color = accent, fontSize = 11.sp)
                }
            }
            if (ui.importBusy) {
                TextButton(onClick = state::stopImportResolve) {
                    Text("Detener búsqueda", color = CypherColors.TextSecondary, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(plan.playlists, key = { it.sourceKey }) { playlist ->
                PlaylistPreview(
                    playlist = playlist,
                    selected = playlist.sourceKey in ui.spotifySelected,
                    accent = accent,
                    onToggle = { state.toggleSpotifyPlaylist(playlist.sourceKey) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { state.confirmSpotifyImport(updateExisting = true) },
                enabled = plan.songs > 0 && ui.spotifySelected.isNotEmpty() && !ui.importBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent, contentColor = Color(0xFF14101A),
                    disabledContainerColor = CypherColors.Pill, disabledContentColor = CypherColors.TextTertiary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Importar seleccionadas", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = state::cancelImport) { Text("Cancelar", color = CypherColors.TextSecondary, fontSize = 12.sp) }
            Spacer(Modifier.weight(1f))
            Text(
                "Si ya existe una lista con el mismo nombre, se actualiza.",
                color = CypherColors.TextTertiary, fontSize = 10.sp
            )
        }
        }
    }
}

@Composable
private fun PlaylistPreview(playlist: PlaylistPlan, selected: Boolean, accent: Color, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .background(if (selected) accent else CypherColors.Pill),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF14101A), modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, color = CypherColors.TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append("${playlist.total} canciones · ${playlist.recognized} reconocidas")
                    if (playlist.doubtful > 0) append(" · ${playlist.doubtful} dudosas")
                    if (playlist.unmatched > 0) append(" · ${playlist.unmatched} sin coincidencia")
                    if (playlist.duplicates > 0) append(" · ${playlist.duplicates} repetidas")
                },
                color = CypherColors.TextTertiary, fontSize = 10.sp
            )
        }
        if (playlist.unmatched > 0) {
            Icon(Icons.Rounded.Warning, null, tint = Color(0xFFE0B36A), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("conserva referencias", color = CypherColors.TextTertiary, fontSize = 9.sp)
        }
    }
}

@Composable
private fun Chip(label: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

package com.cypher.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.UiState
import com.cypher.desktop.core.Track
import java.io.File

/**
 * Local downloads: a folder of audio files that is not the main library yet. Nothing is
 * moved or deleted — importing only reads tags and adds the file to the library.
 */
@Composable
fun DownloadsView(ui: UiState, accent: Color, state: AppState) {
    val candidates = ui.downloadCandidates
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    ui.data.downloadsFolder ?: "Ninguna carpeta de descargas elegida",
                    color = CypherColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Los archivos se leen donde están: Cypher no los mueve ni los copia.",
                    color = CypherColors.TextTertiary,
                    fontSize = 10.sp
                )
            }
            if (candidates.isNotEmpty()) {
                TextButton(onClick = state::importAllDownloads) {
                    Icon(Icons.Rounded.Add, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Añadir todo (${candidates.size})", color = accent, fontSize = 12.sp)
                }
            }
            TextButton(onClick = {
                pickDownloadsFolder(state)
            }) {
                Icon(Icons.Rounded.FolderOpen, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Elegir carpeta", color = accent, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        when {
            ui.scanningDownloads -> Text("Leyendo etiquetas…", color = accent, fontSize = 12.sp)
            candidates.isEmpty() -> EmptyState(
                Icons.Rounded.Download,
                if (ui.data.downloadsFolder == null) "Descargas locales" else "No hay nada nuevo",
                if (ui.data.downloadsFolder == null)
                    "Elige la carpeta donde guardas la música descargada (por ejemplo, la de YouTube Music o tu gestor de descargas). " +
                        "Cypher leerá las etiquetas y podrás añadirla a tu biblioteca."
                else
                    "Todos los archivos de esa carpeta ya están en tu biblioteca. Si descargas algo nuevo, vuelve a elegir la carpeta.",
                accent,
                if (ui.data.downloadsFolder == null) null else "Volver a escanear"
            ) { if (ui.data.downloadsFolder != null) state.scanDownloads(File(ui.data.downloadsFolder)) }
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(candidates, key = { it.id }) { track ->
                    DownloadRow(track, accent) { state.importDownload(track) }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(track: Track, accent: Color, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(58.dp)
            .background(CypherColors.Card.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).background(accent.copy(alpha = 0.16f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Download, null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = CypherColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    track.artist.takeIf { it.isNotBlank() },
                    track.album.takeIf { it.isNotBlank() },
                    formatFileSize(track.path)
                ).joinToString(" · "),
                color = CypherColors.TextTertiary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(formatTime(track.durationMs), color = CypherColors.TextTertiary, fontSize = 11.sp)
        TextButton(onClick = onAdd) {
            Text("Añadir", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatFileSize(path: String): String {
    val bytes = runCatching { File(path).length() }.getOrDefault(0L)
    if (bytes <= 0) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
}

private fun pickDownloadsFolder(state: AppState) {
    state.openFilePicker(
        FilePickerConfig(
            title = "Tu carpeta de descargas",
            subtitle = "Donde yt-dlp deja la música bajada.",
            mode = PickerMode.FOLDER,
            confirmLabel = "Usar esta carpeta",
            startDirectory = java.io.File(System.getProperty("user.home"), "Downloads"),
            onPick = { files -> files.firstOrNull()?.let(state::scanDownloads) }
        )
    )
}

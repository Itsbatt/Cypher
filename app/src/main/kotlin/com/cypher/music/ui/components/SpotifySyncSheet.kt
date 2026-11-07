package com.cypher.music.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.data.SpotifyTransferUiState
import com.cypher.music.network.SpotifySession
import com.cypher.music.ui.theme.ResolvedThemeColors

private val SpotifyGreen = Color(0xFF1DB954)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifySyncSheet(
    state: SpotifyTransferUiState,
    session: SpotifySession,
    clientId: String,
    themeColors: ResolvedThemeColors,
    onClientIdChange: (String) -> Unit,
    onConnect: () -> Unit,
    onLoadPlaylists: () -> Unit,
    onTogglePlaylist: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onStartTransfer: () -> Unit,
    onCancelTransfer: () -> Unit,
    onCopyFailed: () -> Unit,
    onShareFailed: () -> Unit,
    onOpenLinkImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var clientIdDraft by remember(clientId) { mutableStateOf(clientId) }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MusicNote, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Transferir desde Spotify",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Conecta tu cuenta o importa un archivo, y elige qué traer",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(Icons.Filled.Close, "Cerrar", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!session.isLoggedIn && state.playlists.isEmpty()) {
                Text(
                    text = "Autoriza tu cuenta de Spotify una vez y Cypher podrá leer todas tus playlists y tus «Me gusta», sin copiar enlaces.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                if (clientId.isBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Client ID de Spotify",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = clientIdDraft,
                        onValueChange = { clientIdDraft = it },
                        singleLine = true,
                        placeholder = {
                            Text("Pega aquí tu Client ID", color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyGreen,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            cursorColor = SpotifyGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Crea una app en developer.spotify.com y añade el redirect " +
                            "cypher://spotify-callback. El Client ID es público: no hace falta secreto.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onClientIdChange(clientIdDraft.trim()) },
                        enabled = clientIdDraft.isNotBlank() && !state.isImporting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Text("Guardar Client ID", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onConnect,
                    enabled = clientId.isNotBlank() && !state.isLoadingPlaylists,
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    if (state.isLoadingPlaylists) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Link, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Conectar con Spotify", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                if (session.isLoggedIn)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2C2C2E))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.displayName ?: "Cuenta de Spotify",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = session.email ?: "Sesión activa",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onLoadPlaylists, enabled = !state.isImporting) {
                        Icon(Icons.Filled.Sync, null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Recargar", color = SpotifyGreen, fontSize = 12.sp)
                    }
                }

                if (state.isLoadingPlaylists) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                        color = SpotifyGreen,
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                }

                if (state.playlists.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${state.playlists.size} playlists",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                onSelectAll(state.selectedCount != state.playlists.size)
                            },
                            enabled = !state.isImporting
                        ) {
                            Text(
                                text = if (state.selectedCount == state.playlists.size) "Quitar todas" else "Elegir todas",
                                color = SpotifyGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF232325))
                    ) {
                        items(state.playlists, key = { it.id }) { playlist ->
                            val selected = playlist.id in state.selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !state.isImporting) { onTogglePlaylist(playlist.id) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { onTogglePlaylist(playlist.id) },
                                    enabled = !state.isImporting,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = SpotifyGreen,
                                        uncheckedColor = Color.White.copy(alpha = 0.35f)
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append("${playlist.trackCount} canciones")
                                            playlist.ownerName?.let { append(" · $it") }
                                        },
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (state.importIssues.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2A2422))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Archivos omitidos",
                            color = Color(0xFFFF9F43),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 130.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            state.importIssues.forEach { issue ->
                                Text(
                                    text = "• $issue",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                if (state.isImporting) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                            color = SpotifyGreen,
                            trackColor = Color.White.copy(alpha = 0.08f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.status,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onCancelTransfer, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancelar transferencia", color = Color(0xFFFF6B6B), fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = onStartTransfer,
                            enabled = state.selectedCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Filled.Sync, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Transferir ${state.selectedCount} playlists",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                state.summary?.let { summary ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF232325))
                            .border(1.dp, SpotifyGreen.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Transferencia completada",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "✓ ${summary.importedTracks} canciones transferidas en ${summary.playlists} playlists",
                            color = SpotifyGreen,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (summary.failedCount == 0) {
                                "Todas las canciones se encontraron en YouTube Music."
                            } else {
                                "✕ ${summary.failedCount} canciones no encontradas"
                            },
                            color = if (summary.failedCount == 0) Color.White.copy(alpha = 0.6f) else Color(0xFFFF9F43),
                            fontSize = 12.sp
                        )

                        if (summary.failed.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 170.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                summary.failed.forEach { failed ->
                                    Text(
                                        text = "• ${failed.title} — ${failed.artist}",
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                    Text(
                                        text = failed.playlist,
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onCopyFailed,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copiar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = onShareFailed,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Icon(Icons.Filled.Share, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compartir", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            state.error?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = message, color = Color(0xFFFF6B6B), fontSize = 12.sp, lineHeight = 16.sp)
            }

            if (!state.isImporting) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = onOpenLinkImport, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "¿Solo una playlist? Importar con un enlace",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

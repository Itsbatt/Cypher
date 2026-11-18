package com.cypher.desktop.ui

import com.cypher.desktop.app.UiState

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cypher.desktop.core.LyricDoc
import com.cypher.desktop.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File

/**
 * Diálogo interactivo para exportar y compartir tarjetas estéticas de letras a 1080p.
 */
@Composable
fun LyricCardDialog(
    track: Track,
    ui: UiState,
    accent: Color,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var generatedImage by remember { mutableStateOf<BufferedImage?>(null) }
    var copiedNotice by remember { mutableStateOf(false) }
    var savedNotice by remember { mutableStateOf<String?>(null) }

    val view = viewOf(ui.data, track.id) ?: TrackView(track.id, track.title, track.artist, track.album, track.durationMs, track.artwork)
    val songLyrics = rememberSongLyrics(ui.data, view, track.path)

    val availableLines = remember(songLyrics.lines) {
        songLyrics.lines.map { it.text.trim() }.filter { it.isNotBlank() }.take(8)
    }

    var selectedLines by remember(availableLines) {
        mutableStateOf(availableLines.take(3).ifEmpty { listOf("♪ Disfrutando de la música en Cypher ♪") })
    }

    fun regenerate() {
        scope.launch(Dispatchers.IO) {
            val img = LyricCardRenderer.renderCard(
                track = track,
                lyricLines = selectedLines,
                accentColor = java.awt.Color(accent.red, accent.green, accent.blue)
            )
            withContext(Dispatchers.Main) {
                generatedImage = img
            }
        }
    }

    LaunchedEffect(track, selectedLines) {
        regenerate()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(480.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF16121E))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .clickable(enabled = false) {}
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Header
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Tarjeta de Letra (1080p)",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, "Cerrar", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Card Image Preview
                    Box(
                        Modifier
                            .width(280.dp)
                            .height(350.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0C0912))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val img = generatedImage
                        if (img != null) {
                            Image(
                                bitmap = img.toComposeImageBitmap(),
                                contentDescription = "Vista previa de la tarjeta",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("Generando tarjeta HD…", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Action buttons
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val img = generatedImage ?: return@Button
                                scope.launch(Dispatchers.IO) {
                                    val success = LyricCardRenderer.copyToClipboard(img)
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            copiedNotice = true
                                            savedNotice = null
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(
                                if (copiedNotice) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                tint = Color(0xFF14101A),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (copiedNotice) "¡Copiada!" else "Copiar al portapapeles",
                                color = Color(0xFF14101A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val img = generatedImage ?: return@Button
                                scope.launch(Dispatchers.IO) {
                                    val userPictures = File(System.getProperty("user.home"), "Pictures/Cypher")
                                    val safeTitle = track.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
                                    val file = File(userPictures, "lyric_$safeTitle.png")
                                    val ok = LyricCardRenderer.saveToFile(img, file)
                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            savedNotice = file.name
                                            copiedNotice = false
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (savedNotice != null) "¡Guardada!" else "Guardar PNG",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (copiedNotice) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "✓ Imagen copiada. ¡Pégala directamente en Discord o WhatsApp con Ctrl+V!",
                            color = accent,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    } else if (savedNotice != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "✓ Guardada en Imágenes/Cypher/$savedNotice",
                            color = Color(0xFF86EFAC),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

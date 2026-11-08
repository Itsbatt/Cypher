package com.cypher.music.ui.share

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cypher.music.data.WrappedStats
import kotlinx.coroutines.launch

@Composable
fun WrappedShareSheet(
    stats: WrappedStats,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var format by remember { mutableStateOf(ShareFormat.VERTICAL) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(format) {
        rendering = true
        preview = WrappedCardRenderer.render(
            stats = stats,
            format = format,
            accent = accentColor,
            targetWidth = 480
        )
        rendering = false
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0910))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (!busy) onDismiss() },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = "Compartir Wrapped",
                        color = Color(0xFFF8F6FB),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tu ${stats.year} en una imagen",
                        color = Color(0xFFA9A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val ratio = format.width.toFloat() / format.height.toFloat()
                val cardWidth = minOf(maxWidth, maxHeight * ratio)
                Box(
                    modifier = Modifier
                        .width(cardWidth)
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF16131D)),
                    contentAlignment = Alignment.Center
                ) {
                    preview?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Previsualización del Wrapped",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (rendering) {
                        CircularProgressIndicator(
                            color = accentColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Text(
                text = "FORMATO",
                color = Color(0xFF8F89A0),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShareFormat.entries.forEach { option ->
                    FormatChip(
                        label = option.label,
                        selected = format == option,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    ) { format = option }
                }
            }

            Text(
                text = "Solo se comparten tus estadísticas reales de escucha.",
                color = Color(0xFF6F697E),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (busy) return@OutlinedButton
                        busy = true
                        scope.launch {
                            try {
                                val full = WrappedCardRenderer.render(stats, format, accentColor)
                                val uri = LyricsShare.saveToGallery(
                                    context,
                                    full,
                                    "cypher-wrapped-${stats.year}-${format.name.lowercase()}.png"
                                )
                                val message = if (uri != null) {
                                    "Guardado en Pictures/Cypher"
                                } else {
                                    "No se pudo guardar la imagen"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && !rendering,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                ) {
                    Text(
                        text = "Guardar",
                        color = Color(0xFFF8F6FB),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        scope.launch {
                            try {
                                val full = WrappedCardRenderer.render(stats, format, accentColor)
                                LyricsShare.shareBitmap(
                                    context = context,
                                    bitmap = full,
                                    fileName = "cypher-wrapped-${stats.year}-${format.name.lowercase()}.png",
                                    caption = "Mi ${stats.year} en CYPHER\nCompartido desde CYPHER",
                                    chooserTitle = "Compartir Wrapped"
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "No se pudo compartir", Toast.LENGTH_SHORT).show()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && !rendering,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color(0xFF160F1D),
                        disabledContainerColor = accentColor.copy(alpha = 0.35f),
                        disabledContentColor = Color(0xFF160F1D).copy(alpha = 0.6f)
                    )
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = Color(0xFF160F1D),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = "Compartir", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FormatChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (selected) accentColor.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFFF8F6FB) else Color(0xFFA9A3B8),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

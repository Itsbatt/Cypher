package com.cypher.music.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Legal notice shown on first launch and reopenable from Settings. It does not block
 * playback: it only states what Cypher is and how downloaded content may be used.
 */
@Composable
fun LegalNoticeDialog(
    onDismiss: () -> Unit,
    confirmLabel: String = "Entendido"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Aviso legal",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Text(
                text = LEGAL_NOTICE_TEXT,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmLabel)
            }
        }
    )
}

private const val LEGAL_NOTICE_TEXT =
    "Cypher es un reproductor de música personal y no está afiliado, asociado, autorizado " +
        "ni respaldado por YouTube, Google ni Spotify.\n\n" +
        "1. Streaming. El catálogo en línea se obtiene de contenido público de YouTube. Al usarlo " +
        "aceptas los Términos del Servicio de YouTube (https://www.youtube.com/t/terms) y asumes " +
        "que el acceso puede dejar de funcionar si esos términos o la plataforma cambian.\n\n" +
        "2. Descargas. La función de descargas está pensada para uso personal y privado: " +
        "guardar copias de contenido al que ya tienes acceso legítimo para escucharlo sin " +
        "conexión. No redistribuyas, publiques, vendas ni compartas ese contenido, y respeta " +
        "los derechos de autor de artistas y sellos.\n\n" +
        "3. Responsabilidad. Eres responsable del contenido que descargas y del uso que haces " +
        "de la app. Cypher no aloja música ni vídeos: solo reproduce lo que tú eliges.\n\n" +
        "4. Datos. Cypher guarda tu biblioteca, playlists, favoritos y ajustes solo en este " +
        "dispositivo. No hay cuentas en la nube ni envío de estadísticas de uso.\n\n" +
        "Si no estás de acuerdo con estos puntos, no utilices la aplicación."

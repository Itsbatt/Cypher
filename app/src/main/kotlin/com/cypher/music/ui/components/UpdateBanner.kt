package com.cypher.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.cypher.music.update.UpdateUiState

/**
 * Small, dismissible in-app notice for a new GitHub release. Never takes over
 * the screen: it floats above the dock and can be ignored.
 */
@Composable
fun UpdateBanner(
    state: UpdateUiState,
    accentColor: Color,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val release = state.release ?: return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xF21E1B24))
            .border(1.dp, accentColor.copy(alpha = 0.32f), RoundedCornerShape(20.dp))
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            ) {
                Text(
                    text = "Nueva versión ${release.versionName}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (release.summary.isNotBlank()) {
                    Text(
                        text = release.summary,
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Descartar actualización",
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        if (state.downloading) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = accentColor,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
            Text(
                text = "Descargando… ${(state.progress * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White.copy(alpha = 0.6f)
                    )
                ) {
                    Text("Ahora no", fontSize = 12.sp)
                }
                TextButton(
                    onClick = onUpdate,
                    colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
                ) {
                    Text(
                        text = "Actualizar",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        state.error?.let { message ->
            Text(
                text = message,
                color = Color(0xFFFF8A80),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp, end = 8.dp)
            )
        }
    }
}

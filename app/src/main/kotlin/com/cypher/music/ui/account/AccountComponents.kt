package com.cypher.music.ui.account

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cypher.music.sync.AccountUiState
import com.cypher.music.sync.SyncPhase
import com.cypher.music.ui.theme.ResolvedThemeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Account card used inside Settings.
 *
 * It follows Cypher's own visual language (dark card, lavender accent, 20 dp radius)
 * instead of looking like a generic account screen, and it never shows an action that
 * cannot work: without a Google client id in the build it explains what is missing
 * rather than offering a dead button.
 */
@Composable
fun CloudAccountSection(
    state: AccountUiState,
    themeColors: ResolvedThemeColors,
    hapticsEnabled: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (state.isSignedIn) Icons.Filled.CloudDone else Icons.Filled.Cloud,
                contentDescription = null,
                tint = themeColors.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CUENTA Y NUBE",
                color = themeColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeColors.cardBackground, shape)
                .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                .padding(18.dp)
        ) {
            val account = state.account
            when {
                !state.authConfigured -> NotConfiguredNote(themeColors)
                account == null -> LocalOnlyNote(themeColors, state, onConnect)
                else -> LinkedAccount(
                    state = state,
                    themeColors = themeColors,
                    onDisconnect = onDisconnect,
                    onSyncNow = onSyncNow
                )
            }

            state.message?.takeIf { it.isNotBlank() }?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    color = themeColors.textSecondary,
                    fontSize = 12.sp
                )
            }

            if (state.isSignedIn && state.pendingChanges > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.pendingChanges == 1) {
                        "1 cambio pendiente de subir"
                    } else {
                        "${state.pendingChanges} cambios pendientes de subir"
                    },
                    color = themeColors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun NotConfiguredNote(themeColors: ResolvedThemeColors) {
    Text(
        text = "Esta compilación no incluye el identificador de Google, así que la cuenta " +
            "todavía no puede conectarse. Añade tu client id para activarla.",
        color = themeColors.textSecondary,
        fontSize = 13.sp
    )
}

@Composable
private fun LocalOnlyNote(
    themeColors: ResolvedThemeColors,
    state: AccountUiState,
    onConnect: () -> Unit
) {
    Text(
        text = "Cypher funciona sin cuenta: todo se guarda en este dispositivo. " +
            "Conecta Google para tener tus playlists, favoritos y estadísticas en la nube.",
        color = themeColors.textSecondary,
        fontSize = 13.sp
    )
    Spacer(modifier = Modifier.height(14.dp))
    Button(
        onClick = onConnect,
        enabled = !state.signingIn,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = themeColors.accent,
            contentColor = Color(0xFF14101A)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.signingIn) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF14101A)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = if (state.signingIn) "Conectando…" else "Conectar con Google",
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LinkedAccount(
    state: AccountUiState,
    themeColors: ResolvedThemeColors,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit
) {
    val account = state.account ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(themeColors.accent.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials(account.displayName, account.email),
                color = themeColors.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                color = themeColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = account.email,
                color = themeColors.textSecondary,
                fontSize = 12.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = when (state.phase) {
                is SyncPhase.Syncing -> Icons.Filled.Sync
                SyncPhase.Offline -> Icons.Filled.CloudOff
                is SyncPhase.Failed -> Icons.Filled.CloudOff
                else -> Icons.Filled.CloudDone
            },
            contentDescription = null,
            tint = themeColors.accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = phaseLabel(state),
            color = themeColors.textPrimary,
            fontSize = 13.sp
        )
    }

    val lastSync = state.lastSyncedAt
    if (lastSync != null && lastSync > 0L) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Última sincronización: ${formatTimestamp(lastSync)}",
            color = themeColors.textSecondary,
            fontSize = 12.sp
        )
    }

    Spacer(modifier = Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onSyncNow,
            enabled = state.phase !is SyncPhase.Syncing,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (state.phase is SyncPhase.Syncing) "Sincronizando…" else "Sincronizar ahora",
                fontSize = 13.sp,
                color = themeColors.accent
            )
        }
        TextButton(onClick = onDisconnect, shape = RoundedCornerShape(12.dp)) {
            Text(text = "Cerrar sesión", fontSize = 13.sp, color = themeColors.textSecondary)
        }
    }
}

/**
 * Popup offered to people who already had local data.
 *
 * Shown once the library is loaded (never on an empty one), dismissible, and it never
 * blocks the app: "Ahora no" just records the decision and remembers it.
 */
@Composable
fun CloudLinkAccountDialog(
    accent: Color,
    signingIn: Boolean,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null
) {
    Dialog(onDismissRequest = { if (!signingIn) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF16141D),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(accent.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Tu Cypher, en todas partes.",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Conecta tu cuenta de Google para mantener tus playlists, favoritos y " +
                        "estadísticas sincronizados en la nube y poder recuperarlos en otros dispositivos.",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                message?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = it,
                        color = accent,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = onConnect,
                    enabled = !signingIn,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF14101A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (signingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF14101A)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = if (signingIn) "Conectando…" else "Conectar con Google",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !signingIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ahora no",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

internal fun phaseLabel(state: AccountUiState): String = when (val phase = state.phase) {
    SyncPhase.LocalOnly -> "Solo en este dispositivo"
    SyncPhase.Offline -> "Sin conexión · se reintentará solo"
    SyncPhase.Syncing -> "Sincronizando…"
    is SyncPhase.Synced -> "Sincronizado"
    is SyncPhase.Failed -> "Error: ${phase.message}"
}

internal fun formatTimestamp(at: Long): String =
    SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(at))

internal fun initials(name: String, email: String): String {
    val source = name.trim().ifBlank { email.substringBefore('@') }
    val parts = source.split(" ", ".").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(2).uppercase(Locale.getDefault())
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase(Locale.getDefault())
    }
}

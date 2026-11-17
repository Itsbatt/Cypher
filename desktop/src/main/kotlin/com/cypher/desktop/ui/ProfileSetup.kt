package com.cypher.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cypher.desktop.ui.glass.cypherGlass

/**
 * First run, and later renames from the profile menu.
 *
 * The name lives in the local document only: no account, no sign-in, no request. The
 * preview shows exactly the avatar the header will draw, so what the user approves here
 * is what they see afterwards.
 */
@Composable
fun ProfileSetupDialog(
    initialName: String,
    accent: Color,
    firstRun: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(if (initialName == "Cypher") "" else initialName) }
    val trimmed = name.trim()
    val preview = trimmed.ifBlank { "Tú" }

    Dialog(
        onDismissRequest = { if (!firstRun) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .width(440.dp)
                .background(CypherColors.Surface, RoundedCornerShape(26.dp))
                .border(1.dp, CypherColors.Border, RoundedCornerShape(26.dp))
                .padding(horizontal = 28.dp, vertical = 26.dp)
        ) {
            Text(
                if (firstRun) "Te damos la bienvenida a Cypher" else "Tu perfil",
                color = CypherColors.TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Elige el nombre que quieres ver en la esquina superior derecha. " +
                    "Se guarda solo en este equipo y no se envía a ningún servidor.",
                color = CypherColors.TextTertiary, fontSize = 12.sp, lineHeight = 17.sp
            )
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).background(accent.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initials(preview),
                        color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 24) name = it },
                        singleLine = true,
                        placeholder = { Text("Tu nombre", color = CypherColors.TextTertiary, fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (firstRun || trimmed.isNotBlank()) onSave(trimmed) }),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = CypherColors.Border,
                            focusedContainerColor = Color.White.copy(alpha = 0.04f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                            focusedTextColor = CypherColors.TextPrimary,
                            unfocusedTextColor = CypherColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Así se verá tu avatar",
                        color = CypherColors.TextTertiary, fontSize = 10.sp
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!firstRun) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = CypherColors.TextSecondary, fontSize = 12.sp)
                    }
                } else {
                    TextButton(onClick = { onSave("") }) {
                        Text("Ahora no", color = CypherColors.TextTertiary, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .background(accent, RoundedCornerShape(12.dp))
                        .clickable { onSave(trimmed) }
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Guardar",
                        color = Color(0xFF17111F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


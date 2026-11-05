package com.cypher.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import com.cypher.music.ui.theme.ResolvedThemeColors

/**
 * Shared name dialog. Used non-dismissible for first-launch onboarding and
 * dismissible when editing the name from Settings, so both flows share one UI.
 */
@Composable
fun UsernameDialog(
    currentName: String,
    themeColors: ResolvedThemeColors,
    isOnboarding: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    val canSave = name.isNotBlank()

    Dialog(
        onDismissRequest = { if (!isOnboarding) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isOnboarding,
            dismissOnClickOutside = !isOnboarding
        )
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = themeColors.cardBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isOnboarding) "Bienvenido a Cypher" else "Cambiar nombre",
                    color = themeColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isOnboarding) {
                        "¿Cómo quieres que te llamemos?"
                    } else {
                        "Tu nombre se actualizará al instante en toda la aplicación."
                    },
                    color = themeColors.textSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tu nombre") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = themeColors.textPrimary,
                        unfocusedTextColor = themeColors.textPrimary,
                        focusedBorderColor = themeColors.accent,
                        unfocusedBorderColor = themeColors.separator,
                        focusedLabelColor = themeColors.accent,
                        unfocusedLabelColor = themeColors.textSecondary,
                        cursorColor = themeColors.accent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (canSave) onConfirm(name) }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isOnboarding) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = "Cancelar",
                                color = themeColors.textSecondary,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Button(
                        onClick = { if (canSave) onConfirm(name) },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themeColors.accent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = if (isOnboarding) "Empezar" else "Guardar",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

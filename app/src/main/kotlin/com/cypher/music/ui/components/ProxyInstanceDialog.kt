package com.cypher.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cypher.music.ui.theme.ResolvedThemeColors

private const val DEFAULT_PIPED_INSTANCE = "https://pipedapi.kavin.rocks"

@Composable
fun ProxyInstanceDialog(
    currentUrl: String,
    themeColors: ResolvedThemeColors,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl.ifBlank { DEFAULT_PIPED_INSTANCE }) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = themeColors.cardBackground,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Instancia Proxy de Contingencia",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeColors.textPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "URL base de una instancia de Piped o Invidious. Cypher la usará como fallback cuando YouTube bloquee la conexión directa.",
                    fontSize = 13.sp,
                    color = themeColors.textSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = {
                        Text(DEFAULT_PIPED_INSTANCE, color = themeColors.textSecondary.copy(alpha = 0.5f))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val clean = url.trim().removeSuffix("/")
                            if (clean.isNotBlank()) onConfirm(clean)
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeColors.accent,
                        unfocusedBorderColor = themeColors.separator,
                        focusedTextColor = themeColors.textPrimary,
                        unfocusedTextColor = themeColors.textPrimary,
                        cursorColor = themeColors.accent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { url = DEFAULT_PIPED_INSTANCE },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Restaurar instancia sugerida",
                        color = themeColors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = themeColors.textSecondary)
                    }

                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))

                    Button(
                        onClick = {
                            val clean = url.trim().removeSuffix("/")
                            onConfirm(clean)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.accent),
                        shape = RoundedCornerShape(12.dp),
                        enabled = url.isNotBlank()
                    ) {
                        Text("Guardar", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

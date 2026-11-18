package com.cypher.music.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.playback.CypherConnectManager
import com.cypher.music.ui.theme.CypherLavender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CypherDeviceSheet(
    onDismiss: () -> Unit,
    accentColor: Color = Color(0xFFFA243C)
) {
    val context = LocalContext.current
    val connectManager = remember { CypherConnectManager.getInstance(context) }
    val isConnected by connectManager.isConnected.collectAsState()
    val isRemoteActive by connectManager.isRemoteActive.collectAsState()
    val pcIp by connectManager.pcIp.collectAsState()
    val isScanning by connectManager.isScanning.collectAsState()

    var manualIp by remember(pcIp) { mutableStateOf(pcIp ?: "") }
    var isConnectingManual by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14101A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp, top = 4.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CypherLavender.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Computer,
                        contentDescription = null,
                        tint = CypherLavender,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Dispositivos Cypher Connect",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Elige dónde quieres escuchar tu música",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Device Option 1: Este teléfono
            val isLocalActive = !isRemoteActive
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isLocalActive) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f))
                    .border(
                        1.dp,
                        if (isLocalActive) Color(0xFF30D158).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.06f),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        connectManager.setRemoteActive(false)
                        Toast.makeText(context, "Reproduciendo en este teléfono", Toast.LENGTH_SHORT).show()
                    }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = if (isLocalActive) Color(0xFF30D158) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Este teléfono",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (isLocalActive) "Dispositivo activo • Altavoces del móvil" else "Toca para escuchar en este teléfono",
                            color = if (isLocalActive) Color(0xFF30D158) else Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp
                        )
                    }
                    if (isLocalActive) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Activo",
                            tint = Color(0xFF30D158),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Device Option 2: Cypher PC
            if (isConnected && !pcIp.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isRemoteActive) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f))
                        .border(
                            1.dp,
                            if (isRemoteActive) Color(0xFF30D158).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            connectManager.setRemoteActive(true)
                            Toast.makeText(context, "Conectado y reproduciendo en PC", Toast.LENGTH_SHORT).show()
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Computer,
                            contentDescription = null,
                            tint = if (isRemoteActive) Color(0xFF30D158) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Cypher PC ($pcIp)",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (isRemoteActive) "Reproduciendo en PC • Altavoces del PC" else "Conectado • Toca para escuchar en PC",
                                color = if (isRemoteActive) Color(0xFF30D158) else Color.White.copy(alpha = 0.45f),
                                fontSize = 12.sp
                            )
                        }
                        if (isRemoteActive) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Activo",
                                tint = Color(0xFF30D158),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Disconnect or switch button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                connectManager.disconnect()
                                Toast.makeText(context, "Desconectado de Cypher PC", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Desconectar PC",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                // Not connected yet: Quick connect card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Computer,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Conectar con Cypher PC",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Auto-Scan Wi-Fi Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CypherLavender.copy(alpha = 0.2f))
                                .clickable(enabled = !isScanning) {
                                    connectManager.autoScan(
                                        onFound = { ip ->
                                            Toast.makeText(context, "¡Conectado con Cypher PC ($ip)!", Toast.LENGTH_SHORT).show()
                                        },
                                        onNotFound = {
                                            Toast.makeText(context, "No se encontró PC en Wi-Fi. Prueba con la IP manual.", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = CypherLavender,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Buscando Cypher PC en Wi-Fi...", color = CypherLavender, fontSize = 13.sp)
                                } else {
                                    Icon(Icons.Filled.Search, contentDescription = null, tint = CypherLavender, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("🔍 Buscar PC automáticamente en Wi-Fi", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Manual IP row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = { manualIp = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Ej: 192.168.1.14", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CypherLavender,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = CypherLavender
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable(enabled = !isConnectingManual && manualIp.isNotBlank()) {
                                        isConnectingManual = true
                                        focusManager.clearFocus()
                                        connectManager.connect(manualIp) { ok ->
                                            isConnectingManual = false
                                            if (ok) {
                                                Toast.makeText(context, "¡Conectado con Cypher PC ($manualIp)!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "No se pudo conectar a $manualIp:8484", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isConnectingManual) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White, modifier = Modifier.size(16.dp))
                                } else {
                                    Text("Conectar", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

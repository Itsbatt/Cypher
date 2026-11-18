package com.cypher.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cypher.music.ui.theme.CypherLavender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cypher Remote (Control remoto de PC desde la app móvil).
 * Se conecta a Cypher PC en la red local (puerto 8484) y permite controlar
 * la reproducción, pistas y volumen en tiempo real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CypherRemoteSheet(
    accentColor: Color = CypherLavender,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val prefs = remember { context.getSharedPreferences("cypher_remote", android.content.Context.MODE_PRIVATE) }
    var pcIp by remember { mutableStateOf(prefs.getString("last_pc_ip", "192.168.1.") ?: "192.168.1.") }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }

    var trackTitle by remember { mutableStateOf("Conectando con PC…") }
    var trackArtist by remember { mutableStateOf("Cypher Desktop") }
    var trackArtwork by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.8f) }

    fun cleanIp(raw: String): String {
        return raw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore(":")
            .substringBefore("?")
            .substringBefore("/")
    }

    fun sendAction(action: String, value: String? = null) {
        val ip = cleanIp(pcIp)
        scope.launch(Dispatchers.IO) {
            runCatching {
                val url = URL("http://$ip:8484/api/action")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val json = JSONObject().apply {
                    put("action", action)
                    if (value != null) put("value", value)
                }
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                conn.responseCode
            }
        }
    }

    fun testAndConnect(targetIp: String, showToastOnFail: Boolean = true) {
        val ip = cleanIp(targetIp)
        if (ip.isBlank()) return
        isConnecting = true
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val url = URL("http://$ip:8484/api/status")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 2500
                conn.readTimeout = 2500
                conn.responseCode == 200
            }.getOrDefault(false)

            withContext(Dispatchers.Main) {
                isConnecting = false
                if (ok) {
                    pcIp = ip
                    isConnected = true
                    prefs.edit().putString("last_pc_ip", ip).apply()
                    Toast.makeText(context, "¡Conectado con Cypher PC ($ip)!", Toast.LENGTH_SHORT).show()
                } else if (showToastOnFail) {
                    Toast.makeText(context, "No se pudo conectar con $ip:8484. Verifica que Cypher PC esté abierto en la misma red Wi-Fi.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun autoScan() {
        isConnecting = true
        scope.launch(Dispatchers.IO) {
            val baseSubnet = runCatching {
                val wm = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                val ipInt = wm?.connectionInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    "${ipInt and 0xFF}.${(ipInt shr 8) and 0xFF}.${(ipInt shr 16) and 0xFF}."
                } else "192.168.1."
            }.getOrDefault("192.168.1.")

            var found: String? = null
            coroutineScope {
                val deferreds = (1..254).map { i ->
                    async {
                        val candidate = "$baseSubnet$i"
                        val ok = runCatching {
                            val conn = URL("http://$candidate:8484/api/status").openConnection() as HttpURLConnection
                            conn.connectTimeout = 700
                            conn.readTimeout = 700
                            conn.responseCode == 200
                        }.getOrDefault(false)
                        if (ok) candidate else null
                    }
                }
                for (d in deferreds) {
                    val candidate = d.await()
                    if (candidate != null) {
                        found = candidate
                        break
                    }
                }
            }

            withContext(Dispatchers.Main) {
                isConnecting = false
                if (found != null) {
                    testAndConnect(found!!, showToastOnFail = false)
                } else {
                    Toast.makeText(context, "No se encontró ningún Cypher PC activo en $baseSubnet*. Introduce la IP manualmente.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val saved = prefs.getString("last_pc_ip", null)
        if (!saved.isNullOrBlank() && saved.count { it == '.' } == 3) {
            testAndConnect(saved, showToastOnFail = false)
        }
    }

    // Polling status loop
    LaunchedEffect(isConnected, pcIp) {
        if (!isConnected) return@LaunchedEffect
        while (isConnected) {
            val ip = cleanIp(pcIp)
            withContext(Dispatchers.IO) {
                runCatching {
                    val url = URL("http://$ip:8484/api/status")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 1500
                    conn.readTimeout = 1500
                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(text)
                        withContext(Dispatchers.Main) {
                            trackTitle = json.optString("title", "Sin reproducción")
                            trackArtist = json.optString("artist", "Cypher PC")
                            trackArtwork = json.optString("artwork", null)?.takeIf { it.isNotBlank() }
                            isPlaying = json.optBoolean("playing", false)
                            volume = json.optDouble("volume", 0.8).toFloat()
                        }
                    }
                }
            }
            delay(1500L)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14101A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Dns, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cypher Remote", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isConnected) {
                Text(
                    "Introduce la dirección IP de tu ordenador (la que muestra Cypher PC en Ajustes o en el código QR):",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pcIp,
                    onValueChange = { pcIp = it },
                    label = { Text("IP de Cypher PC (ej. 192.168.1.50)") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = accentColor,
                        focusedIndicatorColor = accentColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        testAndConnect(pcIp, showToastOnFail = true)
                    },
                    enabled = !isConnecting,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (isConnecting) "Conectando…" else "Conectar con PC", color = Color(0xFF14101A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        autoScan()
                    },
                    enabled = !isConnecting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("🔍 Buscar PC automáticamente en Wi-Fi", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                // Connected Remote Control Panel
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF221A2C)),
                    contentAlignment = Alignment.Center
                ) {
                    if (trackArtwork != null) {
                        AsyncImage(
                            model = trackArtwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Icon(Icons.Filled.Dns, contentDescription = null, tint = accentColor, modifier = Modifier.size(54.dp))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(trackTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(4.dp))
                Text(trackArtist, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(24.dp))

                // Playback Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { sendAction("previous") }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.width(18.dp))
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                            .clickable { sendAction("toggle"); isPlaying = !isPlaying },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "Reproducir / Pausar",
                            tint = Color(0xFF14101A),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(18.dp))
                    IconButton(onClick = { sendAction("next") }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Volume slider
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            sendAction("volume", it.toString())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Desconectar",
                    color = Color(0xFFE06A6A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { isConnected = false }.padding(8.dp)
                )
            }
        }
    }
}

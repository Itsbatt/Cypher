package com.cypher.desktop.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.app.Screen
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.LibrarySection
import com.cypher.desktop.app.UiState
import com.cypher.desktop.core.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.ChevronRight

@Composable
fun SettingsView(ui: UiState, accent: Color, state: AppState) {
    val scope = rememberCoroutineScope()
    Column(
            Modifier.fillMaxSize()
                // The dock floats over the window, so the settings viewport stops above it:
                // content can never slide under the player.
                .padding(bottom = 110.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 18.dp)
        ) {
        SectionHeader("REPRODUCCIÓN", accent)
        SettingsCard {
            Text("Volumen inicial", color = CypherColors.TextPrimary, fontSize = 13.sp)
            Text("${(ui.data.preferences.volume * 100).toInt()} %", color = CypherColors.TextTertiary, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { value ->
                    Pill("${(value * 100).toInt()} %", kotlin.math.abs(ui.data.preferences.volume - value) < 0.01f, accent) {
                        state.setVolume(value)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Repetición actual: ${when (ui.data.preferences.repeat) {
                    "ONE" -> "una canción"
                    "ALL" -> "toda la cola"
                    else -> "desactivada"
                }} · Aleatorio ${if (ui.data.preferences.shuffle) "activado" else "desactivado"}",
                color = CypherColors.TextTertiary,
                fontSize = 11.sp
            )
        }

        SectionHeader("APARIENCIA", accent)
        SettingsCard {
            Text("Cristal del dock", color = CypherColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            val state = when {
                !com.cypher.desktop.ui.glass.GlassSpec.enabled -> "Desactivado (arrancado con -Dcypher.glass=off)"
                com.cypher.desktop.ui.glass.GlassSupport.shaderCompiled -> "Activo · shader SkSL en la GPU, solo en el dock flotante"
                com.cypher.desktop.ui.glass.GlassSupport.lastError != null ->
                    "Cristal degradado · la GPU no compiló el shader: ${com.cypher.desktop.ui.glass.GlassSupport.lastError}"
                else -> "Cristal degradado · shader aún no compilado"
            }
            Text(state, color = CypherColors.TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Rail, menú de biblioteca y panel derecho son superficies planas; el desenfoque vive solo en el dock.",
                color = CypherColors.TextTertiary, fontSize = 10.sp
            )
            Spacer(Modifier.height(16.dp))
        }
        SettingsCard {
            Text("Acento", color = CypherColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFF9D7BB0, 0xFF7FC8A9, 0xFF6FA8DC, 0xFFE0A96D, 0xFFE06A6A).forEach { value ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(Color(value), CircleShape)
                            .border(2.dp, if (ui.data.preferences.accent == value) Color.White else Color.Transparent, CircleShape)
                            .clickable { state.setAccent(value) }
                    )
                }
                // Anything the presets do not cover: same HSV picker as the palette.
                AccentPickerSwatch(ui.data.preferences.accent, accent) { state.setAccent(it) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Se aplica al instante y se guarda en este equipo.",
                color = CypherColors.TextTertiary,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reducir animaciones", color = CypherColors.TextPrimary, fontSize = 13.sp)
                    Text("Desactiva transiciones largas y efectos de movimiento.", color = CypherColors.TextTertiary, fontSize = 11.sp)
                }
                Switch(
                    checked = ui.data.preferences.reduceMotion,
                    onCheckedChange = { enabled ->
                        state.replaceLibrary { it.copy(preferences = it.preferences.copy(reduceMotion = enabled)) }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.4f))
                )
            }
        }

        SectionHeader("COLORES", accent)
        ThemeColorsCard(
            colors = ui.data.preferences.colors,
            accent = accent,
            onChange = { state.setThemeColors(it) },
            onReset = { state.resetThemeColors() }
        )

        SectionHeader("LETRAS", accent)
        // The preview borrows the cover of whatever is playing, so ARTWORK and LIQUID look
        // like they will look in the player instead of showing a made-up palette.
        val playing by state.player.state.collectAsState()
        val previewView = playing.track?.let { track ->
            viewOf(ui.data, track.id)
                ?: TrackView(track.id, track.title, track.artist, track.album, track.durationMs, track.artwork)
        }
        val previewArt = rememberArtwork(
            previewView?.artwork?.takeIf { it.isNotBlank() } ?: previewView?.artworkUrl
        )
        val previewColors = rememberCoverPalette(previewArt, accent)
        SettingsCard {
            // Live preview: this is the exact painter used by the right panel and by the
            // full screen, so what is chosen here is what those two will show.
            var demoClock by remember { mutableStateOf(0L) }
            LaunchedEffect(Unit) {
                while (true) {
                    androidx.compose.runtime.withFrameMillis { frame ->
                        demoClock = frame % 9_000L
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(174.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CypherColors.Background)
                    .border(1.dp, CypherColors.Border, RoundedCornerShape(16.dp))
            ) {
                LyricsStage(
                    lines = lyricPreviewLines(),
                    positionMs = demoClock,
                    prefs = ui.data.preferences,
                    accent = accent,
                    artworkColors = previewColors,
                    fullscreen = false,
                    // The preview is the only place where the letters have to move on
                    // their own, because there is no player here.
                    playing = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(14.dp))

            LyricSettingRow("Tamaño") {
                listOf("S" to "Pequeña", "M" to "Normal", "L" to "Grande").forEach { (key, label) ->
                    LyricSizeChip(label, ui.data.preferences.lyricsSize == key, accent) { state.setLyricsSize(key) }
                }
            }
            LyricSettingRow("Animación") {
                listOf("NONE" to "Fija", "KARAOKE" to "Karaoke").forEach { (key, label) ->
                    LyricSizeChip(label, ui.data.preferences.lyricsAnimation == key, accent) { state.setLyricsAnimation(key) }
                }
            }
            LyricSettingRow("Shader de la letra") {
                listOf(
                    "NONE" to "Ninguno",
                    "AURORA" to "Aurora",
                    "GRADIENT" to "Degradado",
                    "GLOW" to "Brillo",
                    "LIQUID" to "Líquido",
                    "NEON" to "Neón"
                ).forEach { (key, label) ->
                    LyricSizeChip(label, ui.data.preferences.lyricsShader == key, accent) { state.setLyricsShader(key) }
                }
            }
            LyricSettingRow("Interlineado") {
                listOf("COMPACT" to "Compacto", "NORMAL" to "Normal", "WIDE" to "Amplio").forEach { (key, label) ->
                    LyricSizeChip(label, ui.data.preferences.lyricsLineSpacing == key, accent) { state.setLyricsLineSpacing(key) }
                }
            }
            LyricSettingRow("Alineación") {
                listOf("START" to "Izquierda", "CENTER" to "Centro").forEach { (key, label) ->
                    LyricSizeChip(label, ui.data.preferences.lyricsAlign == key, accent) { state.setLyricsAlign(key) }
                }
            }
            LyricSettingRow("Color") {
                listOf("ACCENT" to "Acento", "ARTWORK" to "Portada").forEach { (key, label) ->
                    LyricSizeChip(label, ui.data.preferences.lyricsColor == key, accent) { state.setLyricsColor(key) }
                }
                LyricSizeChip(
                    "Personalizado",
                    ui.data.preferences.lyricsColor == "CUSTOM",
                    accent
                ) { state.setLyricsColor("CUSTOM") }
                Spacer(Modifier.width(4.dp))
                LyricColorSwatch(ui.data.preferences.lyricsColorCustom, accent) {
                    state.setLyricsColorCustom(it)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Opacidad de las líneas que no están sonando",
                color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SlimSlider(
                    value = (ui.data.preferences.lyricsInactiveOpacity - 0.15f) / 0.75f,
                    onValueChange = { state.setLyricsInactiveOpacity(0.15f + it * 0.75f) },
                    color = accent,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${(ui.data.preferences.lyricsInactiveOpacity * 100).toInt()} %",
                    color = CypherColors.TextSecondary, fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (lyricsShaderAvailable) {
                    "El texto se pinta con un shader SkSL (AGSL en el móvil): la línea activa " +
                        "lleva degradado animado y brillo, y la animación de karaoke la va " +
                        "llenando al ritmo real. Se aplica al panel y a la vista ampliada."
                } else {
                    "Esta GPU no compiló el shader: las letras usan el degradado de respaldo. " +
                        "El resto de ajustes sí se aplica."
                },
                color = CypherColors.TextTertiary, fontSize = 10.sp
            )
        }

        SectionHeader("FONDO", accent)
        SettingsCard {
            Text("Elige cómo se ve la ventana", color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackgroundModeCard("ORIGINAL", "Original", "El fondo de Cypher, sin efectos.", ui.data.preferences.backgroundMode, accent, Modifier.weight(1f)) {
                    state.setBackgroundMode("ORIGINAL")
                }
                BackgroundModeCard("GLASS", "Plano", "Un solo color de fondo, sin degradados.", ui.data.preferences.backgroundMode, accent, Modifier.weight(1f)) {
                    state.setBackgroundMode("GLASS")
                }
                BackgroundModeCard("IMAGE", "Imagen propia", "Tu foto, desenfocada y oscurecida.", ui.data.preferences.backgroundMode, accent, Modifier.weight(1f)) {
                    state.setBackgroundMode("IMAGE")
                }
            }
            if (ui.data.preferences.backgroundMode == "IMAGE") {
                Spacer(Modifier.height(14.dp))
                val image = ui.data.preferences.backgroundImage
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            image?.let { File(it).name } ?: "Sin imagen elegida",
                            color = CypherColors.TextSecondary, fontSize = 12.sp, maxLines = 1
                        )
                        Text(
                            "Se guarda una copia en la carpeta de datos de Cypher.",
                            color = CypherColors.TextTertiary, fontSize = 10.sp
                        )
                    }
                    TextButton(onClick = {
                        state.openFilePicker(
                            FilePickerConfig(
                                title = "Elige una imagen de fondo",
                                subtitle = "Se guarda una copia dentro de Cypher.",
                                extensions = setOf("jpg", "jpeg", "png", "webp", "bmp"),
                                startDirectory = java.io.File(System.getProperty("user.home"), "Pictures"),
                                onPick = { files ->
                                    files.firstOrNull()?.let { file -> importBackgroundPicture(file)?.let(state::setBackgroundImage) }
                                }
                            )
                        )
                    }) { Text("Elegir imagen…", color = accent, fontSize = 12.sp) }
                    if (image != null) {
                        TextButton(onClick = { state.setBackgroundImage(null) }) {
                            Text("Quitar", color = CypherColors.Danger, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingSlider("Desenfoque", "${ui.data.preferences.backgroundBlur}", ui.data.preferences.backgroundBlur / 40f, accent) {
                    state.setBackgroundBlur((it * 40).toInt())
                }
                SettingSlider("Oscurecido", "${(ui.data.preferences.backgroundDim * 100).toInt()} %", ui.data.preferences.backgroundDim / 0.85f, accent) {
                    state.setBackgroundDim(it * 0.85f)
                }
                SettingSlider("Opacidad", "${(ui.data.preferences.backgroundOpacity * 100).toInt()} %", (ui.data.preferences.backgroundOpacity - 0.15f) / 0.85f, accent) {
                    state.setBackgroundOpacity(0.15f + it * 0.85f)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CROP" to "Cubrir", "FIT" to "Contener", "FILL" to "Estirar").forEach { (fit, label) ->
                        Pill(label, ui.data.preferences.backgroundFit == fit, accent) { state.setBackgroundFit(fit) }
                    }
                }
            } else if (ui.data.preferences.backgroundMode == "GLASS") {
                Spacer(Modifier.height(10.dp))
                Text(
                    "El fondo y los paneles comparten el shader SkSL: el cristal refracta esta " +
                        "malla, con borde luminoso y dispersión en las esquinas.",
                    color = CypherColors.TextTertiary, fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Se aplica al instante y se guarda como preferencia.", color = CypherColors.TextTertiary, fontSize = 10.sp)
        }

        // The rail keeps four destinations; the rest of the screens live here instead of
        // competing with the music for a spot on the left edge.
        SectionHeader("BIBLIOTECA LOCAL", accent)
        SettingsCard {
            Text(
                "${ui.data.tracks.size} pistas · ${ui.data.albums.size} álbumes · ${ui.data.playlists.size} listas en este equipo",
                color = CypherColors.TextSecondary, fontSize = 11.sp
            )
            Spacer(Modifier.height(14.dp))
            Text("Pantalla inicial de la biblioteca", color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySection.entries.forEach { section ->
                    val label = when (section) {
                        LibrarySection.ALBUMS -> "Álbumes"
                        LibrarySection.ARTISTS -> "Artistas"
                        LibrarySection.SONGS -> "Canciones"
                    }
                    Pill(label, ui.data.preferences.libraryStart == section.name, accent) {
                        state.setLibraryStart(section)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Carpeta de música", color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(ui.data.libraryFolder ?: "Sin carpeta elegida", color = CypherColors.TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { pickFolder(state) }) { Text("Elegir carpeta", color = accent, fontSize = 12.sp) }
                TextButton(onClick = { scope.launch(Dispatchers.IO) { state.rescan() } }) {
                    Text("Volver a escanear", color = accent, fontSize = 12.sp)
                }
            }
            Text(
                "Formatos: mp3, m4a, aac, flac, ogg, opus y wav. Las carátulas se copian a la carpeta de datos.",
                color = CypherColors.TextTertiary,
                fontSize = 10.sp
            )
        }

        SectionHeader("DATOS", accent)

        Spacer(Modifier.height(12.dp))
        SettingsCard {
            Text(
                "Sincronización Universal (.cypherpack)", color = CypherColors.TextPrimary,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Empaqueta toda tu biblioteca, playlists, favoritos, ajustes y carátulas en un único archivo " +
                    ".cypherpack interoperable entre Windows y Android. Al hacer doble clic en un .cypherpack en Windows se importa automáticamente.",
                color = CypherColors.TextTertiary, fontSize = 10.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(accent)
                        .clickable {
                            val userDownloads = File(System.getProperty("user.home"), "Downloads")
                            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm").format(java.util.Date())
                            val packFile = File(userDownloads, "cypher_backup_$stamp.cypherpack")
                            state.exportCypherPack(packFile)
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text("Exportar .cypherpack", color = Color(0xFF14101A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.18f))
                        .clickable {
                            state.openFilePicker(
                                FilePickerConfig(
                                    title = "Elige el paquete Cypher (.cypherpack)",
                                    subtitle = "Paquete .cypherpack exportado desde móvil u otro PC.",
                                    extensions = setOf("cypherpack", "zip", "json"),
                                    onPick = { files -> files.firstOrNull()?.let { state.importCypherPack(it) } }
                                )
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text("Importar paquete…", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (ui.backupNotice != null || ui.notice != null) {
                Spacer(Modifier.height(8.dp))
                Text(ui.notice ?: ui.backupNotice.orEmpty(), color = CypherColors.TextSecondary, fontSize = 10.sp)
            }
        }

        SectionHeader("HERRAMIENTAS", accent)
        SettingsCard {
            Text(
                "Estas pantallas ya no están en el rail. Siguen aquí, a un clic.",
                color = CypherColors.TextTertiary, fontSize = 11.sp
            )
            Spacer(Modifier.height(10.dp))
            SettingsLink("Biblioteca local", "Archivos de este equipo (modo local)", accent) { state.select(Screen.LIBRARY) }
            SettingsLink("Estadísticas", "Qué escuchaste y cuándo", accent) { state.select(Screen.STATS) }
            SettingsLink("Wrapped", "Tu resumen del año", accent) { state.select(Screen.WRAPPED) }
            SettingsLink("Descargas", "Lo que ya está en este equipo", accent) { state.select(Screen.DOWNLOADS) }
            SettingsLink("YouTube Music", "Buscar, importar y descargar", accent) { state.select(Screen.ONLINE) }
            SettingsLink("Importar", "Carpetas, ZIP y listas", accent) { state.select(Screen.IMPORT) }
        }
        SettingsCard {
            Text("Almacenamiento y modo de ejecución", color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (Storage.isPortable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.22f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("PORTABLE", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Carpeta autónoma", color = CypherColors.TextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Todos los datos de usuario (biblioteca, carátulas, letras, descargas y Chromium) se guardan dentro de la carpeta «data/» de esta instalación.",
                    color = CypherColors.TextTertiary, fontSize = 11.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(Storage.dataDir.toString(), color = CypherColors.TextSecondary, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "La actualización automática por MSI está desactivada en modo portable para preservar la autonomía de tu carpeta.",
                    color = CypherColors.TextTertiary, fontSize = 10.sp
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (ui.updateState.isMsi) accent.copy(alpha = 0.22f) else CypherColors.Pill)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (ui.updateState.isMsi) "INSTALACIÓN MSI" else "SISTEMA",
                            color = if (ui.updateState.isMsi) accent else CypherColors.TextSecondary,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(Storage.dataDir.toString(), color = CypherColors.TextSecondary, fontSize = 10.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Escritura atómica y copia previa a cada escaneo. Todo se queda en este equipo.",
                    color = CypherColors.TextTertiary,
                    fontSize = 11.sp
                )
            }
        }

        SectionHeader("INTEGRACIÓN Y ESCRITORIO", accent)
        SettingsCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Discord Rich Presence", color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Muestra en Discord qué canción y artista estás escuchando en tiempo real.", color = CypherColors.TextTertiary, fontSize = 10.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (ui.data.preferences.discordRpc) accent else CypherColors.Pill)
                        .clickable { state.setDiscordRpc(!ui.data.preferences.discordRpc) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (ui.data.preferences.discordRpc) "Activado" else "Desactivado",
                        color = if (ui.data.preferences.discordRpc) Color(0xFF14101A) else CypherColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Minimizar a la bandeja al cerrar", color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Al pulsar la 'X', Cypher se oculta en la bandeja junto al reloj para seguir sonando.", color = CypherColors.TextTertiary, fontSize = 10.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (ui.data.preferences.minimizeToTray) accent else CypherColors.Pill)
                        .clickable { state.setMinimizeToTray(!ui.data.preferences.minimizeToTray) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (ui.data.preferences.minimizeToTray) "Activado" else "Desactivado",
                        color = if (ui.data.preferences.minimizeToTray) Color(0xFF14101A) else CypherColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Atajos de teclado globales", color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Ctrl+Alt+Espacio (Play/Pausa), Ctrl+Alt+Flechas (Pistas y volumen) desde cualquier app o juego.", color = CypherColors.TextTertiary, fontSize = 10.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (ui.data.preferences.globalHotkeys) accent else CypherColors.Pill)
                        .clickable { state.setGlobalHotkeys(!ui.data.preferences.globalHotkeys) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (ui.data.preferences.globalHotkeys) "Activado" else "Desactivado",
                        color = if (ui.data.preferences.globalHotkeys) Color(0xFF14101A) else CypherColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Normalización de volumen", color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Nivela automáticamente la ganancia entre canciones para evitar picos y sobresaltos.", color = CypherColors.TextTertiary, fontSize = 10.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (ui.data.preferences.normalizeVolume) accent else CypherColors.Pill)
                        .clickable { state.setNormalizeVolume(!ui.data.preferences.normalizeVolume) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (ui.data.preferences.normalizeVolume) "Activado" else "Desactivado",
                        color = if (ui.data.preferences.normalizeVolume) Color(0xFF14101A) else CypherColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Scroll de volumen en barra de tareas", color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Gira la rueda del ratón sobre el icono de Cypher junto al reloj para regular el volumen rápidamente.", color = CypherColors.TextTertiary, fontSize = 10.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (ui.data.preferences.trayVolumeScroll) accent else CypherColors.Pill)
                        .clickable { state.setTrayVolumeScroll(!ui.data.preferences.trayVolumeScroll) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (ui.data.preferences.trayVolumeScroll) "Activado" else "Desactivado",
                        color = if (ui.data.preferences.trayVolumeScroll) Color(0xFF14101A) else CypherColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Calibración acústica ReplayGain (EBU R128)", color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Nivelación perceptual de volumen para que canciones de cualquier época suenen con igual presión sonora.", color = CypherColors.TextTertiary, fontSize = 10.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (ui.data.preferences.replayGain) accent else CypherColors.Pill)
                        .clickable { state.setReplayGain(!ui.data.preferences.replayGain) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (ui.data.preferences.replayGain) "Activado" else "Desactivado",
                        color = if (ui.data.preferences.replayGain) Color(0xFF14101A) else CypherColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Transición suave (Crossfade)", color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (ui.data.preferences.crossfadeSeconds > 0) "${ui.data.preferences.crossfadeSeconds} seg" else "Desactivado",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("Mezcla progresiva de audio entre canciones consecutivas para evitar cortes abruptos.", color = CypherColors.TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Off", 2 to "2s", 3 to "3s", 4 to "4s", 6 to "6s").forEach { (secs, label) ->
                        Pill(label, ui.data.preferences.crossfadeSeconds == secs, accent) {
                            state.setCrossfadeSeconds(secs)
                        }
                    }
                }
            }
        }

        SectionHeader("ECUALIZADOR Y PROCESAMIENTO NATIVO", accent)
        SettingsCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ecualizador de audio (10 bandas)", color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Calibración de audio en tiempo real integrada en el motor de sonido.", color = CypherColors.TextTertiary, fontSize = 10.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (ui.data.preferences.equalizerEnabled) accent else CypherColors.Pill)
                        .clickable { state.setEqualizerEnabled(!ui.data.preferences.equalizerEnabled) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (ui.data.preferences.equalizerEnabled) "Activado" else "Desactivado",
                        color = if (ui.data.preferences.equalizerEnabled) Color(0xFF14101A) else CypherColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (ui.data.preferences.equalizerEnabled) {
                Spacer(Modifier.height(14.dp))
                Text("Perfiles de sonido", color = CypherColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "FLAT" to "Plano",
                        "BASS_BOOST" to "Bass Boost",
                        "ROCK" to "Rock",
                        "POP" to "Pop",
                        "VOCAL" to "Vocal",
                        "ELECTRONIC" to "Electrónica"
                    ).forEach { (presetKey, presetName) ->
                        Pill(presetName, ui.data.preferences.equalizerPreset == presetKey, accent) {
                            state.setEqualizerPreset(presetKey)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Refuerzo de graves (Bass Boost)", color = CypherColors.TextPrimary, fontSize = 11.sp)
                    Text("${(ui.data.preferences.bassBoost * 100).toInt()}%", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.Slider(
                    value = ui.data.preferences.bassBoost,
                    onValueChange = { state.setBassBoost(it) },
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent
                    ),
                    modifier = Modifier.fillMaxWidth().height(22.dp)
                )
            }
        }

        SectionHeader("CYPHER CONNECT", accent)
        SettingsCard {
            Text("Control remoto desde el móvil (Cypher Remote)", color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Controla la música, pasa canciones, ajusta el volumen y ve la letra en tiempo real desde la app de Cypher en Android o cualquier navegador en tu red local.",
                color = CypherColors.TextTertiary, fontSize = 10.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent)
                        .clickable { state.openConnectServerDialog() }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text("Mostrar código QR de conexión", color = Color(0xFF14101A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("IP LAN: ${state.connectServer.lanIp}:8484", color = CypherColors.TextSecondary, fontSize = 11.sp)
            }
        }

        if (ui.updateState.isMsi) {
            SectionHeader("ACTUALIZACIONES", accent)
            SettingsCard {
                Text("Cypher PC para Windows", color = CypherColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Versión instalada: ${com.cypher.desktop.core.DesktopUpdater.CURRENT_VERSION}", color = CypherColors.TextTertiary, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))

                val update = ui.updateState.available
                if (ui.updateState.downloading) {
                    Text("Descargando actualización… ${(ui.updateState.progress * 100).toInt()}%", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(CypherColors.Pill)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(ui.updateState.progress.coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accent)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Al finalizar la descarga, el instalador MSI se iniciará automáticamente.", color = CypherColors.TextTertiary, fontSize = 10.sp)
                } else if (update != null) {
                    Text("¡Nueva versión disponible: Cypher PC ${update.version}!", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent)
                                .clickable { state.installUpdate() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Actualizar ahora", color = Color(0xFF14101A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (ui.updateState.checking) "Comprobando actualizaciones…" else "Cypher PC está al día.",
                            color = CypherColors.TextSecondary,
                            fontSize = 11.sp
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent.copy(alpha = 0.18f))
                                .clickable(enabled = !ui.updateState.checking) { state.checkForUpdates() }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                if (ui.updateState.checking) "Buscando…" else "Buscar ahora",
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (ui.updateState.error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(ui.updateState.error, color = Color(0xFFE06A6A), fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Cypher Desktop ${com.cypher.desktop.core.DesktopUpdater.CURRENT_VERSION} · biblioteca local-first", color = CypherColors.TextTertiary, fontSize = 10.sp)
    }
}

@Composable
private fun BackgroundModeCard(
    mode: String,
    title: String,
    body: String,
    current: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selected = current == mode
    Column(
        modifier
            .background(if (selected) accent.copy(alpha = 0.18f) else CypherColors.Pill, RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(title, color = if (selected) accent else CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(body, color = CypherColors.TextTertiary, fontSize = 10.sp)
    }
}

@Composable
private fun SettingSlider(label: String, value: String, fraction: Float, accent: Color, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CypherColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.width(92.dp))
        Box(Modifier.weight(1f)) {
            Slider(
                value = fraction.coerceIn(0f, 1f),
                onValueChange = onChange,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = CypherColors.Pill),
                modifier = Modifier.height(24.dp)
            )
        }
        Text(value, color = CypherColors.TextTertiary, fontSize = 10.sp, modifier = Modifier.width(44.dp))
    }
}


/* ---------------------------------------------------------------- links */

@Composable
private fun SettingsLink(label: String, hint: String, accent: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) CypherColors.Pill.copy(alpha = 0.45f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = CypherColors.TextTertiary, fontSize = 10.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = accent, modifier = Modifier.size(16.dp))
    }
}


@Composable
private fun LyricSettingRow(label: String, content: @Composable () -> Unit) {
    Text(
        label,
        color = CypherColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 7.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun LyricSizeChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.20f) else CypherColors.Pill.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) accent else CypherColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

package com.cypher.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PickerMode {
    /** One file. */
    FILE,

    /** Several files at once. */
    MULTIPLE,

    /** A folder; the row list is used to walk into it. */
    FOLDER
}

/**
 * What the picker should look for. Call sites build one of these and hand it to
 * [com.cypher.desktop.app.AppState.openFilePicker]; the picker is a plain Compose dialog,
 * so the app never falls back to the grey Swing chooser again.
 */
data class FilePickerConfig(
    val title: String,
    val subtitle: String? = null,
    val mode: PickerMode = PickerMode.FILE,
    /** Lowercase, no dot. Empty means every file is shown. */
    val extensions: Set<String> = emptySet(),
    val startDirectory: File? = null,
    val confirmLabel: String = "Elegir",
    val onPick: (List<File>) -> Unit
)

private var lastDirectory: File? = null

private fun places(): List<Triple<String, ImageVector, File>> = buildList {
    val home = File(System.getProperty("user.home"))
    add(Triple("Inicio", Lucide.Home, home))
    listOf(
        "Escritorio" to "Desktop",
        "Documentos" to "Documents",
        "Descargas" to "Downloads",
        "Música" to "Music",
        "Imágenes" to "Pictures",
        "Vídeos" to "Videos"
    ).forEach { (label, folder) ->
        val directory = File(home, folder)
        if (directory.isDirectory) add(Triple(label, Lucide.FolderOpen, directory))
    }
}

private fun iconFor(file: File): ImageVector = when {
    file.isDirectory -> Lucide.Folder
    file.extension.lowercase() in setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "lrc") -> Lucide.Music
    file.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif") -> Lucide.Image
    file.extension.lowercase() in setOf("zip", "7z", "rar") -> Lucide.FileArchive
    else -> Lucide.File
}

private fun readableSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

@Composable
fun FilePickerDialog(config: FilePickerConfig, accent: Color, onDismiss: () -> Unit) {
    val initial = remember(config) {
        config.startDirectory?.takeIf { it.isDirectory }
            ?: lastDirectory?.takeIf { it.isDirectory }
            ?: File(System.getProperty("user.home"))
    }
    var current by remember { mutableStateOf(initial) }
    var selection by remember { mutableStateOf<Set<File>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var typedPath by remember { mutableStateOf(initial.absolutePath) }
    val searchFocus = remember { FocusRequester() }

    fun goTo(folder: File) {
        if (!folder.isDirectory) return
        current = folder.absoluteFile
        typedPath = current.absolutePath
        selection = emptySet()
        query = ""
        lastDirectory = current
    }

    fun accept(files: List<File>) {
        if (files.isEmpty()) return
        lastDirectory = files.first().let { if (it.isDirectory) it else it.parentFile }
        config.onPick(files)
        onDismiss()
    }

    val entries = remember(current, query, config.extensions) {
        val visible = current.listFiles()?.filter { file ->
            if (file.name.startsWith(".")) return@filter false
            if (!file.isDirectory) {
                val extension = file.extension.lowercase()
                if (extension == "sys" || extension == "dll" || extension == "ini" || extension == "lnk") return@filter false
                if (config.extensions.isNotEmpty() && extension !in config.extensions) return@filter false
            }
            if (query.isBlank()) true
            else file.name.lowercase().contains(query.trim().lowercase())
        }.orEmpty()
        visible.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }
    val validSelection = remember(selection, current) { selection.filter { it.exists() } }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(1180.dp).height(700.dp)
                .background(CypherColors.Surface, RoundedCornerShape(22.dp))
                .border(1.dp, CypherColors.Border, RoundedCornerShape(22.dp))
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> { onDismiss(); true }
                        Key.Enter -> {
                            when {
                                config.mode == PickerMode.FOLDER -> { accept(listOf(current)); true }
                                validSelection.isNotEmpty() -> { accept(validSelection.toList()); true }
                                else -> false
                            }
                        }
                        else -> false
                    }
                }
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(config.title, color = CypherColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (config.subtitle != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(config.subtitle, color = CypherColors.TextTertiary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onDismiss, Lucide.X, "Cerrar", CypherColors.TextSecondary)
            }

            Row(Modifier.weight(1f).fillMaxWidth()) {
                // Places
                Column(Modifier.width(190.dp).fillMaxHeight().padding(start = 14.dp, bottom = 14.dp)) {
                    Text("SITIOS", color = CypherColors.TextTertiary, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        items(places()) { (label, icon, target) ->
                            PlaceRow(label, icon, accent, selected = current == target.absoluteFile) { goTo(target) }
                        }
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("UNIDADES", color = CypherColors.TextTertiary, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp))
                        }
                        items(File.listRoots().toList()) { root ->
                            PlaceRow(root.absolutePath, Lucide.HardDrive, accent, selected = current.absolutePath.startsWith(root.absolutePath)) {
                                goTo(root)
                            }
                        }
                    }
                }

                // Files
                Column(Modifier.weight(1f).fillMaxHeight().padding(end = 18.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ current.parentFile?.let { goTo(it) } }, Lucide.ArrowUp, "Subir un nivel", CypherColors.TextSecondary, enabled = current.parentFile != null)
                        Spacer(Modifier.width(6.dp))
                        Row(
                            Modifier.weight(1f).padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val parts = current.absolutePath.split(File.separatorChar).filter { it.isNotBlank() }
                            parts.forEachIndexed { index, part ->
                                val target = File(parts.take(index + 1).joinToString(File.separator) + File.separator)
                                Text(
                                    part,
                                    color = CypherColors.TextSecondary, fontSize = 11.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                        .clickable { goTo(if (target.isDirectory) target else current) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                if (index < parts.lastIndex) {
                                    Icon(Lucide.ChevronRight, null, tint = CypherColors.TextTertiary, modifier = Modifier.size(11.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    // Editable path, so a known location can be typed in one step.
                    Box(
                        Modifier.fillMaxWidth().height(34.dp)
                            .background(CypherColors.Card, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = typedPath,
                            onValueChange = { typedPath = it },
                            singleLine = true,
                            textStyle = TextStyle(color = CypherColors.TextPrimary, fontSize = 11.sp),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                        val typed = File(typedPath.trim())
                                        if (typed.isDirectory) goTo(typed) else if (typed.isFile && config.mode != PickerMode.FOLDER) accept(listOf(typed))
                                        true
                                    } else false
                                }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    // Search inside the folder
                    Row(
                        Modifier.fillMaxWidth().height(34.dp)
                            .background(CypherColors.Card, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Lucide.Search, null, tint = CypherColors.TextTertiary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Buscar en esta carpeta", color = CypherColors.TextTertiary, fontSize = 11.sp)
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = CypherColors.TextPrimary, fontSize = 11.sp),
                                cursorBrush = SolidColor(accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (query.isNotEmpty()) IconButton({ query = "" }, Lucide.X, "Borrar búsqueda", CypherColors.TextTertiary, 24)
                    }

                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.weight(1f).fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CypherColors.Background.copy(alpha = 0.35f))
                    ) {
                        if (entries.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (query.isNotBlank()) "Nada coincide con «${query.trim()}»" else "Esta carpeta está vacía",
                                    color = CypherColors.TextTertiary, fontSize = 11.sp
                                )
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                items(entries, key = { it.absolutePath }) { file ->
                                    val isSelected = file in selection
                                    PickerRow(
                                        file = file,
                                        accent = accent,
                                        selected = isSelected,
                                        multi = config.mode == PickerMode.MULTIPLE,
                                        onOpen = {
                                            if (file.isDirectory) goTo(file)
                                            else if (config.mode == PickerMode.MULTIPLE) {
                                                selection = if (isSelected) selection - file else selection + file
                                            } else accept(listOf(file))
                                        },
                                        onSelect = {
                                            selection = if (config.mode == PickerMode.MULTIPLE) {
                                                if (isSelected) selection - file else selection + file
                                            } else if (file.isDirectory) emptySet() else setOf(file)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                config.mode == PickerMode.FOLDER -> "Carpeta: ${current.name.ifBlank { current.absolutePath }}"
                                validSelection.isEmpty() -> if (config.mode == PickerMode.MULTIPLE) "Ningún archivo elegido" else "Ningún archivo elegido"
                                validSelection.size == 1 -> validSelection.first().name
                                else -> "${validSelection.size} archivos elegidos"
                            },
                            color = if (validSelection.isEmpty() && config.mode != PickerMode.FOLDER) CypherColors.TextTertiary else CypherColors.TextSecondary,
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismiss) { Text("Cancelar", color = CypherColors.TextSecondary, fontSize = 12.sp) }
                        Spacer(Modifier.width(6.dp))
                        val enabled = config.mode == PickerMode.FOLDER || validSelection.isNotEmpty()
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (enabled) accent else CypherColors.Pill)
                                .clickable(enabled = enabled) {
                                    accept(if (config.mode == PickerMode.FOLDER) listOf(current) else validSelection.toList())
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text(
                                config.confirmLabel,
                                color = if (enabled) Color(0xFF14101A) else CypherColors.TextTertiary,
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(label: String, icon: ImageVector, accent: Color, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(
                when {
                    selected -> accent.copy(alpha = 0.16f)
                    hovered -> CypherColors.Card.copy(alpha = 0.9f)
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) accent else CypherColors.TextSecondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(9.dp))
        Text(label, color = if (selected) accent else CypherColors.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PickerRow(
    file: File,
    accent: Color,
    selected: Boolean,
    multi: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var lastClick by remember { mutableStateOf(0L) }
    Row(
        Modifier.fillMaxWidth().height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(
                when {
                    selected -> accent.copy(alpha = 0.16f)
                    hovered -> CypherColors.Card.copy(alpha = 0.85f)
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .clickable {
                val now = System.currentTimeMillis()
                if (now - lastClick < 400L) onOpen() else onSelect()
                lastClick = now
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            iconFor(file), null,
            tint = if (selected) accent else if (file.isDirectory) CypherColors.LavenderLight.copy(alpha = 0.8f) else CypherColors.TextTertiary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            file.name,
            color = if (selected) accent else CypherColors.TextPrimary,
            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!file.isDirectory) {
            Text(readableSize(file.length()), color = CypherColors.TextTertiary, fontSize = 10.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                dateFormat.format(Date(file.lastModified())),
                color = CypherColors.TextTertiary, fontSize = 10.sp
            )
        } else {
            Icon(Lucide.ChevronRight, null, tint = CypherColors.TextTertiary, modifier = Modifier.size(13.dp))
        }
        if (selected && multi) {
            Spacer(Modifier.width(10.dp))
            Icon(Lucide.Check, "Elegido", tint = accent, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun IconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String?,
    tint: Color,
    size: Int = 30,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(9.dp))
            .background(if (hovered && enabled) CypherColors.Card else Color.Transparent)
            .hoverable(interaction)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(15.dp))
    }
}

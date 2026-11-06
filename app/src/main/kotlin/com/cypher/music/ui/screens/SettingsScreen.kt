package com.cypher.music.ui.screens

import com.cypher.music.wrapped.WrappedAvailability

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.data.AccentPalette
import com.cypher.music.data.BackgroundTones
import com.cypher.music.data.CardTones
import com.cypher.music.data.ThemePreferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.FormatQuote
import com.cypher.music.data.LyricFontSize
import com.cypher.music.data.LyricAlignment
import com.cypher.music.data.LyricColorMode
import com.cypher.music.data.LyricsDisplaySettings
import com.cypher.music.data.LyricsSettingsManager

import com.cypher.music.data.User
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.platform.LocalContext
import com.cypher.music.data.BackupImportSummary
import com.cypher.music.data.CrossfadeOptions
import com.cypher.music.playback.SleepTimerMode
import com.cypher.music.data.UserListeningStats
import com.cypher.music.network.YouTubeAccountSession
import com.cypher.music.ui.components.UsernameDialog
import com.cypher.music.ui.theme.ResolvedThemeColors
import com.cypher.music.ui.utils.AppHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    user: User?,
    listeningStats: UserListeningStats = UserListeningStats(),
    ytSession: YouTubeAccountSession = YouTubeAccountSession(),
    isSyncingYouTube: Boolean = false,
    syncStatusMsg: String? = null,
    themePrefs: ThemePreferences,
    themeColors: ResolvedThemeColors,
    onUpdatePrefs: ((ThemePreferences) -> ThemePreferences) -> Unit,
    onResetDefaults: () -> Unit,
    onUpdateUsername: (String) -> Unit = {},
    onOpenYouTubeLogin: () -> Unit = {},
    onSyncYouTube: () -> Unit = {},
    onUnlinkYouTube: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    isLoudnessNormalizationEnabled: Boolean = true,
    onToggleLoudnessNormalization: (Boolean) -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    onOpenLiquidGlassSettings: () -> Unit = {},
    onOpenWrapped: () -> Unit = {},
    sleepTimerMode: SleepTimerMode = SleepTimerMode.OFF,
    sleepTimerRemainingSeconds: Long? = null,
    onExportBackup: () -> String = { "" },
    onImportBackup: (String) -> BackupImportSummary = { BackupImportSummary(0, 0, 0) },
    bottomPadding: androidx.compose.ui.unit.Dp = 110.dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lyricsSettingsManager = remember { LyricsSettingsManager(context) }
    val lyricsSettings by lyricsSettingsManager.settings.collectAsState()
    val wrappedYear = WrappedAvailability.activeYear()
    var isUsernameDialogOpen by remember { mutableStateOf(false) }


    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = onExportBackup()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Copia de seguridad exportada correctamente", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                val summary = onImportBackup(json)
                Toast.makeText(
                    context,
                    "Restauradas ${summary.playlistsCount} playlists y ${summary.favoritesCount} favoritos",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al importar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    ) {
        // Navigation Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBackIosNew,
                        contentDescription = "Volver",
                        tint = themeColors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Ajustes y Personalización",
                    color = themeColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Section: Cuenta y Sesión Local
        item {
            SettingsSectionHeader(
                title = "Perfil y Sesión Local",
                icon = Icons.Filled.Person,
                tint = themeColors.accent
            )

            SettingsCard(themeColors = themeColors) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (user != null) Color(user.avatarColor) else themeColors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user?.displayName ?: "Usuario Local",
                            color = themeColors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${user?.username ?: "default"}",
                            color = themeColors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            isUsernameDialogOpen = true
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = themeColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cambiar nombre",
                        color = themeColors.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: Estadísticas y Horas de Reproducción
        item {
            SettingsSectionHeader(
                title = "Estadísticas y Tiempo de Escucha",
                icon = Icons.Filled.BarChart,
                tint = themeColors.accent
            )

            SettingsCard(themeColors = themeColors) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Horas totales acumuladas",
                            color = themeColors.textSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = listeningStats.formattedTotalTime,
                            color = themeColors.accent,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Reproducciones",
                            color = themeColors.textSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${listeningStats.totalPlays}",
                            color = themeColors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // The recap is an end-of-year event: only listed while it is live.
                if (wrappedYear != null) {
                    HorizontalDivider(color = themeColors.separator, modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                onOpenWrapped()
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = themeColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                        ) {
                            Text(
                                text = "Cypher Wrapped $wrappedYear",
                                color = themeColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tu año en música, en stories",
                                color = themeColors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Abrir Wrapped",
                            tint = themeColors.textSecondary
                        )
                    }
                }

                if (listeningStats.topArtists.isNotEmpty()) {
                    HorizontalDivider(color = themeColors.separator, modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "Top Artistas Normalizados",
                        color = themeColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listeningStats.topArtists.take(5)) { art ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(themeColors.background)
                                    .border(1.dp, themeColors.separator, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${art.artist} · ${art.formattedTime}",
                                    color = themeColors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (listeningStats.topTracks.isNotEmpty()) {
                    HorizontalDivider(color = themeColors.separator, modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "Pistas más repetidas",
                        color = themeColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    listeningStats.topTracks.take(3).forEach { trk ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = trk.title,
                                    color = themeColors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = trk.artist,
                                    color = themeColors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "${trk.playCount}x",
                                color = themeColors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: Vinculación con YouTube Music & Google
        item {
            SettingsSectionHeader(
                title = "Sincronización YouTube Music (Google)",
                icon = Icons.Filled.CloudSync,
                tint = themeColors.accent
            )

            SettingsCard(themeColors = themeColors) {
                if (!ytSession.isLoggedIn) {
                    Text(
                        text = "Vincula tu cuenta de Google para sincronizar tus playlists, biblioteca remota y canciones con 'Me gusta' de YouTube Music.",
                        color = themeColors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onOpenYouTubeLogin()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.accent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Iniciar sesión con Google",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Conectado a YouTube Music",
                                color = themeColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = ytSession.accountEmail ?: "Sesión InnerTube activa",
                                color = themeColors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (syncStatusMsg != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = syncStatusMsg,
                            color = themeColors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                onSyncYouTube()
                            },
                            enabled = !isSyncingYouTube,
                            colors = ButtonDefaults.buttonColors(containerColor = themeColors.accent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            if (isSyncingYouTube) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sincronizar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = {
                                AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                onUnlinkYouTube()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColors.background),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .border(1.dp, themeColors.separator, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LinkOff,
                                contentDescription = null,
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Desvincular", color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: Color de Acento Principal
        item {
            SettingsSectionHeader(
                title = "Color de Acento Principal",
                icon = Icons.Filled.Palette,
                tint = themeColors.accent
            )

            SettingsCard(themeColors = themeColors) {
                Text(
                    text = "Elige tu color distintivo favorito",
                    color = themeColors.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AccentPalette.forEach { (colorHex, name) ->
                        val isSelected = themePrefs.accentColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                    onUpdatePrefs { it.copy(accentColor = colorHex) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = name,
                                    tint = if (colorHex == 0xFFFFFFFFL) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: Color de Fondo Base (OLED / Oscuro)
        item {
            SettingsSectionHeader(
                title = "Color de Fondo Base",
                icon = Icons.Filled.Tune,
                tint = themeColors.accent
            )

            SettingsCard(themeColors = themeColors) {
                Text(
                    text = "Tonalidad base del sistema (OLED puro u oscuros profundos)",
                    color = themeColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BackgroundTones.forEach { (bgHex, title) ->
                        val isSelected = themePrefs.customBackground == bgHex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) themeColors.accent else themeColors.background)
                                .border(1.dp, if (isSelected) themeColors.accent else themeColors.separator, RoundedCornerShape(12.dp))
                                .clickable {
                                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                    onUpdatePrefs { it.copy(customBackground = bgHex) }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Color.White else themeColors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: Tono de Tarjetas y Contenedores
        item {
            SettingsSectionHeader(
                title = "Tono de Tarjetas y Superficies",
                icon = Icons.Filled.Tune,
                tint = themeColors.accent
            )

            SettingsCard(themeColors = themeColors) {
                Text(
                    text = "Nivel de elevación de las tarjetas sobre el fondo",
                    color = themeColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardTones.forEach { (cardHex, title) ->
                        val isSelected = themePrefs.customCard == cardHex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) themeColors.accent else themeColors.background)
                                .border(1.dp, if (isSelected) themeColors.accent else themeColors.separator, RoundedCornerShape(12.dp))
                                .clickable {
                                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                    onUpdatePrefs { it.copy(customCard = cardHex) }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Color.White else themeColors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = themeColors.separator,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Bordes discretos de 1 dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bordes discretos de 1 dp",
                            color = themeColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Líneas de separación suave en tarjetas y dock",
                            color = themeColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = themePrefs.hasBorder,
                        onCheckedChange = { chk -> onUpdatePrefs { it.copy(hasBorder = chk) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = themeColors.accent
                        )
                    )
                }

                HorizontalDivider(
                    color = themeColors.separator,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Respuesta háptica
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Respuesta háptica",
                            color = themeColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Micro-ticks táctiles al pulsar botones y controles",
                            color = themeColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = themePrefs.hapticsEnabled,
                        onCheckedChange = { chk -> onUpdatePrefs { it.copy(hapticsEnabled = chk) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = themeColors.accent
                        )
                    )
                }

                HorizontalDivider(color = themeColors.separator, modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onOpenLiquidGlassSettings()
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Shader AGSL Liquid Glass",
                            color = themeColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (themePrefs.liquidGlassConfig.isEnabled) "Activo • Modo ${themePrefs.liquidGlassConfig.colorMode.label}" else "Desactivado",
                            color = themeColors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Personalizar Shader",
                        tint = themeColors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: Audio y Reproducción (Crossfade, Ecualizador DSP & Temporizador)
        item {
            SettingsSectionHeader(
                title = "Audio y Reproducción",
                icon = Icons.Filled.GraphicEq,
                tint = themeColors.accent
            )

            SettingsCard(themeColors = themeColors) {
                // Ecualizador DSP
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onOpenEqualizer()
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(themeColors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Equalizer,
                                contentDescription = null,
                                tint = themeColors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Ecualizador Nativo DSP",
                                color = themeColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Bandas de frecuencia, presets y Bass Boost",
                                color = themeColors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text(
                        text = "Ajustar →",
                        color = themeColors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = themeColors.separator, modifier = Modifier.padding(vertical = 12.dp))

                // Normalización de Volumen (-14 LUFS)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(themeColors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = themeColors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Normalización de Volumen",
                                color = themeColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Equipara el volumen sonoro (-14 LUFS) entre pistas",
                                color = themeColors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Switch(
                        checked = isLoudnessNormalizationEnabled,
                        onCheckedChange = {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onToggleLoudnessNormalization(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = themeColors.accent
                        )
                    )
                }

                HorizontalDivider(color = themeColors.separator, modifier = Modifier.padding(vertical = 12.dp))

                // Temporizador de Apagado
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            onOpenSleepTimer()
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(themeColors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bedtime,
                                contentDescription = null,
                                tint = themeColors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Temporizador de Apagado",
                                color = themeColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val timerStatus = if (sleepTimerRemainingSeconds != null && sleepTimerRemainingSeconds > 0) {
                                "${sleepTimerRemainingSeconds / 60}m restantes"
                            } else {
                                sleepTimerMode.label
                            }
                            Text(
                                text = "Estado: $timerStatus (con desvanecimiento suave)",
                                color = themeColors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text(
                        text = "Configurar →",
                        color = themeColors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = themeColors.separator, modifier = Modifier.padding(vertical = 12.dp))

                // Transición Continua (Crossfade)
                Text(
                    text = "Transición Continua entre Pistas (Crossfade)",
                    color = themeColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "Rampa de volumen suave entre canciones al finalizar",
                    color = themeColors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CrossfadeOptions.forEach { (sec, label) ->
                        val isSelected = themePrefs.crossfadeSeconds == sec
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) themeColors.accent else themeColors.background)
                                .border(1.dp, if (isSelected) themeColors.accent else themeColors.separator, RoundedCornerShape(10.dp))
                                .clickable {
                                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                    onUpdatePrefs { it.copy(crossfadeSeconds = sec) }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else themeColors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: Datos y Almacenamiento (Copia de Seguridad y Restauración)
        
        // Section: Personalización de Letras Sincronizadas
        item {
            SettingsSectionHeader(
                title = "Personalización de Letras",
                icon = Icons.Filled.FormatQuote,
                tint = themeColors.accent
            )

            SettingsCard(themeColors = themeColors) {
                // Live Preview Card
                Text(
                    text = "Vista Previa en Tiempo Real",
                    color = themeColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(themeColors.background.copy(alpha = 0.6f))
                        .border(1.dp, themeColors.separator, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (lyricsSettings.alignment == LyricAlignment.CENTER) Alignment.CenterHorizontally else Alignment.Start
                    ) {
                        Text(
                            text = "El cielo se vuelve carmesí al atardecer",
                            color = themeColors.textPrimary.copy(alpha = lyricsSettings.inactiveOpacity),
                            fontSize = lyricsSettings.fontSize.inactiveSp,
                            fontWeight = FontWeight.Normal,
                            textAlign = lyricsSettings.alignment.textAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Y la melodía resuena en nuestro corazón",
                            color = themeColors.accent,
                            fontSize = lyricsSettings.fontSize.activeSp,
                            fontWeight = FontWeight.Bold,
                            textAlign = lyricsSettings.alignment.textAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Bajo el eco de un verano inolvidable",
                            color = themeColors.textPrimary.copy(alpha = lyricsSettings.inactiveOpacity),
                            fontSize = lyricsSettings.fontSize.inactiveSp,
                            fontWeight = FontWeight.Normal,
                            textAlign = lyricsSettings.alignment.textAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider(color = themeColors.separator, modifier = Modifier.padding(vertical = 14.dp))

                // Tamaño de Fuente
                Text(
                    text = "Tamaño de Fuente",
                    color = themeColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LyricFontSize.values().forEach { size ->
                        val isSelected = lyricsSettings.fontSize == size
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) themeColors.accent else themeColors.background)
                                .border(1.dp, if (isSelected) themeColors.accent else themeColors.separator, RoundedCornerShape(10.dp))
                                .clickable {
                                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                    lyricsSettingsManager.updateSettings(lyricsSettings.copy(fontSize = size))
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = size.label,
                                color = if (isSelected) Color.White else themeColors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                HorizontalDivider(color = themeColors.separator, modifier = Modifier.padding(vertical = 14.dp))

                // Alineación de Texto
                Text(
                    text = "Alineación de Texto",
                    color = themeColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LyricAlignment.values().forEach { align ->
                        val isSelected = lyricsSettings.alignment == align
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) themeColors.accent else themeColors.background)
                                .border(1.dp, if (isSelected) themeColors.accent else themeColors.separator, RoundedCornerShape(10.dp))
                                .clickable {
                                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                    lyricsSettingsManager.updateSettings(lyricsSettings.copy(alignment = align))
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = align.label,
                                color = if (isSelected) Color.White else themeColors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                HorizontalDivider(color = themeColors.separator, modifier = Modifier.padding(vertical = 14.dp))

                // Opacidad de Líneas Inactivas
                Text(
                    text = "Atenuación de Versos Inactivos",
                    color = themeColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.25f to "Sutil (25%)", 0.45f to "Normal (45%)", 0.65f to "Clara (65%)", 0.85f to "Intensa (85%)").forEach { (op, label) ->
                        val isSelected = kotlin.math.abs(lyricsSettings.inactiveOpacity - op) < 0.05f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) themeColors.accent else themeColors.background)
                                .border(1.dp, if (isSelected) themeColors.accent else themeColors.separator, RoundedCornerShape(10.dp))
                                .clickable {
                                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                                    lyricsSettingsManager.updateSettings(lyricsSettings.copy(inactiveOpacity = op))
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label.substringBefore(" "),
                                color = if (isSelected) Color.White else themeColors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            SettingsSectionHeader(
                title = "Datos y Almacenamiento",
                icon = Icons.Filled.Storage,
                tint = themeColors.accent
            )

            SettingsCard(themeColors = themeColors) {
                Text(
                    text = "Respaldo y Restauración de Base de Datos",
                    color = themeColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Exporta o importa tus playlists y canciones favoritas en un archivo JSON local. El perfil, las estadísticas y los ajustes no se incluyen.",
                    color = themeColors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            val name = "cypher_backup_${user?.username ?: "backup"}_${System.currentTimeMillis()}.json"
                            exportLauncher.launch(name)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.accent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileDownload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            AppHaptics.actionClick(themePrefs.hapticsEnabled)
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.background),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .border(1.dp, themeColors.separator, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileUpload,
                            contentDescription = null,
                            tint = themeColors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importar", color = themeColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: Reset Defaults
        item {
            Button(
                onClick = {
                    AppHaptics.actionClick(themePrefs.hapticsEnabled)
                    onResetDefaults()
                },
                colors = ButtonDefaults.buttonColors(containerColor = themeColors.cardBackground),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, themeColors.separator, RoundedCornerShape(14.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = null,
                    tint = Color(0xFFFF453A),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Restaurar valores predeterminados",
                    color = Color(0xFFFF453A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    if (isUsernameDialogOpen) {
        UsernameDialog(
            currentName = user?.displayName.orEmpty(),
            themeColors = themeColors,
            isOnboarding = false,
            onConfirm = { name ->
                onUpdateUsername(name)
                isUsernameDialogOpen = false
            },
            onDismiss = { isUsernameDialogOpen = false }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            text = title.uppercase(),
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun SettingsCard(
    themeColors: ResolvedThemeColors,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(themeColors.cardBackground)
            .border(1.dp, themeColors.separator, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

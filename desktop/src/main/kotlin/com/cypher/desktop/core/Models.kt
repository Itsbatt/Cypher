package com.cypher.desktop.core

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    val path: String,
    val artwork: String? = null,
    val addedAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val trackIds: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** Local cover (file path or URL). */
    val artwork: String? = null,
    /** LOCAL / YOUTUBE / SPOTIFY. */
    val origin: String = "LOCAL",
    /** Canonical playlist link/id the content came from, used to detect re-imports. */
    val sourceUrl: String? = null
)

/**
 * A song that exists in a service but not as a file on this computer.
 *
 * [id] always keeps the service's identity: `yt_<videoId>` for YouTube Music (the same
 * shape Android writes) or `sp_<hash>` for a Spotify row we could not resolve. It is
 * never rewritten to a local id just because its audio was cached for playback.
 */
@Serializable
data class RemoteTrack(
    val id: String,
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val artworkUrl: String? = null,
    /** YOUTUBE / SPOTIFY */
    val source: String = "YOUTUBE",
    /** False once the service reports the song is gone; the reference is kept. */
    val available: Boolean = true,
    val isrc: String? = null,
    /** HIGH / MEDIUM / LOW / NONE for imported matches; null when it comes from YouTube directly. */
    val confidence: String? = null,
    /** Local track this row was matched to, when the match was automatic or manual. */
    val matchedTrackId: String? = null,
    val updatedAt: Long = 0
)

/** Lyrics tied to a stable song id, so they survive a reinstall and a different path. */
@Serializable
data class LyricDoc(
    val trackId: String,
    /** Raw LRC (or plain text) exactly as imported; timestamps are never rewritten. */
    val lrc: String,
    /** LOCAL / SYNCED / IMPORTED / LRCLIB / LYRICS_OVH */
    val source: String = "LOCAL",
    /** True when the document carries at least one timestamp. */
    val synced: Boolean = false,
    val language: String? = null,
    /** Provider that produced the document, when it was fetched automatically. */
    val providerId: String? = null,
    /** Where the lyrics came from, kept so the match can be audited later. */
    val sourceUrl: String? = null,
    /** When Cypher asked the provider, in epoch milliseconds. */
    val fetchedAt: Long = 0,
    /** 0..1 match score; null for manual or imported documents. */
    val confidence: Double? = null,
    val updatedAt: Long = 0
)

@Serializable
data class DailyStat(
    val day: String,
    val trackId: String,
    val listenDurationMs: Long = 0,
    val playCount: Int = 0,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String? = null,
    val lastListenedAt: Long = 0
)

@Serializable
data class Profile(val displayName: String = "Cypher", val avatarColor: Long = 0)

/**
 * An accent is always opaque. A stored value without alpha (0x009D7BB0) would draw
 * invisible buttons and tabs, and Android themes can send signed ints.
 */
fun normalizeAccent(value: Long): Long {
    var color = value and 0xFFFFFFFFL
    if ((color ushr 24) and 0xFFL == 0L) color = color or 0xFF000000L
    return color
}

/**
 * Every colour the interface is painted with, so a user can rebuild the palette instead
 * of choosing between a handful of presets. Defaults are the reference SVG palette.
 */
@Serializable
data class ThemeColors(
    val background: Long = 0xFF261B26,
    val surface: Long = 0xFF322332,
    val card: Long = 0xFF413041,
    val pill: Long = 0xFF4B3A4B,
    val border: Long = 0xFF5D4A5E,
    val separator: Long = 0xFF3A2C3A,
    val textPrimary: Long = 0xFFEDE7ED,
    val textSecondary: Long = 0xFFB8A9B8,
    val textTertiary: Long = 0xFF9E8B9C
)

@Serializable
data class Preferences(
    val accent: Long = 0xFF9D7BB0,
    /** Interface palette; see [ThemeColors]. */
    val colors: ThemeColors = ThemeColors(),
    val reduceMotion: Boolean = false,
    val volume: Float = 0.8f,
    /** RESUMEN / CANCIONES / ALBUMES / ARTISTAS */
    val libraryStart: String = "CANCIONES",
    val shuffle: Boolean = false,
    /** OFF / ALL / ONE */
    val repeat: String = "OFF",
    /** Optional explicit path to yt-dlp.exe, chosen by the user. */
    val ytDlpPath: String? = null,
    /** ORIGINAL / GLASS / IMAGE — see AppearanceBackground. */
    val backgroundMode: String = "ORIGINAL",
    /** Copy of the chosen picture inside the app-data folder, never a temp path. */
    val backgroundImage: String? = null,
    /** Blur radius in dp, only used by the IMAGE mode. */
    val backgroundBlur: Int = 18,
    /** 0..0.8 black veil for legibility over bright pictures. */
    val backgroundDim: Float = 0.42f,
    val backgroundOpacity: Float = 0.9f,
    /** CROP / FIT / FILL */
    val backgroundFit: String = "CROP",
    // ---------------------------------------------------------------- surfaces
    /** OLED / CHARCOAL / GRAPHITE / LIGHT — same names as the Android setting. */
    val surfaceStyle: String = "OLED",
    /** 0 disables the 1 dp hairline around cards and panels. */
    val borders: Boolean = true,
    /** 8..32 dp, shared with the Android corner radius. */
    val cornerRadius: Int = 20,
    /** 0..1 strength of the ambient glow behind the content. */
    val ambientGlow: Float = 0.85f,
    /** 0..0.3 tint of the translucent panels. */
    val glassTint: Float = 0.05f,
    // ----------------------------------------------------------------- cards
    /** FLAT / ELEVATED / OUTLINED */
    val cardStyle: String = "ELEVATED",
    /** COMPACT / COMFORTABLE / SPACIOUS */
    val density: String = "COMFORTABLE",
    // ---------------------------------------------------------------- panels
    val showRightPanel: Boolean = true,
    val showLibraryRail: Boolean = true,
    /** FLOATING / DOCKED */
    val playerStyle: String = "FLOATING",
    // ------------------------------------------------------------ transitions
    val animations: Boolean = true,
    // ----------------------------------------------------------------- lyrics
    /** S / M / L / XL — the same four sizes as Android. */
    val lyricsSize: String = "M",
    /** START / CENTER */
    val lyricsAlign: String = "START",
    /** 0.2..0.9 opacity for the lines that are not being sung. */
    val lyricsInactiveOpacity: Float = 0.45f,
    /** ACCENT / ARTWORK / CUSTOM */
    val lyricsColor: String = "ACCENT",
    /** Used when [lyricsColor] is CUSTOM. */
    val lyricsColorCustom: Long = 0xFFD8BEE0,
    /** Automatic lookup through the registered providers. */
    val lyricsAutoFetch: Boolean = true,
    /** NONE / KARAOKE — how the line being sung is revealed. */
    val lyricsAnimation: String = "KARAOKE",
    /** NONE / AURORA / GRADIENT / GLOW — runtime shader painted on the letters. */
    val lyricsShader: String = "AURORA",
    /** COMPACT / NORMAL / WIDE — distance between lyric lines. */
    val lyricsLineSpacing: String = "NORMAL",
    // ------------------------------------------------------------- desktop v1.2.0
    /** Rich Presence in Discord desktop client. */
    val discordRpc: Boolean = true,
    /** Custom Discord Application/Client ID, or null to use Cypher default. */
    val discordClientId: String? = null,
    /** Minimize to Windows System Tray on close instead of exiting. */
    val minimizeToTray: Boolean = true,
    /** System-wide hotkeys (Ctrl+Alt+Space/Arrows) via Win32. */
    val globalHotkeys: Boolean = true,
    /** Dynamic volume leveling to prevent volume jumps between tracks. */
    val normalizeVolume: Boolean = true,
    // ------------------------------------------------------------- desktop v1.3.0
    /** Crossfade duration in seconds (0 = disabled, 2..6 = crossfade). */
    val crossfadeSeconds: Int = 3,
    /** Whether EBU R128 / ReplayGain loudness normalization is active. */
    val replayGain: Boolean = true,
    /** Whether Equalizer is enabled. */
    val equalizerEnabled: Boolean = false,
    /** Preset name: FLAT, ROCK, POP, BASS_BOOST, VOCAL, ELECTRONIC, CUSTOM. */
    val equalizerPreset: String = "FLAT",
    /** 10 band gains in dB (-12.0..+12.0). */
    val equalizerGains: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    /** Bass boost level 0.0..1.0. */
    val bassBoost: Float = 0f,
    /** Volume scroll on Windows System Tray / taskbar icon. */
    val trayVolumeScroll: Boolean = true,
    /** Cypher Connect LAN remote server port (default 8484, 0 = disabled). */
    val connectServerPort: Int = 8484
)

/**
 * One entry of the search dropdown. Either a typed query or a concrete result the user
 * opened; both persist locally so the history survives a restart.
 */
@Serializable
data class RecentSearch(
    /** "q:<query>" for a query, "<KIND>:<id>" for an opened result. */
    val key: String,
    val query: String = "",
    val title: String,
    val subtitle: String = "",
    /** QUERY / TRACK / ALBUM / ARTIST / PLAYLIST / REMOTE */
    val kind: String = "QUERY",
    val targetId: String? = null,
    val artwork: String? = null,
    val at: Long = 0
)

/** A home block the user can reorder or hide, mirroring Android's HomeSection. */
@Serializable
data class HomeSectionPref(val id: String, val enabled: Boolean = true)

@Serializable
data class LibraryData(
    /** 3 = local-first, no account and no cloud copy of the library. */
    val version: Int = 3,
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<String> = emptyList(),
    val stats: List<DailyStat> = emptyList(),
    val profile: Profile = Profile(),
    val profileUpdatedAt: Long = 0,
    /**
     * False until the user has seen the welcome screen, even if they chose to skip it.
     * Without this flag a fresh install and a deliberate "Ahora no" look identical.
     */
    val profileSetupDone: Boolean = false,
    val preferences: Preferences = Preferences(),
    val themeUpdatedAt: Long = 0,
    val recent: List<String> = emptyList(),
    val recentSearches: List<RecentSearch> = emptyList(),
    val homeSections: List<HomeSectionPref> = defaultHomeSections(),
    val libraryFolder: String? = null,
    val downloadsFolder: String? = null,
    /** Service songs referenced by imported playlists; no audio is stored here. */
    val remoteTracks: List<RemoteTrack> = emptyList(),
    val lyrics: List<LyricDoc> = emptyList(),
    val lastScanAt: Long = 0
) {
    fun track(id: String): Track? = tracks.firstOrNull { it.id == id }
    fun playlist(id: String): Playlist? = playlists.firstOrNull { it.id == id }
    fun isFavorite(id: String): Boolean = favorites.contains(id)
    fun remoteTrack(id: String): RemoteTrack? = remoteTracks.firstOrNull { it.id == id }
    fun lyric(id: String): LyricDoc? = lyrics.firstOrNull { it.trackId == id }
    fun titleOf(id: String): String =
        track(id)?.title ?: remoteTrack(id)?.title ?: "Pista desconocida"
    fun artistOf(id: String): String =
        track(id)?.artist ?: remoteTrack(id)?.artist.orEmpty()

    val albums: List<AlbumGroup>
        get() = tracks.groupBy { it.album.ifBlank { "Sin álbum" } }
            .map { (name, list) -> AlbumGroup(name, list.first().artist, list) }
            .sortedBy { it.name.lowercase() }

    val artists: List<ArtistGroup>
        get() = tracks.groupBy { it.artist.ifBlank { "Artista desconocido" } }
            .map { (name, list) -> ArtistGroup(name, list) }
            .sortedBy { it.name.lowercase() }

    fun search(query: String): List<Track> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return tracks
        return tracks.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
        }
    }
}

@Serializable
data class AlbumGroup(val name: String, val artist: String, val tracks: List<Track>)

@Serializable
data class ArtistGroup(val name: String, val tracks: List<Track>)

/** Same identifiers as the Android home screen, so both products can share a preset. */
fun defaultHomeSections(): List<HomeSectionPref> = listOf(
    HomeSectionPref("CONTINUE", true),
    HomeSectionPref("RECENT_PLAYLISTS", true),
    HomeSectionPref("FAVORITES", true),
    HomeSectionPref("RECOMMENDATIONS", true),
    HomeSectionPref("ARTISTS", true),
    HomeSectionPref("RECENT", true)
)

/**
 * The phone backup stores the YouTube identity only inside the id (`yt_<videoId>`), so an
 * imported song used to arrive with an empty videoId: it was unplayable even though the
 * reference was complete. This resolves it once, wherever it is needed.
 */
fun RemoteTrack.videoKey(): String =
    videoId.ifBlank { id.removePrefix("yt_").removePrefix("yt:").substringAfter("yt_") }

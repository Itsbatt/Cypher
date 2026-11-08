package com.cypher.music.data

import android.content.Context
import android.content.SharedPreferences
import com.cypher.music.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * Library ordering chosen by the user. The preference is stored once and reused by
 * every screen that lists songs, so the order does not change while navigating.
 */
enum class TrackSortKey(val title: String) {
    ADDED("Fecha de añadido"),
    ARTIST("Artista"),
    ALBUM("Álbum"),
    TITLE("Nombre")
}

data class LibrarySortState(
    val key: TrackSortKey = TrackSortKey.TITLE,
    val ascending: Boolean = true
) {
    val label: String
        get() = "${key.title} · ${if (ascending) "A-Z" else "Z-A"}"
}

/** Every block the home screen can show, in the order the user chooses. */
enum class HomeSection(val title: String, val description: String) {
    RECOMMENDATIONS("Elegido para ti", "Recomendaciones a partir de lo que escuchas"),
    RECENTS("Escuchado recientemente", "Las últimas canciones reproducidas"),
    FAVORITES("Canciones favoritas", "Tus canciones marcadas con corazón"),
    PLAYLISTS("Playlists", "Todas tus playlists"),
    FAVORITE_PLAYLISTS("Playlists favoritas", "Solo las playlists que marques como favoritas"),
    QUICK_ACCESS("Accesos rápidos", "Artistas y canciones a un toque")
}

data class HomeSectionState(val section: HomeSection, val enabled: Boolean = true)

data class HomePreferences(val sections: List<HomeSectionState> = defaultHomeSections()) {
    val enabledSections: List<HomeSection>
        get() = sections.filter { it.enabled }.map { it.section }
}

fun defaultHomeSections(): List<HomeSectionState> = listOf(
    HomeSectionState(HomeSection.RECOMMENDATIONS, true),
    HomeSectionState(HomeSection.RECENTS, true),
    HomeSectionState(HomeSection.FAVORITES, true),
    HomeSectionState(HomeSection.PLAYLISTS, true),
    HomeSectionState(HomeSection.FAVORITE_PLAYLISTS, false),
    HomeSectionState(HomeSection.QUICK_ACCESS, true)
)

/**
 * Persists the parts of the UI the user rearranges. Everything lives in one small
 * SharedPreferences file written only when the user changes something.
 */
class UserViewPreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cypher_view_prefs", Context.MODE_PRIVATE)

    private val _sort = MutableStateFlow(loadSort())
    val sort: StateFlow<LibrarySortState> = _sort.asStateFlow()

    private val _home = MutableStateFlow(loadHome())
    val home: StateFlow<HomePreferences> = _home.asStateFlow()

    private val _favoritePlaylists = MutableStateFlow(loadFavoritePlaylists())
    val favoritePlaylists: StateFlow<Set<String>> = _favoritePlaylists.asStateFlow()

    private val _addedDates = MutableStateFlow(loadAddedDates())
    val addedDates: StateFlow<Map<String, Long>> = _addedDates.asStateFlow()

    fun setSort(state: LibrarySortState) {
        _sort.value = state
        prefs.edit().putString(
            KEY_SORT,
            JSONObject().apply {
                put("key", state.key.name)
                put("ascending", state.ascending)
            }.toString()
        ).apply()
    }

    fun updateHome(transform: (HomePreferences) -> HomePreferences) {
        val updated = transform(_home.value)
        _home.value = updated
        prefs.edit().putString(
            KEY_HOME,
            JSONObject().apply {
                put("sections", JSONArray().apply {
                    updated.sections.forEach { entry ->
                        put(JSONObject().apply {
                            put("id", entry.section.name)
                            put("enabled", entry.enabled)
                        })
                    }
                })
            }.toString()
        ).apply()
    }

    fun moveHomeSection(from: Int, to: Int) {
        val sections = _home.value.sections.toMutableList()
        if (from !in sections.indices || to !in sections.indices || from == to) return
        val moved = sections.removeAt(from)
        sections.add(to, moved)
        updateHome { it.copy(sections = sections) }
    }

    fun setHomeSectionEnabled(section: HomeSection, enabled: Boolean) {
        updateHome { current ->
            current.copy(sections = current.sections.map {
                if (it.section == section) it.copy(enabled = enabled) else it
            })
        }
    }

    fun toggleFavoritePlaylist(playlistId: String) {
        val next = _favoritePlaylists.value.toMutableSet()
        if (!next.add(playlistId)) next.remove(playlistId)
        _favoritePlaylists.value = next
        prefs.edit().putStringSet(KEY_FAVORITE_PLAYLISTS, next).apply()
    }

    /**
     * Records the first time Cypher sees each track so "fecha de añadido" has a real
     * meaning without touching the media metadata or the database schema.
     */
    fun noteKnownTracks(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val dates = _addedDates.value
        val now = System.currentTimeMillis()
        val missing = ids.filter { it.isNotBlank() && !dates.containsKey(it) }
        if (missing.isEmpty()) return
        val updated = HashMap(dates)
        missing.forEach { updated[it] = now }
        _addedDates.value = updated
        val json = JSONObject()
        updated.forEach { (key, value) -> json.put(key, value) }
        prefs.edit().putString(KEY_ADDED_DATES, json.toString()).apply()
    }

    fun addedAt(trackId: String): Long = _addedDates.value[trackId] ?: 0L

    fun sortTracks(tracks: List<Track>): List<Track> =
        sortLibraryTracks(tracks, _sort.value, _addedDates.value)

    private fun loadSort(): LibrarySortState {
        val raw = prefs.getString(KEY_SORT, null) ?: return LibrarySortState()
        return runCatching {
            val json = JSONObject(raw)
            LibrarySortState(
                key = runCatching { TrackSortKey.valueOf(json.optString("key", TrackSortKey.TITLE.name)) }
                    .getOrDefault(TrackSortKey.TITLE),
                ascending = json.optBoolean("ascending", true)
            )
        }.getOrDefault(LibrarySortState())
    }

    private fun loadHome(): HomePreferences {
        val raw = prefs.getString(KEY_HOME, null) ?: return HomePreferences()
        return runCatching {
            val stored = JSONObject(raw).optJSONArray("sections") ?: return HomePreferences()
            val sections = mutableListOf<HomeSectionState>()
            for (index in 0 until stored.length()) {
                val entry = stored.optJSONObject(index) ?: continue
                val section = runCatching { HomeSection.valueOf(entry.optString("id")) }.getOrNull() ?: continue
                if (sections.none { it.section == section }) {
                    sections.add(HomeSectionState(section, entry.optBoolean("enabled", true)))
                }
            }
            // Sections added in a later version are appended instead of hidden.
            defaultHomeSections().forEach { default ->
                if (sections.none { it.section == default.section }) sections.add(default)
            }
            HomePreferences(sections)
        }.getOrDefault(HomePreferences())
    }

    private fun loadFavoritePlaylists(): Set<String> =
        prefs.getStringSet(KEY_FAVORITE_PLAYLISTS, emptySet()) ?: emptySet()

    private fun loadAddedDates(): Map<String, Long> {
        val raw = prefs.getString(KEY_ADDED_DATES, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            val map = HashMap<String, Long>(json.length())
            json.keys().forEach { key -> map[key] = json.optLong(key) }
            map
        }.getOrDefault(emptyMap())
    }

    companion object {
        private const val KEY_SORT = "library_sort"
        private const val KEY_HOME = "home_sections"
        private const val KEY_FAVORITE_PLAYLISTS = "favorite_playlists"
        private const val KEY_ADDED_DATES = "track_added_dates"
    }
}

/** Accent-insensitive, case-insensitive key so "Ámbar" sorts next to "ambar". */
private fun sortKey(value: String): String {
    if (value.isBlank()) return String(charArrayOf(0xFFFF.toChar()))
    val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
    val builder = StringBuilder(decomposed.length)
    for (character in decomposed) {
        if (Character.getType(character) != Character.NON_SPACING_MARK.toInt()) {
            builder.append(character)
        }
    }
    return builder.toString().lowercase(Locale.ROOT)
}

/**
 * Pure ordering used by the library: same rules everywhere, no hidden state.
 * Ordering never alternates randomly and never mutates the track metadata.
 */
fun sortLibraryTracks(
    tracks: List<Track>,
    state: LibrarySortState,
    addedAt: Map<String, Long>
): List<Track> {
    val byTitle = compareBy<Track> { sortKey(it.title) }
    val comparator: Comparator<Track> = when (state.key) {
        TrackSortKey.TITLE -> byTitle
        TrackSortKey.ARTIST -> compareBy(
            { sortKey(it.artist) },
            { sortKey(it.album) },
            { sortKey(it.title) }
        )
        TrackSortKey.ALBUM -> compareBy({ sortKey(it.album) }, { sortKey(it.title) })
        TrackSortKey.ADDED -> compareBy<Track> { addedAt[it.id] ?: 0L }.then(byTitle)
    }
    val ordered = tracks.sortedWith(comparator)
    return if (state.ascending) ordered else ordered.reversed()
}

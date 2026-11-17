package com.cypher.desktop.core

/**
 * Turning a loaded YouTube Music playlist into a Cypher playlist.
 *
 * The songs stay YouTube songs: the playlist holds `yt_<videoId>` references and their
 * metadata, never local files. Audio is only cached when the user presses play, and that
 * cache never changes the id.
 */
object YouTubeImport {

    data class Plan(
        val name: String,
        val description: String,
        val cover: String?,
        val sourceKey: String,
        val hits: List<MusicHit>
    ) {
        val total: Int get() = hits.size
        val unavailable: Int get() = hits.count { !it.available }
    }

    data class Result(val data: LibraryData, val playlist: Playlist, val imported: Boolean, val remoteAdded: Int)

    fun plan(list: YouTubeMusic.MusicList): Plan {
        val key = list.sourceKey ?: "yt:" + SpotifyMatcher.hashKey(list.title, list.hits.firstOrNull()?.artist.orEmpty())
        return Plan(
            name = list.title.trim().ifBlank { "Playlist de YouTube Music" },
            description = list.description.trim(),
            cover = list.cover,
            sourceKey = key,
            hits = list.hits
        )
    }

    fun remoteId(hit: MusicHit): String =
        if (hit.videoId.isNotBlank()) "yt_${hit.videoId}"
        else "yt_missing_${SpotifyMatcher.hashKey(hit.title, hit.artist)}"

    /**
     * Creates the playlist, or replaces the contents of the one imported from the same
     * link when [updateExisting] is true. Existing songs are reused by id, so importing
     * twice cannot duplicate a song.
     */
    fun apply(
        data: LibraryData,
        plan: Plan,
        updateExisting: Boolean,
        now: Long = System.currentTimeMillis()
    ): Result {
        val newRemote = plan.hits.mapNotNull { hit ->
            val id = remoteId(hit)
            if (data.remoteTrack(id) != null) return@mapNotNull null
            RemoteTrack(
                id = id,
                videoId = hit.videoId,
                title = hit.title,
                artist = hit.artist,
                album = "YouTube Music",
                durationMs = hit.durationMs,
                artworkUrl = hit.thumbnail,
                source = "YOUTUBE",
                available = hit.available,
                updatedAt = now
            )
        }
        val ids = plan.hits.map(::remoteId).distinct()
        val doc = data.copy(remoteTracks = data.remoteTracks + newRemote)
        val existing = doc.playlists.firstOrNull { it.origin == "YOUTUBE" && it.sourceUrl == plan.sourceKey }
        return if (existing != null && updateExisting) {
            val changed = existing.copy(
                name = plan.name,
                description = plan.description,
                trackIds = ids,
                artwork = plan.cover ?: existing.artwork,
                updatedAt = now
            )
            Result(doc.copy(playlists = doc.playlists.map { if (it.id == existing.id) changed else it }), changed, true, newRemote.size)
        } else {
            val playlist = Playlist(
                id = java.util.UUID.randomUUID().toString(),
                name = if (existing != null) "${plan.name} (importada)" else plan.name,
                description = plan.description.ifBlank { "Importada de YouTube Music · ${ids.size} canciones" },
                trackIds = ids,
                createdAt = now,
                updatedAt = now,
                artwork = plan.cover,
                origin = "YOUTUBE",
                sourceUrl = plan.sourceKey
            )
            Result(doc.copy(playlists = doc.playlists + playlist), playlist, false, newRemote.size)
        }
    }
}

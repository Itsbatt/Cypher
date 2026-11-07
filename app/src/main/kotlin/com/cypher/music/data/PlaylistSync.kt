package com.cypher.music.data

import com.cypher.music.model.Track

/**
 * How a playlist relates to its YouTube Music counterpart.
 *
 * Playlists imported by the sync are **hybrid**: remote tracks keep their remote
 * order, and any track added by hand on the device is kept after them instead of
 * being deleted on the next sync. The UI states this explicitly in the playlist
 * header so the user knows what to expect.
 */
object PlaylistSync {
    /** Every remote page was fetched and the playlist mirrors YouTube Music. */
    const val COMPLETE = "complete"

    /** At least one page, playlist or the whole sync failed; a retry is offered. */
    const val INCOMPLETE = "incomplete"

    fun isSynced(syncState: String?): Boolean = syncState != null

    /** Same playlist name ignoring case and extra spaces, as used by file/ZIP imports. */
    fun namesMatch(first: String, second: String): Boolean =
        normalizeName(first) == normalizeName(second)

    fun normalizeName(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), " ")

    fun stateFor(complete: Boolean): String = if (complete) COMPLETE else INCOMPLETE

    /**
     * Hybrid merge used by every sync: remote order first, then whatever the user
     * added locally. Duplicates are removed by track id on both sides.
     */
    fun mergeHybrid(remote: List<Track>, existing: List<Track>): List<Track> {
        val uniqueRemote = remote.distinctBy { it.id }
        val remoteIds = uniqueRemote.mapTo(HashSet()) { it.id }
        val localOnly = existing.filterNot { it.id in remoteIds }.distinctBy { it.id }
        return uniqueRemote + localOnly
    }
}

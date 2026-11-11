package com.cypher.music.sync

/**
 * Conflict rules for the sync engine.
 *
 * Pure functions on purpose: they are the part that must behave identically on
 * Android and on the future desktop client, so they are unit tested without a
 * device and kept free of Room, Android or network types.
 */
object SyncMerge {

    /**
     * Picks the surviving version of a record.
     *
     * Rules, in order:
     * 1. The newest [RemoteRecord.updatedAt] wins (last writer wins).
     * 2. On a tie, a live record beats a tombstone, so a deletion racing an edit
     *    never loses data silently.
     * 3. Still tied: the smallest id string wins, purely to converge on any device.
     */
    fun resolve(local: RemoteRecord?, remote: RemoteRecord?): RemoteRecord? {
        if (local == null) return remote
        if (remote == null) return local
        if (local.updatedAt != remote.updatedAt) {
            return if (remote.updatedAt > local.updatedAt) remote else local
        }
        if (local.deleted != remote.deleted) {
            return if (local.deleted) remote else local
        }
        return if (remote.id < local.id) remote else local
    }

    /**
     * Union of a playlist's track ids that keeps both devices' additions.
     *
     * Remote order is preserved (it is the canonical order on the cloud) and the
     * local-only tracks are appended in their local order, so two devices that
     * edited the same playlist converge on the same list instead of one wiping
     * the other's work.
     */
    fun mergePlaylistTracks(remoteOrder: List<String>, localOrder: List<String>): List<String> {
        val merged = LinkedHashSet<String>(remoteOrder.size + localOrder.size)
        remoteOrder.forEach { if (it.isNotBlank()) merged.add(it) }
        localOrder.forEach { if (it.isNotBlank()) merged.add(it) }
        return merged.toList()
    }

    /**
     * Merges a listening rollup row.
     *
     * Counters are monotonic per (day, track): taking the maximum keeps the result
     * idempotent, which is what stops a repeated sync from inflating the minutes
     * that Wrapped shows. Metadata comes from the newer side.
     */
    fun mergeDailyStats(local: RemoteRecord, remote: RemoteRecord): RemoteRecord {
        val (newer, older) = if (local.updatedAt >= remote.updatedAt) local to remote else remote to local
        val duration = maxOf(
            local.payload["listenDurationMs"]?.toLongOrNull() ?: 0L,
            remote.payload["listenDurationMs"]?.toLongOrNull() ?: 0L
        )
        val plays = maxOf(
            local.payload["playCount"]?.toIntOrNull() ?: 0,
            remote.payload["playCount"]?.toIntOrNull() ?: 0
        )
        val lastListened = maxOf(
            local.payload["lastListenedAt"]?.toLongOrNull() ?: 0L,
            remote.payload["lastListenedAt"]?.toLongOrNull() ?: 0L
        )
        val payload = newer.payload.toMutableMap().apply {
            put("listenDurationMs", duration.toString())
            put("playCount", plays.toString())
            put("lastListenedAt", lastListened.toString())
            older.payload["album"]?.takeIf { it.isNotBlank() && (this["album"].isNullOrBlank()) }
                ?.let { put("album", it) }
            older.payload["genre"]?.takeIf { it.isNotBlank() && (this["genre"].isNullOrBlank()) }
                ?.let { put("genre", it) }
        }
        return newer.copy(
            updatedAt = maxOf(local.updatedAt, remote.updatedAt),
            deleted = local.deleted && remote.deleted,
            payload = payload
        )
    }

    /**
     * True when the local row is newer than the cloud copy, i.e. it must be pushed.
     * Equal timestamps mean the cloud already has it (pull is idempotent).
     */
    fun needsPush(local: RemoteRecord, remote: RemoteRecord?): Boolean =
        remote == null || local.updatedAt > remote.updatedAt ||
            (local.updatedAt == remote.updatedAt && local.deleted && !remote.deleted)
}

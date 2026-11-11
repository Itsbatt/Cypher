package com.cypher.music.sync

import android.content.Context
import com.cypher.music.data.ThemePreferencesManager
import com.cypher.music.data.UserProfileStore
import com.cypher.music.data.room.CypherDatabase
import com.cypher.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Builds the cloud representation of what lives on this device.
 *
 * Only portable fields travel: no audio files, no cached URIs, no cookies, no
 * download queue and no device paths. A playlist cover chosen from the gallery is
 * local, so it stays local; the cloud copy keeps a mosaic generated from its tracks.
 */
class LocalSyncSource(private val context: Context) {

    private val db = CypherDatabase.getInstance(context)
    private val profileStore = UserProfileStore(context)
    private val themeManager = ThemePreferencesManager(context)

    /** Reads the current state of one queued row. Null means "nothing to upload". */
    suspend fun buildRecord(
        kind: SyncEntityKind,
        entityId: String,
        updatedAt: Long,
        deleted: Boolean
    ): RemoteRecord? = withContext(Dispatchers.IO) {
        if (deleted) return@withContext RemoteRecord(kind, entityId, updatedAt, deleted = true)
        when (kind) {
            SyncEntityKind.PLAYLIST -> buildPlaylist(entityId, updatedAt)
            SyncEntityKind.FAVORITE -> buildFavorite(entityId, updatedAt)
            SyncEntityKind.PROFILE -> buildProfile(updatedAt)
            SyncEntityKind.PREFERENCE -> buildPreferences(updatedAt)
            SyncEntityKind.STATS -> buildStats(entityId, updatedAt)
        }
    }

    private fun buildPlaylist(id: String, updatedAt: Long): RemoteRecord? {
        val entity = db.playlistDao().getPlaylistById(id) ?: return null
        val trackIds = db.playlistDao().getOrderedTracks(id).map { it.id }
        return RemoteRecord(
            kind = SyncEntityKind.PLAYLIST,
            id = id,
            updatedAt = maxOf(updatedAt, entity.updatedAt),
            payload = mapOf(
                "name" to entity.name.orEmpty(),
                "description" to entity.description.orEmpty(),
                "createdAt" to entity.createdAt.toString(),
                "updatedAt" to entity.updatedAt.toString(),
                "isPrivate" to entity.isPrivate.toString(),
                "trackIds" to RemoteRecordCodec.encodeTrackIds(trackIds)
            )
        )
    }

    private fun buildFavorite(trackId: String, updatedAt: Long): RemoteRecord? {
        val entity = db.trackDao().getTrackById(trackId) ?: return null
        if (!entity.isLiked) {
            // The row may be a favorite that was un-liked: send it as a tombstone.
            return RemoteRecord(SyncEntityKind.FAVORITE, trackId, maxOf(updatedAt, entity.updatedAt), deleted = true)
        }
        return RemoteRecord(
            kind = SyncEntityKind.FAVORITE,
            id = trackId,
            updatedAt = maxOf(updatedAt, entity.updatedAt),
            payload = mapOf(
                "title" to entity.title.orEmpty(),
                "artist" to entity.artist.orEmpty(),
                "album" to entity.album.orEmpty(),
                "durationMs" to entity.durationMs.toString(),
                "artworkUrl" to entity.artworkUrl.orEmpty(),
                "streamUrl" to entity.streamUrl.orEmpty(),
                "sourceType" to entity.sourceType.orEmpty(),
                "path" to entity.path.orEmpty(),
                "updatedAt" to entity.updatedAt.toString()
            )
        )
    }

    private fun buildProfile(updatedAt: Long): RemoteRecord {
        val user = profileStore.user.value
        return RemoteRecord(
            kind = SyncEntityKind.PROFILE,
            id = "profile",
            updatedAt = updatedAt,
            payload = mapOf(
                "displayName" to user.displayName,
                "avatarColor" to user.avatarColor.toString()
            )
        )
    }

    private fun buildPreferences(updatedAt: Long): RemoteRecord = RemoteRecord(
        kind = SyncEntityKind.PREFERENCE,
        id = "theme",
        updatedAt = updatedAt,
        payload = mapOf("json" to themeManager.exportJson())
    )

    private fun buildStats(entityId: String, updatedAt: Long): RemoteRecord? {
        val parts = entityId.split("|", limit = 2)
        if (parts.size != 2) return null
        val row = db.playbackDailyDao().getAllRows().firstOrNull {
            it.day == parts[0] && it.trackId == parts[1]
        } ?: return null
        return RemoteRecord(
            kind = SyncEntityKind.STATS,
            id = entityId,
            updatedAt = maxOf(updatedAt, row.lastListenedAt),
            payload = mapOf(
                "day" to row.day,
                "trackId" to row.trackId,
                "title" to row.title.orEmpty(),
                "artist" to row.artist.orEmpty(),
                "artworkUrl" to row.artworkUrl.orEmpty(),
                "album" to (row.album ?: ""),
                "genre" to (row.genre ?: ""),
                "listenDurationMs" to row.listenDurationMs.toString(),
                "playCount" to row.playCount.toString(),
                "lastListenedAt" to row.lastListenedAt.toString()
            )
        )
    }

    /**
     * Seeds the cloud with everything this device already had, used when an existing
     * local profile is linked to Google for the first time. Local data is never
     * deleted: these are upserts.
     */
    suspend fun enqueueFullSnapshot(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val outbox = db.syncOutboxDao()
        val rows = buildList {
            db.playlistDao().getAllPlaylists().forEach { playlist ->
                add(SyncOutboxEntityFactory.create(SyncEntityKind.PLAYLIST, playlist.id, playlist.updatedAt, false))
            }
            db.trackDao().getFavoriteTrackIds().forEach { trackId ->
                val track = db.trackDao().getTrackById(trackId)
                add(SyncOutboxEntityFactory.create(
                    SyncEntityKind.FAVORITE,
                    trackId,
                    track?.updatedAt ?: now,
                    false
                ))
            }
            db.playbackDailyDao().getAllRows().forEach { row ->
                add(SyncOutboxEntityFactory.create(
                    SyncEntityKind.STATS,
                    "${row.day}|${row.trackId}",
                    row.lastListenedAt,
                    false
                ))
            }
            add(SyncOutboxEntityFactory.create(SyncEntityKind.PROFILE, "profile", now, false))
            add(SyncOutboxEntityFactory.create(SyncEntityKind.PREFERENCE, "theme", now, false))
        }
        outbox.upsertAll(rows)
        rows.size
    }

    /** Used by the Wrapped rollup writer to enqueue the row it just updated. */
    fun enqueueStats(day: String, trackId: String, updatedAt: Long) {
        SyncQueue.enqueue(context, SyncEntityKind.STATS, "$day|$trackId", updatedAt)
    }

    companion object {
        /** Short probe used by the UI to know whether there is anything worth linking. */
        fun hasLocalData(context: Context): Boolean = runCatching {
            val db = CypherDatabase.getInstance(context)
            db.playlistDao().getAllPlaylists().isNotEmpty() ||
                db.trackDao().getFavoriteTrackIds().isNotEmpty() ||
                db.playbackDailyDao().getAllRows().isNotEmpty()
        }.getOrDefault(false)
    }
}

/** Small factory so both the snapshot and the repositories build outbox rows the same way. */
object SyncOutboxEntityFactory {
    fun create(
        kind: SyncEntityKind,
        entityId: String,
        updatedAt: Long,
        deleted: Boolean,
        now: Long = System.currentTimeMillis()
    ) = com.cypher.music.data.room.SyncOutboxEntity(
        "${kind.name}:$entityId",
        kind.name,
        entityId,
        updatedAt,
        deleted,
        now
    )
}

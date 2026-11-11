package com.cypher.music.sync

import android.content.Context
import android.util.Log
import com.cypher.music.data.room.CypherDatabase
import com.cypher.music.data.room.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The sync engine: pull, merge, push, remember the cursor.
 *
 * It is Android-agnostic on purpose (all Room access is behind [SyncApplier] and
 * [LocalSyncSource], all transport behind [SyncBackend]) so a desktop client can port
 * the same order of operations and the same conflict rules.
 */
class SyncEngine(
    private val context: Context,
    private val backend: SyncBackend,
    private val source: LocalSyncSource,
    private val applier: SyncApplier
) {
    private val db = CypherDatabase.getInstance(context)

    suspend fun run(
        accountId: String,
        idToken: String,
        onProgress: (pending: Int) -> Unit = {}
    ): SyncOutcome = withContext(Dispatchers.IO) {
        if (accountId.isBlank() || idToken.isBlank()) return@withContext SyncOutcome.NotSignedIn
        if (!backend.isConfigured) return@withContext SyncOutcome.NotConfigured

        val stateDao = db.syncStateDao()
        val state = stateDao.get(accountId) ?: SyncStateEntity(accountId, null, 0L, 0L)
        var pulled = 0
        var pushed = 0

        try {
            var cursor = state.cursor
            var guard = 0
            do {
                val page = withRetry { backend.pull(accountId, idToken, cursor, SyncPolicy.BATCH_SIZE) }
                if (page.records.isNotEmpty()) {
                    applier.apply(page.records)
                    pulled += page.records.size
                }
                cursor = page.cursor ?: cursor
                guard += 1
            } while (page.hasMore && guard < MAX_PAGES)

            pushed = pushOutbox(accountId, idToken, onProgress)

            val now = System.currentTimeMillis()
            stateDao.upsert(
                SyncStateEntity(accountId, cursor, now, state.lastAttemptAt)
            )
            SyncOutcome.Success(pulled, pushed, now)
        } catch (rejected: SyncRejectedException) {
            Log.w(TAG, "Sync rejected: ${rejected.message}")
            markAttempt(state)
            SyncOutcome.Failed(
                if (rejected.code == 401 || rejected.code == 403) {
                    "La cuenta ya no autoriza la sincronización. Vuelve a conectar Google."
                } else {
                    "El servidor rechazó la sincronización (${rejected.code})."
                }
            )
        } catch (unavailable: SyncUnavailableException) {
            Log.w(TAG, "Sync unavailable: ${unavailable.message}")
            markAttempt(state)
            SyncOutcome.Offline
        } catch (error: Exception) {
            Log.e(TAG, "Sync failed", error)
            markAttempt(state)
            SyncOutcome.Failed(error.message ?: "Error inesperado de sincronización")
        }
    }

    private fun markAttempt(state: SyncStateEntity) {
        runCatching {
            db.syncStateDao().upsert(
                SyncStateEntity(
                    state.accountKey,
                    state.cursor,
                    state.lastSyncedAt,
                    System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun pushOutbox(
        accountId: String,
        idToken: String,
        onProgress: (Int) -> Unit
    ): Int {
        val outbox = db.syncOutboxDao()
        var sent = 0
        while (true) {
            val pending = outbox.pending(SyncPolicy.BATCH_SIZE)
            if (pending.isEmpty()) break

            val records = pending.mapNotNull { row ->
                source.buildRecord(
                    kind = runCatching { SyncEntityKind.valueOf(row.kind) }.getOrDefault(SyncEntityKind.PROFILE),
                    entityId = row.entityId,
                    updatedAt = row.updatedAt,
                    deleted = row.deleted
                )
            }
            if (records.isEmpty()) {
                // Nothing left to upload (row deleted locally): drop the queue entries.
                outbox.deleteAll(pending.map { it.entityKey })
                continue
            }

            val result = withRetry { backend.push(accountId, idToken, records) }
            val acceptedKeys = result.accepted.ifEmpty { records.map { it.key } }
            outbox.deleteAll(pending.map { it.entityKey }.filter { key ->
                acceptedKeys.any { it == key } || acceptedKeys.isEmpty()
            })
            sent += records.size
            onProgress(SyncQueue.pendingCount(context))
        }
        return sent
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                SyncTelemetry.recordAttempt()
                return block()
            } catch (rejected: SyncRejectedException) {
                // 4xx are not transient: retrying cannot help.
                throw rejected
            } catch (error: SyncUnavailableException) {
                if (!SyncPolicy.shouldRetry(attempt)) throw error
                delay(SyncPolicy.retryDelayMs(attempt))
                attempt += 1
            }
        }
    }

    private companion object {
        const val TAG = "SyncEngine"

        /** Safety valve so a broken cursor cannot loop forever. */
        const val MAX_PAGES = 200
    }
}

/** Counters for the diagnostics journal; no payload or token is ever recorded. */
object SyncTelemetry {
    @Volatile
    private var attempts: Int = 0

    fun recordAttempt() {
        attempts += 1
    }

    fun attemptsSnapshot(): Int = attempts
}

/**
 * Writes a pulled batch into Room through the DAO layer.
 *
 * It bypasses the repositories, so applying remote data never re-enqueues the same
 * change and a device cannot ping-pong edits with the cloud.
 */
class SyncApplier(
    private val context: Context,
    private val onApplied: suspend () -> Unit = {}
) {
    private val db = CypherDatabase.getInstance(context)

    suspend fun apply(records: List<RemoteRecord>) = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        records.forEach { record ->
            runCatching { applyOne(record) }.onFailure {
                Log.w("SyncApplier", "No se pudo aplicar ${record.key}: ${it.message}")
            }
        }
        onApplied()
    }

    private fun applyOne(record: RemoteRecord) {
        when (record.kind) {
            SyncEntityKind.PLAYLIST -> applyPlaylist(record)
            SyncEntityKind.FAVORITE -> applyFavorite(record)
            SyncEntityKind.PROFILE -> applyProfile(record)
            SyncEntityKind.PREFERENCE -> applyPreference(record)
            SyncEntityKind.STATS -> applyStats(record)
        }
    }

    private fun applyPlaylist(record: RemoteRecord) {
        val playlistDao = db.playlistDao()
        val existing = playlistDao.getPlaylistById(record.id)
        if (record.deleted) {
            if (existing != null) {
                playlistDao.clearPlaylistTracks(record.id)
                playlistDao.deletePlaylist(record.id)
            }
            return
        }
        val name = record.payload["name"].orEmpty().ifBlank { existing?.name ?: "Playlist" }
        val remoteCreatedAt = record.payload["createdAt"]?.toLongOrNull() ?: existing?.createdAt ?: record.updatedAt
        val entity = com.cypher.music.data.room.PlaylistEntity(
            record.id,
            name,
            record.payload["description"].orEmpty(),
            // A gallery cover is a device-local URI: never overwrite the local one.
            existing?.coverUri,
            remoteCreatedAt,
            record.updatedAt,
            record.payload["isPrivate"]?.toBooleanStrictOrNull() ?: true
        ).apply {
            sourceId = existing?.sourceId
            lastSyncedAt = existing?.lastSyncedAt ?: 0L
            syncState = existing?.syncState
        }
        playlistDao.insertPlaylist(entity)

        val remoteOrder = RemoteRecordCodec.decodeTrackIds(record.payload["trackIds"])
        val localOrder = playlistDao.getOrderedTracks(record.id).map { it.id }
        val mergedOrder = SyncMerge.mergePlaylistTracks(remoteOrder, localOrder)
        val addedAt = System.currentTimeMillis()
        playlistDao.clearPlaylistTracks(record.id)
        playlistDao.insertCrossRefs(
            mergedOrder.mapIndexed { index, trackId ->
                com.cypher.music.data.room.PlaylistTrackCrossRef(record.id, trackId, index, addedAt)
            }
        )
    }

    private fun applyFavorite(record: RemoteRecord) {
        val now = record.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        if (record.deleted) {
            db.trackDao().setFavorite(record.id, false, now)
            return
        }
        val existing = db.trackDao().getTrackById(record.id)
        if (existing == null) {
            db.trackDao().insertTrack(
                com.cypher.music.data.room.TrackEntity(
                    record.id,
                    record.payload["title"].orEmpty(),
                    record.payload["artist"].orEmpty(),
                    record.payload["album"].orEmpty().ifBlank { "Sencillo" },
                    record.payload["durationMs"]?.toLongOrNull() ?: 0L,
                    record.payload["artworkUrl"]?.takeIf { it.isNotBlank() },
                    record.payload["streamUrl"].orEmpty(),
                    record.payload["sourceType"].orEmpty().ifBlank { "YOUTUBE" },
                    record.payload["path"]?.takeIf { it.isNotBlank() },
                    true,
                    false,
                    null,
                    now
                )
            )
        } else {
            db.trackDao().setFavorite(record.id, true, now)
        }
    }

    private fun applyProfile(record: RemoteRecord) {
        record.payload["displayName"]?.takeIf { it.isNotBlank() }?.let { name ->
            com.cypher.music.data.UserProfileStore(context).updateDisplayName(name)
        }
    }

    private fun applyPreference(record: RemoteRecord) {
        val json = record.payload["json"].orEmpty()
        if (json.isNotBlank()) {
            com.cypher.music.data.ThemePreferencesManager(context).importJson(json)
        }
    }

    private fun applyStats(record: RemoteRecord) {
        if (record.deleted) return
        val day = record.payload["day"].orEmpty()
        val trackId = record.payload["trackId"].orEmpty()
        if (day.isBlank() || trackId.isBlank()) return
        val existing = db.playbackDailyDao().getAllRows().firstOrNull {
            it.day == day && it.trackId == trackId
        }
        val merged = if (existing == null) {
            record
        } else {
            SyncMerge.mergeDailyStats(
                RemoteRecord(
                    kind = SyncEntityKind.STATS,
                    id = record.id,
                    updatedAt = existing.lastListenedAt,
                    payload = mapOf(
                        "listenDurationMs" to existing.listenDurationMs.toString(),
                        "playCount" to existing.playCount.toString(),
                        "lastListenedAt" to existing.lastListenedAt.toString(),
                        "album" to (existing.album ?: ""),
                        "genre" to (existing.genre ?: "")
                    )
                ),
                record
            )
        }
        db.playbackDailyDao().upsertRow(
            com.cypher.music.data.room.PlaybackDailyEntity(
                day,
                trackId,
                merged.payload["title"].orEmpty(),
                merged.payload["artist"].orEmpty(),
                merged.payload["artworkUrl"]?.takeIf { it.isNotBlank() },
                merged.payload["album"]?.takeIf { it.isNotBlank() },
                merged.payload["genre"]?.takeIf { it.isNotBlank() },
                merged.payload["listenDurationMs"]?.toLongOrNull() ?: 0L,
                merged.payload["playCount"]?.toIntOrNull() ?: 0,
                merged.payload["lastListenedAt"]?.toLongOrNull() ?: record.updatedAt
            )
        )
    }
}


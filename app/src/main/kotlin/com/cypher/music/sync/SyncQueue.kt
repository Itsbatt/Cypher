package com.cypher.music.sync

import android.content.Context
import android.util.Log
import com.cypher.music.data.room.CypherDatabase
import com.cypher.music.data.room.SyncOutboxEntity

/**
 * Entry point repositories call after a real local change.
 *
 * It only records *what* changed; the payload is read from Room when the batch is
 * pushed, so several edits to the same row collapse into one upload of its latest
 * state. Callers run this off the main thread.
 */
object SyncQueue {

    private const val TAG = "SyncQueue"

    fun enqueue(
        context: Context,
        kind: SyncEntityKind,
        entityId: String,
        updatedAt: Long,
        deleted: Boolean = false
    ) {
        if (entityId.isBlank()) return
        runCatching {
            val key = "${kind.name}:$entityId"
            CypherDatabase.getInstance(context).syncOutboxDao().upsert(
                SyncOutboxEntity(
                    key,
                    kind.name,
                    entityId,
                    updatedAt,
                    deleted,
                    System.currentTimeMillis()
                )
            )
        }.onFailure { Log.w(TAG, "No se pudo encolar $kind/$entityId: ${it.message}") }
    }

    fun pendingCount(context: Context): Int = runCatching {
        CypherDatabase.getInstance(context).syncOutboxDao().pendingCount()
    }.getOrDefault(0)
}

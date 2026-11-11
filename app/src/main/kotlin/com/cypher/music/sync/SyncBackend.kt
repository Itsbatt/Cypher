package com.cypher.music.sync

import com.cypher.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Transport contract between Cypher and the cloud.
 *
 * It is deliberately tiny and JSON based, so the future desktop client speaks the
 * same protocol with the same account and the same records:
 *
 *  POST {baseUrl}/v1/sync/pull
 *    { "accountId": "...", "cursor": "opaque|null", "limit": 100 }
 *    -> { "records": [record], "cursor": "opaque", "hasMore": false }
 *
 *  POST {baseUrl}/v1/sync/push
 *    { "accountId": "...", "records": [record] }
 *    -> { "accepted": ["KIND:id", ...] }
 *
 *  record = { "kind": "PLAYLIST", "id": "...", "updatedAt": 123,
 *             "deleted": false, "payload": { "key": "value" } }
 *
 * Both calls carry `Authorization: Bearer <Google ID token>`; per-user scoping is the
 * backend's job (it must derive the account from the token, not from "accountId").
 */
interface SyncBackend {
    val isConfigured: Boolean
    suspend fun pull(accountId: String, idToken: String, cursor: String?, limit: Int): PullPage
    suspend fun push(accountId: String, idToken: String, records: List<RemoteRecord>): PushResult
}

class SyncUnavailableException(message: String, cause: Throwable? = null) : IOException(message, cause)

class SyncRejectedException(val code: Int, message: String) : IOException(message)

class HttpSyncBackend(
    private val baseUrl: String = BuildConfig.SYNC_BASE_URL,
    private val client: OkHttpClient = defaultClient()
) : SyncBackend {

    override val isConfigured: Boolean get() = baseUrl.isNotBlank()

    override suspend fun pull(
        accountId: String,
        idToken: String,
        cursor: String?,
        limit: Int
    ): PullPage = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("accountId", accountId)
            .put("limit", limit)
            .apply { if (!cursor.isNullOrBlank()) put("cursor", cursor) }
        val json = post("/v1/sync/pull", idToken, body)
        val array = json.optJSONArray("records") ?: JSONArray()
        val records = buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { row -> RemoteRecordCodec.decode(row)?.let(::add) }
            }
        }
        PullPage(
            records = records,
            cursor = json.optString("cursor").takeIf { it.isNotBlank() } ?: cursor,
            hasMore = json.optBoolean("hasMore", false)
        )
    }

    override suspend fun push(
        accountId: String,
        idToken: String,
        records: List<RemoteRecord>
    ): PushResult = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext PushResult()
        val array = JSONArray()
        records.forEach { array.put(RemoteRecordCodec.encode(it)) }
        val json = post(
            "/v1/sync/push",
            idToken,
            JSONObject().put("accountId", accountId).put("records", array)
        )
        val accepted = json.optJSONArray("accepted") ?: JSONArray()
        PushResult(buildList { for (i in 0 until accepted.length()) add(accepted.optString(i)) })
    }

    private fun post(path: String, idToken: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $idToken")
            .header("Content-Type", JSON.toString())
            .post(body.toString().toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = "El servidor respondió ${response.code}: ${text.take(180)}"
                    // 4xx: the server understood the request and refused it. Retrying
                    // cannot help, and it must not be reported as "no connection".
                    // 5xx: the backend itself failed; that one is worth retrying.
                    throw if (response.code in 400..499) {
                        SyncRejectedException(response.code, message)
                    } else {
                        SyncUnavailableException(message)
                    }
                }
                if (text.isBlank()) return JSONObject()
                return runCatching { JSONObject(text) }.getOrElse {
                    throw SyncUnavailableException("Respuesta no válida del servidor de sincronización")
                }
            }
        } catch (rejected: SyncRejectedException) {
            // Both exceptions extend IOException, so the specific ones have to be
            // caught first: otherwise a 401 would be wrapped as a network failure.
            throw rejected
        } catch (unavailable: SyncUnavailableException) {
            throw unavailable
        } catch (io: IOException) {
            throw SyncUnavailableException(io.message ?: "Sin conexión con el servidor", io)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}

/** JSON codec shared by the HTTP backend and by the tests. */
object RemoteRecordCodec {

    fun encode(record: RemoteRecord): JSONObject = JSONObject()
        .put("kind", record.kind.name)
        .put("id", record.id)
        .put("updatedAt", record.updatedAt)
        .put("deleted", record.deleted)
        .put("payload", JSONObject().apply {
            record.payload.forEach { (key, value) -> put(key, value) }
        })

    fun decode(json: JSONObject): RemoteRecord? {
        val kindName = json.optString("kind")
        val id = json.optString("id")
        if (id.isBlank()) return null
        val kind = runCatching { SyncEntityKind.valueOf(kindName) }.getOrNull() ?: return null
        val payloadJson = json.optJSONObject("payload") ?: JSONObject()
        val payload = buildMap {
            val keys = payloadJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, payloadJson.optString(key))
            }
        }
        return RemoteRecord(
            kind = kind,
            id = id,
            updatedAt = json.optLong("updatedAt", 0L),
            deleted = json.optBoolean("deleted", false),
            payload = payload
        )
    }

    fun encodeTrackIds(ids: List<String>): String = JSONArray().apply { ids.forEach(::put) }.toString()

    fun decodeTrackIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList { for (i in 0 until array.length()) add(array.optString(i)) }
        }.getOrDefault(emptyList())
    }
}

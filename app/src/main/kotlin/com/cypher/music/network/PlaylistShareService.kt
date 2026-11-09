package com.cypher.music.network

import android.util.Log
import com.cypher.music.data.Playlist
import com.cypher.music.data.SharedPlaylistPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class PlaylistShareResult(
    val shortCode: String,
    val qrPayload: String,
    val p2pQrPayload: String,
    val webUrl: String
)

object PlaylistShareService {
    private const val TAG = "PlaylistShareService"
    private const val USER_AGENT = "curl/8.4.0"

    private val client = OkHttpClient.Builder()
        .dns(ResilientDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val TEXT_MEDIA_TYPE = "text/plain; charset=utf-8".toMediaType()

    /**
     * Publishes a playlist to the cloud and returns shortcode + QR payloads.
     */
    suspend fun sharePlaylist(playlist: Playlist): Result<PlaylistShareResult> = withContext(Dispatchers.IO) {
        val payload = SharedPlaylistPayload.fromPlaylist(playlist)
        val json = payload.toJson()
        val p2pPayload = payload.toP2pQrPayload()

        var isCnet = false
        // 1. Try Dpaste upload (returns ~5 char code e.g. "pU47A")
        var shortCode: String? = uploadToDpaste(json)

        // 2. Fallback to C-Net upload if Dpaste failed
        if (shortCode.isNullOrBlank()) {
            shortCode = uploadToCNet(json)
            if (!shortCode.isNullOrBlank()) {
                isCnet = true
            }
        }

        if (!shortCode.isNullOrBlank()) {
            // If p2p payload is compact (under 1500 chars / ~50 tracks), QR carries direct offline P2P data,
            // otherwise QR links to the cloud code
            val preferredQrPayload = if (p2pPayload.length <= 1500) {
                p2pPayload
            } else {
                "cypher://p/$shortCode"
            }

            val webUrl = if (isCnet) "https://paste.c-net.org/$shortCode" else "https://dpaste.org/$shortCode"

            Result.success(
                PlaylistShareResult(
                    shortCode = shortCode,
                    qrPayload = preferredQrPayload,
                    p2pQrPayload = p2pPayload,
                    webUrl = webUrl
                )
            )
        } else {
            // Offline fallback: Use P2P QR payload only
            val localCode = playlist.name.take(4).uppercase().replace(Regex("[^A-Z0-9]"), "M") +
                    (System.currentTimeMillis() % 10000).toString()
            val safeQr = if (p2pPayload.length <= 1500) p2pPayload else "cypher://p/$localCode"
            Result.success(
                PlaylistShareResult(
                    shortCode = localCode,
                    qrPayload = safeQr,
                    p2pQrPayload = p2pPayload,
                    webUrl = "cypher://p2p?d="
                )
            )
        }
    }

    /**
     * Resolves a playlist from either a QR code payload or an alphanumeric shortcode.
     */
    suspend fun resolvePlaylist(codeOrQr: String): Result<SharedPlaylistPayload> = withContext(Dispatchers.IO) {
        val trimmed = codeOrQr.trim()

        // 1. Check if it's a P2P QR payload (offline instant decode)
        val p2pResult = SharedPlaylistPayload.fromP2pQrPayload(trimmed)
        if (p2pResult != null) {
            Log.d(TAG, "Resolved playlist offline via P2P payload: ${p2pResult.name} (${p2pResult.tracks.size} tracks)")
            return@withContext Result.success(p2pResult)
        }

        // 2. Extract code from URL or clean raw code
        val cleanedCode = extractShortCode(trimmed)

        if (cleanedCode.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Código de playlist vacío o inválido."))
        }

        // 3. Try Dpaste fetch
        var payload = fetchFromDpaste(cleanedCode)

        // 4. Try C-Net fetch
        if (payload == null) {
            payload = fetchFromCNet(cleanedCode)
        }

        if (payload != null) {
            Log.d(TAG, "Resolved playlist from cloud: ${payload.name} (${payload.tracks.size} tracks)")
            Result.success(payload)
        } else {
            Result.failure(IllegalArgumentException("No se encontró ninguna playlist con el código '$cleanedCode'. Verifica el código o la conexión a internet."))
        }
    }

    /**
     * Pulls the shortcode out of anything a user may paste: a full link with or
     * without scheme, a link with a trailing slash, query string or fragment, a
     * cypher:// code, or the bare code. Prefix matching ignores case because
     * links copied from browsers often come capitalised differently.
     */
    private fun extractShortCode(raw: String): String {
        var value = raw.trim()
        val prefixes = listOf(
            "https://dpaste.org/", "http://dpaste.org/", "dpaste.org/",
            "https://paste.c-net.org/", "http://paste.c-net.org/", "paste.c-net.org/",
            "cypher://p/",
            "https://cypher.app/p/", "http://cypher.app/p/", "cypher.app/p/"
        )
        for (prefix in prefixes) {
            if (value.startsWith(prefix, ignoreCase = true)) {
                value = value.substring(prefix.length)
                break
            }
        }
        value = value.substringBefore('?').substringBefore('#').trim().trim('/')
        value = value.removeSuffix("/raw").removeSuffix(".txt").trim().trim('/')
        return value
    }

    private fun uploadToDpaste(json: String): String? {
        try {
            val form = FormBody.Builder()
                .add("content", json)
                .add("format", "url")
                .add("expiry_days", "365")
                .build()

            val request = Request.Builder()
                .url("https://dpaste.org/api/")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .post(form)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val resp = response.body?.string()?.trim() ?: return null
                    // Returns URL like https://dpaste.org/AbCdE
                    val key = resp.substringAfterLast("/").trim()
                    if (key.isNotEmpty()) {
                        Log.d(TAG, "Uploaded to Dpaste: key=$key")
                        return key
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dpaste upload failed: ${e.message}")
        }
        return null
    }

    private fun fetchFromDpaste(code: String): SharedPlaylistPayload? {
        val variants = listOf(code, code.uppercase(), code.lowercase()).distinct()
        for (variant in variants) {
            try {
                val request = Request.Builder()
                    .url("https://dpaste.org/$variant/raw")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        return SharedPlaylistPayload.fromJson(body)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Dpaste fetch attempt failed for $variant: ${e.message}")
            }
        }
        return null
    }

    private fun uploadToCNet(json: String): String? {
        try {
            val body = json.toRequestBody(TEXT_MEDIA_TYPE)
            val request = Request.Builder()
                .url("https://paste.c-net.org/")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val resp = response.body?.string()?.trim() ?: return null
                    val key = resp.substringAfterLast("/").trim()
                    if (key.isNotEmpty()) {
                        Log.d(TAG, "Uploaded to C-Net: key=$key")
                        return key
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "C-Net upload failed: ${e.message}")
        }
        return null
    }

    private fun fetchFromCNet(code: String): SharedPlaylistPayload? {
        try {
            val request = Request.Builder()
                .url("https://paste.c-net.org/$code")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    return SharedPlaylistPayload.fromJson(body)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "C-Net fetch failed for $code: ${e.message}")
        }
        return null
    }
}

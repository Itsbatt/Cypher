package com.cypher.music.network.streaming

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Direct InnerTube `player` call (TVHTML5 client), the last line of defence when yt-dlp
 * is unavailable or every player client it tries is rate-limited.
 */
class InnertubeStreamApi(
    private val client: OkHttpClient = YouTubeHttp.client,
    private val userAgent: String = YouTubeHttp.ANDROID_USER_AGENT
) {

    /** @return a direct audio URL, or null when YouTube answered without one. */
    fun fetchAudioUrl(videoId: String): String? {
        if (videoId.isBlank()) return null
        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "TVHTML5")
                    put("clientVersion", "7.20230405.08.01")
                    put("hl", "es")
                    put("gl", "ES")
                })
            })
            put("videoId", videoId)
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player")
            .header("User-Agent", userAgent)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        return InnertubeStreamParser.audioUrl(body)
    }
}

/**
 * Pure parsing of a `player` response, so the format rules are testable without a network
 * call: adaptiveFormats whose MIME type is audio first, then the legacy formats array.
 */
object InnertubeStreamParser {

    fun audioUrl(body: String): String? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val streamingData = json.optJSONObject("streamingData") ?: return null

        streamingData.optJSONArray("adaptiveFormats")?.let { formats ->
            for (index in 0 until formats.length()) {
                val format = formats.optJSONObject(index) ?: continue
                if (!format.optString("mimeType", "").startsWith("audio/")) continue
                val url = format.optString("url")
                if (url.startsWith("http")) return url
            }
        }

        val legacy = streamingData.optJSONArray("formats")
        if (legacy != null) {
            for (index in 0 until legacy.length()) {
                val url = legacy.optJSONObject(index)?.optString("url").orEmpty()
                if (url.startsWith("http")) return url
            }
        }
        return null
    }
}

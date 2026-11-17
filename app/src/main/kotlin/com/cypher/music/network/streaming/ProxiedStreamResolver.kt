package com.cypher.music.network.streaming

import com.cypher.music.data.StreamingPreferences
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolver that queries an alternative Piped or Invidious instance when configured.
 *
 * It is placed after the native YouTube resolver (yt-dlp + InnerTube), serving as a
 * reliable contingency fallback when Google rate-limits, changes player tokens, or
 * blocks direct mobile endpoints.
 */
class ProxiedStreamResolver(
    private val instanceProvider: () -> String? = { StreamingPreferences.getInstanceUrl() },
    private val client: OkHttpClient = YouTubeHttp.client,
    private val userAgent: String = YouTubeHttp.ANDROID_USER_AGENT
) : StreamResolver {

    override suspend fun resolve(videoId: String): ResolvedStream? {
        val rawInstance = instanceProvider()?.trim().orEmpty()
        if (rawInstance.isBlank() || videoId.isBlank()) return null

        val base = rawInstance.removeSuffix("/")
        val instanceUrl = if (base.startsWith("http://") || base.startsWith("https://")) base else "https://$base"

        // Tier 4: Alternative proxy fallback
        // 1. Try Piped endpoint: {instance}/streams/{videoId}
        val pipedResult = runCatching {
            val request = Request.Builder()
                .url("$instanceUrl/streams/$videoId")
                .header("User-Agent", userAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) ProxiedStreamParser.parsePiped(body) else null
                } else null
            }
        }.getOrNull()

        if (!pipedResult.isNullOrBlank()) {
            return ResolvedStream(
                url = pipedResult,
                source = StreamSource.PIPED,
                tier = 4
            )
        }

        // 2. Try Invidious endpoint: {instance}/api/v1/videos/{videoId}
        val invidiousResult = runCatching {
            val request = Request.Builder()
                .url("$instanceUrl/api/v1/videos/$videoId")
                .header("User-Agent", userAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) ProxiedStreamParser.parseInvidious(body) else null
                } else null
            }
        }.getOrNull()

        if (!invidiousResult.isNullOrBlank()) {
            return ResolvedStream(
                url = invidiousResult,
                source = StreamSource.INVIDIOUS,
                tier = 4
            )
        }

        return null
    }
}

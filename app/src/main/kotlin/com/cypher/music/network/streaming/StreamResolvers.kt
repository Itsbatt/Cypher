package com.cypher.music.network.streaming

import android.util.Log
import com.cypher.music.network.StreamingTelemetry

/**
 * The ordered list of providers the app will try, and the only entry point the player
 * needs. Adding a Piped/Invidious resolver is a one-line change here; nothing else moves.
 */
object StreamResolvers {

    private const val TAG = "StreamResolvers"

    /** Built-in engine: yt-dlp tiers plus the InnerTube player call. */
    private val youTube: StreamResolver by lazy { YouTubeStreamResolver() }

    /** Providers in priority order. A null result simply moves to the next one. */
    private fun providers(): List<StreamResolver> = listOf(youTube)

    /**
     * @return the first URL a provider could produce, with its origin; null when every
     * provider declined, which the player treats as "use the offline copy".
     */
    suspend fun resolve(videoId: String): ResolvedStream? {
        if (videoId.isBlank()) return null
        for (provider in providers()) {
            val resolved = runCatching { provider.resolve(videoId) }.getOrElse { error ->
                // A provider breaking must never take the whole resolution down.
                Log.w(TAG, "provider failed for $videoId: ${error.message}")
                StreamingTelemetry.recordError(
                    errorCode = 500,
                    message = error.message,
                    endpoint = provider::class.java.simpleName
                )
                null
            }
            if (resolved != null && resolved.url.isNotBlank()) return resolved
        }
        return null
    }
}

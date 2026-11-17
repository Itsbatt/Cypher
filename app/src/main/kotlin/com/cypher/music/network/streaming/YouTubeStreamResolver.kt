package com.cypher.music.network.streaming

import android.util.Log
import com.cypher.music.network.StreamingTelemetry
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest

/**
 * The built-in YouTube engine, unchanged in behaviour but now behind [StreamResolver].
 *
 * Tiers, in the order they are tried:
 * 1. yt-dlp with `android,ios,tv` player clients and an m4a preference (bypasses the
 *    default SABR limits).
 * 2. yt-dlp with `tv_embedded,web_creator,mweb` and a relaxed format selector.
 * 3. InnerTube `player` (TVHTML5) directly, for when yt-dlp cannot start or is throttled.
 */
class YouTubeStreamResolver(
    private val innertube: InnertubeStreamApi = InnertubeStreamApi()
) : StreamResolver {

    override suspend fun resolve(videoId: String): ResolvedStream? {
        val cleanVid = videoId.removePrefix("yt_").trim()
        if (cleanVid.isEmpty()) return null
        val targetUrl = "https://www.youtube.com/watch?v=$cleanVid"

        // TIER 1: yt-dlp with android,ios,tv extractor args.
        try {
            val request = YoutubeDLRequest(targetUrl).apply {
                addOption("-f", "140/bestaudio[ext=m4a]/bestaudio/best")
                addOption("-g")
                addOption("--extractor-args", "youtube:player_client=android,ios,tv")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--no-update")
            }
            val directUrl = YoutubeDL.getInstance().execute(request)
                .out.trim().lines().firstOrNull { it.startsWith("http") }
            if (!directUrl.isNullOrBlank()) {
                StreamingTelemetry.recordSuccess(tier = 1)
                StreamingTelemetry.recordTrackResolution(cleanVid, tier = 1)
                return ResolvedStream(directUrl, StreamSource.YT_DLP, tier = 1)
            }
        } catch (error: Exception) {
            StreamingTelemetry.recordError(errorCode = 403, message = error.message, endpoint = "yt-dlp:tier1")
            Log.w(TAG, "yt-dlp tier 1 failed: ${error.message}, falling back to tier 2...")
        }

        // TIER 2: yt-dlp with tv_embedded and a relaxed format.
        try {
            val request = YoutubeDLRequest(targetUrl).apply {
                addOption("-f", "bestaudio/best")
                addOption("-g")
                addOption("--extractor-args", "youtube:player_client=tv_embedded,web_creator,mweb")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--no-update")
            }
            val directUrl = YoutubeDL.getInstance().execute(request)
                .out.trim().lines().firstOrNull { it.startsWith("http") }
            if (!directUrl.isNullOrBlank()) {
                StreamingTelemetry.recordSuccess(tier = 2)
                StreamingTelemetry.recordTrackResolution(cleanVid, tier = 2)
                return ResolvedStream(directUrl, StreamSource.YT_DLP, tier = 2)
            }
        } catch (error: Exception) {
            StreamingTelemetry.recordError(errorCode = 403, message = error.message, endpoint = "yt-dlp:tier2")
            Log.w(TAG, "yt-dlp tier 2 failed: ${error.message}, attempting tier 3 InnerTube...")
        }

        // TIER 3: InnerTube player endpoint.
        try {
            val directUrl = innertube.fetchAudioUrl(cleanVid)
            if (!directUrl.isNullOrBlank()) {
                StreamingTelemetry.recordSuccess(tier = 3)
                StreamingTelemetry.recordTrackResolution(cleanVid, tier = 3)
                return ResolvedStream(directUrl, StreamSource.INNERTUBE, tier = 3)
            }
        } catch (error: Exception) {
            StreamingTelemetry.recordError(errorCode = 500, message = error.message, endpoint = "innertube:tier3")
            Log.e(TAG, "Tier 3 InnerTube fallback failed: ${error.message}")
        }

        return null
    }

    private companion object {
        const val TAG = "YouTubeStreamResolver"
    }
}

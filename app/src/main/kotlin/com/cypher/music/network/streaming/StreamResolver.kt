package com.cypher.music.network.streaming

/**
 * Where a playable URL came from. It is stored in telemetry, so a broken provider can be
 * identified from a report without guessing.
 */
enum class StreamSource {
    /** yt-dlp with mobile/TV player clients (tiers 1 and 2). */
    YT_DLP,

    /** Direct InnerTube `player` endpoint. */
    INNERTUBE,

    /** Self-hosted Piped instance. */
    PIPED,

    /** Self-hosted Invidious instance. */
    INVIDIOUS
}

/**
 * A resolved audio URL plus where it came from.
 *
 * @param tier ordering hint inside a provider; lower is preferred.
 */
data class ResolvedStream(
    val url: String,
    val source: StreamSource,
    val tier: Int
)

/**
 * Engine-agnostic seam for "give me something ExoPlayer can play".
 *
 * Streaming relies on reverse-engineering YouTube's player clients, so the implementation
 * is expected to change without notice. Keeping callers behind this interface means a
 * provider can be added, reordered or disabled without touching the player, and that a
 * YouTube breakage degrades to the next provider instead of requiring an emergency build.
 */
fun interface StreamResolver {
    /**
     * @return a playable URL, or null when this resolver cannot serve the video (it is not
     * configured, or it failed). Implementations must not throw for expected failures.
     */
    suspend fun resolve(videoId: String): ResolvedStream?
}

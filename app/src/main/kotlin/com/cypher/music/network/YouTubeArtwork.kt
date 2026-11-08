package com.cypher.music.network

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import com.cypher.music.model.AudioSourceType

/**
 * YouTube publishes several sizes for the same frame. The app used to hardcode
 * hqdefault (480x360, letterboxed), which is why artwork looked soft on a 1080p
 * phone. maxresdefault (1280x720, no bars) exists for most uploads; the Coil
 * interceptor below retries with hqdefault when a video has no maxres frame, so
 * upgrading never leaves a blank cover.
 */
object YouTubeArtwork {
    private const val MAX_RES = "maxresdefault.jpg"
    private const val SAFE_RES = "hqdefault.jpg"
    private val THUMB_URL = Regex("""https?://i\.ytimg\.com/vi/([A-Za-z0-9_-]{6,})/""")

    /** InnerTube sizes covers with `=w60-h60-l90-rj`; 1080 keeps full-bleed panels sharp. */
    private const val LARGE_SIZE_PARAMS = "=w1080-h1080-l90-rj"
    private const val FALLBACK_SIZE_PARAMS = "=w544-h544-l90-rj"
    private val SIZE_PARAMS = Regex("""=[swh]-?\d.*$""")

    /** Preferred artwork URL for a video id. */
    fun url(videoId: String): String = "https://i.ytimg.com/vi/$videoId/$MAX_RES"

    private val RAW_VIDEO_ID = Regex("^[A-Za-z0-9_-]{8,16}$")
    private val QUERY_VIDEO_ID = Regex("""[?&]v=([A-Za-z0-9_-]{8,16})""")

    /**
     * Hosts that actually serve an image. InnerTube's own Piped proxy answers HTTP 500
     * for every stored cover, which is why whole screens showed empty squares; those
     * URLs are replaced by the YouTube frame instead of being requested.
     */
    fun servesArtwork(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.contains("i.ytimg.com") ||
            url.contains("img.youtube.com") ||
            url.contains("googleusercontent.com") ||
            url.contains("ggpht.com")
    }

    /** Video id from a bare InnerTube id (`Ett9Opd_M6k`), a `v=` link or a full URL. */
    fun videoIdFrom(source: String?): String? {
        if (source.isNullOrBlank()) return null
        val cleaned = source.trim()
            .removePrefix("yt_")
            .removePrefix("track_")
            .substringBefore("?")
            .substringBefore("&")
        if (RAW_VIDEO_ID.matches(cleaned)) return cleaned
        QUERY_VIDEO_ID.find(source)?.groupValues?.getOrNull(1)?.let { return it }
        return source.substringAfterLast("/").takeIf { RAW_VIDEO_ID.matches(it) }
    }

    /**
     * Single decision point for every cover in the UI: a host that works is kept,
     * otherwise the real YouTube frame is derived from the track id.
     */
    fun bestArtwork(
        artworkUrl: String?,
        sourceType: AudioSourceType,
        id: String,
        streamUrl: String
    ): String? {
        val raw = artworkUrl?.takeIf { it.isNotBlank() }
        if (sourceType != AudioSourceType.YOUTUBE) return raw
        if (servesArtwork(raw)) return raw
        val videoId = videoIdFrom(streamUrl) ?: videoIdFrom(id)
        if (videoId != null) return url(videoId)
        return raw
    }

    /**
     * Promotes a thumbnail to the sharpest frame the provider offers. YouTube's
     * InnerTube API hands out tiny Google-hosted covers (`=w60-h60`), which is
     * what made the app look soft; this rewrites them to 1080 px. Local album art
     * and other remote covers are returned untouched.
     */
    fun upgrade(url: String?): String? {
        if (url.isNullOrBlank()) return url
        videoIdOf(url)?.let { videoId ->
            return if (url.contains("/$MAX_RES")) url else "https://i.ytimg.com/vi/$videoId/$MAX_RES"
        }
        return resizeGoogleArtwork(url, LARGE_SIZE_PARAMS)
    }

    /** Only upscaled frames fall back; anything else returns null to stop retrying. */
    fun fallback(url: String?): String? {
        videoIdOf(url)?.let { videoId ->
            return if (url != null && url.contains("/$MAX_RES")) {
                "https://i.ytimg.com/vi/$videoId/$SAFE_RES"
            } else {
                null
            }
        }
        if (url == null || !url.contains(LARGE_SIZE_PARAMS)) return null
        return resizeGoogleArtwork(url, FALLBACK_SIZE_PARAMS, force = true)
    }

    /** Rewrites the `=w…-h…` sizing suffix used by lh3.googleusercontent.com covers. */
    private fun resizeGoogleArtwork(url: String, params: String, force: Boolean = false): String? {
        val isGoogleHost = url.contains("googleusercontent.com") || url.contains("ggpht.com")
        if (!isGoogleHost) return url
        if (!force && url.endsWith(params)) return url
        val base = url.substringBefore("=").trimEnd()
        if (base.isEmpty()) return url
        return base + params
    }

    private fun videoIdOf(url: String?): String? =
        url?.let { THUMB_URL.find(it)?.groupValues?.getOrNull(1) }
}

/**
 * Coil interceptor: a missing maxresdefault frame is retried once with hqdefault.
 * The retry is a different URL, so it cannot loop.
 */
class YouTubeArtworkFallbackInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed(chain.request)
        if (result is ErrorResult) {
            // A cover that 404s at 1080 px (or has no maxres frame) is retried once
            // at a size every provider definitely serves.
            val fallback = YouTubeArtwork.fallback(chain.request.data as? String)
            if (fallback != null) {
                return chain.proceed(chain.request.newBuilder().data(fallback).build())
            }
        }
        return result
    }
}

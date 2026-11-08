package com.cypher.music.network

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult

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

    /** Preferred artwork URL for a video id. */
    fun url(videoId: String): String = "https://i.ytimg.com/vi/$videoId/$MAX_RES"

    /**
     * Promotes a YouTube thumbnail to the sharpest available frame. Any other URL
     * (local album art, remote covers) is returned untouched.
     */
    fun upgrade(url: String?): String? {
        val videoId = videoIdOf(url) ?: return url
        if (url != null && url.contains("/$MAX_RES")) return url
        return "https://i.ytimg.com/vi/$videoId/$MAX_RES"
    }

    /** Only maxres frames fall back; anything else returns null to stop retrying. */
    fun fallback(url: String?): String? {
        val videoId = videoIdOf(url) ?: return null
        if (url == null || !url.contains("/$MAX_RES")) return null
        return "https://i.ytimg.com/vi/$videoId/$SAFE_RES"
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
            val fallback = YouTubeArtwork.fallback(chain.request.data as? String)
            if (fallback != null) {
                return chain.proceed(chain.request.newBuilder().data(fallback).build())
            }
        }
        return result
    }
}

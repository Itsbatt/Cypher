package com.cypher.music.network.streaming

import com.cypher.music.network.YouTubeDns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * A single YouTube-aware HTTP client for the streaming path.
 *
 * The custom DNS matters here: YouTube domains are resolved through DoH with a fixed
 * fallback list, which is what keeps streaming alive on networks that poison or block
 * the default resolver. Redirects stay disabled so an error page is seen as an error
 * instead of being followed silently.
 */
object YouTubeHttp {

    const val ANDROID_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    const val CONSENT_COOKIE = "SOCS=CAESEwgDEgk2OTg1MjY2NzMaAmVzIAEaBgiA_LuuBg; CONSENT=YES+"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(YouTubeDns())
            .followRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

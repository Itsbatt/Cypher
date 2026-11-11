package com.cypher.music.network

/**
 * Fixed network endpoints used only as a last resort when the device DNS is
 * hijacked by the carrier or filtered by a captive portal.
 *
 * They are intentionally kept in one place instead of scattered through the
 * network code: if an address is retired, this is the only file to touch. They
 * are real anycast/service addresses (not version numbers), resolved over HTTPS
 * or used to skip a poisoned resolver; nothing here is a secret.
 */
internal object FallbackEndpoints {

    /** Cloudflare anycast addresses serving dpaste.org (playlist share codes). */
    val DPASTE_FALLBACK_IPS = listOf(
        "172.66.152.175",
        "104.20.36.119",
        "188.114.97.0",
        "188.114.96.0"
    )

    /** Address serving paste.c-net.org, the secondary share-code service. */
    val CNET_FALLBACK_IPS = listOf("20.100.184.134")

    /** Google front-end addresses used when YouTube DNS resolution fails. */
    val YOUTUBE_FALLBACK_IPS = listOf(
        "142.251.156.4",
        "142.251.157.4",
        "142.251.153.4",
        "142.251.154.4"
    )

    /** DNS-over-HTTPS resolvers, both reached over TLS. */
    const val GOOGLE_DOH_URL = "https://dns.google/resolve"
    const val CLOUDFLARE_DOH_URL = "https://cloudflare-dns.com/dns-query"
}

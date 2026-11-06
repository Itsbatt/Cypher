package com.cypher.music.update

/**
 * Picks the best APK asset for the device ABIs. Prefers an exact ABI build over
 * a universal one and never selects debug or foreign-ABI artifacts.
 */
object UpdateAssetSelector {
    private val ABI_TOKENS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun select(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? =
        assets.asSequence()
            .filter { it.name.endsWith(".apk", ignoreCase = true) }
            .filterNot { it.name.contains("debug", ignoreCase = true) }
            .mapNotNull { asset -> score(asset, supportedAbis)?.let { asset to it } }
            .maxByOrNull { it.second }
            ?.first

    private fun score(asset: ReleaseAsset, supportedAbis: List<String>): Int? {
        val name = asset.name.lowercase()
        val tokens = ABI_TOKENS.filter { name.contains(it) }
        val supportsToken = tokens.any { token -> supportedAbis.any { it.equals(token, ignoreCase = true) } }

        if (tokens.isNotEmpty() && !supportsToken) return null

        return when {
            tokens.contains("arm64-v8a") -> 100
            tokens.contains("x86_64") -> 95
            tokens.contains("armeabi-v7a") -> 90
            name.contains("universal") -> 70
            tokens.isEmpty() -> 60
            else -> 50
        }
    }
}

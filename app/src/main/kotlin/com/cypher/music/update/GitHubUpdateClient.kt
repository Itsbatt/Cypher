package com.cypher.music.update

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long
)

data class ReleaseInfo(
    val tag: String,
    val version: AppVersion,
    val title: String,
    val notes: String,
    val prerelease: Boolean,
    val pageUrl: String,
    val assets: List<ReleaseAsset>
) {
    val versionName: String get() = version.toString()

    /** First meaningful line of the GitHub release body, for the compact banner. */
    val summary: String
        get() = notes.lineSequence()
            .map { it.trim() }
            .map { it.removePrefix("#").removePrefix("-").removePrefix("*").removePrefix(">").trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("![") && !it.startsWith("<") }
            ?.replace("**", "")
            ?.take(180)
            .orEmpty()
}

/**
 * Reads published releases (stable, beta and prerelease) from GitHub.
 * Drafts are ignored; a repository tag is the source of truth for the version.
 */
object GitHubUpdateClient {
    private const val TAG = "GitHubUpdateClient"
    private const val OWNER_REPO = "Itsbatt/Cypher"
    private const val RELEASES_URL = "https://api.github.com/repos/$OWNER_REPO/releases?per_page=30"
    private const val MAX_CHECKSUM_BYTES = 256 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Latest published release by semantic version, or null when the list is empty. */
    suspend fun fetchLatest(): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "Cypher-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("GitHub respondió HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                Result.success(parseReleases(body).maxByOrNull { it.version })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "No se pudieron consultar las releases: ${error.message}")
            Result.failure(error)
        }
    }

    /** Small text asset (checksums), with a hard size limit. */
    suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Cypher-Android")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                if (body.contentLength() > MAX_CHECKSUM_BYTES) return@withContext null
                body.string().take(MAX_CHECKSUM_BYTES)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo leer $url: ${error.message}")
            null
        }
    }

    internal fun parseReleases(json: String): List<ReleaseInfo> {
        val array = JSONArray(json)
        val releases = ArrayList<ReleaseInfo>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optBoolean("draft", false)) continue

            val tag = item.optString("tag_name").ifBlank { item.optString("name") }
            val version = AppVersion.parse(tag) ?: continue

            val assetsJson = item.optJSONArray("assets") ?: JSONArray()
            val assets = ArrayList<ReleaseAsset>(assetsJson.length())
            for (assetIndex in 0 until assetsJson.length()) {
                val asset = assetsJson.optJSONObject(assetIndex) ?: continue
                val url = asset.optString("browser_download_url")
                val name = asset.optString("name")
                if (name.isBlank() || !url.startsWith("https://")) continue
                assets += ReleaseAsset(
                    name = name,
                    downloadUrl = url,
                    size = asset.optLong("size", 0L)
                )
            }

            releases += ReleaseInfo(
                tag = tag,
                version = version,
                title = item.optString("name").ifBlank { tag },
                notes = item.optString("body"),
                prerelease = item.optBoolean("prerelease", false),
                pageUrl = item.optString("html_url"),
                assets = assets
            )
        }
        return releases
    }
}

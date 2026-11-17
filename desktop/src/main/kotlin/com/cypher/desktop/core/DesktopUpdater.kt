package com.cypher.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

@Serializable
data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList()
)

data class AvailableUpdate(
    val version: String,
    val tagName: String,
    val releaseNotes: String,
    val msiUrl: String,
    val msiName: String,
    val sizeBytes: Long
)

/**
 * Manages automatic updates for Cypher PC.
 *
 * Automatic update is strictly limited to Windows MSI installations:
 * - Portable versions never update with MSI so they preserve their folder encapsulation.
 * - Source and test runs bypass update installation.
 */
object DesktopUpdater {

    const val CURRENT_VERSION = "1.2.0"
    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/Itsbatt/Cypher/releases"
    private const val CYPHER_MSI_UPGRADE_CODE = "25A9F1C7B1F695C4B9E0F2F5F2B7A601"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    /**
     * Auto-update is ONLY enabled for the MSI installed version on Windows.
     * Portable versions, source builds and uninstalled runtimes return false.
     */
    fun isMsiInstalled(): Boolean {
        // Portable mode explicitly disables MSI auto-updates.
        if (Storage.isPortable) return false

        // Check whether Cypher is registered in the Windows Installer registry
        return runCatching {
            val process = ProcessBuilder(
                "reg.exe", "query",
                "HKCU\\Software\\Microsoft\\Installer\\UpgradeCodes\\$CYPHER_MSI_UPGRADE_CODE"
            ).redirectErrorStream(true).start()
            val completed = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            completed && process.exitValue() == 0
        }.getOrDefault(false)
    }

    /**
     * Checks GitHub Releases for a newer version of Cypher PC (tagged with `pc-v*`).
     * Returns null if no update is available or if running in portable mode.
     */
    suspend fun checkForUpdates(): AvailableUpdate? = withContext(Dispatchers.IO) {
        if (!isMsiInstalled()) return@withContext null

        runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_RELEASES_API))
                .header("User-Agent", "CypherDesktop")
                .header("Accept", "application/vnd.github.v3+json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) return@withContext null

            val releases = json.decodeFromString<List<GithubRelease>>(response.body())
            val latestPcRelease = releases.firstOrNull { it.tagName.startsWith("pc-v") && !it.prerelease }
                ?: return@withContext null

            val remoteVersion = latestPcRelease.tagName.removePrefix("pc-v")
            if (!isNewerVersion(CURRENT_VERSION, remoteVersion)) return@withContext null

            val msiAsset = latestPcRelease.assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
                ?: return@withContext null

            AvailableUpdate(
                version = remoteVersion,
                tagName = latestPcRelease.tagName,
                releaseNotes = latestPcRelease.body,
                msiUrl = msiAsset.downloadUrl,
                msiName = msiAsset.name,
                sizeBytes = msiAsset.size
            )
        }.getOrNull()
    }

    /**
     * Downloads the MSI installer to dataDir/updates and triggers the installation.
     * Cypher exits cleanly so the MSI can replace existing binaries in-place.
     */
    suspend fun downloadAndInstallMsi(
        update: AvailableUpdate,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = Storage.dataDir.resolve("updates").also { Files.createDirectories(it) }
            val targetMsi = updatesDir.resolve(update.msiName)
            val tempMsi = updatesDir.resolve("${update.msiName}.tmp")

            val request = HttpRequest.newBuilder()
                .uri(URI.create(update.msiUrl))
                .header("User-Agent", "CypherDesktop")
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() !in 200..299) {
                error("Error al descargar la actualización: HTTP ${response.statusCode()}")
            }

            val contentLength = response.headers().firstValueAsLong("Content-Length").orElse(update.sizeBytes)
            response.body().use { input ->
                Files.newOutputStream(tempMsi).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var totalRead: Long = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (contentLength > 0) {
                            onProgress(totalRead.toFloat() / contentLength.toFloat())
                        }
                    }
                }
            }

            Files.move(tempMsi, targetMsi, StandardCopyOption.REPLACE_EXISTING)

            // Launch MSI installer
            ProcessBuilder("msiexec.exe", "/i", targetMsi.toAbsolutePath().toString()).start()

            // Close Cypher so files are not locked during installation
            kotlin.system.exitProcess(0)
        }
    }

    fun isNewerVersion(current: String, remote: String): Boolean {
        val cParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(cParts.size, rParts.size)
        for (i in 0 until maxLen) {
            val c = cParts.getOrElse(i) { 0 }
            val r = rParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}

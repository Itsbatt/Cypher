package com.cypher.music.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface InstallResult {
    data object Started : InstallResult
    data object NeedsPermission : InstallResult
    data class Failed(val message: String) : InstallResult
}

object UpdateInstaller {
    private const val TAG = "UpdateInstaller"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val MAX_CHECKSUM_BYTES = 256 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Fetches an optional SHA-256 from the release checksum assets. */
    suspend fun fetchChecksum(release: ReleaseInfo, apkAsset: ReleaseAsset): String? {
        val candidates = release.assets.filter { asset ->
            val name = asset.name.lowercase()
            name.endsWith(".sha256") || name.contains("sha256sum") || name.contains("checksum")
        }
        for (asset in candidates) {
            val text = GitHubUpdateClient.fetchText(asset.downloadUrl) ?: continue
            parseChecksum(text, apkAsset.name)?.let { return it }
        }
        return null
    }

    internal fun parseChecksum(text: String, apkName: String): String? {
        val hash = Regex("(?i)\\b([a-f0-9]{64})\\b")
        val apkLine = text.lineSequence().firstOrNull { line ->
            line.contains(apkName, ignoreCase = true) && hash.containsMatchIn(line)
        }
        if (apkLine != null) return hash.find(apkLine)?.groupValues?.get(1)

        // A single-hash .sha256 file usually has no filename at all.
        val matches = hash.findAll(text).toList()
        val looksLikeList = text.lineSequence().any { it.contains(".apk", ignoreCase = true) }
        return if (matches.size == 1 && !looksLikeList) matches.first().groupValues[1] else null
    }

    suspend fun download(
        asset: ReleaseAsset,
        expectedSha256: String?,
        targetDir: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val safeName = asset.name.substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (!safeName.endsWith(".apk", ignoreCase = true)) {
            return@withContext Result.failure(IOException("El recurso del release no es un APK."))
        }

        targetDir.mkdirs()
        val finalFile = File(targetDir, safeName)
        val partFile = File(targetDir, "$safeName.part")
        partFile.delete()
        finalFile.delete()

        try {
            val request = Request.Builder()
                .url(asset.downloadUrl)
                .header("User-Agent", "Cypher-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("La descarga falló (HTTP ${response.code}).")
                }
                val body = response.body ?: throw IOException("Respuesta de descarga vacía.")
                val total = body.contentLength().takeIf { it > 0 } ?: asset.size
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                var lastProgressAt = 0L

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastProgressAt >= 120) {
                                lastProgressAt = now
                                onProgress(
                                    if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else 0f
                                )
                            }
                        }
                    }
                }

                if (written == 0L) throw IOException("El APK descargado está vacío.")
                if (asset.size > 0 && written != asset.size) {
                    throw IOException("Descarga incompleta ($written de ${asset.size} bytes).")
                }
                if (total > 0) onProgress(1f)

                if (!expectedSha256.isNullOrBlank()) {
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expectedSha256, ignoreCase = true)) {
                        throw IOException("La verificación SHA-256 del APK falló.")
                    }
                }

                if (!partFile.renameTo(finalFile)) {
                    throw IOException("No se pudo guardar el APK descargado.")
                }
            }
            Result.success(finalFile)
        } catch (cancelled: CancellationException) {
            partFile.delete()
            throw cancelled
        } catch (error: Exception) {
            partFile.delete()
            Log.w(TAG, "Descarga fallida: ${error.message}")
            Result.failure(error)
        }
    }

    /** Returns null when the APK matches the app, version and installed signing certificate. */
    fun verifyApk(
        context: Context,
        file: File,
        expectedVersion: AppVersion,
        expectedSha256: String?
    ): String? {
        if (!file.isFile || file.length() == 0L) return "El archivo descargado está vacío."

        if (!expectedSha256.isNullOrBlank()) {
            val actual = sha256(file)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                return "La verificación SHA-256 del APK falló."
            }
        }

        val packageManager = context.packageManager
        val archive = packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES
        ) ?: return "El archivo descargado no es un APK válido."

        if (archive.packageName != context.packageName) {
            return "El APK pertenece a otra aplicación."
        }
        val archiveVersion = AppVersion.parse(archive.versionName)
        if (archiveVersion != null && archiveVersion != expectedVersion) {
            return "El APK descargado no corresponde a la versión ${expectedVersion}."
        }

        val archiveCertificates = archive.signingInfo
            ?.apkContentsSigners
            ?.map { sha256(it.toByteArray()) }
            ?.toSet()
            .orEmpty()
        val installedCertificates = runCatching {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            ).signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }?.toSet().orEmpty()
        }.getOrElse { emptySet() }

        if (archiveCertificates.isEmpty() || installedCertificates.isEmpty()) {
            return "No se pudo comprobar la firma del APK."
        }
        if (archiveCertificates != installedCertificates) {
            return "La firma del APK no coincide con la instalada. Instala esta versión manualmente."
        }
        return null
    }

    fun install(context: Context, file: File): InstallResult {
        if (!file.isFile) return InstallResult.Failed("El APK descargado ya no está disponible.")

        if (!context.packageManager.canRequestPackageInstalls()) {
            openUnknownSourcesSettings(context)
            return InstallResult.NeedsPermission
        }

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallResult.Started
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo abrir el instalador: ${error.message}")
            InstallResult.Failed("No se pudo abrir el instalador de Android: ${error.message}")
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w(TAG, "No se pudo abrir el ajuste de instalación: ${it.message}") }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

package com.cypher.music.update

import android.content.Context
import com.cypher.music.BuildConfig
import java.io.File
import java.security.MessageDigest

/**
 * Identifies the exact compilation installed on the device.
 *
 * A release can be rebuilt without changing versionName/versionCode (that happened with
 * 1.0.5), so comparing version numbers alone misses it. [apkSha256] hashes the installed
 * base APK and [isDifferentBuild] compares it with the SHA-256 published next to the asset.
 */
object InstalledBuild {
    /** Short hash baked into this APK by the build; useful for support and release notes. */
    val id: String get() = BuildConfig.BUILD_ID

    fun isDifferentBuild(installedDigest: String?, publishedDigest: String?): Boolean {
        if (installedDigest.isNullOrBlank() || publishedDigest.isNullOrBlank()) return false
        return !installedDigest.equals(publishedDigest, ignoreCase = true)
    }

    fun apkSha256(context: Context): String? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val apk = File(info.applicationInfo?.sourceDir ?: return null)
        if (!apk.isFile) return null
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}

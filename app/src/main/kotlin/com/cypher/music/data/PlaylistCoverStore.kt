package com.cypher.music.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Custom playlist covers live in the app's own storage. The photo picker only grants
 * temporary read access to what it returns, so storing the picked uri directly would
 * leave the playlist with a broken cover after a reboot. Each cover gets a timestamped
 * file name, which also keeps Coil from showing the previous image from its cache.
 */
object PlaylistCoverStore {
    private const val DIRECTORY = "playlist_covers"
    private const val MIN_BYTES = 1024L

    private fun directory(context: Context): File =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun prefixFor(playlistId: String): String =
        "cover_" + playlistId.replace(Regex("[^A-Za-z0-9_-]"), "_") + "_"

    /** Copies [source] into private storage and returns the stored `file://` uri. */
    suspend fun save(context: Context, playlistId: String, source: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val target = File(directory(context), prefixFor(playlistId) + System.currentTimeMillis() + ".jpg")
                context.contentResolver.openInputStream(source)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                if (target.length() < MIN_BYTES) {
                    target.delete()
                    return@withContext null
                }
                Uri.fromFile(target).toString()
            } catch (e: Exception) {
                Log.e("PlaylistCoverStore", "Could not save the playlist cover", e)
                null
            }
        }

    /** Removes this playlist's earlier covers, keeping [keepUri] when it belongs here. */
    suspend fun deleteUnused(context: Context, playlistId: String, keepUri: String?) =
        withContext(Dispatchers.IO) {
            try {
                val keepPath = keepUri?.let { runCatching { Uri.parse(it).path }.getOrNull() }
                val prefix = prefixFor(playlistId)
                directory(context).listFiles()?.forEach { file ->
                    if (file.name.startsWith(prefix) && file.absolutePath != keepPath) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w("PlaylistCoverStore", "Could not clean old playlist covers", e)
            }
        }

    /** True when [coverUri] points at a cover this device still has (or a remote image). */
    fun isAvailable(coverUri: String?): Boolean {
        val value = coverUri?.takeIf { it.isNotBlank() } ?: return false
        if (!value.startsWith("file://")) return true
        val path = runCatching { Uri.parse(value).path }.getOrNull() ?: return false
        return File(path).exists()
    }
}

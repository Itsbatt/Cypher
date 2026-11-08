package com.cypher.music.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Writes the exported card into the app cache and hands it to Android's own share
 * sheet. Only cache/shared is exposed through the existing FileProvider, and each
 * export overwrites a single predictable file name.
 */
object LyricsShare {

    suspend fun share(
        context: Context,
        bitmap: Bitmap,
        data: LyricsCardData,
        format: ShareFormat
    ) = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { stale ->
            if (System.currentTimeMillis() - stale.lastModified() > 24L * 60L * 60L * 1000L) {
                stale.delete()
            }
        }

        val file = File(dir, "cypher-letra-${format.name.lowercase()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val caption = "${data.trackTitle} · ${data.artist}\nCompartido desde CYPHER"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, "Compartir letra"))
        }
    }
}

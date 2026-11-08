package com.cypher.music.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Writes exported cards and hands them to Android.
 *
 * Sharing goes through the existing FileProvider (cache/shared); saving writes a
 * permanent copy into Pictures/Cypher through MediaStore, so the image survives
 * the system clearing the cache.
 */
object LyricsShare {

    private const val GALLERY_ALBUM = "Pictures/Cypher"

    suspend fun share(
        context: Context,
        bitmap: Bitmap,
        data: LyricsCardData,
        format: ShareFormat
    ) = shareBitmap(
        context = context,
        bitmap = bitmap,
        fileName = "cypher-letra-${format.name.lowercase()}.png",
        caption = "${data.trackTitle} · ${data.artist}\nCompartido desde CYPHER",
        chooserTitle = "Compartir letra"
    )

    suspend fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        caption: String,
        chooserTitle: String
    ) = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        prune(dir)

        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, chooserTitle))
        }
    }

    /** Saves the card into Pictures/Cypher and returns its content Uri. */
    suspend fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = fileName.substringBeforeLast('.') + "-" + System.currentTimeMillis() + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, GALLERY_ALBUM)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: throw IOException("No se pudo abrir el destino")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun prune(dir: File) {
        dir.listFiles()?.forEach { stale ->
            if (System.currentTimeMillis() - stale.lastModified() > 24L * 60L * 60L * 1000L) {
                stale.delete()
            }
        }
    }
}

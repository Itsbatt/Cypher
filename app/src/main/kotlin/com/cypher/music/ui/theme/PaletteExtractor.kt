package com.cypher.music.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AmbientArtworkColors(
    val dominant: Color = Color(0xFF20121C),
    val secondary: Color = Color(0xFF140E1A),
    val accent: Color = Color(0xFFFA243C)
)

object PaletteExtractor {
    // Bounded on purpose: the previous unbounded map kept a palette for every cover
    // ever played. 32 entries covers a listening session without growing forever.
    private val cache = LruCache<String, AmbientArtworkColors>(32)
    private val lock = Mutex()

    suspend fun extractColors(context: Context, imageUrl: String?): AmbientArtworkColors = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrBlank()) {
            return@withContext AmbientArtworkColors()
        }

        cache.get(imageUrl)?.let { return@withContext it }

        lock.withLock {
            cache.get(imageUrl)?.let { return@withContext it }

            try {
                // The application-level loader is shared, so the decoded 96px thumbnail
                // comes from the same memory/disk cache the UI already uses.
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .size(96, 96)
                    .build()

                val result = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap

                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()

                    val dominantInt = palette.getDarkVibrantColor(
                        palette.getDominantColor(0xFF20121C.toInt())
                    )
                    val secondaryInt = palette.getDarkMutedColor(
                        palette.getMutedColor(0xFF140E1A.toInt())
                    )
                    val accentInt = palette.getVibrantColor(
                        palette.getLightVibrantColor(0xFFFA243C.toInt())
                    )

                    val colors = AmbientArtworkColors(
                        dominant = Color(dominantInt).copy(alpha = 0.85f),
                        secondary = Color(secondaryInt).copy(alpha = 0.70f),
                        accent = Color(accentInt)
                    )
                    cache.put(imageUrl, colors)
                    return@withContext colors
                }
            } catch (e: Exception) {
                // Fallback
            }

            AmbientArtworkColors()
        }
    }
}

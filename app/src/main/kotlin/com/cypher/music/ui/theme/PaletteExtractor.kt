package com.cypher.music.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AmbientArtworkColors(
    val dominant: Color = Color(0xFF20121C),
    val secondary: Color = Color(0xFF140E1A),
    val accent: Color = Color(0xFFFA243C)
)

object PaletteExtractor {
    private val cache = mutableMapOf<String, AmbientArtworkColors>()

    suspend fun extractColors(context: Context, imageUrl: String?): AmbientArtworkColors = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrBlank()) {
            return@withContext AmbientArtworkColors()
        }

        cache[imageUrl]?.let { return@withContext it }

        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .size(128, 128)
                .build()

            val result = (loader.execute(request) as? SuccessResult)?.drawable
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
                cache[imageUrl] = colors
                return@withContext colors
            }
        } catch (e: Exception) {
            // Fallback
        }

        AmbientArtworkColors()
    }
}

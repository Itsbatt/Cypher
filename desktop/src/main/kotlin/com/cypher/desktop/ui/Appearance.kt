package com.cypher.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.cypher.desktop.core.Preferences
import com.cypher.desktop.core.Storage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** What the window paints behind everything, already resolved from the preferences. */
data class BackgroundSettings(
    val mode: String,
    val image: String?,
    val blur: Int,
    val dim: Float,
    val opacity: Float,
    val fit: String
) {
    val isCustom: Boolean get() = mode != "ORIGINAL"
}

fun Preferences.backgroundSettings() = BackgroundSettings(
    mode = backgroundMode,
    image = backgroundImage,
    blur = backgroundBlur,
    dim = backgroundDim,
    opacity = backgroundOpacity,
    fit = backgroundFit
)

/**
 * Copies the chosen picture into the app-data folder and returns its absolute path.
 * Keeping a copy means the background survives the user moving or deleting the original
 * file, and no temporary path is ever stored.
 */
fun importBackgroundPicture(source: File): String? = runCatching {
    require(source.isFile)
    val target = Storage.dataDir.resolve("background-${System.currentTimeMillis()}.${source.extension.ifBlank { "img" }}")
    Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
    // Drop older copies: only the current background should stay on disk.
    Storage.dataDir.toFile().listFiles { file -> file.name.startsWith("background-") && file != target.toFile() }
        ?.forEach { it.delete() }
    target.toAbsolutePath().toString()
}.getOrNull()

private val backgroundCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap?>()

@Composable
private fun rememberBackgroundBitmap(source: String?): ImageBitmap? {
    if (source.isNullOrBlank()) return null
    return remember(source) {
        backgroundCache.getOrPut(source) {
            runCatching {
                org.jetbrains.skia.Image.makeFromEncoded(File(source).readBytes()).toComposeImageBitmap()
            }.getOrNull()
        }
    }
}

/**
 * Paints the window background.
 *
 * The image is decoded once and cached; the blur is applied by the compositor to the
 * already-decoded bitmap, so resizing or scrolling never touches the pixels again.
 * Liquid Glass is an approximation: Compose Desktop has no refraction, so it is built
 * from layered translucent gradients and blurred colour blobs instead.
 */
@Composable
fun CypherBackground(settings: BackgroundSettings, accent: Color, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(CypherColors.Background)) {
        when (settings.mode) {
            "IMAGE" -> {
                val bitmap = rememberBackgroundBitmap(settings.image)
                if (bitmap != null) {
                    val scale = when (settings.fit) {
                        "FIT" -> ContentScale.Fit
                        "FILL" -> ContentScale.FillBounds
                        else -> ContentScale.Crop
                    }
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = scale,
                            alpha = settings.opacity,
                            modifier = Modifier.fillMaxSize().blur(settings.blur.dp)
                        )
                        // Legibility veil on top of the picture.
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.dim)))
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, CypherColors.Background.copy(alpha = 0.55f))
                                )
                            )
                        )
                    }
                }
            }
            "GLASS" -> {
                // Flat on purpose: the only blur in the app is the dock.
                Box(Modifier.fillMaxSize().background(CypherColors.Background))
            }
        }
        content()
    }
}

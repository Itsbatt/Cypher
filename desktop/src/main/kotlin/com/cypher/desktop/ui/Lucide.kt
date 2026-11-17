package com.cypher.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Lucide icons (https://lucide.dev), built from their original 24x24 stroke paths.
 *
 * The project had been mixing Material glyphs of different weights, which is why the
 * rail looked uneven. Lucide is a single grid: 24 px box, 2 px stroke, round caps and
 * joins, so every icon in the shell has the same optical weight.
 *
 * Only the icons the interface actually uses live here, instead of pulling a whole
 * icon pack as a dependency.
 */
object Lucide {

    private fun icon(name: String, pathData: String, filled: Boolean = false): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        val nodes = PathParser().parsePathString(pathData).toNodes()
        if (filled) {
            builder.addPath(
                pathData = nodes,
                fill = SolidColor(Color.White),
                stroke = null
            )
        } else {
            builder.addPath(
                pathData = nodes,
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    val Search: ImageVector by lazy {
        icon("lucide-search", "M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16M21 21l-4.35-4.35")
    }

    val Home: ImageVector by lazy {
        icon(
            "lucide-house",
            "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-4v-6H8v6H4a1 1 0 0 1-1-1Z"
        )
    }

    val ListMusic: ImageVector by lazy {
        icon(
            "lucide-list-music",
            "M21 15V6M18.5 18a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5M12 12H3M16 6H3M12 18H3"
        )
    }

    val Heart: ImageVector by lazy {
        icon(
            "lucide-heart",
            "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"
        )
    }

    val Plus: ImageVector by lazy { icon("lucide-plus", "M5 12h14M12 5v14") }

    val MoreHorizontal: ImageVector by lazy {
        icon("lucide-more-horizontal", "M12 12h.01M19 12h.01M5 12h.01")
    }

    val Play: ImageVector by lazy { icon("lucide-play", "M7 4l13 8-13 8z") }

    val Pause: ImageVector by lazy {
        icon("lucide-pause", "M9 4v16M15 4v16")
    }

    val SkipBack: ImageVector by lazy {
        icon("lucide-skip-back", "M19 5l-9 7 9 7zM6 5v14")
    }

    val SkipForward: ImageVector by lazy {
        icon("lucide-skip-forward", "M5 5l9 7-9 7zM18 5v14")
    }

    val Shuffle: ImageVector by lazy {
        icon(
            "lucide-shuffle",
            "M16 3h5v5M4 20 21 3M21 16v5h-5M15 15l6 6M4 4l5 5"
        )
    }

    val Repeat: ImageVector by lazy {
        icon(
            "lucide-repeat",
            "m17 2 4 4-4 4M3 11v-1a4 4 0 0 1 4-4h14M7 22l-4-4 4-4M21 13v1a4 4 0 0 1-4 4H3"
        )
    }

    val RepeatOne: ImageVector by lazy {
        icon(
            "lucide-repeat-1",
            "m17 2 4 4-4 4M3 11v-1a4 4 0 0 1 4-4h14M7 22l-4-4 4-4M21 13v1a4 4 0 0 1-4 4H3M11 10h1v4"
        )
    }

    val Volume: ImageVector by lazy {
        icon(
            "lucide-volume-2",
            "M11 5 6 9H2v6h4l5 4zM15.5 8.5a5 5 0 0 1 0 7M19 5a9 9 0 0 1 0 14"
        )
    }

    val ChevronDown: ImageVector by lazy { icon("lucide-chevron-down", "m6 9 6 6 6-6") }

    val ChevronRight: ImageVector by lazy { icon("lucide-chevron-right", "m9 18 6-6-6-6") }

    val List: ImageVector by lazy { icon("lucide-list", "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01") }

    val X: ImageVector by lazy { icon("lucide-x", "M18 6 6 18M6 6l12 12") }

    val FolderOpen: ImageVector by lazy {
        icon("lucide-folder-open", "M6 14 4 20h16l2-6H6ZM2 10V5a1 1 0 0 1 1-1h5l2 3h7a1 1 0 0 1 1 1v2")
    }

    val Music: ImageVector by lazy {
        icon("lucide-music", "M9 18V5l12-2v13M9 18a3 3 0 1 0 0-6 3 3 0 0 0 0 6M21 16a3 3 0 1 0 0-6 3 3 0 0 0 0 6")
    }

    val Pencil: ImageVector by lazy {
        icon("lucide-pencil", "M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z")
    }

    val Folder: ImageVector by lazy {
        icon("lucide-folder", "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z")
    }

    val File: ImageVector by lazy {
        icon("lucide-file", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Zm0 0v5h5")
    }

    val Image: ImageVector by lazy {
        icon("lucide-image", "M3 3h18v18H3zM8.5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3M21 15l-5-5L5 21")
    }

    val HardDrive: ImageVector by lazy {
        icon("lucide-hard-drive", "M22 12H2M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11M6 16h.01M10 16h.01")
    }

    val ArrowUp: ImageVector by lazy { icon("lucide-arrow-up", "m5 12 7-7 7 7M12 19V5") }

    val Check: ImageVector by lazy { icon("lucide-check", "M20 6 9 17l-5-5") }

    val Download: ImageVector by lazy {
        icon("lucide-download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3")
    }

    val Cloud: ImageVector by lazy {
        icon("lucide-cloud", "M17.5 19a4.5 4.5 0 0 0 0-9 6 6 0 0 0-11.6 1.5A3.5 3.5 0 0 0 6 19z")
    }

    val FileArchive: ImageVector by lazy {
        icon(
            "lucide-file-archive",
            "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7ZM14 2v5h5M10 9v1M10 12v1M10 15v1M10 18v1"
        )
    }
}

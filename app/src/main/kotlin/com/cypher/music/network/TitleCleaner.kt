package com.cypher.music.network

/**
 * Single source of truth for stripping YouTube decorations from track titles.
 * Both the HTML search parser and the InnerTube pager use it, so a title looks
 * the same whichever path resolved the track.
 */
object TitleCleaner {
    private val PATTERNS = listOf(
        Regex("(?i)\\(official video\\)"),
        Regex("(?i)\\[official music video\\]"),
        Regex("(?i)\\(video oficial\\)"),
        Regex("(?i)\\(audio oficial\\)"),
        Regex("(?i)\\(visualizer\\)"),
        Regex("(?i)\\(letra/lyrics\\)")
    )

    private val EXTRA_SPACES = Regex("\\s{2,}")

    fun clean(raw: String): String {
        var value = raw
        for (pattern in PATTERNS) value = value.replace(pattern, "")
        return value.replace(EXTRA_SPACES, " ").trim()
    }
}

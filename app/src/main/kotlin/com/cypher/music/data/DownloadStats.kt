package com.cypher.music.data

import java.util.Locale

/**
 * Space taken by the offline downloads, counted from the files on disk rather than
 * from the database, so a stale row never inflates the number the user sees.
 *
 * @param activeDownloads how many files are still being written right now; those are
 * never deleted and are reported back to the user.
 */
data class DownloadStats(
    val fileCount: Int = 0,
    val totalBytes: Long = 0L,
    val activeDownloads: Int = 0
) {
    val isEmpty: Boolean get() = fileCount == 0
}

/**
 * Outcome of "delete all downloads".
 *
 * @param deleted number of files actually removed from disk.
 * @param skippedActive downloads that were in progress and were left untouched.
 */
data class DownloadDeletionResult(
    val deleted: Int = 0,
    val skippedActive: Int = 0
)

/**
 * Track ids that can be deleted right now: everything known to be downloaded except
 * the ids whose file is still being written by an active download.
 */
fun deletableDownloadIds(
    downloaded: Collection<String>,
    active: Collection<String>
): List<String> {
    val inFlight = active.toSet()
    return downloaded.filterNot { inFlight.contains(it) }.distinct()
}

/**
 * Human readable file size. Keeps a decimal only below 10 units so the row stays
 * short ("975 B", "1,4 GB" style is not used: the decimal point is always a dot).
 */
fun formatDownloadSize(bytes: Long): String {
    if (bytes < 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    val text = if (value >= 10.0) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.1f", value)
    }
    // 1000.0 KB rounded to "1000 KB" reads better as "1,0 MB", but the loop above
    // already stepped the unit whenever the raw value reached 1024.
    return "$text ${units[unit]}"
}

/** Summary line shown in Ajustes: "3 archivos · 45,6 MB". */
fun describeDownloadStats(stats: DownloadStats): String {
    if (stats.isEmpty) return "Sin descargas"
    val files = if (stats.fileCount == 1) "1 archivo" else "${stats.fileCount} archivos"
    return "$files · ${formatDownloadSize(stats.totalBytes)}"
}

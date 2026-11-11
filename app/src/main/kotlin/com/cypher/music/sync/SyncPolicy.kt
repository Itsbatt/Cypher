package com.cypher.music.sync

/**
 * Timings and decisions of the sync system, isolated so they can be tested with a
 * controlled clock instead of waiting minutes on a device.
 */
object SyncPolicy {

    /** Page size of a pull and of a push batch. */
    const val BATCH_SIZE = 100

    /** Attempts per sync pass before the UI is told it failed. */
    const val MAX_ATTEMPTS = 4

    /** Consecutive failed passes before a long, quiet retry window. */
    const val FAILURES_BEFORE_SLOW_RETRY = 3

    /** Re-offer the "connect your account" popup at most once a month. */
    const val LINK_OFFER_COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000

    /** Do not nag within this window even if the previous answer was positive. */
    const val LINK_OFFER_SESSION_MS = 7L * 24 * 60 * 60 * 1000

    /** Minimum gap between automatic passes triggered by connectivity changes. */
    const val AUTO_SYNC_MIN_GAP_MS = 60L * 1000

    /** Exponential backoff with a ceiling, used between attempts of the same pass. */
    fun retryDelayMs(attempt: Int): Long {
        val safeAttempt = attempt.coerceAtLeast(0)
        val base = 500L * (1L shl safeAttempt.coerceAtMost(5))
        return base.coerceAtMost(30_000L)
    }

    fun shouldRetry(attempt: Int): Boolean = attempt + 1 < MAX_ATTEMPTS

    fun shouldSlowRetry(consecutiveFailures: Int): Boolean =
        consecutiveFailures >= FAILURES_BEFORE_SLOW_RETRY

    /**
     * Decides whether the linking popup is offered.
     *
     * It never appears without local data, never for an already linked account and
     * not again before the cooldown, whatever the user answered last time.
     */
    fun shouldOfferLink(
        hasLocalData: Boolean,
        isSignedIn: Boolean,
        authConfigured: Boolean,
        lastOfferAt: Long?,
        lastDismissedAt: Long?,
        now: Long
    ): Boolean {
        if (!hasLocalData || isSignedIn || !authConfigured) return false
        val lastSeen = maxOf(lastOfferAt ?: 0L, lastDismissedAt ?: 0L)
        if (lastSeen == 0L) return true
        return now - lastSeen >= LINK_OFFER_COOLDOWN_MS
    }

    /** Automatic syncs are throttled; a manual one always runs. */
    fun canAutoSync(lastAttemptAt: Long?, now: Long): Boolean =
        lastAttemptAt == null || now - lastAttemptAt >= AUTO_SYNC_MIN_GAP_MS
}

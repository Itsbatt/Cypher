package com.cypher.music.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `backoff grows and is capped`() {
        assertTrue(SyncPolicy.retryDelayMs(0) < SyncPolicy.retryDelayMs(2))
        assertTrue(SyncPolicy.retryDelayMs(10) <= 30_000L)
        assertTrue(SyncPolicy.shouldRetry(0))
        assertFalse(SyncPolicy.shouldRetry(SyncPolicy.MAX_ATTEMPTS))
    }

    @Test
    fun `the link popup needs local data and no account`() {
        val now = 1_000_000_000_000L
        assertTrue(
            SyncPolicy.shouldOfferLink(
                hasLocalData = true, isSignedIn = false, authConfigured = true,
                lastOfferAt = null, lastDismissedAt = null, now = now
            )
        )
        assertFalse(
            SyncPolicy.shouldOfferLink(
                hasLocalData = false, isSignedIn = false, authConfigured = true,
                lastOfferAt = null, lastDismissedAt = null, now = now
            )
        )
        assertFalse(
            SyncPolicy.shouldOfferLink(
                hasLocalData = true, isSignedIn = true, authConfigured = true,
                lastOfferAt = null, lastDismissedAt = null, now = now
            )
        )
        assertFalse(
            SyncPolicy.shouldOfferLink(
                hasLocalData = true, isSignedIn = false, authConfigured = false,
                lastOfferAt = null, lastDismissedAt = null, now = now
            )
        )
    }

    @Test
    fun `dismissing the popup postpones it a month`() {
        val now = 10 * day
        assertFalse(
            SyncPolicy.shouldOfferLink(
                hasLocalData = true, isSignedIn = false, authConfigured = true,
                lastOfferAt = null, lastDismissedAt = now - day, now = now
            )
        )
        assertTrue(
            SyncPolicy.shouldOfferLink(
                hasLocalData = true, isSignedIn = false, authConfigured = true,
                lastOfferAt = null, lastDismissedAt = now - 31 * day, now = now
            )
        )
    }

    @Test
    fun `automatic syncs are throttled but the first one always runs`() {
        val now = 5_000_000L
        assertTrue(SyncPolicy.canAutoSync(null, now))
        assertFalse(SyncPolicy.canAutoSync(now - 5_000L, now))
        assertTrue(SyncPolicy.canAutoSync(now - SyncPolicy.AUTO_SYNC_MIN_GAP_MS, now))
    }
}

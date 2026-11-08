package com.cypher.music.wrapped

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WrappedSchedulePolicyTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")
    private val year = 2026

    private fun clockAt(text: String): Clock =
        Clock.fixed(ZonedDateTime.of(java.time.LocalDateTime.parse(text), zone).toInstant(), zone)

    @Test
    fun `window follows an injected clock`() {
        assertNull(WrappedAvailability.activeYear(clockAt("2026-12-14T23:59:00")))
        assertEquals(2026, WrappedAvailability.activeYear(clockAt("2026-12-15T00:00:00")))
        assertEquals(2026, WrappedAvailability.activeYear(clockAt("2027-01-10T23:59:00")))
        assertNull(WrappedAvailability.activeYear(clockAt("2027-01-11T00:00:00")))
    }

    @Test
    fun `exact alarm is planned when access is granted`() {
        val plan = WrappedScheduler.plan(
            now = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, zone),
            recapYear = year,
            canScheduleExact = true
        )
        assertFalse(plan.alreadyPassed)
        assertTrue(plan.exact)
        assertEquals(WrappedScheduler.eventTime(year, zone).toInstant().toEpochMilli(), plan.triggerAtMillis)
    }

    @Test
    fun `inexact fallback is planned without exact access`() {
        val plan = WrappedScheduler.plan(
            now = ZonedDateTime.of(2026, 12, 30, 9, 0, 0, 0, zone),
            recapYear = year,
            canScheduleExact = false
        )
        assertFalse(plan.alreadyPassed)
        assertFalse(plan.exact)
    }

    @Test
    fun `passed events are not scheduled`() {
        val plan = WrappedScheduler.plan(
            now = ZonedDateTime.of(2026, 12, 31, 20, 0, 0, 0, zone),
            recapYear = year,
            canScheduleExact = true
        )
        assertTrue(plan.alreadyPassed)
        assertNull(plan.triggerAtMillis)
    }

    @Test
    fun `next event year rolls forward in january`() {
        assertEquals(2027, WrappedScheduler.nextEventYear(LocalDate.of(2027, 1, 3)))
        assertEquals(2026, WrappedScheduler.nextEventYear(LocalDate.of(2026, 6, 1)))
    }
}

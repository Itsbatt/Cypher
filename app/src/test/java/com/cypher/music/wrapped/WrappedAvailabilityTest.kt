package com.cypher.music.wrapped

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WrappedAvailabilityTest {

    @Test
    fun `hidden before the fifteenth of december`() {
        assertNull(WrappedAvailability.activeYear(LocalDate.of(2026, 12, 14)))
        assertFalse(WrappedAvailability.isAvailable(LocalDate.of(2026, 12, 14)))
    }

    @Test
    fun `available from the fifteenth of december`() {
        assertEquals(2026, WrappedAvailability.activeYear(LocalDate.of(2026, 12, 15)))
        assertEquals(2026, WrappedAvailability.activeYear(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `january still shows the previous year until the tenth`() {
        assertEquals(2026, WrappedAvailability.activeYear(LocalDate.of(2027, 1, 1)))
        assertEquals(2026, WrappedAvailability.activeYear(LocalDate.of(2027, 1, 10)))
    }

    @Test
    fun `hidden after the tenth of january`() {
        assertNull(WrappedAvailability.activeYear(LocalDate.of(2027, 1, 11)))
        assertTrue(WrappedAvailability.isAvailable(LocalDate.of(2027, 1, 10)))
    }

    @Test
    fun `window dates round trip`() {
        val start = WrappedAvailability.startsOn(2026)
        val end = WrappedAvailability.endsOn(2026)
        assertEquals(LocalDate.of(2026, 12, 15), start)
        assertEquals(LocalDate.of(2027, 1, 10), end)
        assertEquals(2026, WrappedAvailability.activeYear(start))
        assertEquals(2026, WrappedAvailability.activeYear(end))
    }
}

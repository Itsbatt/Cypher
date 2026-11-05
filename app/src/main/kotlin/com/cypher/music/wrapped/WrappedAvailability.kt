package com.cypher.music.wrapped

import java.time.LocalDate
import java.time.Month

/**
 * Cypher Wrapped only goes live at the end of the year: from 1 December until
 * 7 January, when the recap of the year that just ended is still relevant.
 *
 * Outside that window the recap is neither offered automatically nor listed in
 * Settings, so the annual story keeps its "event" feel.
 */
object WrappedAvailability {

    /** First day the recap becomes available. */
    const val START_DAY = 1

    /** Last day the recap stays available (January of the following year). */
    const val END_DAY = 7

    /**
     * Year of the recap that should be shown today, or `null` when the app is
     * outside the end-of-year window. January belongs to the previous year.
     */
    fun activeYear(today: LocalDate = LocalDate.now()): Int? = when {
        today.month == Month.DECEMBER && today.dayOfMonth >= START_DAY -> today.year
        today.month == Month.JANUARY && today.dayOfMonth <= END_DAY -> today.year - 1
        else -> null
    }

    fun isAvailable(today: LocalDate = LocalDate.now()): Boolean =
        activeYear(today) != null

    fun startsOn(recapYear: Int): LocalDate =
        LocalDate.of(recapYear, Month.DECEMBER, START_DAY)

    fun endsOn(recapYear: Int): LocalDate =
        LocalDate.of(recapYear + 1, Month.JANUARY, END_DAY)
}

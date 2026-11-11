package com.cypher.music.wrapped

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules the annual Cypher Wrapped notification on 31 December at 20:00 in
 * the device time zone.
 *
 * The trigger time is a pure function so it can be unit tested with a fixed clock.
 * The alarm is deliberately inexact (but idle-safe): a yearly notification does
 * not need "Alarms & reminders" access, so Cypher never asks for that special
 * permission just to announce the recap a few minutes late.
 */
object WrappedScheduler {
    /** Recap year used by legacy deep links that do not carry an explicit year. */
    const val YEAR = 2026
    const val EVENT_HOUR = 20
    const val ACTION_OPEN_WRAPPED = "com.cypher.music.action.OPEN_WRAPPED"
    const val ACTION_WRAPPED_ALARM = "com.cypher.music.action.WRAPPED_ALARM"
    const val EXTRA_YEAR = "wrapped_year"
    const val DEEP_LINK_SCHEME = "cypher"
    const val DEEP_LINK_HOST = "wrapped"
    const val REQUEST_CODE = 20261231

    private const val TAG = "WrappedScheduler"

    /** Outcome of planning the alarm; [triggerAtMillis] is null when the date already passed. */
    data class AlarmPlan(val triggerAtMillis: Long?) {
        val alreadyPassed: Boolean get() = triggerAtMillis == null
    }

    fun eventTime(year: Int, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        ZonedDateTime.of(year, 12, 31, EVENT_HOUR, 0, 0, 0, zone)

    /** Pure planning function: no Android APIs, deterministic for tests. */
    fun plan(now: ZonedDateTime, recapYear: Int): AlarmPlan {
        val trigger = eventTime(recapYear, now.zone).toInstant().toEpochMilli()
        if (now.toInstant().toEpochMilli() >= trigger) return AlarmPlan(null)
        return AlarmPlan(trigger)
    }

    /** 31 December of the current year; January already points at the new year. */
    fun nextEventYear(today: LocalDate): Int = today.year

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = ZonedDateTime.now()
        val year = nextEventYear(now.toLocalDate())
        val plan = plan(now, year)
        if (plan.alreadyPassed) {
            Log.i(TAG, "Wrapped $year event already passed; nothing to schedule")
            return
        }
        val triggerAt = plan.triggerAtMillis ?: return
        val pendingIntent = alarmPendingIntent(context, year)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        Log.i(TAG, "Wrapped $year scheduled for ${eventTime(year)} (inexact, idle-safe)")
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val year = nextEventYear(LocalDate.now())
        alarmManager.cancel(alarmPendingIntent(context, year))
    }

    fun deepLink(year: Int = YEAR): String =
        "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST?year=$year"

    private fun alarmPendingIntent(context: Context, year: Int): PendingIntent {
        val intent = Intent(context, WrappedAlarmReceiver::class.java).apply {
            action = ACTION_WRAPPED_ALARM
            putExtra(EXTRA_YEAR, year)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

package com.cypher.music.wrapped

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules the annual Cypher Wrapped notification for 31 December 2026 at 20:00
 * in the device time zone. Uses an exact alarm when the platform allows it and
 * falls back to an inexact idle-safe alarm when the user has not granted
 * "Alarms & reminders" access.
 */
object WrappedScheduler {
    const val YEAR = 2026
    const val ACTION_OPEN_WRAPPED = "com.cypher.music.action.OPEN_WRAPPED"
    const val ACTION_WRAPPED_ALARM = "com.cypher.music.action.WRAPPED_ALARM"
    const val EXTRA_YEAR = "wrapped_year"
    const val DEEP_LINK_SCHEME = "cypher"
    const val DEEP_LINK_HOST = "wrapped"
    const val REQUEST_CODE = 20261231

    private const val TAG = "WrappedScheduler"

    val eventTime: ZonedDateTime
        get() = ZonedDateTime.of(YEAR, 12, 31, 20, 0, 0, 0, ZoneId.systemDefault())

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = eventTime.toInstant().toEpochMilli()
        if (System.currentTimeMillis() >= triggerAt) {
            Log.i(TAG, "Wrapped $YEAR event already passed; nothing to schedule")
            return
        }

        val pendingIntent = alarmPendingIntent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                Log.w(TAG, "Exact alarms not permitted; scheduled inexact fallback for $eventTime")
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                Log.i(TAG, "Wrapped $YEAR scheduled exactly for $eventTime")
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            Log.w(TAG, "Exact alarm denied; scheduled inexact fallback", e)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(alarmPendingIntent(context))
    }

    fun deepLink(year: Int = YEAR): String =
        "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST?year=$year"

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WrappedAlarmReceiver::class.java).apply {
            action = ACTION_WRAPPED_ALARM
            putExtra(EXTRA_YEAR, YEAR)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

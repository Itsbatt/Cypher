package com.cypher.music.wrapped

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cypher.music.MainActivity
import com.cypher.music.R

class WrappedAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != WrappedScheduler.ACTION_WRAPPED_ALARM) return
        val year = intent.getIntExtra(WrappedScheduler.EXTRA_YEAR, WrappedScheduler.YEAR)
        showNotification(context, year)
    }

    companion object {
        const val CHANNEL_ID = "cypher_wrapped"
        const val NOTIFICATION_ID = 2026

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cypher Wrapped",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Resumen anual de tu música"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        fun showNotification(context: Context, year: Int) {
            ensureChannel(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val deepLink = Uri.parse(WrappedScheduler.deepLink(year))
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                action = WrappedScheduler.ACTION_OPEN_WRAPPED
                data = deepLink
                putExtra(WrappedScheduler.EXTRA_YEAR, year)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                WrappedScheduler.REQUEST_CODE,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_apple_music_note)
                .setContentTitle("Tu Cypher Wrapped $year ya está aquí")
                .setContentText("Mira tus minutos, tus canciones y tu artista del año.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Tu resumen anual está listo: minutos escuchados, Top 5 de canciones, " +
                            "tus artistas favoritos y el día en que más música pusiste."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // Notification permission was revoked between the check and notify().
            }
        }
    }
}

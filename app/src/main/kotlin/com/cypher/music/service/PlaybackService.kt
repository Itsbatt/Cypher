package com.cypher.music.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaStyleNotificationHelper
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.cypher.music.MainActivity
import com.cypher.music.R
import com.cypher.music.playback.MusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    companion object {
        const val CHANNEL_ID = "cypher_playback"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.cypher.music.ACTION_PLAY"
        const val ACTION_PAUSE = "com.cypher.music.ACTION_PAUSE"
        const val ACTION_TOGGLE = "com.cypher.music.ACTION_TOGGLE"
        const val ACTION_NEXT = "com.cypher.music.ACTION_NEXT"
        const val ACTION_PREV = "com.cypher.music.ACTION_PREV"
        const val ACTION_UPDATE = "com.cypher.music.ACTION_UPDATE"

        fun updateService(context: Context) {
            try {
                val intent = Intent(context, PlaybackService::class.java).apply {
                    action = ACTION_UPDATE
                }
                context.startService(intent)
            } catch (e: Exception) {
                // Ignore if service cannot be contacted
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var lastArtworkUrl: String? = null
    private var lastArtworkBitmap: Bitmap? = null
    private var isNoisyReceiverRegistered = false

    /**
     * Pauses playback when headphones/Bluetooth are disconnected. ExoPlayer already
     * handles this natively for local/offline audio; this receiver stops the YouTube
     * IFrame engine, whose audio does not go through ExoPlayer.
     */
    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                MusicPlayerManager.activeInstance?.pauseForHeadphoneDisconnect()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val session = MusicPlayerManager.getInstance(this).mediaLibrarySession
        if (session !in sessions) { try { addSession(session) } catch (e: Exception) {} }
        registerAudioBecomingNoisyReceiver()
    }

    private fun registerAudioBecomingNoisyReceiver() {
        if (isNoisyReceiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                this,
                audioBecomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isNoisyReceiverRegistered = true
        } catch (e: Exception) {
            isNoisyReceiverRegistered = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cypher Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Apple Music Audio Controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> MusicPlayerManager.activeInstance?.togglePlayPause()
            ACTION_PLAY -> {
                if (MusicPlayerManager.activeInstance?.state?.value?.isPlaying == false) {
                    MusicPlayerManager.activeInstance?.togglePlayPause()
                }
            }
            ACTION_PAUSE -> {
                if (MusicPlayerManager.activeInstance?.state?.value?.isPlaying == true) {
                    MusicPlayerManager.activeInstance?.togglePlayPause()
                }
            }
            ACTION_NEXT -> MusicPlayerManager.activeInstance?.playNext()
            ACTION_PREV -> MusicPlayerManager.activeInstance?.playPrevious()
        }

        // Attach MediaSession to MediaSessionService
        MusicPlayerManager.activeMediaSession?.let { session ->
            if (session !in sessions) {
                try {
                    addSession(session)
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }

        buildAndPostNotification()

        return super.onStartCommand(intent, flags, startId)
    }

    private fun buildAndPostNotification() {
        val playerManager = MusicPlayerManager.activeInstance
        val currentTrack = playerManager?.state?.value?.currentTrack
        val isPlaying = playerManager?.state?.value?.isPlaying ?: false

        val title = currentTrack?.title ?: "Apple Music"
        val artist = currentTrack?.artist ?: "Reproduciendo música"
        val album = currentTrack?.album ?: ""

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // PendingIntents for Media Actions
        val prevPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val togglePendingIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // media3 owns the notification style now: it reads the session directly and
        // keeps the compact actions and the large icon in sync without the legacy
        // androidx.media token bridge.
        val mediaStyle = MusicPlayerManager.activeMediaSession?.let { session ->
            MediaStyleNotificationHelper.MediaStyle(session)
                .setShowActionsInCompactView(0, 1, 2)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_apple_music_note)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(album.ifEmpty { null })
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_skip_previous, "Anterior", prevPendingIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (isPlaying) "Pausar" else "Reproducir",
                togglePendingIntent
            )
            .addAction(R.drawable.ic_skip_next, "Siguiente", nextPendingIntent)

        mediaStyle?.let { builder.setStyle(it) }

        if (lastArtworkBitmap != null) {
            builder.setLargeIcon(lastArtworkBitmap)
        }

        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Asynchronously load high-res album artwork if needed
        val artUrl = currentTrack?.artworkUrl
        if (artUrl.isNullOrEmpty()) {
            // Never inherit the previous cover when the new track has no artwork.
            lastArtworkUrl = null
            lastArtworkBitmap = null
        } else if (artUrl != lastArtworkUrl) {
            lastArtworkUrl = artUrl
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val loader = ImageLoader(this@PlaybackService)
                    val req = ImageRequest.Builder(this@PlaybackService)
                        .data(artUrl)
                        .allowHardware(false)
                        .build()
                    val result = (loader.execute(req) as? SuccessResult)?.drawable
                    val bmp = (result as? BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        lastArtworkBitmap = bmp
                        val nm = getSystemService(NotificationManager::class.java)
                        builder.setLargeIcon(bmp)
                        nm?.notify(NOTIFICATION_ID, builder.build())
                    }
                } catch (e: Exception) {
                    // Ignore art load failure
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return MusicPlayerManager.getInstance(this).mediaLibrarySession
    }

    override fun onDestroy() {
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(audioBecomingNoisyReceiver)
            } catch (e: IllegalArgumentException) {
                // Already unregistered
            }
            isNoisyReceiverRegistered = false
        }
        super.onDestroy()
    }
}

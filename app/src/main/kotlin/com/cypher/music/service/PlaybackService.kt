package com.cypher.music.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
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
    private var isNoisyReceiverRegistered = false
    private var wakeLockObserverJob: Job? = null
    private var playbackWakeLock: PowerManager.WakeLock? = null

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
        setMediaNotificationProvider(buildNotificationProvider())
        createNotificationChannel()
        val session = MusicPlayerManager.getInstance(this).mediaLibrarySession
        if (session !in sessions) { try { addSession(session) } catch (e: Exception) {} }
        registerAudioBecomingNoisyReceiver()
        observePlaybackForWakeLock()
    }

    /**
     * media3 builds and updates the media notification itself, so the platform gets a
     * real MediaStyle token and the transport buttons come from the session commands
     * instead of a hand-written action list (which some OEM skins ignore).
     */
    private fun buildNotificationProvider(): MediaNotification.Provider {
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.notification_channel_playback)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        provider.setSmallIcon(R.drawable.ic_apple_music_note)
        return provider
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

    /**
     * Holds a partial wake lock while a track is playing or buffering. Without it the
     * CPU can fall into Doze between two songs (the engine releases its own locks when
     * the current item ends), so the next stream never resolves and the queue stops
     * when the screen is off or the app is in the background.
     */
    private fun observePlaybackForWakeLock() {
        val manager = MusicPlayerManager.getInstance(this)
        wakeLockObserverJob?.cancel()
        wakeLockObserverJob = serviceScope.launch {
            manager.state.collect { snapshot ->
                val wantsWakeLock = snapshot.currentTrack != null &&
                    (snapshot.isPlaying || snapshot.isBuffering)
                if (wantsWakeLock) acquirePlaybackWakeLock() else releasePlaybackWakeLock()
            }
        }
    }

    private fun acquirePlaybackWakeLock() {
        if (playbackWakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(PowerManager::class.java) ?: return
            playbackWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "cypher:playback"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            playbackWakeLock = null
        }
    }

    private fun releasePlaybackWakeLock() {
        try {
            playbackWakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            // Already released
        }
        playbackWakeLock = null
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

        super.onStartCommand(intent, flags, startId)

        // Keep the service alive after the system reclaims it: media3 re-posts the
        // notification from the restored session.
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return MusicPlayerManager.getInstance(this).mediaLibrarySession
    }

    override fun onDestroy() {
        wakeLockObserverJob?.cancel()
        releasePlaybackWakeLock()
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

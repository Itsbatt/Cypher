package com.cypher.music

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CypherApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        com.cypher.music.wrapped.WrappedScheduler.schedule(this)
        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@CypherApplication)
                Log.i("CypherApplication", "YoutubeDL initialized successfully")
                try {
                    val updateStatus = YoutubeDL.getInstance().updateYoutubeDL(this@CypherApplication, YoutubeDL.UpdateChannel.STABLE)
                    Log.i("CypherApplication", "YoutubeDL update status: $updateStatus")
                } catch (ue: Exception) {
                    Log.w("CypherApplication", "yt-dlp auto update note: ${ue.message}")
                }
            } catch (e: Exception) {
                Log.e("CypherApplication", "Failed to initialize YoutubeDL", e)
            }

            try {
                FFmpeg.getInstance().init(this@CypherApplication)
                Log.i("CypherApplication", "FFmpeg initialized successfully")
            } catch (e: Exception) {
                Log.e("CypherApplication", "Failed to initialize FFmpeg", e)
            }

            try {
                com.cypher.music.playback.MediaCacheManager.syncDownloadedIds(this@CypherApplication)
            } catch (e: Exception) {
                Log.e("CypherApplication", "Failed to sync downloaded IDs", e)
            }

            try {
                // Downloads interrupted by a reboot or a kill resume here.
                com.cypher.music.playback.MediaCacheManager.resumePendingDownloads(this@CypherApplication)
            } catch (e: Exception) {
                Log.e("CypherApplication", "Failed to resume pending downloads", e)
            }
        }
    }
}

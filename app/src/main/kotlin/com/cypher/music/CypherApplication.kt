package com.cypher.music

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.cypher.music.network.YouTubeArtworkFallbackInterceptor
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CypherApplication : Application(), ImageLoaderFactory {
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

    /**
     * One shared Coil loader for the whole app: a bounded in-memory cache, a persistent
     * disk cache so covers are not re-downloaded on every launch, and the maxres fallback.
     * Palette extraction reuses this loader instead of building a second one per track.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(140)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("cypher_images"))
                .maxSizeBytes(192L * 1024L * 1024L)
                .build()
        }
        .components { add(YouTubeArtworkFallbackInterceptor()) }
        .build()
}

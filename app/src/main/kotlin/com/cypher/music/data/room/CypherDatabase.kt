package com.cypher.music.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        TrackEntity::class,
        PlaylistTrackCrossRef::class,
        RecentlyPlayedEntity::class,
        PlaybackAnalyticsEntity::class,
        PlaybackDailyEntity::class,
        SearchQueryEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CypherDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun trackDao(): TrackDao
    abstract fun recentlyPlayedDao(): RecentlyPlayedDao
    abstract fun playbackAnalyticsDao(): PlaybackAnalyticsDao
    abstract fun playbackDailyDao(): PlaybackDailyDao
    abstract fun searchQueryDao(): SearchQueryDao

    companion object {
        private const val DATABASE_NAME = "cypher_room.db"

        @Volatile
        private var INSTANCE: CypherDatabase? = null

        /**
         * Adds the daily rollup used by Cypher Wrapped without wiping existing
         * playlists, tracks or analytics.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playback_daily` (" +
                        "`day` TEXT NOT NULL, " +
                        "`trackId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, " +
                        "`artworkUrl` TEXT, " +
                        "`listenDurationMs` INTEGER NOT NULL, " +
                        "`playCount` INTEGER NOT NULL, " +
                        "`lastListenedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`day`, `trackId`))"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_daily_day` ON `playback_daily` (`day`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_daily_trackId` ON `playback_daily` (`trackId`)")

                // Keep listening time recorded before this table existed. Legacy rows are
                // attributed to their last listening day, so totals and rankings stay correct.
                database.execSQL(
                    "INSERT INTO `playback_daily` " +
                        "(`day`, `trackId`, `title`, `artist`, `artworkUrl`, `listenDurationMs`, `playCount`, `lastListenedAt`) " +
                        "SELECT date(`lastListenedAt` / 1000, 'unixepoch', 'localtime'), `trackId`, `title`, `artist`, `artworkUrl`, `listenDurationMs`, `playCount`, `lastListenedAt` " +
                        "FROM `playback_analytics` WHERE `listenDurationMs` > 0 AND `lastListenedAt` > 0 " +
                        "ON CONFLICT(`day`, `trackId`) DO NOTHING"
                )
            }
        }

        @JvmStatic
        fun getInstance(context: Context): CypherDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CypherDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build().also { INSTANCE = it }
            }
        }
    }
}

val Context.cypherDatabase: CypherDatabase
    get() = CypherDatabase.getInstance(this)

inline fun <T> Context.withCypherDatabase(block: CypherDatabase.() -> T): T {
    return cypherDatabase.block()
}

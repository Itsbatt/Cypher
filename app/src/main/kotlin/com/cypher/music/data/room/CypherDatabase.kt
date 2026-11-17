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
    version = 9,
    exportSchema = true
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

        /**
         * Adds the YouTube Music playlist id used to update a synced playlist in place.
         * Existing rows keep their data; their sourceId stays null until the next sync.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `playlists` ADD COLUMN `sourceId` TEXT")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_playlists_sourceId` ON `playlists` (`sourceId`)")
            }
        }

        /**
         * Records when a playlist was last mirrored and whether every page was imported.
         * Local playlists keep syncState null, so no existing row changes meaning.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `playlists` ADD COLUMN `lastSyncedAt` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `playlists` ADD COLUMN `syncState` TEXT")
            }
        }

        /**
         * Wrapped ranks albums and genres, so the daily rollup keeps both. Nullable columns
         * let existing rows stay valid: they simply have no album/genre until played again.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `playback_daily` ADD COLUMN `album` TEXT")
                database.execSQL("ALTER TABLE `playback_daily` ADD COLUMN `genre` TEXT")
            }
        }

        /**
         * 1.4.0 (v7) and this release (v9) have the same user-facing schema: the cloud
         * bookkeeping tables that 1.5.0 added never existed in v7, so this path only has
         * to be declared, not to change anything.
         */
        private val MIGRATION_7_9 = object : Migration(7, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Nothing to do: v7 already matches the v9 schema.
            }
        }

        /**
         * Drops the two tables that only existed to push a copy of the library to a
         * server. No playlists, tracks, favourites, history or statistics live there.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `sync_outbox`")
                database.execSQL("DROP TABLE IF EXISTS `sync_state`")
            }
        }

        /**
         * Every migration the app registers, in the order Room should try them.
         * The migration tests read this list instead of reflecting on private fields.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_9,
            MIGRATION_8_9
        )

        fun getInstance(context: Context): CypherDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CypherDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

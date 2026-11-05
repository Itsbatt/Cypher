package com.cypher.music.data.room;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {
        PlaylistEntity.class,
        TrackEntity.class,
        PlaylistTrackCrossRef.class,
        RecentlyPlayedEntity.class,
        PlaybackAnalyticsEntity.class,
        PlaybackDailyEntity.class,
        SearchQueryEntity.class
    },
    version = 4,
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class CypherDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "cypher_room.db";
    private static volatile CypherDatabase INSTANCE;

    /**
     * Adds the daily rollup used by Cypher Wrapped without wiping existing
     * playlists, tracks or analytics.
     */
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `playback_daily` (" +
                "`day` TEXT NOT NULL, " +
                "`trackId` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`artist` TEXT NOT NULL, " +
                "`artworkUrl` TEXT, " +
                "`listenDurationMs` INTEGER NOT NULL, " +
                "`playCount` INTEGER NOT NULL, " +
                "`lastListenedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`day`, `trackId`))");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_daily_day` ON `playback_daily` (`day`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_daily_trackId` ON `playback_daily` (`trackId`)");

            // Keep listening time recorded before this table existed. Legacy rows are
            // attributed to their last listening day, so totals and rankings stay correct.
            database.execSQL("INSERT INTO `playback_daily` " +
                "(`day`, `trackId`, `title`, `artist`, `artworkUrl`, `listenDurationMs`, `playCount`, `lastListenedAt`) " +
                "SELECT date(`lastListenedAt` / 1000, 'unixepoch', 'localtime'), `trackId`, `title`, `artist`, `artworkUrl`, `listenDurationMs`, `playCount`, `lastListenedAt` " +
                "FROM `playback_analytics` WHERE `listenDurationMs` > 0 AND `lastListenedAt` > 0 " +
                "ON CONFLICT(`day`, `trackId`) DO NOTHING");
        }
    };

    public abstract PlaylistDao playlistDao();
    public abstract TrackDao trackDao();
    public abstract RecentlyPlayedDao recentlyPlayedDao();
    public abstract PlaybackAnalyticsDao playbackAnalyticsDao();
    public abstract PlaybackDailyDao playbackDailyDao();
    public abstract SearchQueryDao searchQueryDao();

    public static CypherDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (CypherDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        CypherDatabase.class,
                        DATABASE_NAME
                    )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}

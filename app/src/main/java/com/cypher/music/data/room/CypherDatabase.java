package com.cypher.music.data.room;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(
    entities = {
        PlaylistEntity.class,
        TrackEntity.class,
        PlaylistTrackCrossRef.class,
        RecentlyPlayedEntity.class,
        PlaybackAnalyticsEntity.class,
        SearchQueryEntity.class
    },
    version = 3,
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class CypherDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "cypher_room.db";
    private static volatile CypherDatabase INSTANCE;

    public abstract PlaylistDao playlistDao();
    public abstract TrackDao trackDao();
    public abstract RecentlyPlayedDao recentlyPlayedDao();
    public abstract PlaybackAnalyticsDao playbackAnalyticsDao();
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
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}

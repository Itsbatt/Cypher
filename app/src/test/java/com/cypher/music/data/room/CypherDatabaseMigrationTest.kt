package com.cypher.music.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Runs the real Room migrations against real SQLite files.
 *
 * Every database is created from the exported schema of the version 1.4.0 / 1.5.0
 * actually shipped, then opened with the same [Migration] objects the app registers,
 * so Room itself validates the resulting schema (a missing step fails the test with
 * "A migration from X to Y was required but not found").
 *
 * Nothing here touches a device database: each file lives in Robolectric's sandbox and
 * is deleted afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CypherDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val opened = mutableListOf<String>()

    @After
    fun removeDatabases() {
        opened.forEach { name -> context.deleteDatabase(name) }
        opened.clear()
    }

    // ------------------------------------------------------------------ fixtures

    private fun schema(version: Int): JSONObject {
        val relatives = "schemas/com.cypher.music.data.room.CypherDatabase/$version.json"
        val candidates = listOf(File(relatives), File("app/$relatives"), File("../app/$relatives"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Falta el esquema exportado $version (buscado en ${candidates.map { it.absolutePath }})")
        return JSONObject(file.readText())
    }

    /** Builds a database exactly as that version left it: exported DDL, no Room master table. */
    private fun buildAt(version: Int, name: String): File {
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        file.delete()
        val entities = schema(version).getJSONObject("database").getJSONArray("entities")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (i in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            db.version = version
        }
        return file
    }

    /** The same user data for every starting version, inserted with plain SQL. */
    private fun seed(file: File) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO `playlists` (`id`, `name`, `description`, `coverUri`, `createdAt`, `updatedAt`, " +
                    "`isPrivate`, `sourceId`, `lastSyncedAt`, `syncState`) VALUES " +
                    "('p1', 'Noche', 'para dormir', 'file:///cover/p1.jpg', 100, 200, 0, NULL, 0, NULL)"
            )
            db.execSQL(
                "INSERT INTO `tracks` (`id`, `title`, `artist`, `album`, `durationMs`, `artworkUrl`, `streamUrl`, " +
                    "`sourceType`, `path`, `isLiked`, `isDownloaded`, `downloadedUri`, `updatedAt`) VALUES " +
                    "('a1', 'Uno', 'Artista B', 'Álbum Uno', 1000, 'file:///art/a1.jpg', NULL, 'LOCAL', '/m/a1.flac', 1, 0, NULL, 300), " +
                    "('a2', 'Dos', 'Artista A', 'Álbum Uno', 2000, NULL, NULL, 'LOCAL', '/m/a2.flac', 1, 0, NULL, 301), " +
                    "('a3', 'Tres', 'Artista C', 'Álbum Dos', 3000, NULL, NULL, 'LOCAL', '/m/a3.flac', 0, 0, NULL, 302)"
            )
            db.execSQL("INSERT INTO `playlist_tracks_crossref` VALUES ('p1', 'a1', 0, 400), ('p1', 'a2', 1, 401)")
            db.execSQL(
                "INSERT INTO `recently_played` (`id`, `itemType`, `title`, `subtitle`, `artworkUrl`, `streamUrl`, " +
                    "`sourceType`, `extraId`, `timestamp`) VALUES ('a1', 'TRACK', 'Uno', 'Artista B', NULL, NULL, 'LOCAL', NULL, 500)"
            )
            db.execSQL(
                "INSERT INTO `playback_analytics` (`trackId`, `title`, `artist`, `artworkUrl`, `listenDurationMs`, " +
                    "`playCount`, `lastListenedAt`) VALUES ('a1', 'Uno', 'Artista B', NULL, 4000, 2, 600), " +
                    "('a2', 'Dos', 'Artista A', NULL, 5000, 3, 601)"
            )
            db.execSQL(
                "INSERT INTO `playback_daily` (`day`, `trackId`, `title`, `artist`, `artworkUrl`, `album`, `genre`, " +
                    "`listenDurationMs`, `playCount`, `lastListenedAt`) VALUES " +
                    "('2026-11-14', 'a1', 'Uno', 'Artista B', NULL, 'Álbum Uno', 'Pop', 4000, 2, 600), " +
                    "('2026-11-15', 'a2', 'Dos', 'Artista A', NULL, 'Álbum Uno', 'Pop', 5000, 3, 601)"
            )
            db.execSQL("INSERT INTO `search_queries` VALUES ('noche', 700), ('dormir', 701)")
        }
    }

    /** Only v8 has these; the outbox rows must never count as user data. */
    private fun seedSyncBookkeeping(file: File) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO `sync_outbox` VALUES ('PLAYLIST:p1', 'PLAYLIST', 'p1', 200, 0, 0, 800, 0, NULL)"
            )
            db.execSQL("INSERT INTO `sync_state` VALUES ('account-1', 'cursor-9', 900, 901)")
        }
    }

    private fun migrations(): Array<Migration> {
        val names = listOf(
            "MIGRATION_3_4", "MIGRATION_4_5", "MIGRATION_5_6",
            "MIGRATION_6_7", "MIGRATION_7_9", "MIGRATION_8_9"
        )
        return names.map { name ->
            CypherDatabase::class.java.getDeclaredField(name).apply { isAccessible = true }
                .get(null) as Migration
        }.toTypedArray()
    }

    /** Opens with the app's own builder: Room picks the path, runs it and validates v9. */
    private fun openMigrated(name: String): CypherDatabase {
        opened += name
        val database = Room.databaseBuilder(context, CypherDatabase::class.java, name)
            .addMigrations(*migrations())
            .allowMainThreadQueries()
            .build()
        // Touching the helper is what triggers onUpgrade + validateMigration.
        database.openHelper.writableDatabase
        return database
    }

    /** RoomDatabase is AutoCloseable but not Closeable, so [use] does not apply. */
    private inline fun <T> withDatabase(name: String, block: (CypherDatabase) -> T): T {
        val database = openMigrated(name)
        return try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun CypherDatabase.count(sql: String): Long =
        openHelper.readableDatabase.query(sql).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else -1L
        }

    private fun CypherDatabase.text(sql: String): String? =
        openHelper.readableDatabase.query(sql).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }

    // --------------------------------------------------------------------- tests

    @Test
    fun `the shipped migration list covers every version up to 9`() {
        val edges = migrations().map { it.startVersion to it.endVersion }.toSet()
        assertTrue("3→4 declarada", edges.contains(3 to 4))
        assertTrue("4→5 declarada", edges.contains(4 to 5))
        assertTrue("5→6 declarada", edges.contains(5 to 6))
        assertTrue("6→7 declarada", edges.contains(6 to 7))
        // The two paths that exist in the wild: 1.4.0 (v7) and 1.5.0 beta (v8).
        assertTrue("7→9 declarada", edges.contains(7 to 9))
        assertTrue("8→9 declarada", edges.contains(8 to 9))
        for (start in 3..8) {
            val reachable = edges.any { it.first == start && it.second == 9 } ||
                edges.any { it.first == start } && edges.any { it.second == 9 }
            assertTrue("no hay ruta $start→9", reachable)
        }
    }

    @Test
    fun `schema 7 reaches 9 without losing playlists tracks favourites stats or history`() {
        val name = "migration-v7.db"
        seed(buildAt(7, name))

        withDatabase(name) { database ->
            assertEquals(1, database.count("SELECT COUNT(*) FROM playlists"))
            assertEquals("Noche", database.text("SELECT name FROM playlists WHERE id = 'p1'"))
            assertEquals("para dormir", database.text("SELECT description FROM playlists WHERE id = 'p1'"))
            // Carátula de la playlist y del tema.
            assertEquals("file:///cover/p1.jpg", database.text("SELECT coverUri FROM playlists WHERE id = 'p1'"))
            assertEquals("file:///art/a1.jpg", database.text("SELECT artworkUrl FROM tracks WHERE id = 'a1'"))

            assertEquals(3, database.count("SELECT COUNT(*) FROM tracks"))
            assertEquals("Dos", database.text("SELECT title FROM tracks WHERE id = 'a2'"))
            assertEquals("/m/a3.flac", database.text("SELECT path FROM tracks WHERE id = 'a3'"))

            // Orden de la playlist, que es lo que decide la reproducción.
            assertEquals(2, database.count("SELECT COUNT(*) FROM playlist_tracks_crossref WHERE playlistId = 'p1'"))
            assertEquals("a1", database.text("SELECT trackId FROM playlist_tracks_crossref WHERE playlistId = 'p1' ORDER BY position LIMIT 1"))
            assertEquals("a2", database.text("SELECT trackId FROM playlist_tracks_crossref WHERE playlistId = 'p1' ORDER BY position DESC LIMIT 1"))

            // Favoritos viven en tracks.isLiked.
            assertEquals(2, database.count("SELECT COUNT(*) FROM tracks WHERE isLiked = 1"))

            // Estadísticas: totales y el rollup diario con álbum y género.
            assertEquals(9000, database.count("SELECT SUM(listenDurationMs) FROM playback_analytics"))
            assertEquals(5, database.count("SELECT SUM(playCount) FROM playback_analytics"))
            assertEquals(2, database.count("SELECT COUNT(*) FROM playback_daily"))
            assertEquals("Álbum Uno", database.text("SELECT album FROM playback_daily WHERE day = '2026-11-14'"))
            assertEquals("Pop", database.text("SELECT genre FROM playback_daily WHERE day = '2026-11-14'"))

            assertEquals(1, database.count("SELECT COUNT(*) FROM recently_played"))
            assertEquals(2, database.count("SELECT COUNT(*) FROM search_queries"))

            // La ruta 7→9 no crea tablas de nube ni toca nada más.
            assertEquals(0, database.count("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('sync_outbox', 'sync_state')"))
        }
    }

    @Test
    fun `schema 8 reaches 9 and only the two sync tables disappear`() {
        val name = "migration-v8.db"
        val file = buildAt(8, name)
        seed(file)
        seedSyncBookkeeping(file)

        withDatabase(name) { database ->
            // Las dos tablas de contabilidad desaparecen…
            assertEquals(
                0,
                database.count("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('sync_outbox', 'sync_state')")
            )

            // …y todo lo escrito por la persona sigue exactamente igual.
            assertEquals(1, database.count("SELECT COUNT(*) FROM playlists"))
            assertEquals("Noche", database.text("SELECT name FROM playlists WHERE id = 'p1'"))
            assertEquals(3, database.count("SELECT COUNT(*) FROM tracks"))
            assertEquals(2, database.count("SELECT COUNT(*) FROM tracks WHERE isLiked = 1"))
            assertEquals(2, database.count("SELECT COUNT(*) FROM playlist_tracks_crossref"))
            assertEquals("a1", database.text("SELECT trackId FROM playlist_tracks_crossref WHERE playlistId = 'p1' ORDER BY position LIMIT 1"))
            assertEquals(9000, database.count("SELECT SUM(listenDurationMs) FROM playback_analytics"))
            assertEquals(2, database.count("SELECT COUNT(*) FROM playback_daily"))
            assertEquals("Álbum Uno", database.text("SELECT album FROM playback_daily WHERE day = '2026-11-15'"))
            assertEquals(1, database.count("SELECT COUNT(*) FROM recently_played"))
            assertEquals(2, database.count("SELECT COUNT(*) FROM search_queries"))
            assertNotNull(database.text("SELECT coverUri FROM playlists WHERE id = 'p1'"))
        }
    }

    @Test
    fun `a stale sync table left behind never blocks the upgrade`() {
        val name = "migration-v8-partial.db"
        val file = buildAt(8, name)
        seed(file)
        seedSyncBookkeeping(file)
        // A device that already dropped sync_outbox by hand: DROP IF EXISTS must cope.
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DROP TABLE `sync_outbox`")
        }

        withDatabase(name) { database ->
            assertEquals(3, database.count("SELECT COUNT(*) FROM tracks"))
            assertEquals(
                0,
                database.count("SELECT COUNT(*) FROM sqlite_master WHERE name = 'sync_state'")
            )
        }
    }

    @Test
    fun `opening a v9 database twice keeps the identity and the rows`() {
        val name = "migration-v9-reopen.db"
        seed(buildAt(7, name))
        withDatabase(name) { database ->
            assertEquals(3, database.count("SELECT COUNT(*) FROM tracks"))
        }
        // Reopening must not need a migration nor wipe anything.
        withDatabase(name) { database ->
            assertEquals(3, database.count("SELECT COUNT(*) FROM tracks"))
            assertEquals("Noche", database.text("SELECT name FROM playlists WHERE id = 'p1'"))
            assertNull(database.text("SELECT name FROM sqlite_master WHERE name = 'sync_outbox'"))
        }
    }
}

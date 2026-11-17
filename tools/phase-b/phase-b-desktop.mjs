// Phase B on Cypher Desktop: no account of any kind, no library copy in the cloud.
// Every pattern must match; a miss is reported and fails the run.
// Strings that contain Kotlin `$` interpolation are built with single quotes so the
// script never interpolates them itself.
import { readFileSync, writeFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const J = (...lines) => lines.join("\n");
const edits = [];
const edit = (file, steps) => edits.push({ file, steps });
const fn = (from, to = "") => ({ from, to, re: false });
const re = (from, to = "") => ({ from, to, re: true });

const APPSTATE = "desktop/src/main/kotlin/com/cypher/desktop/app/AppState.kt";

edit(APPSTATE, [
  fn("import com.cypher.desktop.core.OutboxRow\n"),
  fn(
    J(
      "import com.cypher.desktop.sync.GoogleAuth",
      "import com.cypher.desktop.sync.SyncClient",
      "import com.cypher.desktop.sync.SyncOutcome",
      "import com.cypher.desktop.sync.SyncService",
      ""
    ),
    "import com.cypher.desktop.importers.YouTubeOAuth\n"
  ),
  fn(
    "enum class Screen { HOME, LIBRARY, FAVORITES, PLAYLISTS, STATS, WRAPPED, DOWNLOADS, ONLINE, IMPORT, SETTINGS, ACCOUNT }",
    "enum class Screen { HOME, LIBRARY, FAVORITES, PLAYLISTS, STATS, WRAPPED, DOWNLOADS, ONLINE, IMPORT, SETTINGS }"
  ),
  fn(
    J(
      "enum class SyncPhase { LOCAL_ONLY, SYNCING, SYNCED, OFFLINE, ERROR, NEEDS_RECONNECT }",
      "",
      "data class AccountInfo(val email: String?, val subject: String?, val expiresAt: Long)",
      ""
    )
  ),
  fn(
    J(
      "    val account: AccountInfo? = null,",
      "    val signingIn: Boolean = false,",
      "    val syncPhase: SyncPhase = SyncPhase.LOCAL_ONLY,",
      "    val syncMessage: String? = null,",
      "    val lastSyncedAt: Long = 0L",
      ""
    )
  ),
  fn("    val isSignedIn: Boolean get() = account != null\n"),
  fn(
    J(
      "    /** YouTube-only consent, kept apart from the sync session so one cannot break the other. */",
      "    private var youtubeTokens: GoogleAuth.Tokens? = null",
      "",
      "    /** Kept in memory only: never written to disk, never logged. */",
      "    private var tokens: GoogleAuth.Tokens? = null",
      ""
    ),
    J(
      "    /** Consent for the importer only: memory-only, never written to disk or logged. */",
      "    private var youtubeTokens: YouTubeOAuth.Tokens? = null",
      ""
    )
  ),
  re(/    private fun enqueue\(data: LibraryData, kind: String[\s\S]*?\r?\n    \}\r?\n\r?\n/),
  fn(
    J(
      "    /** Used by the sync engine to persist an already-merged document. */",
      "    fun applySynced(block: (LibraryData) -> LibraryData) = mutate(block)",
      ""
    ),
    J(
      "    /** Test and demo hook: replaces the whole document and persists it. */",
      "    fun replaceLibrary(block: (LibraryData) -> LibraryData) = mutate(block)",
      ""
    )
  ),
  fn(
    J(
      "        data.cloudTrack(id)?.let { return Triple(it.title.ifBlank { \"Pista de la nube\" }, \"Canción · " +
        "$" +
        "{it.artist}\", it.artworkUrl) }",
      ""
    )
  ),
  fn(
    J(
      "        // Local files, songs that exist in the account and YouTube playlists all play",
      "        // from the same queue; only true gaps (an unavailable video) are skipped.",
      "        val ids = playlist.trackIds.filter { id ->",
      "            data.track(id) != null ||",
      "                data.remoteTrack(id)?.let { it.source == \"YOUTUBE\" && it.available } == true ||",
      "                data.cloudTrack(id)?.streamUrl?.startsWith(\"http\") == true",
      "        }"
    ),
    J(
      "        // Local files and imported YouTube references play from the same queue; only",
      "        // true gaps (an unavailable video) are skipped.",
      "        val ids = playlist.trackIds.filter { id ->",
      "            data.track(id) != null ||",
      "                data.remoteTrack(id)?.let { it.source == \"YOUTUBE\" && it.available } == true",
      "        }"
    )
  ),
  fn(
    J(
      "            val cloud = data.cloudTrack(trackId)",
      "            _state.update {",
      "                it.copy(",
      "                    notice = if (cloud != null) \"«" + "$" + "{cloud.title}» está en tu cuenta pero no tiene una fuente reproducible en este equipo.\"",
      "                    else \"Esa canción ya no está en la biblioteca.\"",
      "                )",
      "            }",
      "            return"
    ),
    J(
      "            _state.update { it.copy(notice = \"Esa canción ya no está en la biblioteca.\") }",
      "            return"
    )
  ),
  fn(
    J(
      "            val next = data.copy(stats = stats)",
      "            next.copy(outbox = enqueue(next, \"STATS\", \"" + "$" + "day|" + "$" + "{track.id}\", now))"
    ),
    "            data.copy(stats = stats)"
  ),
  fn(
    J(
      "            val next = data.copy(",
      "                favorites = favorites,",
      "                cloudTracks = if (isFav) data.cloudTracks.filterNot { it.id == trackId } else data.cloudTracks",
      "            )",
      "            next.copy(outbox = enqueue(next, \"FAVORITE\", trackId, now, deleted = isFav))"
    ),
    "            data.copy(favorites = favorites)"
  ),
  fn(
    J(
      "            val next = data.copy(",
      "                profile = data.profile.copy(displayName = name.ifBlank { \"Cypher\" }),",
      "                profileUpdatedAt = now",
      "            )",
      "            next.copy(outbox = enqueue(next, \"PROFILE\", \"profile\", now))"
    ),
    J(
      "            data.copy(",
      "                profile = data.profile.copy(displayName = name.ifBlank { \"Cypher\" }),",
      "                profileUpdatedAt = now",
      "            )"
    )
  ),
  fn(
    J(
      "        mutate { data ->",
      "            val next = data.copy(preferences = data.preferences.copy(accent = color), themeUpdatedAt = now)",
      "            // Only pushed when the phone's theme document was pulled: it is rewritten",
      "            // preserving every other field, never invented from scratch.",
      "            next.copy(",
      "                outbox = if (next.remoteThemeJson != null) enqueue(next, \"PREFERENCE\", \"theme\", now)",
      "                else next.outbox",
      "            )",
      "        }"
    ),
    J(
      "        mutate { data ->",
      "            data.copy(preferences = data.preferences.copy(accent = color), themeUpdatedAt = now)",
      "        }"
    )
  ),
  fn("                GoogleAuth(includeYouTubeScope = true).signIn()", "                YouTubeOAuth().signIn()"),
  re(
    /    \/\/ -+ sync\r?\n\r?\n    fun signIn\(\)[\s\S]*?\r?\n\}\r?\n\r?\n\/\*\*\r?\n \* Pure helpers/,
    "}\n\n/**\n * Pure helpers"
  ),
  fn(
    "        val ids = data.tracks.map { it.id }.toSet() + data.cloudTracks.map { it.id }.toSet()",
    "        val ids = data.tracks.map { it.id }.toSet() + data.remoteTracks.map { it.id }.toSet()"
  ),
]);

edit("desktop/src/main/kotlin/com/cypher/desktop/ui/App.kt", [
  fn("                                Screen.ACCOUNT -> AccountView(ui, accent, state)\n"),
  fn("                                Screen.ACCOUNT -> \"Cuenta\"\n"),
  fn("        RailButton(Icons.Rounded.Person, \"Cuenta\", ui.screen == Screen.ACCOUNT, accent) { state.select(Screen.ACCOUNT) }\n"),
  fn(
    "Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { state.select(Screen.ACCOUNT) }) {",
    "Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { state.select(Screen.SETTINGS) }) {"
  ),
  fn(
    "                        ui.account?.email ?: \"Solo en este equipo\",",
    "                        \"" + "$" + "{ui.data.tracks.size} pistas · solo en este equipo\","
  ),
  fn(
    J(
      "            data.cloudTracks.filter {",
      "                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)",
      "            }.take(3).forEach { cloud ->",
      "                add(",
      "                    SearchEntry(\"Canción · cuenta\", cloud.title.ifBlank { \"Pista de la nube\" }, cloud.artist, cloud.artworkUrl) {",
      "                        state.recordEntitySearch(\"CLOUD\", cloud.id)",
      "                        state.playQueue(listOf(cloud.id))",
      "                        state.closeSearch()",
      "                    }",
      "                )",
      "            }"
    ),
    J(
      "            data.remoteTracks.filter {",
      "                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)",
      "            }.take(3).forEach { remote ->",
      "                add(",
      "                    SearchEntry(\"Canción · YouTube\", remote.title, remote.artist, remote.artworkUrl) {",
      "                        state.recordEntitySearch(\"REMOTE\", remote.id)",
      "                        state.playQueue(listOf(remote.id))",
      "                        state.closeSearch()",
      "                    }",
      "                )",
      "            }"
    )
  ),
]);

edit("desktop/src/main/kotlin/com/cypher/desktop/ui/HomeFeed.kt", [
  fn("    val hasCloud = data.cloudTracks.isNotEmpty()\n"),
  fn(
    "    if (data.tracks.isEmpty() && data.playlists.isEmpty() && !hasCloud) {",
    "    if (data.tracks.isEmpty() && data.playlists.isEmpty() && data.remoteTracks.isEmpty()) {"
  ),
  fn(
    "                \"Todo se queda en este equipo hasta que decidas conectar tu cuenta.\",",
    "                \"O importa una lista de YouTube Music desde Importar.\","
  ),
  fn("                                    \"" + "$" + "{data.cloudTracks.size} en tu cuenta\".takeIf { hasCloud }\n"),
  re(/            if \(data\.cloudTracks\.isNotEmpty\(\)\) \{[\s\S]*?\r?\n            \}\r?\n\r?\n(?=            if \(youtubeLocal)/),
]);

edit("desktop/src/main/kotlin/com/cypher/desktop/ui/SettingsAccount.kt", [
  fn("import com.cypher.desktop.app.SyncPhase\n"),
  fn(
    "                \"Escritura atómica y copia previa a cada escaneo. " + "$" + "{ui.data.pendingChanges()} cambios esperan sincronización.\",",
    "                \"Escritura atómica y copia previa a cada escaneo. Todo se queda en este equipo.\","
  ),
  re(/@Composable\r?\nfun AccountView\([\s\S]*$/),
]);

edit("desktop/src/main/kotlin/com/cypher/desktop/core/SpotifyImport.kt", [
  fn("            data.cloudTracks.map { Candidate(it.id, it.title, it.artist, it.durationMs) } +\n"),
]);

edit("desktop/src/main/kotlin/com/cypher/desktop/ScreenshotTool.kt", [
  fn(
    J(
      "                cloudTracks = listOf(",
      "                    CloudTrack(",
      "                        \"cloud_1\", \"Una bala\", \"Milo J, Peso Pluma\", \"Álbum Uno\", 201_000,",
      "                        covers[1], \"https://example.invalid/stream\", \"YOUTUBE\"",
      "                    ),",
      "                    CloudTrack(",
      "                        \"cloud_2\", \"Bandida\", \"Bardero" + "$" + "\", \"Disco\", 188_000,",
      "                        covers[5], \"https://example.invalid/stream\", \"YOUTUBE\"",
      "                    )",
      "                ),"
    ),
    J(
      "                remoteTracks = listOf(",
      "                    RemoteTrack(",
      "                        id = \"yt_cloud000001\", videoId = \"cloud000001\", title = \"Una bala\",",
      "                        artist = \"Milo J, Peso Pluma\", album = \"Álbum Uno\", durationMs = 201_000,",
      "                        artworkUrl = covers[1], source = \"YOUTUBE\"",
      "                    ),",
      "                    RemoteTrack(",
      "                        id = \"yt_cloud000002\", videoId = \"cloud000002\", title = \"Bandida\",",
      "                        artist = \"Bardero" + "$" + "\", album = \"Disco\", durationMs = 188_000,",
      "                        artworkUrl = covers[5], source = \"YOUTUBE\"",
      "                    )",
      "                ),"
    )
  ),
]);

edit("desktop/src/test/kotlin/com/cypher/desktop/AppStateTest.kt", [
  fn(
    J(
      "        assertEquals(1, state.snapshot().outbox.count { it.key == \"FAVORITE:t1\" })",
      "        state.toggleFavorite(\"t1\")",
      "        assertTrue(!state.snapshot().isFavorite(\"t1\"))",
      "        assertTrue(state.snapshot().outbox.first { it.key == \"FAVORITE:t1\" }.deleted)"
    ),
    J(
      "        state.toggleFavorite(\"t1\")",
      "        assertTrue(!state.snapshot().isFavorite(\"t1\"))"
    )
  ),
  fn(
    J(
      "    fun `favorites of cloud-only tracks survive a rescan reconcile`() {",
      "        val data = LibraryData(",
      "            tracks = listOf(Track(\"t1\", \"Uno\", \"A\", path = \"C:/m/1.mp3\")),",
      "            favorites = listOf(\"t1\", \"cloud-1\"),",
      "            cloudTracks = listOf(CloudTrack(\"cloud-1\", \"Del móvil\"))",
      "        )"
    ),
    J(
      "    fun `favorites of remote-only tracks survive a rescan reconcile`() {",
      "        val data = LibraryData(",
      "            tracks = listOf(Track(\"t1\", \"Uno\", \"A\", path = \"C:/m/1.mp3\")),",
      "            favorites = listOf(\"t1\", \"cloud-1\"),",
      "            remoteTracks = listOf(RemoteTrack(\"cloud-1\", title = \"Del móvil\"))",
      "        )"
    )
  ),
]);

edit("desktop/src/test/kotlin/com/cypher/desktop/YouTubeDataTest.kt", [
  fn("import com.cypher.desktop.sync.GoogleAuth", "import com.cypher.desktop.importers.YouTubeOAuth"),
  re(
    /        assertTrue\(GoogleAuth\.YOUTUBE_SCOPE[\s\S]*?scopes\(includeYouTube = true\)\.contains\("youtube\.readonly"\)/,
    J(
      "        assertTrue(YouTubeOAuth.SCOPE.endsWith(\"youtube.readonly\"))",
      "        assertTrue(YouTubeOAuth.SCOPE.contains(\"openid\"))",
      "        assertTrue(!YouTubeOAuth.SCOPE.contains(\"auth/youtube \"))"
    )
  ),
]);

const escape = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
let failures = 0;
for (const { file, steps } of edits) {
  let text = readFileSync(file, "utf8");
  for (const step of steps) {
    const pattern = step.re ? step.from : new RegExp(escape(step.from).replace(/\n/g, "\\r?\\n"));
    if (!pattern.test(text)) {
      console.error("MISS  " + file + "\n      " + String(step.from).slice(0, 82).replace(/\n/g, " ⏎ "));
      failures++;
      continue;
    }
    text = text.replace(pattern, step.to);
  }
  writeFileSync(file, text);
}

// Every remaining caller of the old sync hook now uses the neutral name.
const walk = (dir) => readdirSync(dir).flatMap((name) => {
  const path = join(dir, name);
  return statSync(path).isDirectory() ? walk(path) : path.endsWith(".kt") ? [path] : [];
});
for (const file of walk("desktop/src")) {
  const text = readFileSync(file, "utf8");
  if (text.includes("applySynced")) writeFileSync(file, text.split("applySynced").join("replaceLibrary"));
}

console.log(failures === 0 ? "OK: escritorio sin cuenta ni nube" : "FALLOS: " + failures);
process.exit(failures === 0 ? 0 : 1);

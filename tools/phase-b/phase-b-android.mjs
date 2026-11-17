// Surgical removal of the Cypher cloud account + sync layer (phase B).
// Fails loudly when a pattern does not match, so nothing is silently skipped.
import { readFileSync, writeFileSync } from "node:fs";

const edits = [];
const edit = (file, steps) => edits.push({ file, steps });

const fn = (from, to = "") => ({ from, to, re: false });
const re = (from, to = "") => ({ from, to, re: true });

edit("app/src/main/kotlin/com/cypher/music/ui/main/MainViewModel.kt", [
  fn(
    `    // Cloud account + sync. The repository holds the Google session and the engine;
    // this ViewModel only exposes its state and actions to Compose.
    private val accountRepository = com.cypher.music.sync.AccountRepository(application)
    val accountState: StateFlow<com.cypher.music.sync.AccountUiState> = accountRepository.state
    private val _linkOfferVisible = MutableStateFlow(false)
    val linkOfferVisible: StateFlow<Boolean> = _linkOfferVisible.asStateFlow()

`
  ),
  re(
    /    init \{\r?\n        \/\/ Deliberately delayed[\s\S]*?accountRepository\.remoteChanges\.collect[\s\S]*?\r?\n    \}\r?\n\r?\n/
  ),
  re(
    /    fun signInWithGoogle\([\s\S]*?fun notifyAppResumed\(\) = accountRepository\.requestAutoSync\("app en primer plano"\)\r?\n\r?\n/
  ),
]);

edit("app/src/main/kotlin/com/cypher/music/ui/main/MainScreen.kt", [
  fn(`    val accountState by viewModel.accountState.collectAsState()\n`),
  fn(`    val linkOfferVisible by viewModel.linkOfferVisible.collectAsState()\n`),
  fn(
    `                accountState = accountState,
                onAccountConnect = { viewModel.signInWithGoogle(context) },
                onAccountDisconnect = { viewModel.signOutCloudAccount() },
                onAccountSyncNow = { viewModel.syncCloudNow() },
`
  ),
  re(
    /        \/\/ Cloud account linking popup:[\s\S]*?CloudLinkAccountDialog\([\s\S]*?\r?\n        \}\r?\n\r?\n/
  ),
]);

edit("app/src/main/kotlin/com/cypher/music/ui/screens/SettingsScreen.kt", [
  fn(
    `    accountState: com.cypher.music.sync.AccountUiState = com.cypher.music.sync.AccountUiState(),
    onAccountConnect: () -> Unit = {},
    onAccountDisconnect: () -> Unit = {},
    onAccountSyncNow: () -> Unit = {},
`
  ),
  re(
    /        \/\/ Section: Cuenta y Nube \(Google \+ sincronización\)\r?\n        item \{[\s\S]*?CloudAccountSection\([\s\S]*?\r?\n        \}\r?\n\r?\n/
  ),
]);

edit("app/src/main/kotlin/com/cypher/music/data/FavoritesManager.kt", [
  re(
    /            \/\/ Queued for the cloud: un-liking becomes a tombstone[\s\S]*?deleted = exists\r?\n            \)\r?\n/
  ),
]);

edit("app/src/main/kotlin/com/cypher/music/data/PlaylistRepository.kt", [
  fn(`                enqueueChangedPlaylists(domainPlaylists)\n`),
  re(
    /    \/\/ Playlists already uploaded, so a reload only enqueues the ones that really changed\.\r?\n    private val lastSyncedUpdatedAt[\s\S]*?\r?\n    \}\r?\n\r?\n/
  ),
  re(
    /            lastSyncedUpdatedAt\.remove\(playlistId\)\r?\n            com\.cypher\.music\.sync\.SyncQueue\.enqueue\([\s\S]*?deleted = true\r?\n            \)\r?\n/
  ),
]);

edit("app/src/main/java/com/cypher/music/data/room/CypherDatabase.java", [
  fn(`        SearchQueryEntity.class,\n        SyncOutboxEntity.class,\n        SyncStateEntity.class\n    },\n    version = 8,`,
     `        SearchQueryEntity.class\n    },\n    version = 9,`),
  re(
    /    \/\*\*\r?\n     \* Adds the cloud-sync bookkeeping tables\.[\s\S]*?MIGRATION_7_8 = new Migration\(7, 8\) \{[\s\S]*?\r?\n    \};\r?\n\r?\n/
  ),
  fn(`    public abstract SyncOutboxDao syncOutboxDao();\n    public abstract SyncStateDao syncStateDao();\n`, ``),
  fn(`.addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)`,
     `.addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_9, MIGRATION_8_9)`),
]);

edit("app/build.gradle.kts", [
  re(
    /    \/\/ Cloud account \+ sync credentials\.[\s\S]*?buildConfigField\("String", "SYNC_BASE_URL", [^\n]*\n/
  ),
  fn(
    `  // Google sign-in through Credential Manager (no passwords stored by the app)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)

`
  ),
]);

edit("gradle/libs.versions.toml", [
  fn(`credentials = "1.3.0"\ngoogleid = "1.1.1"\n`),
  fn(`androidx-credentials = { module = "androidx.credentials:credentials", version.ref = "credentials" }\nandroidx-credentials-play-services = { module = "androidx.credentials:credentials-play-services-auth", version.ref = "credentials" }\ngoogleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }\n`),
]);

let failures = 0;
for (const { file, steps } of edits) {
  let text = readFileSync(file, "utf8");
  for (const step of steps) {
    const pattern = step.re
      ? step.from
      : new RegExp(step.from.replace(/[.*+?^${}()|[\]\\]/g, "\\$&").replace(/\n/g, "\\r?\\n"));
    const match = text.match(pattern);
    if (!match) {
      console.error(`MISS  ${file}\n      ${String(step.from).slice(0, 90).replace(/\n/g, " ⏎ ")}`);
      failures++;
      continue;
    }
    text = text.replace(pattern, step.to);
  }
  writeFileSync(file, text);
}

// Tables 7 and 9 share the same schema: a 1.4.0 database only needs to skip the
// bookkeeping tables that no longer exist.
const db = "app/src/main/java/com/cypher/music/data/room/CypherDatabase.java";
const bt = String.fromCharCode(96);
const migrationBlock = [
  "    /**",
  "     * 1.4.0 (v7) and this release (v9) have the same user-facing schema: the cloud",
  "     * bookkeeping tables that 1.5.0 added never existed in v7, so this path only has",
  "     * to be declared, not to change anything.",
  "     */",
  "    private static final Migration MIGRATION_7_9 = new Migration(7, 9) {",
  "        @Override",
  "        public void migrate(SupportSQLiteDatabase database) {",
  "            // Nothing to do: v7 already matches the v9 schema.",
  "        }",
  "    };",
  "",
  "    /**",
  "     * Drops the two tables that only existed to push a copy of the library to a",
  "     * server. No playlists, tracks, favourites, history or statistics live there.",
  "     */",
  "    private static final Migration MIGRATION_8_9 = new Migration(8, 9) {",
  "        @Override",
  "        public void migrate(SupportSQLiteDatabase database) {",
  "            database.execSQL(\"DROP TABLE IF EXISTS " + bt + "sync_outbox" + bt + "\");",
  "            database.execSQL(\"DROP TABLE IF EXISTS " + bt + "sync_state" + bt + "\");",
  "        }",
  "    };",
  "",
  "    public abstract PlaylistDao playlistDao();",
].join("\n");
let java = readFileSync(db, "utf8");
if (!java.includes("    public abstract PlaylistDao playlistDao();")) {
  console.error("MISS  PlaylistDao anchor");
  process.exit(1);
}
java = java.replace("    public abstract PlaylistDao playlistDao();", migrationBlock);
writeFileSync(db, java);

console.log(failures === 0 ? "OK: todas las ediciones aplicadas" : `FALLOS: ${failures}`);
process.exit(failures === 0 ? 0 : 1);

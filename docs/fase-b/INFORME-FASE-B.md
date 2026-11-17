# FASE B — Retirada de cuenta y sincronización

Fecha: 16 de noviembre de 2026
Base Android: `fcab005` (1.4.0, versionCode 19)
Árbol de trabajo: sin commits, sin cambios de rama. El WIP 1.5.0 previo está en
`.backups/wip-1.5.0-20261116-111617/`.

---

## 1. Qué se ha eliminado

### Android (`com.cypher.music`)

| Elemento | Acción |
| --- | --- |
| `app/src/main/kotlin/com/cypher/music/sync/` (paquete completo) | Borrado |
| `ui/account/` (pantalla de cuenta) | Borrado |
| `data/LocalTrackRestore.kt` (restauración desde la nube) | Borrado |
| `SyncOutbox*.java`, `SyncState*.java` | Borrados |
| Tests de sincronización | Borrados |
| `hilt`/`retrofit` de sync, dependencias en `libs.versions.toml` y `app/build.gradle.kts` | Retiradas |
| Room 8→9: `sync_outbox`, `sync_state` | `DROP` + `MIGRATION_7_9` no-op para conservar la ruta 1.4.0→9 |

`MainViewModel`, `MainScreen`, `SettingsScreen`, `FavoritesManager`,
`PlaylistRepository` y `CypherDatabase` quedan sin ninguna referencia a cuenta,
sesión, outbox ni estado de sincronización.

### Desktop (`com.cypher.desktop`)

| Elemento | Acción |
| --- | --- |
| `desktop/src/main/kotlin/com/cypher/desktop/sync/` (paquete completo) | Borrado |
| `core/SyncMerge.kt` | Borrado |
| 7 tests: `SyncEngine`, `SyncCodec`, `SyncCore`, `SyncDiagnostics`, `SyncFixture`, `OAuthFlow`, `LoopbackLogin` | Borrados |
| `Screen.ACCOUNT` y `AccountView` | Eliminados |
| `CloudTrack`, `cloudTracks`, `LibraryData.sync`, `outbox`, `enqueue()`, `applySynced()`, `streamOf()` | Eliminados |
| `UiState.account`, `signingIn`, `syncPhase`, `syncMessage`, `lastSyncedAt`, `isSignedIn` | Eliminados |
| `SyncPhase`, `AccountInfo`, `OutboxRow` | Eliminados |

Textos de la interfaz que prometían sincronización («hasta que decidas conectar tu
cuenta», «solo en tu cuenta», «cambios pendientes») se han neutralizado. El badge
`SyncBadge` sigue existiendo como componente genérico, sin uso.

## 2. Qué se ha conservado a propósito

- **Importación desde servicios externos**: sigue viva. `YouTubeOAuth`
  (PKCE + loopback, scope `youtube.readonly`), `YouTubeData`, `YouTubeImport`,
  `SpotifyImport` y la pantalla `IMPORT`. Ya no comparten sesión con nada: el
  consentimiento vive en un `youtubeTokens` en memoria y nunca se escribe en disco.
- **Biblioteca local**: reproducción de archivos y carpeta de escaneo intactas.
- **Ajustes**: tema, acento, fondo, carpeta y almacenamiento siguen ahí; la
  pantalla de cuenta ya no.
- **Letras**: `LyricDoc` se amplía con `language`, `providerId`, `sourceUrl`,
  `fetchedAt` y `confidence` para la FASE F.

## 3. Migración de datos del Desktop

`Storage.migrateToCurrent` sube `library.json` de v2 a v3:

1. Copia de seguridad `library-v2-<marca>.json.bak` antes de tocar nada.
2. `cloudTracks` se convierte en `RemoteTrack` (misma información: id, título,
   artista, álbum, carátula, URL de stream) para no perder las canciones que el
   móvil había marcado.
3. Se eliminan las claves `cloudTracks` y `sync` del documento.
4. Los campos nuevos (`homeSections`, preferencias de personalización) entran con
   valores por defecto.

La escritura sigue siendo atómica (temporal + rename) y con copia previa, como
antes.

## 4. Verificación ejecutada

| Comprobación | Resultado |
| --- | --- |
| `./gradlew :app:assembleDebug` (Android) | BUILD SUCCESSFUL (1 m 13 s) |
| `./gradlew :app:testDebugUnitTest` (Android) | BUILD SUCCESSFUL, 0 fallos |
| `./gradlew test` (Desktop) | BUILD SUCCESSFUL — **78 tests, 0 fallos, 0 saltados** |
| `./gradlew run --args="--self-test"` (Desktop) | `OK: reproducción verificada`, audio real, 450 ms de 1000 ms |
| `grep` de `OutboxRow|SyncPhase|SyncService|SyncClient|cloudTrack|enqueue(` en `desktop/src` | Sin resultados |
| `grep` de `ACCOUNT` en `desktop/src` | Sin resultados |

Los tests de Desktop cubren: ciclo de vida de playlists, orden y borrado,
favoritos, estadísticas, letras, migración de almacenamiento y el renderizado de
todas las vistas (`UiSmokeTest`) en una escena de Compose sin pantalla.

## 5. Limitaciones conocidas

- **Android no se ha probado en un dispositivo físico.** Solo compilación y tests
  de unidad en JVM. No hay forma de afirmar nada sobre el comportamiento en móvil.
- Las migraciones de Room 7→9 y 8→9 **sí** se ejecutan de verdad, sobre bases SQLite
  construidas desde los esquemas exportados (ver `VERIFICACION-FASE-B.md`), pero en
  JVM con Robolectric, no en un dispositivo.
- **Los tokens de YouTube en Android sí se guardan de forma permanente**
  (`EncryptedSharedPreferences` + cookie jar del WebView). Es el método heredado de
  1.4.0 y es lo que deben sustituir D11/D12; Desktop los mantiene solo en memoria.
- `YouTubeOAuth` todavía no se ha ejecutado contra Google: la FASE E sigue
  bloqueada por la decisión D11/D12 (cliente OAuth, verificación de scope sensible
  y permiso para usar una cuenta real).

## 5 bis. Verificación adicional

Las comprobaciones de migraciones reales y separación de autenticación pedidas
aparte están en `docs/fase-b/VERIFICACION-FASE-B.md`. Resumen: Room 7→9 y 8→9
pasan con datos intactos (5 tests), `library.json` v2→v3 conserva todo y su `.bak`
restaura el original byte a byte (6 tests), no queda Google Login como cuenta de
Cypher, YouTube OAuth es independiente en ambas apps, y Android todavía persiste
la sesión de YouTube (hallazgo documentado, sin cambiar por mi cuenta).

## 6. Lo que viene

- **FASE C** — Rediseño Desktop según el PDF: vista ampliada con letras, panel
  derecho con pestañas, Inicio reordenado y Biblioteca local movida a Ajustes.
- **FASE D** — `cypher-library.zip` versionado: exportación desde Android,
  importación transaccional en Desktop, tests cruzados.
- **FASE F** — Letras automáticas (LRCLIB → lyrics.ovh, `.lrc` manual) y
  personalización.
- **FASE G** — Pruebas finales e informes.

# Cypher — Documentación integral del proyecto

> Documento maestro generado leyendo el código y la configuración del repositorio local
> (`C:\Users\bat\.gemini\antigravity\scratch\meloflow`), no de documentación previa.
> Fecha de esta revisión: 11 de noviembre de 2026.
> Rama documentada: `feature/ajustes-atajos-sueno` (con `main` en `e8a847f` como base).

---

## 1. Ficha del proyecto

| Dato | Valor |
| --- | --- |
| Nombre | **Cypher** |
| `applicationId` / paquete | `com.cypher.music` |
| `versionName` / `versionCode` | **1.5.0** / **21** |
| `minSdk` | **29** (Android 10) |
| `targetSdk` / `compileSdk` | **36** (Android 16) |
| Lenguaje | Kotlin **2.3.20** + 16 clases Java (solo la capa Room) |
| JVM | Java **17** (`jvmToolchain(17)`, source/target 17) |
| UI | Jetpack Compose + Material 3, Navigation 3 (`navigation3` 1.0.1), shaders AGSL |
| Reproductor | AndroidX Media3 **1.11.1** (ExoPlayer + `MediaLibraryService`) + IFrame de YouTube (`androidyoutubeplayer` 13.1.0) |
| Persistencia | Room **2.6.1**, base `cypher_room.db`, **versión 7**, 7 entidades / 6 DAO + `SharedPreferences` por dominio |
| Red | OkHttp 4.12.0, yt-dlp nativo (`youtubedl-android` 0.18.1 + `ffmpeg` 0.18.1), InnerTube, DNS propio (DoH) |
| Imágenes | Coil 2.7.0 (un solo `ImageLoader`) + `palette-ktx` 1.0.0 |
| Extras | ZXing 3.5.3 (QR), Google Code Scanner 16.1.0, `security-crypto` 1.1.0, Guava 33.3.1 |
| Build | Gradle con AGP **9.0.1**, Compose BOM `2026.03.01`, R8 + resource shrinking en release |
| Firma | `keystore.properties` → `keystore/cypher-release.jks` (ignorados por git). Flag `-PcypherDebugSigning=true` para firmar con debug |
| Licencia | GPL-3.0 (`LICENSE`) |
| Tamaño del código | **219 ficheros** en `app/src` (**177 fuentes**: 137 `.kt` de main, 24 de test y 16 `.java`), **35 219 líneas Kotlin** |
| Tests | **24 clases / 118 tests JVM**, JUnit4 (todos verdes en esta revisión) |
| CI | GitHub Actions `.github/workflows/android.yml` (tests + APK + publicación en tags) |

### Identidad visual

- Acento oficial **Lavanda Cypher `#9D7BB0`**, fondo **`#0D0B11`**, tarjetas **`#16141D`**.
- Perfiles de superficie: `OLED_PURE`, `DEEP_CHARCOAL`, `GRAPHITE`, `LIGHT_CLEAN` (`SurfaceStyle`).
- Paletas editables en Ajustes: 10 acentos, 5 fondos, 5 tonos de tarjeta (`AccentPalette`, `BackgroundTones`, `CardTones`).
- Shader **Liquid Glass** AGSL con 3 colores, modo de color (dinámico/manual), velocidad, distorsión y opacidad (`LiquidGlassConfig`).
- Movimiento reducido (`LocalReducedMotion`) respeta la preferencia del sistema y desactiva shaders/animación ambiental.

---

## 2. Estado actual del repositorio y de la app

### 2.1 Git

- Rama actual: **`feature/ajustes-atajos-sueno`** (creada desde `main`).
- Commits propios de la rama (3): `a068178` ajustes/descargas, `786c0cd` atajos, `fcab005` fade del temporizador.
- `main` en `e8a847f` *feat(playlists): portadas propias elegidas de la galería*; 72 commits en el historial.
- Otras ramas locales: `release/1.0.9`, `release/1.2.0`, `release/1.3.0`, `release/1.3.1`, `backup-before-push`. Remotas: `origin/main`, `origin/release/1.0.9`, `origin/release/1.1.0`, `origin/release/1.2.0`.
- Último tag: **`v1.4.0`** (los tags llegan hasta `v1.4.0`; `v1.4.1` no existe).
- **Árbol de trabajo sucio a propósito**: 18 ficheros modificados + 9 sin seguimiento pertenecientes al trabajo de la **1.4.1** de otra tarea (`LegalNoticeStore`, `MotionPreferences`, `FallbackEndpoints`, `DiagnosticsLog`, `ReducedMotion`, `LegalNoticeDialog`, docs de 1.4.1, `build-output/`). La rama de atajos **no** los commiteó; solo commitearon sus propios cambios.

### 2.2 Versión y artefactos

- `versionName 1.5.0`, `versionCode 21`: coincide con lo instalado y probado en el SM-S901E (`RZCT106THDK`).
- APK de release en `app/build/outputs/apk/release/`:

| Fichero | Tamaño |
| --- | --- |
| `app-arm64-v8a-release.apk` | 61 167 658 B |
| `app-armeabi-v7a-release.apk` | 54 514 524 B |
| `app-universal-release.apk` | 104 667 094 B |

- Copias de entrega en `build-output/feature-ajustes/` (arm64 y universal) con `SHA256SUMS.txt` verificado:
  - arm64: `890e47ddd725254f4dc10fdf1da20854b61bb39b0228d4dce4121ab5f269cc52`
  - universal: `171a451561470dd556719085355660679194e6a7f969ce4dbdceb3c96a403e07`

### 2.3 Dispositivos usados en pruebas

| Dispositivo | Modelo | Android | Notas |
| --- | --- | --- | --- |
| `LNH6FY8T6PDEZHXC` | OPPO CPH2579 | 15 (API 35), 720×1612 | Streaming correcto; base de pruebas de descargas y atajos |
| `3300b1a22e597563` | Samsung SM-T580 | 10 (API 29), 1200×1920 | WebView 81; históricamente el dispositivo problemático con el streaming |
| `RZCT106THDK` | Samsung SM-S901E | 16 (API 36) | Streaming y fade del temporizador verificados |

### 2.4 Estado funcional resumido

- Reproducción: funciona en los tres dispositivos. El móvil antiguo (tablet Android 10) reproduce por la ruta de URL directa/ExoPlayer y, si el IFrame no emite eventos, la posición avanza con un reloj de respaldo.
- Descargas: cola persistente, reanudación tras reinicio, borrado individual y total, estadísticas en Ajustes.
- Atajos de icono: registrados y funcionando por intent y por `dumpsys shortcut`.
- Temporizador de sueño: fade de 30 s verificado en dispositivo (rampa 1.00→0.00 y pausa en el SM-S901E; cancelación con restauración a 1.00 en el SM-T580).
- Tests JVM: 118/118 en verde.
- Pendiente de verificación manual: long press real del launcher y borrado coincidiendo con una descarga en curso (ver `docs/feature-ajustes-atajos-sueno.md`).

---

## 3. Arquitectura

### 3.1 Capas

```
┌──────────────────────────────────────────────────────────────────────────┐
│ PRESENTACIÓN — Compose + Material 3 + shaders AGSL                      │
│ MainScreen (scaffold, mini player, WebView del IFrame)                   │
│ Pantallas: Inicio · Explorar · Biblioteca · Buscar · Detalle playlist    │
│ Ajustes · Wrapped · Now Playing · Hojas (cola, letras, EQ, temporizador) │
└───────────────────────────┬──────────────────────────────────────────────┘
                            │ StateFlow / callbacks
┌───────────────────────────▼──────────────────────────────────────────────┐
│ VIEWMODELS — MainViewModel (orquestador) + Player/Library/YouTube/Share  │
│ UpdateViewModel (actualizaciones) · cypherWidthClass (responsive)        │
└───────────────────────────┬──────────────────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────────────────┐
│ REPRODUCCIÓN — MusicPlayerManager (singleton)                            │
│  · ExoPlayer (local, descargas, URL directa de streaming)                │
│  · IFrame de YouTube (WebView) como motor/carril alternativo             │
│  · PlaybackService (MediaLibraryService: notificación, Android Auto)     │
│  · EqualizerManager · AudioVolumeManager · MediaCacheManager             │
│  · PlaybackSessionStore · SleepFade · políticas puras (PlaybackResilience)│
└───────────────────────────┬──────────────────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────────────────┐
│ DATOS — Room v7 · MediaStore · SharedPreferences · red                   │
│ PlaylistRepository · FavoritesManager · MusicRepository · LocalMusicRepo │
│ YouTubeRepository (yt-dlp + InnerTube) · LrcLibService · PlaylistShare   │
│ SpotifyImport/Export · YouTubeAccountManager · StreamingTelemetry        │
└──────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Flujo de una reproducción

1. La UI llama a `MainViewModel.playTrack(track, queue)` → `MusicPlayerManager.playTrack`.
2. Se decide el motor con `PlaybackOutputPolicy.usesExoPlayer(isLocalSource, isOfflineDownload)`:
   - pista local o descarga offline → **ExoPlayer**;
   - streaming → se resuelve el id de vídeo (`extractVideoId`) y se intenta la reproducción (URL directa / IFrame según disponibilidad).
3. `PlaybackService` se asegura en primer plano (`ensurePlaybackService`) y mantiene un *wakelock* parcial mientras hay reproducción o buffering.
4. `PlaybackSessionStore` persiste pista, cola, índice, posición, shuffle y repeat; se restaura **en pausa** para no arrancar audio solo.
5. Al acercarse el final de la pista se pre-resuelve la siguiente (`precacheNextTrack`), con `StreamUrlPolicy` limitando antigüedad (2 h), intentos (2) y backoff.
6. `PlayCountTracker` acumula tiempo escuchado y dispara `onListeningTimeAccumulated`, que escribe en `playback_analytics` y en el rollup `playback_daily`.

### 3.3 Resiliencia de streaming

- `StreamingTelemetry`: `HEALTHY` / `DEGRADED` (≥1 fallo) / `CRITICAL` (≥4), detecta HTTP 403 y SABR, guarda el *tier* que resolvió cada vídeo y sugiere el siguiente `tier`.
- `YouTubeRepository`: búsqueda y resolución multi-nivel (yt-dlp con varios clientes, luego InnerTube TVHTML5) con `FallbackEndpoints` para hosts alternativos.
- `ResilientDns` / `YouTubeDns`: DNS con DoH (Cloudflare/Google) para redes que no resuelven los CDN de Google; el mismo DNS se usa en Coil.
- `TrackTransitionGuard`: ignora el `PAUSED` espurio del IFrame durante un cambio de pista.
- `ResumePolicy`: el *watchdog* de buffering no pisa una pausa pedida por el usuario.
- Reloj de respaldo de posición cuando el IFrame no emite `onCurrentSecond` (WebView antiguos).

---

## 4. Modelo de datos

### 4.1 Room (`cypher_room.db`, versión 7)

| Tabla | Entidad | Clave | Columnas principales |
| --- | --- | --- | --- |
| `tracks` | `TrackEntity` | `id` | title, artist, album, durationMs, artworkUrl, streamUrl, sourceType (`LOCAL`/`YOUTUBE`), path, isLiked, isDownloaded, downloadedUri, updatedAt |
| `playlists` | `PlaylistEntity` | `id` | name, description, coverUri, createdAt, updatedAt, isPrivate, sourceId (indice), lastSyncedAt, syncState |
| `playlist_tracks_crossref` | `PlaylistTrackCrossRef` | (`playlistId`,`trackId`) | orden de pistas |
| `recently_played` | `RecentlyPlayedEntity` | `id` | itemType (`TRACK`/`ALBUM`/`PLAYLIST`), title, subtitle, artworkUrl, streamUrl, sourceType, extraId, timestamp (índice DESC) |
| `playback_analytics` | `PlaybackAnalyticsEntity` | `trackId` | title, artist, artworkUrl, listenDurationMs, playCount, lastListenedAt |
| `playback_daily` | `PlaybackDailyEntity` | (`day`,`trackId`) | title, artist, artworkUrl, album, genre, listenDurationMs, playCount, lastListenedAt (índices por `day` y `trackId`) |
| `search_queries` | `SearchQueryEntity` | `queryText` | timestamp |

Migraciones registradas: **3→4** (crea `playback_daily` y vuelca `playback_analytics`), **4→5** (`sourceId` + índice), **5→6** (`lastSyncedAt`, `syncState`), **6→7** (`album`, `genre`). Esquemas exportados en `app/schemas/` (6.json, 7.json).

DAO (`app/src/main/java/com/cypher/music/data/room/`):

- `TrackDao`: insert/update, `getTrackById`, favoritos (lista e ids), `setFavorite`, descargas, `setDownloaded`, borrados (incluida purga de demos antiguos), búsqueda por título/artista/álbum.
- `PlaylistDao`: CRUD, `PlaylistWithTracks` (`@Relation`), crossrefs, reordenación, `searchPlaylists`, `getTrackCount`, estado de sync.
- `RecentlyPlayedDao`: insertar, últimos N, borrar uno/todo, purga de demos.
- `PlaybackAnalyticsDao`: `recordPlayback` (UPSERT acumulando), top artistas/canciones, totales, limpieza.
- `PlaybackDailyDao`: `recordDailyPlayback`, totales por rango, top canciones/artistas/álbumes/géneros, día pico, canción más repetida.
- `SearchQueryDao`: insertar, recientes, borrar una/todas.

Acceso: `CypherDatabase.getInstance(context)` con doble comprobación; `Context.cypherDatabase` y `dbRead { }` (`RoomReads.kt`) para lecturas en IO. `RoomMappers.kt` convierte entidad ↔ dominio (incluye tuples de estadísticas).

### 4.2 Persistencia fuera de Room

| Almacén | Fichero/Preferencia | Contenido |
| --- | --- | --- |
| `ThemePreferencesManager` | `cypher_theme_prefs` (JSON `theme_config`) | acento, superficie, fondos, radio, háptica, glow, crossfade, config del shader |
| `UserViewPreferencesManager` | preferencias de vistas | orden de biblioteca, secciones de Inicio y su orden, playlists favoritas, fechas de alta de pistas |
| `PlaybackSessionStore` | `cypher_playback_session` | snapshot JSON de la sesión (versión 1) |
| `MediaCacheManager` | `cypher_download_queue` | ids pendientes de descarga (reanudación tras reinicio) |
| `UpdatePreferences` | preferencias de update | última comprobación, versión/recompilación descartada |
| `WrappedPreferences` | preferencias de Wrapped | aviso ya mostrado / descartado |
| `LegalNoticeStore` | preferencias | aceptación del aviso legal |
| `MotionPreferences` | preferencias | movimiento reducido |
| `UserProfileStore` | preferencias | nombre y color de avatar del perfil local |
| `YouTubeAccountManager` | `EncryptedSharedPreferences` | cookies de YouTube (AES256-GCM, clave del Keystore) |
| `PlaylistCoverStore` | ficheros de la app | portadas propias elegidas de la galería |
| `SearchHistoryManager` | Room `search_queries` | búsquedas recientes |

### 4.3 Descargas ("offline vault")

- Carpeta `offline_vault` dentro de `getExternalFilesDirs()`: prioriza **MicroSD extraíble** montada, si no el primer volumen externo, si no `filesDir`. Migra automáticamente desde el `filesDir` antiguo.
- Se crea `.nomedia` para que la galería y los escáneres no vean los audios. **No hay cifrado**: es ocultación, no caja fuerte.
- Extensiones aceptadas: `flac, wav, m4a, webm, opus, mp3, aac, ogg`.
- Límites: caché LRU de **2 GB**, mínimo **80 MB** libres para descargar, descarte de ficheros < 1 KB.
- Cola persistente con reanudación (`resumePendingDownloads`) y sincronización de ids con Room (`syncDownloadedIds`).
- Descarga con yt-dlp + validación de contenedor; exportación a `MediaStore` disponible.

---

## 5. Mapa de ficheros y funciones

Ruta base: `app/src/main/kotlin/com/cypher/music/` salvo donde se indique.

### 5.1 Núcleo

| Fichero | Contenido |
| --- | --- |
| `CypherApplication.kt` | `Application` + `ImageLoaderFactory`. Arranca `DiagnosticsLog` (journal + crash handler), programa el Wrapped, inicializa yt-dlp (con auto-update), FFmpeg, sincroniza ids descargados y reanuda descargas pendientes. Expone un `ImageLoader` único (memoria 20 %, disco 192 MB, DNS resiliente, interceptor de portadas). |
| `MainActivity.kt` | Punto de entrada. `enableEdgeToEdge`, permisos runtime (audio + notificaciones), y `handleIncomingIntent`: deep link `cypher://wrapped` (año validado 2000–2100), atajos de icono (acción consumida una vez), `ACTION_SEARCH` / `MEDIA_PLAY_FROM_SEARCH` (query ≤ 200 caracteres), `ACTION_VIEW`/`MUSIC_PLAYER` con URI de audio `content://` o `file://`. `onResume` reprograma el Wrapped. |
| `Navigation.kt` | `MainNavigation`: aplica movimiento reducido y muestra el aviso legal la primera vez. |
| `model/Track.kt` | `AudioSourceType {LOCAL, YOUTUBE}` y `Track` (id, título, artista, álbum, duración, artwork, streamUrl, path, isLiked, isDownloaded, género). |
| `domain/usecase/GetMediaUseCase.kt` | Caso de uso de obtención de medios. |

### 5.2 `data/` (36 ficheros)

| Fichero | Contenido |
| --- | --- |
| `DownloadStats.kt` | `DownloadStats`, `DownloadDeletionResult`, `deletableDownloadIds` (excluye descargas en curso), `formatDownloadSize`, `describeDownloadStats`. |
| `ShortcutPlan.kt` | `ShortcutAction` (con `fromIntentAction`), `ShortcutOutcome`, `resolveShortcut`, `shortcutOutcomeMessage`. |
| `PlaylistRepository.kt` | CRUD de playlists sobre Room, orden, recientes, búsqueda, sincronización de estado. |
| `FavoritesManager.kt` | Flujos de ids y pistas favoritas, `toggleFavorite`, `isFavorite`. |
| `PlaylistSync.kt` | Fusión/merge de playlists sincronizadas (con tests). |
| `MusicRepository.kt` | Catálogo unificado: locales, destacados y `MediaCollection` editoriales. |
| `LocalMusicRepository.kt` | Escaneo de MediaStore (MP3/FLAC/WAV/AAC…), sin mocks. |
| `PlaylistModel.kt` | `Playlist`, `UnifiedSearchResults`, `RecentlyPlayedType/Item`, `BackupImportSummary`, `DownloadedPlaylist`, `toTrackCountString`. |
| `PlaybackStatsModel.kt` | `TopArtistStat`, `TopTrackStat`, `UserListeningStats`. |
| `WrappedStats.kt` | `WrappedTrackStat`, `WrappedArtistStat`, `WrappedPeakDay`, `WrappedStats`. |
| `RecommendationModel.kt` | `RecommendationSection` para Inicio. |
| `SearchCategory.kt` | Categorías/géneros de Explorar y Buscar. |
| `SearchHistoryManager.kt` | Historial reactivo de búsquedas. |
| `SpotifyImporter.kt`, `SpotifyImportModel.kt`, `SpotifyTransferModel.kt` | Flujo de importación (ZIP/CSV/JSON/enlace) y su estado de UI. |
| `SharedPlaylistModel.kt` | Payload de compartir/importar playlists por QR o código (`SharedTrackPayload`). |
| `ArtistNormalizer.kt` | Normalización de nombres de artista para agrupar variantes. |
| `HexColor.kt`, `HsvColor.kt` | Conversión de color para los selectores. |
| `ThemePreferences.kt` | `SurfaceStyle`, `ThemePreferences`, paletas, `CrossfadeOptions` (0/2/4/6/8 s) y `ThemePreferencesManager` (export/import JSON). |
| `UserViewPreferences.kt` | `TrackSortKey`, `LibrarySortState`, `HomeSection/HomePreferences`, `defaultHomeSections`, `sortLibraryTracks`. |
| `LyricsDisplaySettings.kt` | Tamaño, alineación, modo de color y `LyricsSettingsManager`. |
| `LiquidGlassConfig.kt` | `ShaderColorMode`, `LiquidGlassConfig`. |
| `MotionPreferences.kt` | Preferencia de movimiento reducido. |
| `LegalNoticeStore.kt` | Aceptación del aviso legal. |
| `UserModel.kt`, `UserProfileStore.kt` | Perfil local (nombre, avatar). |
| `PlaylistCoverStore.kt` | Portadas propias. |
| `WrappedPreferences.kt` | Estado del aviso de Wrapped. |
| `room/` | `CypherDatabase.kt` (accesores), `RoomMappers.kt`, `RoomReads.kt`; y en `app/src/main/java/.../room/` los 7 `*Entity.java`, 6 `*Dao.java`, `Converters.java` y `CypherDatabase.java`. |

### 5.3 `network/` (14 ficheros)

| Fichero | Contenido |
| --- | --- |
| `YouTubeRepository.kt` | `YouTubeDns` (DoH + IPs de respaldo) y `YouTubeRepository`: `searchTracks`, `getTrending`, `getStreamUrl` (multi-tier con `StreamingTelemetry`), `getDefaultFeaturedTracks`, `fetchLikedMusic`, `fetchUserPlaylists`, `fetchPlaylistTracksPaged`, `getSmartRecommendations`, contexto y browse de InnerTube. |
| `StreamingTelemetry.kt` | Salud del streaming (`HealthStatus`, `StreamingHealth`), `recordSuccess/recordError/recordTrackResolution`, `tierUsageSnapshot`, `getSuggestedFallbackTier`. |
| `ResilientDns.kt` | `Dns` con DoH/fallbacks. |
| `FallbackEndpoints.kt` | Endpoints alternativos por host. |
| `innertube/InnerTubePageParser.kt` | Parseo de páginas de InnerTube a pistas/playlists (con tests). |
| `innertube/InnerTubePager.kt` | Paginación (`PageSlice`, `PagedResult`, `InnerTubePager`). |
| `innertube/RemotePlaylist.kt` | Modelo de playlist remota. |
| `YouTubeAccountManager.kt` | Sesión de cuenta YouTube en `EncryptedSharedPreferences` (`YouTubeAccountSession`). |
| `YouTubeArtwork.kt` | URL `maxresdefault` + `YouTubeArtworkFallbackInterceptor` (reintenta con `hqdefault`). |
| `YouTubeMetadata.kt` | Álbum/género desde la descripción pública del vídeo, con caché. |
| `TitleCleaner.kt` | Limpieza de títulos de YouTube. |
| `LrcLibService.kt` | Letras de LRCLIB: `LyricLine`, `TrackLyrics`. |
| `PlaylistShareService.kt` | Publicación/resolución de códigos de playlist. |
| `SpotifyImportService.kt`, `SpotifyExportParser.kt` | Lectura de enlaces públicos y de exportaciones ZIP/CSV/JSON. |

### 5.4 `playback/` (12 ficheros) y `service/`

| Fichero | Contenido |
| --- | --- |
| `MusicPlayerManager.kt` | Corazón del reproductor (2 229 líneas). Estado `PlayerState` (pista, playing, buffering, posición, duración, cola, índice, shuffle, repeat), `EqualizerManager`, avisos (`playbackNotice`), temporizador (`sleepTimerMode`, `sleepTimerRemainingSeconds`), `mediaLibrarySession`. API: `playTrack`, `togglePlayPause`, `resumeLastSession`, `pause`, `pauseForHeadphoneDisconnect`, `seekToProgress`, `playNext/playPrevious`, `toggleShuffle/toggleRepeat`, `enqueueNext`, `addToQueue`, `clearQueue`, `removeFromQueue`, `moveQueueItem`, `jumpToQueueIndex`, `setMasterPlaybackVolume`, `setCrossfadeDuration`, `setSleepTimer`, `attachLoudnessEnhancer`, `setLoudnessEnhancerEnabled`, `attachYouTubePlayer`, `release`. Interno: `restoreSession/persistSession`, `precacheNextTrack`, `onTrackLoadFailed`, `recoverFromStall`, `ensurePlaybackService`, `silencePreviousAudio`, `startStreamingViaExo`, `startStreamingViaIframe`, `startFadeIn`, `checkVolumeTransitions`, `sleepTimerFadeFraction`, `startLocalProgressTracker`. |
| `MediaCacheManager.kt` | Singleton de caché y descargas: `downloadProgressMap`, `downloadedTrackIds`, `playlistProgressMap`, `failedDownloads`, `retryDownload`, `syncDownloadedIds`, `getSimpleCache`, `buildMediaSourceFactory`, `getOfflineVaultDir`, `getOfflineTrackFile`, `isTrackDownloaded/Downloading/Progress`, `getDownloadedTracks`, `hasFreeSpace`, `pendingDownloadIds`, `enqueueDownload`, `removeQueuedDownload`, `resumePendingDownloads`, `exportToMediaStore`, `downloadTrackOffline`, `downloadPlaylistOffline`, `deletePlaylistDownloads`, `deleteOfflineTrack`, `downloadStats`, `deleteAllDownloads`. |
| `PlaybackResilience.kt` | `CachedStreamUrl`, `StreamUrlPolicy` (frescura 2 h, 2 intentos, backoff), `TrackTransitionGuard`, `ResumePolicy`. |
| `PlaybackSessionStore.kt` | Snapshot JSON de la sesión; `RestoredSession`; restaura en pausa; `clear`. |
| `PlaybackOutputPolicy.kt` | Qué motor reproduce: local o descarga → ExoPlayer. |
| `TrackRowPlayback.kt` | Estado visual de la fila (actual vs. sonando de verdad). |
| `SleepFade.kt` | Curva del fade del temporizador (30 s, lineal, `volumeFraction`, `isFading`). |
| `PlayCountTracker.kt` | Acumulación de tiempo escuchado y conteo de reproducción. |
| `EqualizerManager.kt` | `EqualizerBand`, `EqualizerState`, presets y BassBoost sobre la sesión de audio. |
| `AudioVolumeManager.kt` | Volumen maestro/fracción expuesta a la UI. |
| `service/PlaybackService.kt` | `MediaLibraryService`: notificación MediaStyle (canal `cypher_playback`, id 1001), acciones internas (`ACTION_PLAY/PAUSE/TOGGLE/NEXT/PREV/UPDATE`), *wakelock* parcial durante reproducción/buffering, `ACTION_AUDIO_BECOMING_NOISY` (pausa al desconectar auriculares, también para el IFrame), `START_STICKY`, gestión de sesiones. |

### 5.5 `ui/main/` (ViewModel y contenedor)

| Fichero | Contenido |
| --- | --- |
| `MainViewModel.kt` | Orquestador (1 901 líneas). Expone ~70 `StateFlow` (player, progreso, favoritos, descargas, playlists, recientes, estadísticas, Wrapped, recomendaciones, búsqueda, tema, shader, perfiles, cuenta YouTube, Spotify, UI sheets, tab actual, sección de biblioteca, artista/álbum seleccionados, avisos). Métodos: reproducción y cola, favoritos, playlists (crear/añadir/quitar/reordenar/portada/borrar), tema y shader, descargas (individual, playlist, borrar, `downloadStats`, `deleteAllDownloads`), atajos (`handleShortcut`, `shortcutNotice`), búsqueda (`onSearchQueryChange`, historial, categorías), utilidades de perfil, Spotify (importar), YouTube (login, sync, retry), Wrapped (`openWrapped`, `loadWrappedStats`), navegación y sheets. |
| `MainScreen.kt` | Scaffold: barra inferior (`CypherNavBar`), mini reproductor, contenido por pestaña (`LISTEN_NOW`, `LIBRARY`, `SEARCH`), WebView interno + `IFramePlayerOptions` del motor YouTube, `BackHandler` jerárquico, overlays de Ajustes, Now Playing, Wrapped y hojas. |
| `viewmodels/PlayerViewModel.kt` | Controles, volumen, EQ y cola. |
| `viewmodels/LibraryViewModel.kt` | Colecciones locales, playlists, favoritos y descargas. |
| `viewmodels/YouTubeViewModel.kt` | Login y sincronización con YouTube Music. |
| `viewmodels/ShareViewModel.kt` | Compartir/importar por QR y código. |

### 5.6 `ui/screens/` (8 ficheros)

| Fichero | Contenido |
| --- | --- |
| `ListenNowScreen.kt` | Inicio: saludo, recomendaciones, recientes, playlists, accesos rápidos. |
| `HomeSections.kt` | Secciones de Inicio: `HomeRecommendationsSection`, `HomeRecentsSection`, `HomeFavoriteTracksSection`, `HomePlaylistsSection`, `HomeFavoritePlaylistsSection`, `HomeQuickAccessSection`. |
| `BrowseScreen.kt` | Explorar por categorías y estados de ánimo. |
| `LibraryScreen.kt` | Biblioteca con subsecciones `playlists`, `albumes`, `artistas`, `canciones`, `favoritos`, `descargas`, `archivos_locales`, más vistas de detalle de artista/álbum. |
| `SearchScreen.kt` | Búsqueda unificada (local + YouTube), voz, historial y categorías. |
| `PlaylistDetailScreen.kt` | Cabecera, edición, reordenación y compartir de una playlist. |
| `SettingsScreen.kt` | Ajustes (2 022 líneas): perfil, tema/acentos/fondos/tarjetas/bordes, shader, tipografía de letras, movimiento, secciones de Inicio, audio (crossfade, EQ, normalización, temporizador), **descargas con estadísticas y borrado total**, backup export/import, YouTube, Wrapped, actualizaciones, diagnóstico (exportar log, aviso legal), restaurar valores. |
| `DownloadsSectionView.kt` | Vista reutilizable de descargas (lista, estados, progreso, borrado). |

### 5.7 `ui/components/` (30 ficheros)

`CypherNavBar` (`CypherTab`: LISTEN_NOW/LIBRARY/SEARCH), `CypherMiniPlayer`, `CypherNowPlayingModal` (scrubber, volumen, cola, letras, temporizador), `CypherQueueSheet` (buscar y reordenar), `CypherLyricsSheet` (letras sincronizadas + selección de 6 líneas), `CypherEqualizerSheet`, `CypherSleepTimerSheet` (15/30/45/60 min, fin de pista, desactivado), `CypherTrackRow` (estado, favorito, descarga, menú), `CypherMediaCard`, `CypherSectionHeader`, `CypherContextMenu`, `AddToPlaylistSheet`, `CreatePlaylistDialog`, `SharePlaylistDialog`, `QrScannerSheet`, `PlaylistImportPreviewDialog`, `PlaylistMosaicCover`, `YouTubeLoginDialog`, `CypherVoiceSearchModal`, `ArtworkPlaceholder`, `LiquidGlassSettingsSheet`, `LiquidGlass` / `RealLiquidGlass` / `AmbientMeshBackground`, `HexColorPickerDialog`, `HsvColorPicker`, `HomeCustomizationSheet`, `UsernameDialog`, `LegalNoticeDialog`, `UpdateBanner`, `SpotifyImportSheet`, `SpotifySyncSheet`.

### 5.8 Otros paquetes

| Paquete | Ficheros |
| --- | --- |
| `ui/share/` | `LyricsFormatModels/LyricsShareModels` (formatos Historia/Cuadrado, diseños Minimal/Portada/Tipografía), `LyricsCardRenderer` (Canvas 1080 px), `LyricsShare` (MediaStore + `ACTION_SEND`), `LyricsShareSheet`, `WrappedCardRenderer`, `WrappedShareSheet`. |
| `ui/theme/` | `Color.kt` (paleta), `Theme.kt` (`CypherTheme`, `AppleMusicTheme`), `Type.kt`, `ThemeManager.kt` (`ThemeResolver`, `ResolvedThemeColors`), `PaletteExtractor.kt` (colores de la carátula, caché acotada), `ReducedMotion.kt`. |
| `ui/utils/` | `AppHaptics` (incluye *throttle* de 45 ms en el scrubber), `CypherWindowSize` (`CypherWidthClass`, ancho máximo de contenido). |
| `ui/wrapped/` | `WrappedScreen` (historias con avance táctil y háptica) y `WrappedSlides`: `WrappedMinutesSlide`, `WrappedTopTracksSlide`, `WrappedArtistsSlide` (con porcentaje y barras) y `WrappedSummarySlide` (cierre con los totales, álbumes, géneros y día pico). |
| `wrapped/` | `WrappedAvailability` (ventana **15 dic – 10 ene**), `WrappedScheduler` (alarma inexacta el **31 de diciembre a las 20:00**, deep link `cypher://wrapped?year=`), `WrappedAlarmReceiver` (canal `cypher_wrapped`, notificación 2026). |
| `update/` | `AppVersion` (parseo y comparación), `GitHubUpdateClient` (`ReleaseInfo`, `ReleaseAsset`, `fetchLatest`, `parseReleases`), `UpdateAssetSelector` (por ABI), `InstalledBuild` (SHA-256 del APK instalado), `UpdateInstaller` (`fetchChecksum`, `download` con progreso y tope, `verifyApk` firma+hash, `install`, ajustes de origen desconocido), `UpdatePreferences`, `UpdateViewModel` (`checkForUpdates` con cooldown de 15 min, detección de recompilación, `downloadAndInstall`, `dismiss`). |
| `utils/` | `DiagnosticsLog` (journal de errores, crash handler, exportable desde Ajustes), `QrCodeGenerator` (ZXing). |

### 5.9 Recursos

- `res/xml`: `shortcuts.xml` (atajos), `automotive_app_desc.xml` (Android Auto), `network_security_config.xml`, `file_paths.xml` (FileProvider), `backup_rules.xml`, `data_extraction_rules.xml`.
- `res/raw/silence.wav`: portador silencioso para mantener viva la sesión de ExoPlayer mientras el audio real lo emite el IFrame.
- `res/values`: `strings.xml` (6 cadenas; el grueso de textos vive en el código), `colors.xml`, `themes.xml`.
- 8 drawables propios (iconos de atajos, nota musical y variantes) + mipmaps adaptativos.

---

## 6. Funcionalidades, de punta a punta

### 6.1 Reproducción

- Motores: ExoPlayer (local, descargas, URL directa) e IFrame (navegador WebView). La elección es determinista y está testeada.
- Cola: añadir a continuación, añadir al final, quitar, reordenar por arrastre, buscar dentro, saltar a índice, vaciar con confirmación.
- Modos: aleatorio y repetir (off/todo/una), crossfade configurable 0/2/4/6/8 s.
- DSP: normalización de sonoridad (LoudnessEnhancer), ecualizador de bandas con presets y BassBoost.
- Temporizador de sueño: 15/30/45/60 min, fin de pista; **fade de 30 s** con restauración al cancelar.
- Sesión: notificación MediaStyle, controles externos, Android Auto (`MediaLibraryService`), pausa al desconectar auriculares, *wakelock* durante la reproducción, restauración en pausa tras reiniciar.
- Indicadores: solo la pista que suena muestra barras animadas; en pausa se mantiene el resaltado.

### 6.2 Descargas

- Individual o playlist completa, con progreso por pista y por playlist, fallos consultables y reintento.
- Cola persistente que se reanuda al abrir la app; comprobación de espacio libre.
- Cifras y borrado total en Ajustes (espacio, nº de archivos, confirmación, respeto de descargas en curso).
- Exportación de un archivo descargado a `MediaStore` cuando el usuario lo pide.

### 6.3 Playlists y datos

- Crear, renombrar, describir, portada propia o mosaico 2×2, borrar, reordenar.
- Favoritos de pistas y de playlists.
- Backup JSON (playlists, favoritos y ajustes; no incluye perfil, estadísticas ni audios) con exportación e importación.
- Compartir por QR o código corto e importar en otro dispositivo.
- Migración desde Spotify sin cuenta (ZIP/CSV/JSON o enlace público).

### 6.4 Búsqueda y exploración

- Búsqueda unificada local + YouTube Music con resultados globales, categorías, historial y búsqueda por voz.
- Integración con asistente/Bixby (`ACTION_SEARCH`, `MEDIA_PLAY_FROM_SEARCH`).
- Recomendaciones basadas en historial, con deduplicación y estados vacíos honestos.

### 6.5 Letras y Wrapped

- Letras de LRCLIB con resaltado línea a línea y selección de hasta 6 líneas consecutivas.
- Tarjetas de letras: 3 diseños × 2 formatos, render nativo a 1080 px, guardar en `Pictures/Cypher` o compartir.
- Wrapped: historia de 4 pantallas con datos reales de `playback_daily` (minutos, top 5 canciones, top 5 artistas y resumen final), tarjeta compartible y aviso el 31 de diciembre.

### 6.6 Actualizaciones y diagnóstico

- Comprobación de GitHub Releases con cooldown, selección de APK por ABI, descarga con tope de 300 MB, verificación de SHA-256 y firma, e instalación con el instalador del sistema.
- Detección de recompilaciones de la misma versión por digest del APK.
- `DiagnosticsLog` recoge errores de reproducción/descargas y permite exportarlos desde Ajustes.

### 6.7 Atajos de icono y sistema

- Atajos: «Reproducir favoritas» y «Continuar última sesión» (intents explícitos con acciones distintas; sin favoritas o sin sesión abren la app y avisan).
- Intents aceptados: launcher, `MUSIC_PLAYER`, `MEDIA_PLAY_FROM_SEARCH`, `ACTION_SEARCH`, `ACTION_VIEW` de audio, deep link del Wrapped.
- Permisos declarados: INTERNET, ACCESS_NETWORK_STATE, READ_MEDIA_AUDIO, READ_EXTERNAL_STORAGE (≤32), RECORD_AUDIO, FOREGROUND_SERVICE(+MEDIA_PLAYBACK), POST_NOTIFICATIONS, REQUEST_INSTALL_PACKAGES, WAKE_LOCK, VIBRATE.

---

## 7. Tests

Comando: `./gradlew :app:testDebugUnitTest` → **BUILD SUCCESSFUL, 118 tests, 0 fallos, 0 errores** (24 clases).

| Clase | Tests | Cubre |
| --- | --- | --- |
| `SpotifyExportTest` | 12 | Parseo de exportaciones ZIP/CSV/JSON |
| `InnerTubePageParserTest` | 8 | Parseo de páginas de InnerTube |
| `InnerTubePagerTest` | 8 | Paginación y continuations |
| `DownloadStatsTest` | 6 | Tamaños, resumen, exclusión de descargas activas |
| `SpotifyImportTest` | 6 | Importación y emparejado |
| `ShortcutPlanTest` | 5 | Selección de atajos y mensajes |
| `PlaylistSyncMergeTest` | 5 | Fusión de playlists sincronizadas |
| `PlayCountTrackerTest` | 5 | Acumulación de tiempo y conteo |
| `SleepFadeTest` | 5 | Curva del fade (1.00 → 0.00) |
| `StreamResiliencePolicyTest` | 5 | Frescura, reintentos y backoff de URL |
| `TrackTransitionGuardTest` | 5 | Pausas espurias del IFrame |
| `UpdateParsingTest` | 5 | Parseo de releases y checksums |
| `WrappedAvailabilityTest` | 5 | Ventana estacional |
| `WrappedSchedulePolicyTest` | 5 | Planificación de la alarma |
| `HexColorTest` / `HsvColorTest` | 4 / 4 | Conversión de color |
| `PlaybackOutputPolicyTest` | 4 | Motor por tipo de pista |
| `TrackRowPlaybackTest` | 4 | Estado visual de la fila |
| `AppVersionTest` | 4 | Comparación de versiones |
| `PlaylistNameMatchTest` | 3 | Coincidencia de nombres |
| `TitleCleanerTest` | 3 | Limpieza de títulos |
| `ResumePolicyTest` | 3 | Watchdog vs. pausa del usuario |
| `InstalledBuildTest` | 3 | Digest del APK instalado |
| `LiveInnerTubePaginationTest` | 1 | Paginación real (red) |

No hay tests instrumentados propios en el repo (solo dependencias de `androidTest`).

---

## 8. Build, CI y firma

### 8.1 Compilar

```bash
export JAVA_HOME="C:/Users/bat/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"
./gradlew :app:testDebugUnitTest        # tests JVM
./gradlew :app:assembleRelease          # APK firmados (splits por ABI + universal)
```

Flags de Gradle útiles:

| Flag | Efecto |
| --- | --- |
| `-PcypherDebugSigning=true` | Firma el release con la clave de debug |
| `-PcypherForceWrapped=true` / `CYPHER_FORCE_WRAPPED=true` | Permite abrir Wrapped fuera de temporada (solo debug) |
| `-PcypherEmulatorAbi=true` | Añade `x86_64` para emulador |
| `CYPHER_BUILD_STAMP` | Fija el `BUILD_ID` de `BuildConfig` |

- `splits.abi`: `arm64-v8a`, `armeabi-v7a` (+ `x86_64` opcional) con universal.
- Release con R8 + `shrinkResources`, `proguard-rules.pro` del proyecto.
- Room exporta esquema a `app/schemas` (`room.schemaLocation`).

### 8.2 CI

`.github/workflows/android.yml` (activado, commit `9824043`):

1. En push a `main`, PR y manual: `testDebugUnitTest` + `assembleRelease`, APK como artefacto `cypher-release-apks`.
2. En tags `v*`: calcula `SHA256SUMS.txt` y publica APK + sumas en la release (`gh release upload --clobber`) si existen los secretos `CYPHER_KEYSTORE_BASE64`, `CYPHER_KEYSTORE_PASSWORD`, `CYPHER_KEY_ALIAS`, `CYPHER_KEY_PASSWORD`.
3. `ci/README.md` documenta por qué el YAML vivió en `ci/` (token OAuth sin scope `workflow`).

### 8.3 Firma

- `keystore.properties` (ignorado) + `keystore/cypher-release.jks` (ignorado). Certificado `CN=Cypher`, SHA-256 `54:41:5F:F1:…:C1:77:20`.
- Nunca se ha commiteado material de firma; sin esa clave no se puede actualizar una instalación existente (habría que desinstalar).

---

## 9. Historial de versiones

Notas completas en `docs/releases/` (`RELEASE_NOTES_v1.0.0` … `v1.0.9`, `v1.2.0`, `v1.3.0`, `v1.3.1`, `v1.4.0`, más `1.4.1.md` y `1.5.0.md`).

| Versión | Hitos |
| --- | --- |
| 1.0.0 – 1.0.2 | Base Cypher (rebranding lavanda), Room, streaming híbrido, descargas ocultas, Wrapped inicial |
| 1.0.3 – 1.0.4 | Actualizador in-app, firma original recuperada, primera auditoría |
| 1.0.5 | Descargas robustas, `PlaybackOutputPolicy`, indicador de reproducción |
| 1.0.6 – 1.0.8 | Estabilización, playlists sincronizadas, correcciones de reproducción |
| 1.0.9 | Compartir letras (3×2), Wrapped con álbum/género (Room 6→7), portadas maxres, recomendaciones |
| 1.1.0 – 1.2.0 | Reparación de reproducción en WebView antiguos (reloj de respaldo, sin reinicios), publicación 1.2.0 |
| 1.3.0 / 1.3.1 | Streaming por URL directa con ExoPlayer, controles en notificación y cola con pantalla apagada |
| 1.4.0 | Portadas propias de playlist, correcciones de transición entre descargada y no descargada |
| 1.4.1 | Ajustes, privacidad de descargas, permisos, auditoría del APK (trabajo en curso de otra tarea) |
| 1.5.0 | Cuenta de Google opcional, popup de vinculación, sincronización offline-first, Room 8 (login real y sync bloqueados hasta tener client id y backend) |

Auditorías y reparaciones: `AUDIT_REPORT.md` (ciclo 1.0.9 y 1.0.4), `INFORME_REPARACION_REPRODUCCION.md` (1.2.0), `docs/feature-ajustes-atajos-sueno.md` (rama actual).

---

## 10. Incidencias conocidas y deuda técnica

Extraídas de los informes y del estado real del árbol:

1. **Streaming en WebView antiguos**: el IFrame no emite `PLAYING` ni `onCurrentSecond` en la tablet Android 10; hoy se compensa con reloj de respaldo y la ruta de URL directa/ExoPlayer. Queda dependencia de que la URL resuelta sea aceptada por ExoPlayer.
2. **Árbol de trabajo con 1.4.1 sin commitear**: cualquier build mezcla ese trabajo con la rama actual; conviene aislarlo antes de publicar.
3. **Recomposición elevada en `MainScreen`** (registrada en el informe de 1.2.0): separar el estado de posición del árbol completo.
4. **`ResilientDns` con IPs fijas y DoH forzado**: puede alterar la resolución en redes locales; se recomendó hacerlo configurable.
5. **Sin tests instrumentados** propios: toda la verificación de UI es manual vía ADB.
6. **Media3 1.11.1 / Room 2.6.1 / OkHttp 4.12**: Room y OkHttp están por detrás de sus últimas versiones; actualizarlos exige regresión específica.
7. **Long press del launcher** no automatizable por ADB: verificado por intent y `dumpsys shortcut`, no por gesto.
8. **Borrado con descarga en curso**: lógica cubierta por test, no observada en pantalla.
9. **Documentación antigua desalineada**: `README.md` (habla de 1.0.9/API 33), `PROJECT_DOCUMENTATION.md` (catalán, 1.0.9, Room v3) y `MAPEO_ARCHIVOS.md` (ruta y ficheros de un proyecto anterior). Este documento es la referencia actualizada.
10. **`README` y versión**: el README publicado dice minSdk 33, el proyecto real usa 29.

---

## 11. Cómo trabajar en este repo (práctico)

```bash
# Estado
git status --short && git log --oneline -5

# Tests y APK
export JAVA_HOME="C:/Users/bat/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease

# Instalar y observar
adb devices
adb install -r app/build/outputs/apk/release/app-universal-release.apk
adb logcat -s AppleMusicPlayer:* StreamingTelemetry:* MediaCacheManager:*
```

Logs más útiles:

| Etiqueta | Contenido |
| --- | --- |
| `AppleMusicPlayer` | Reproducción (`Playing '...' via ExoPlayer`), fade y cancelación del temporizador, fallos de IFrame |
| `StreamingTelemetry` | Anomalías de streaming y *tier* usado |
| `MediaCacheManager` | Descargas, borrados, espacio |
| `UpdateViewModel` | Comprobación y descarga de actualizaciones |
| `WrappedScheduler` | Programación de la alarma anual |
| `DiagnosticsLog` | Journal exportable desde Ajustes |

---

## 12. Inventario completo de fuentes

### Kotlin (`app/src/main/kotlin/com/cypher/music/`, 137 ficheros)

```
CypherApplication.kt · MainActivity.kt · Navigation.kt
data/: ArtistNormalizer, DownloadStats, FavoritesManager, HexColor, HsvColor,
  LegalNoticeStore, LiquidGlassConfig, LocalMusicRepository, LyricsDisplaySettings,
  MotionPreferences, MusicRepository, PlaybackStatsModel, PlaylistCoverStore,
  PlaylistModel, PlaylistRepository, PlaylistSync, RecommendationModel,
  SearchCategory, SearchHistoryManager, SharedPlaylistModel, ShortcutPlan,
  SpotifyImportModel, SpotifyImporter, SpotifyTransferModel, ThemePreferences,
  UserModel, UserProfileStore, UserViewPreferences, WrappedPreferences, WrappedStats
data/room/: CypherDatabase.kt, RoomMappers, RoomReads
domain/usecase/: GetMediaUseCase
model/: Track
network/: FallbackEndpoints, LrcLibService, PlaylistShareService, ResilientDns,
  SpotifyExportParser, SpotifyImportService, StreamingTelemetry, TitleCleaner,
  YouTubeAccountManager, YouTubeArtwork, YouTubeMetadata, YouTubeRepository
network/innertube/: InnerTubePageParser, InnerTubePager, RemotePlaylist
playback/: AudioVolumeManager, EqualizerManager, MediaCacheManager,
  MusicPlayerManager, PlaybackOutputPolicy, PlaybackResilience,
  PlaybackSessionStore, PlayCountTracker, SleepFade, TrackRowPlayback
service/: PlaybackService
ui/components/: AddToPlaylistSheet, ArtworkPlaceholder, CreatePlaylistDialog,
  CypherContextMenu, CypherEqualizerSheet, CypherLyricsSheet, CypherMediaCard,
  CypherMiniPlayer, CypherNavBar, CypherNowPlayingModal, CypherQueueSheet,
  CypherSectionHeader, CypherSleepTimerSheet, CypherTrackRow,
  CypherVoiceSearchModal, HexColorPickerDialog, HomeCustomizationSheet,
  HsvColorPicker, LegalNoticeDialog, LiquidGlass, LiquidGlassSettingsSheet,
  PlaylistImportPreviewDialog, PlaylistMosaicCover, QrScannerSheet,
  RealLiquidGlass, SharePlaylistDialog, SpotifyImportSheet, SpotifySyncSheet,
  UpdateBanner, UsernameDialog, YouTubeLoginDialog
ui/main/: MainScreen, MainViewModel
ui/main/viewmodels/: LibraryViewModel, PlayerViewModel, ShareViewModel, YouTubeViewModel
ui/screens/: BrowseScreen, DownloadsSectionView, HomeSections, LibraryScreen,
  ListenNowScreen, PlaylistDetailScreen, SearchScreen, SettingsScreen
ui/share/: LyricsCardRenderer, LyricsShare, LyricsShareModels, LyricsShareSheet,
  WrappedCardRenderer, WrappedShareSheet
ui/theme/: Color, PaletteExtractor, ReducedMotion, Theme, ThemeManager, Type
ui/utils/: AppHaptics, CypherWindowSize
ui/wrapped/: WrappedScreen, WrappedSlides
update/: AppVersion, GitHubUpdateClient, InstalledBuild, UpdateAssetSelector,
  UpdateInstaller, UpdatePreferences, UpdateViewModel
utils/: DiagnosticsLog, QrCodeGenerator
wrapped/: WrappedAlarmReceiver, WrappedAvailability, WrappedScheduler
```

### Kotlin de test (`app/src/test/java/com/cypher/music/`, 24 ficheros)

```
data/: DownloadStatsTest, HexColorTest, HsvColorTest, PlaylistNameMatchTest,
  PlaylistSyncMergeTest, ShortcutPlanTest
network/: SpotifyExportTest, SpotifyImportTest, TitleCleanerTest
network/innertube/: InnerTubePageParserTest, InnerTubePagerTest, LiveInnerTubePaginationTest
playback/: PlayCountTrackerTest, PlaybackOutputPolicyTest, ResumePolicyTest,
  SleepFadeTest, StreamResiliencePolicyTest, TrackRowPlaybackTest,
  TrackTransitionGuardTest
update/: AppVersionTest, InstalledBuildTest, UpdateParsingTest
wrapped/: WrappedAvailabilityTest, WrappedSchedulePolicyTest
```

### Java (`app/src/main/java/com/cypher/music/data/room/`, 16 ficheros)

```
Converters, CypherDatabase, TrackDao, TrackEntity,
PlaylistDao, PlaylistEntity, PlaylistTrackCrossRef, PlaylistWithTracks,
RecentlyPlayedDao, RecentlyPlayedEntity,
PlaybackAnalyticsDao, PlaybackAnalyticsEntity,
PlaybackDailyDao, PlaybackDailyEntity,
SearchQueryDao, SearchQueryEntity
```

---

## 13. Limitaciones de este documento

- Se ha redactado leyendo el código y los informes del repositorio, **sin ejecutar** un recorrido funcional completo en esta sesión. Lo verificado en dispositivo está en `docs/feature-ajustes-atajos-sueno.md` y en los informes de auditoría; aquí se resume, no se amplía.
- El estado del árbol (ficheros sucios, versión, APK y hashes) corresponde a la revisión del 11 de noviembre de 2026 y cambia con el trabajo en curso.
- Los ficheros de configuración privada (`keystore.properties`, `local.properties`) no se leen por contener credenciales.

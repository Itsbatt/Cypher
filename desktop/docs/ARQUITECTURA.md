# Cypher Desktop — arquitectura

## Decisión tecnológica

**Kotlin + Compose Multiplatform (Compose Desktop), JVM 17, objetivo Windows.**

Se evaluaron cuatro opciones:

| Opción | Reutilización de Cypher Android | Distribución Windows | Riesgo |
| --- | --- | --- | --- |
| **Compose Desktop** | Alta: mismo lenguaje, mismos modelos, misma paleta y tipografía, `Theme.kt` se traduce casi literalmente | `.exe`/`.msi` con `packageMsi`, sin runtime aparte (jpackage) | Medio: pesa ~120 MB por el JRE embebido |
| Tauri (Rust + web) | Ninguna: habría que reescribir UI y lógica en TS/Rust | Excelente (instalador pequeño) | Alto: dos lenguajes nuevos y WebView2 como dependencia |
| Flutter | Baja: reescritura total en Dart | Buena | Alto: sin reutilización real, ecosistema de audio limitado |
| Electron | Ninguna | Aceptable | Alto: 150 MB+, dos procesos, audio vía web |

Ganó Compose Desktop porque el objetivo del proyecto es que Desktop **sea** Cypher y no una
imitación: la paleta, la tipografía y los modelos de sincronización ya existen en Kotlin y
se portan sin traducción. El coste (JRE embebido) es aceptable para una app de escritorio
local-first y no añade dependencias de sistema.

Consecuencia práctica: el mismo código corre en macOS y Linux cambiando solo los
clasificadores de JavaFX y el formato de paquete; no hay código específico de Windows
salvo el nombre del paquete nativo.

## Estructura

```
desktop/
  build.gradle.kts            Compose 1.7.3, Kotlin 2.0.21, serialización, jaudiotagger, JavaFX media
  src/main/kotlin/com/cypher/desktop/
    Main.kt                   ventana 1320×860, tema con acento del usuario
    core/
      Models.kt               Track, CloudTrack, Playlist, DailyStat, Profile, Preferences, LibraryData
      Storage.kt              JSON en %APPDATA%/Cypher Desktop/library.json, escritura atómica
      Scanner.kt              lectura de etiquetas y duración, ids estables por ruta
      SyncMerge.kt            LWW + tombstones + unión de pistas + needsPush + política de reintentos
    player/AudioPlayer.kt     JavaFX Media, estado observable, fin de pista y errores explícitos
    sync/
      SyncClient.kt           pull/push HTTP, bearer = ID token, 401/5xx/red clasificados
      GoogleAuth.kt           PKCE + loopback, validación de audiencia/caducidad, sin secretos
      SyncCodec.kt            traducción local ⇄ remoto con los payloads exactos de Android
      SyncService.kt          pull completo → merge → push solo del outbox → limpiar lo aceptado
    app/AppState.kt           biblioteca, cola, playlists, favoritos, sesión y estados de sync
    SelfTest.kt               `--self-test`: audio real en el runtime empaquetado
    ui/
      Theme.kt                colores y escala tipográfica de Android
      Components.kt           filas, portadas, tarjetas, píldoras y estados vacíos
      App.kt                  shell: barra lateral, búsqueda, barra de reproducción y cola
      LibraryViews.kt         inicio, biblioteca, álbumes/artistas, favoritos, playlists, estadísticas
      SettingsAccount.kt      ajustes y cuenta con estados de sesión y sincronización
  src/test/kotlin/...         pruebas de fusión, reconciliado e ids
  docs/                       esta documentación
```

## Flujo de datos

1. `AppState` es el único dueño de `LibraryData`; toda mutación llama a `Storage.save`.
2. La UI observa `StateFlow`: nunca hay estado duplicado entre pantallas.
3. Las mutaciones que el servidor necesita conocer añaden una fila al `outbox` con su
   `kind`, `entityId`, `updatedAt` y `deleted`.
4. La sincronización **primero lee** y aplica LWW; después envía solo el outbox. Un
   registro más nuevo en Android gana localmente y jamás se reenvía.

## Reglas de sincronización respetadas

- **PROFILE** (`id=profile`): `displayName`, `avatarColor`.
- **PLAYLIST**: `name`, `description`, `createdAt`, `updatedAt`, `isPrivate`, `trackIds`
  (lista separada por comas, igual que `RemoteRecordCodec` en Android).
- **FAVORITE**: `id = trackId`; el borrado viaja como tombstone.
- **STATS**: `id = "día|trackId"` con contadores acumulativos (`max` por campo).
- **PREFERENCE**: el documento de tema se lee entero y solo se reescribe `accentColor`,
  conservando las demás claves que escribió el móvil. Si nunca se recibió el documento, no
  se envía nada: Desktop no inventa un tema que podría resetear el del teléfono.
- Tombstones: `deleted=true` gana solo si es más reciente o empata en estado.
- Orden de playlists: se conserva el remoto y se añaden al final las pistas locales que
  falten, de forma que añadir en el móvil nunca pierde canciones.

## Cuenta

- Cliente OAuth de escritorio: `764777457932-goan61rtqc8g3u6t7o7if2pdm6pkkc97`.
- Flujo instalado: PKCE `S256` + `http://127.0.0.1:<puerto libre>/oauth2callback`.
- El token vive solo en memoria; cerrar sesión no toca los datos locales.
- `CYPHER_DESKTOP_CLIENT_SECRET` (variable de entorno) solo si Google exige el secreto en
  el intercambio. Nada se escribe en el repositorio.
- Endpoint: `https://mdwjcphgidagmasehtsg.supabase.co/functions/v1/sync`,
  sobreescribible con `CYPHER_SYNC_BASE_URL`.

## Persistencia local

`%APPDATA%\Cypher Desktop\library.json` con escritura `tmp` + `ATOMIC_MOVE`, y copia
`library-prescan-<epoch>.json.bak` antes de cada escaneo. Las carátulas se copian a
`artwork/`. Android usa Room; ambos documentos comparten los mismos modelos lógicos, no el
formato de archivo, y se comunican solo por el contrato de sync.

## Reproducción

JavaFX Media decodifica mp3/m4a/flac/ogg con sus propios nativos. Si el runtime no puede
cargarlo, `AudioPlayer.available` queda en `false` y la barra muestra el aviso en lugar de
simular reproducción.

## Límites conscientes de esta primera versión

- La sincronización está implementada pero **no se ha ejecutado contra el backend** desde
  Desktop: requiere un inicio de sesión Google real que no se puede completar en esta
  sesión sin la ventana interactiva y, posiblemente, el secreto del cliente.
- **YouTube Music** (`YouTubeMusic.kt`): búsqueda, URL de streaming y descarga en m4a se
  resuelven con **yt-dlp**, porque los endpoints públicos de YouTube exigen firmas y
  bloquean la reproducción anónima desde la mayoría de IPs.
  - `yt-dlp` no se empaqueta: se busca en `preferences.ytDlpPath`, `CYPHER_YTDLP`,
    `%APPDATA%\Cypher Desktop\yt-dlp.exe`, `tools/yt-dlp.exe` y el `PATH`, en ese orden.
  - La pantalla ofrece instalarlo (descarga el binario a la carpeta de datos) o elegir uno.
  - Sin `yt-dlp` no se hace ninguna petición de red: la pantalla lo explica.
  - La descarga va a la carpeta de Descargas elegida (o `~/Music`), se leen sus etiquetas
    y se importa a la biblioteca. El binario nunca se sube al repositorio.
- "Descargas" importa una carpeta local sin mover archivos.
- Los favoritos con `streamUrl` se reproducen en streaming; sin URL válida se muestra el error.
- Sin ecualizador, sin letras, sin importación directa desde Android.

# Cypher 1.0.6 — Fiabilidad de biblioteca, descargas y actualizaciones

## Actualizador

- **Detecta recompilaciones de la misma versión.** Además de `versionName`/`versionCode`,
  ahora compara el SHA-256 del APK instalado con el publicado en la release. Si la versión
  es la misma pero el APK cambió, aparece «Recompilación de 1.0.6» con el botón **Reinstalar**.
- Cada compilación incluye un `BUILD_ID` en `BuildConfig` para identificarla en soporte.

## Sincronización de YouTube Music

- **Estado persistente por playlist**: se guardan `lastSyncedAt` y `syncState`
  (`complete` / `incomplete`) junto al `sourceId`, con insignia en la playlist y botón
  **Reintentar** que reimporta solo esa lista.
- **Política híbrida explícita**: la sincronización mantiene el orden remoto y **conserva las
  canciones que añadas a mano** en la playlist; la pantalla de la playlist lo indica.
- El aviso de sincronización incompleta ya no desaparece al cerrar la app.

## Transferencia desde Spotify

- **Conecta tu cuenta con OAuth 2.0 + PKCE** desde Ajustes → «Conectar con Spotify»,
  sin compartir contraseña: la autorización ocurre en el navegador y solo se guardan
  tokens cifrados en el dispositivo.
- Tras conectar, Cypher lista **todas tus playlists y tu biblioteca de «Me gusta»**, con
  paginación completa de la API: no se corta en 50 playlists ni en 100 canciones, así que
  también funciona con listas de miles de canciones.
- Eliges qué playlists transferir y la app hace el resto: lee cada playlist de Spotify,
  busca cada canción en YouTube con el mismo motor que la sincronización de YouTube Music
  y guarda la playlist aquí. La transferencia se puede cancelar y muestra el progreso en
  tiempo real (playlist actual, canciones procesadas, barra de avance).
- Al terminar aparece un **resumen**: canciones transferidas, canciones no encontradas y
  el nombre y artista de cada fallo, con botones para **copiar** la lista al portapapeles
  o **compartirla** con otra app.
- Las playlists importadas conservan su origen `spotify:`: volver a transferirlas actualiza
  la existente, no las duplica, y se respeta la política híbrida con las canciones añadidas a mano.
- Límites de la API controlados: `429` con espera según `Retry-After`, renovación automática
  del token, reintentos ante errores 5xx y mensajes claros si la sesión caduca o se revoca.
- Sigue disponible la importación rápida **pegando el enlace** de una playlist pública, sin conectar cuenta.

### Configurar Spotify (una vez)

1. Crea una app en <https://developer.spotify.com/dashboard>.
2. Añade el redirect URI `cypher://spotify-callback`.
3. Copia el **Client ID** (no hace falta Client Secret), pégalo en la hoja de Spotify o
   define `spotifyClientId` en `local.properties` / `SPOTIFY_CLIENT_ID` al compilar.

## Descargas

- Se comprueba el **espacio libre** antes de descargar (mínimo 80 MB) y se avisa en el log.
- Las descargas interrumpidas se **reanudan al abrir la app** (cola persistente).
- **Exportar a Música** (MediaStore) desde la lista de descargas, para que los archivos
  sobrevivan a la desinstalación.

## Base de datos

- Migración **5 → 6** no destructiva (nuevas columnas `lastSyncedAt` y `syncState`).
- Eliminado `fallbackToDestructiveMigration()`: una versión futura sin migración ya no puede
  borrar playlists, descargas ni estadísticas.
- Eliminado `allowMainThreadQueries()`; las lecturas de Media3/Android Auto pasan a IO.
- `exportSchema = true` con esquemas versionados en `app/schemas`.

## Limpieza y UI

- Un único `TitleCleaner` compartido por la búsqueda HTML y el paginador InnerTube.
- Eliminados el parser de una sola página, `RemotePlaylist.tracks` y las regex duplicadas.
- Corregidos los textos cortados: «Reproducir» en Descargas y playlist, y «Ajustar →» /
  «Configurar →» en Ajustes.
- Copy de estadísticas: «Tiempo total escuchado» y «Artistas más escuchados».
- Una playlist ya no repite su descripción cuando es igual al nombre.

## Notas

- `versionCode` 8 / `versionName` 1.0.6.
- Actualización manual sobre 1.0.5 conservando datos (misma firma).
- Instalar el APK arm64 en dispositivos ARM64; el universal sigue disponible.

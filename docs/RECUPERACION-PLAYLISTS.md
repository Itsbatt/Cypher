# Recuperación de playlists tras borrar los datos (Android 1.5.0)

Fecha: 12 de noviembre de 2026. Diagnóstico y corrección **sin escribir** en Supabase y
sin tocar datos locales.

## Qué pasaba

### Las canciones

Una playlist se guarda en tres sitios:

- `playlists`: id, nombre, descripción, `coverUri`, fechas.
- `playlist_tracks_crossref`: `playlistId`, `trackId`, `position`.
- `tracks`: la fila real de la canción.

Los identificadores son **propios de la app**, no URIs:

- Archivos locales: `local_<MediaStore._ID>` (el `_ID` de MediaStore, que vive fuera de
  los datos de la app).
- YouTube: `yt_<videoId>`.

El payload de sincronización viajaba así:

```
PLAYLIST { name, description, createdAt, updatedAt, isPrivate, trackIds: "[\"local_123\", …]" }
```

Al borrar los datos y volver a iniciar sesión, el pull hacía `applyPlaylist`, que insertaba
la playlist y **las referencias**, pero **nunca las filas de `tracks`**. La relación de Room
(`PlaylistWithTracks`) usa `@Junction` sobre `tracks`, así que el JOIN no encontraba nada:
nombre sí, canciones no. Además, el pull puede ocurrir antes de que la biblioteca termine de
escanearse, y nada reconstruía las filas después.

No se descartaban las referencias: seguían en la base. Simplemente no había filas a las que
unirlas, y la interfaz mostraba una playlist vacía.

### Las carátulas

`coverUri` es una URI local (`content://…` o un archivo elegido de la galería) y el código
la excluía a propósito del payload ("una portada de galería es local"). La alternativa, el
mosaico, se calcula a partir de `artworkUrl` de las canciones… que tampoco se recuperaban.
Resultado: ninguna carátula tras el borrado.

## Qué se ha corregido

1. **Referencias con metadatos** (`PlaylistTrackCodec`): el payload de playlist añade
   `tracks`, un JSON compacto con id, título, artista, álbum, duración, artwork, tipo y
   stream. Se corta solo antes de 12 KB para no chocar con el límite de 16 KB del servidor
   (un registro rechazado no se sincroniza nunca).
2. **Se envían también las referencias huérfanas**: los ids salen del crossref, no del
   JOIN, así que una canción no desaparece del payload solo porque falte su fila.
3. **Reconstrucción al aplicar** (`SyncApplier`), en este orden:
   1. fila local existente → se conserva;
   2. `local_<id>` → se relee de MediaStore (título, artista, álbum, duración, artwork,
      stream) con `LocalTrackRestore`;
   3. metadatos del payload → se crea la fila;
   4. `yt_<videoId>` → se derivan stream y portada del propio id;
   5. sin nada de lo anterior → fila "Pista no disponible", pero **la referencia se
      mantiene** y el orden no cambia.
4. **Carátulas**: solo viaja una portada portable (`http(s)`), y al aplicar se restaura si
   el dispositivo no tiene una propia. Con las canciones recuperadas, el mosaico vuelve a
   funcionar para las playlists locales.
5. **Sin sobrescribir datos buenos**: al subir, una fila restaurada como placeholder se
   envía sin título ni artista, para no reemplazar en la nube un título correcto por
   "Pista no disponible".
6. **Desktop comparte el formato**: mismo códec (`desktop/.../PlaylistTrackCodec.kt`), misma
   reconstrucción (`CloudTrack`), mismo límite de tamaño y misma regla de portada. Desktop
   no envía rutas de Windows.

## Qué hay hoy en Supabase

Por contrato (validado en `supabase/functions/sync/validation.ts`), un registro PLAYLIST
guarda únicamente: `name`, `description`, `createdAt`, `updatedAt`, `isPrivate` y
`trackIds` (array JSON de ids). **No** guarda títulos, artistas, duraciones ni carátulas de
las canciones, y las filas escritas antes de este arreglo no tienen `tracks` ni `cover`.

Consecuencia: los nombres y el orden de las canciones sí están; los metadatos solo se
pueden reconstruir en el dispositivo que conserva los archivos (MediaStore) o cuando el id
es un `yt_<videoId>`.

No se ha leído ni escrito nada en la cuenta: no hay credenciales en esta sesión y el
diagnóstico se ha hecho sobre el código y el contrato del servidor.

## Pruebas ejecutadas

- Android: **159 pruebas, 0 fallos**, incluidas:
  - `PlaylistTrackCodecTest` (6): ida y vuelta, payload roto, presupuesto de tamaño,
    recuperación desde base vacía conservando orden, portadas portables, referencia sin
    metadatos.
  - `RestoredPlaylistTracksTest` (3): `yt_` reconstruye stream y portada; metadatos del
    payload; referencia sin datos sigue creando fila.
- Desktop: **71 pruebas, 0 fallos**, incluidas 6 nuevas: payload con metadatos, playlist
  remota reconstruida en `cloudTracks`, portada de galería no restaurada, referencia sin
  metadatos conservada, `yt_` reproducible y límite de tamaño.

## Qué falta comprobar

Hace falta **una prueba de recuperación en el móvil** con esta build:

1. Borrar datos de Cypher.
2. Iniciar sesión y esperar al pull.
3. Comprobar playlists, canciones, orden y carátulas.

No he ejecutado ninguna sincronización de escritura. La primera que se haga subirá los
payloads nuevos; los ids y el orden se conservan y los placeholders no sobrescriben títulos.

Limitación honesta: si al borrar los datos MediaStore reasignó los `_ID`, las canciones
locales aparecerán como "Pista no disponible" (la referencia y el orden se conservan, pero
sin metadatos). Las de YouTube se reconstruyen siempre porque su id lleva el video.

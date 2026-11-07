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

## Migración desde Spotify

- **Importa playlists públicas de Spotify** pegando su enlace en Ajustes → «Migrar desde Spotify».
  Cypher lee la playlist desde Spotify y busca cada canción en YouTube para que sea
  reproducible aquí; muestra cuántas coincidencias encontró antes de guardar.
- Las canciones que no aparezcan en YouTube se cuentan y no se inventan; si falta alguna,
  la playlist queda marcada como incompleta con opción de reintentar.
- La playlist importada conserva su enlace de origen: volver a importarla actualiza la
  existente en lugar de duplicarla, y respeta la política híbrida con las canciones añadidas a mano.
- Las playlists privadas o «Me gusta» requieren una sesión OAuth de Spotify y no están
  disponibles por este camino sin credenciales.

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

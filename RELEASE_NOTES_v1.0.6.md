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

- **Importa toda tu biblioteca desde un archivo, sin cuenta ni Premium.** Acepta el CSV que
  exporta Exportify, el JSON de «Descargar tus datos» de Spotify y un **ZIP con todas las
  playlists** (CSV y/o JSON) sin extraerlo a mano. Incluye playlists privadas y «Me gusta».
- Con un ZIP, Cypher detecta todas las playlists, las importa de una vez y conserva el nombre
  de cada una. Aguanta cientos de playlists y miles de canciones, ignora metadatos y archivos
  ocultos, y si un CSV está dañado, vacío o supera 8 MB lo indica con su nombre y el motivo
  **sin cancelar el resto de la importación**.
- Eliges qué playlists transferir y la app hace el resto: busca cada canción en YouTube con el
  mismo motor que la sincronización de YouTube Music y guarda la playlist aquí. La transferencia
  se puede cancelar y muestra el progreso en tiempo real (playlist actual, canciones procesadas,
  barra de avance).
- Al terminar aparece un **resumen**: canciones transferidas, canciones no encontradas y el
  nombre y artista de cada fallo, con botones para **copiar** la lista al portapapeles o
  **compartirla** con otra app.
- **Sin duplicados**: volver a importar actualiza la playlist existente y añade solo las
  canciones que falten. Si ya tienes una playlist con el mismo nombre (aunque la crearas a
  mano), se fusiona con ella conservando tus canciones, portada y descripción. Dentro de un
  ZIP, las playlists con el mismo nombre se omiten.
- Límites seguros para archivos grandes: hasta 1.000 playlists, 100.000 canciones, 8 MB por
  archivo y 2.000 entradas; al alcanzarlos se avisa y se conserva lo importado.
- Sigue disponible la importación rápida **pegando el enlace** de una playlist pública, sin
  cuenta ni archivo.
- **Conexión directa con la cuenta (opcional, solo en compilaciones con Client ID).** OAuth 2.0
  + PKCE, tokens cifrados en el dispositivo, paginación completa y control de 429/401/5xx.
  No viene activada en el APK publicado: desde febrero de 2026 Spotify exige **Premium en la
  cuenta dueña de la app** para usar la Web API en modo desarrollo y limita a 5 usuarios
  autorizados, así que la vía gratuita y universal es el archivo o el ZIP.

### Configurar Spotify (opcional)

1. Crea una app en <https://developer.spotify.com/dashboard> (requiere Premium en la cuenta).
2. Añade el redirect URI `cypher://spotify-callback`.
3. Copia el **Client ID** (no hace falta Client Secret) y defínelo con `spotifyClientId` en
   `local.properties` o `SPOTIFY_CLIENT_ID` al compilar. Sin Client ID, Ajustes muestra
   directamente «Importar ZIP o archivo» y oculta la conexión de cuenta.

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

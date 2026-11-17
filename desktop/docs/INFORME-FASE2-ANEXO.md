# Anexo — revisión visual, Wrapped y descargas

Continuación del 12 de noviembre de 2026, sobre la versión 0.2.

## 1. Revisión visual real

Para revisar sin depender de capturas manuales se añadió `--screenshots <carpeta>`: renderiza
la ventana y cada pantalla a PNG con datos sintéticos. Las imágenes están en
`docs/capturas-desktop/`.

Defectos encontrados y corregidos:

| Defecto | Causa | Corrección |
| --- | --- | --- |
| La barra de reproducción quedaba fuera de la ventana | el contenido usaba `fillMaxSize()` en una `Column` y se comía el alto | el contenido ahora usa `weight(1f)` |
| El encabezado de Playlists se solapaba con la tarjeta | un `Box(weight(1f))` sin altura dentro de una fila | fila sustituida por un texto con padding |
| Portadas de playlist y álbum se salían de la esquina redondeada | faltaba recorte en el contenedor | `clip(RoundedCornerShape(...))` en tarjetas |
| Tarjetas de álbum sin superficie, pegadas al borde | no tenían fondo ni rejilla horizontal | fondo `Card`, padding 12 dp y separación horizontal de 14 dp |
| El progreso y el volumen se dibujaban como una línea de 1 px | el `Slider` con `weight` colapsaba y el pulgar quedaba sin pista | pista dentro de un `Box` con peso y colores de pista inactiva definidos |
| El volumen se cortaba en el borde derecho | ancho fijo 110 dp sin margen final | 88 dp con padding final |

Las capturas de `docs/capturas-desktop/` son el estado posterior: ventana completa, Wrapped,
álbumes, playlists y detalle de playlist.

## 2. Wrapped

Implementado con la misma forma de datos que Android (`WrappedStats.kt`):
horas totales, reproducciones, canciones distintas, artista del año con su porcentaje del
tiempo, top 5 de canciones, la más repetida y el día con más música. Selector de año real
(solo años con datos). Se calcula de `STATS` sincronizadas o locales, sin estimaciones.

Pruebas: `WrappedTest` (7) — filtrado por año, reparto del artista, orden por tiempo,
desempate por reproducciones, día récord, caída a metadatos de la biblioteca y año vacío.

## 3. Descargas y streaming

- **Descargas locales**: se elige una carpeta (por ejemplo la de un gestor o la de YouTube
  Music), Cypher lee etiquetas y muestra los archivos que aún no están en la biblioteca,
  con tamaño y duración. Se añaden de uno en uno o todos. Nada se mueve, copia ni borra.
- **Streaming de favoritos**: un favorito que llega del móvil con `streamUrl` se marca como
  reproducible y se envía a JavaFX Media directamente; si la URL caducó, el error aparece en
  la barra de reproducción en lugar de fallar en silencio.

No se implementó descarga desde YouTube Music: requiere resolver streams con credenciales y
no es seguro hacerlo sin el backend ni sin tu autorización.

## 4. YouTube Music (búsqueda, streaming y descarga)

No es posible reproducir YouTube Music con peticiones propias: los endpoints del reproductor
exigen firmas y bloquean el acceso anónimo (probado aquí: la API pública de Piped devuelve
`LOGIN_REQUIRED` y la clave antigua de InnerTube responde `FAILED_PRECONDITION`). La vía que
funciona de verdad es **yt-dlp**, así que Cypher lo usa como motor externo.

- Pantalla nueva **YouTube Music**: buscar, **Escuchar** (streaming) y **Descargar**.
- `yt-dlp` se instala desde la propia pantalla (botón *Instalar yt-dlp*, descarga a
  `%APPDATA%\Cypher Desktop\yt-dlp.exe`) o se indica su ruta. También se acepta `CYPHER_YTDLP`.
  Sin él, la pantalla lo explica y no hace ninguna petición.
- El stream se resuelve en m4a/AAC (`bestaudio[ext=m4a]`) y JavaFX lo reproduce directamente.
- La descarga escribe en la carpeta de Descargas elegida (o `~/Music`), conserva etiquetas y
  se importa a la biblioteca. Nada se mueve ni se borra.

### Verificación real ejecutada

Autoprueba `--yt-selftest` sobre la app empaquetada, con yt-dlp instalado por la propia app:

```
yt-dlp: C:\Users\bat\AppData\Roaming\Cypher Desktop\yt-dlp.exe
resultado: Milo J - Bajo De La Piel · MILO J · 210000 ms · 9GCyD6HexgM
stream: https://rr3---sn-...googlevideo.com/videoplayback
descargado: Milo J - Bajo De La Piel (Official Video) [9GCyD6HexgM].m4a (3389048 bytes)
importable: durationMs=209000
exit=0
```

Además: `--yt-install-test` → exit 0 (descarga y detección del binario), y 6 pruebas nuevas
(`YouTubeMusicTest`) para el parseo de resultados, la caída a valores vacíos y la resolución
del binario. Total: **64 pruebas, 0 fallos**.

### Límites conocidos

- YouTube cambia a menudo; si una versión de yt-dlp deja de funcionar, el error se muestra en
  la pantalla y basta actualizar el binario.
- Es un cliente no oficial: úsalo con contenido que tengas derecho a descargar.
- La descarga de una pista tarda lo que tarde la red; no hay cola en paralelo (una cada vez).

## 5. Playlists de YouTube Music y pulido de la interfaz

- La pantalla acepta el **enlace de una playlist o álbum** y la carga con `--flat-playlist`
  (sin resolver los 182 streams de golpe). El botón **Reproducir todo** la escucha entera.
- **Cola de streaming**: la lista visible es la cola; al terminar una canción se resuelve la
  siguiente, respetando repetición (apagada/toda/una). Una canción de la biblioteca limpia la
  cola online, para no mezclar dos fuentes sin avisar.
- **Pulido visual** (comparado con Android en las capturas de `capturas-desktop/`):
  - Cabecera con degradado y botón principal en YouTube Music.
  - Filas con **miniatura real** (carga en segundo plano, caché por sesión), hover, canción
    activa resaltada y botón circular de reproducción.
  - Inicio: estante horizontal de playlists con portada compuesta, y acceso directo a
    YouTube Music junto a «Reproducir todo».
  - Biblioteca: reproducción con un clic al pasar el ratón por la portada de un álbum.
  - La portada remota también se usa en la barra de reproducción (ya no queda vacía al
    escuchar un stream).
- Verificación real: `--yt-playlist` sobre la app empaquetada → `Popular Music Videos`,
  **182 canciones**, exit 0. Pruebas: **65**, 0 fallos.

## 6. Guardar una playlist de YouTube Music como playlist local

- Con una lista cargada, el botón **Guardar en Cypher** crea la playlist local y va
  descargando canción por canción, en orden, mostrando `Guardando 3/24` y el título actual.
  Se puede **Detener** en cualquier momento: lo descargado queda añadido.
- Cada archivo se importa con el título y artista de la búsqueda (más fiables que las
  etiquetas del vídeo), se asocia a la playlist y conserva su miniatura como portada.
- La playlist guarda una **portada propia** (`Playlist.artwork`), que se queda en el equipo:
  el payload de sincronización con Android no tiene ese campo, y al recibir una playlist
  remota se conserva la portada local.
- Bug real encontrado y corregido en la prueba: yt-dlp imprime rutas con acentos y el proceso
  leía la salida con la codificación de Windows, así que las canciones con títulos no ASCII
  se daban por fallidas. Ahora se lee en UTF-8 y el archivo se localiza por su id en la carpeta.

### Verificación real (`--yt-save`, biblioteca temporal)

```
lista: 'Popular Music Videos' · 182 canciones
playlist local: 'Popular Music Videos' · 3 canciones · portada=https://i.ytimg.com/vi/fOT0BUpITw8/...
  BELLAKEO (Video Oficial) - Peso Pluma, Anitta · Peso Pluma · 3794186 bytes
  Arcángel - FN8 ( Video Lyric ) · Arcangel · 10506244 bytes
  Becky G - POR EL CONTRARIO · Becky G · 3806137 bytes
archivos presentes: true
estado: Playlist guardada: 3 canciones en «Popular Music Videos»
exit=0
```

La prueba usa una carpeta de datos temporal: no toca la biblioteca real.

## 7. Estado tras el anexo

- Pruebas: **58** (51 anteriores + 7 de Wrapped), 0 fallos.
- Compilación de Windows regenerada y verificada.
- Login real y sincronización contra el backend siguen pendientes de tu intervención, igual
  que en el informe principal.

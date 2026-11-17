# Auditoría del sistema de portadas — Cypher Desktop

Fecha: 16 de noviembre de 2026 · Estado: causa raíz corregida en el código, verificación con
biblioteca ficticia **pendiente** (§7).

## 1. Cómo funcionaba

Cada pantalla llamaba a `rememberArtwork(source)` (`ui/Components.kt`). Esa función:

1. Miraba un `ConcurrentHashMap` en memoria.
2. Si no estaba, abría la URL o el archivo y decodificaba con Skia.
3. Guardaba el resultado en el mismo mapa y nada más.

## 2. Causas encontradas

| # | Síntoma | Causa | Efecto |
| --- | --- | --- | --- |
| C1 | Las portadas desaparecen al reiniciar | La caché era un mapa en RAM | Cada arranque volvía a descargar todo |
| C2 | Descargas repetidas al navegar | Mapa por proceso, sin copia en disco; un fallo no se recordaba | Volvía a pedir la misma URL en cada pantalla y en cada reintento |
| C3 | Huecos vacíos y “imagen rota” | `?.takeIf { isNotBlank() }` y `getOrNull()`: fallo, 404 y “no hay portada” terminaban en el mismo `null` | Ninguna pantalla podía distinguir cargando de no encontrada |
| C4 | Placeholder que parecía portada real | El degradado de `coverBrush` se pintaba también mientras cargaba | Se veía una portada inventada durante un segundo |
| C5 | Portadas distintas para lo mismo | Cada pantalla resolvía `artwork` o `artworkUrl` por su cuenta, en órdenes distintos | La misma canción podía mostrar dos imágenes |
| C6 | Playlists sin portada | `playlist.artwork` nulo → degradado sin relación con sus canciones | Listas reales con aspecto de vacías |
| C7 | URL caducada bloquea la imagen | Sin política de reintento ni de conservación | Al morir la URL se perdía la última copia válida |

## 3. Prioridad de obtención (implementada)

1. **Archivo local** del usuario (`artwork` que apunta a disco). Es suyo: nunca se sustituye.
2. **Portada de los metadatos de importación** (`artwork` incrustado en el archivo o entregado por
   el importador). Se guarda junto a la pista y se trata como local.
3. **URL del proveedor** autorizado (`artworkUrl`, YouTube Music / metadatos). Solo http(s) que el
   servicio ya entregó: no hay scraping ni descarga de audio.
4. **Placeholder** cuando no hay imagen verificable. Nunca se asigna la portada de otro álbum.

## 4. Caché persistente (`core/ArtworkStore.kt`)

- Directorio `artwork-cache/` dentro de la carpeta de datos (`CYPHER_DATA_DIR` respeta esto).
- Nombre de archivo = SHA-1 de la fuente → **sin duplicados** aunque la misma portada llegue por
  dos rutas.
- Escritura **atómica** (`.tmp` + `Files.move`): una descarga interrumpida no deja un archivo a
  medio escribir que luego se lea como corrupto.
- Límite de 12 MB y *timeouts* de 8 s / 12 s.
- Un archivo que no decodifica se **borra** y se informa como `CORRUPT`.
- Si la URL falla pero hay copia en disco, **la copia local gana**: la portada sigue disponible sin
  conexión.
- Un `Mutex` por fuente: dos pantallas que piden la misma portada producen **una** descarga.

## 5. Estados

`ArtworkStore.Status`: `LOADING`, `AVAILABLE`, `MISSING`, `CORRUPT`, `OFFLINE`.

`Artwork()` pinta la imagen real solo en `AVAILABLE`; en el resto usa el placeholder de Cypher
(degradado estable por id + iniciales), que se distingue de una foto. No hay icono de imagen rota
ni espacio vacío.

## 6. Portadas de playlist

`PlaylistArtwork(data, playlist, size, accent)`:

1. `playlist.artwork` si existe (portada propia).
2. Si no, mosaico 2×2 con las portadas de sus primeras canciones (sin repetir).
3. Si no hay ninguna, placeholder con las iniciales del nombre.

## 7. Qué está probado y qué no

**Probado:**
- `./gradlew test` en verde con el nuevo módulo (compilación, decodificación, placeholder).
- Las capturas de `docs/fase-c/capturas-desktop/` usan una biblioteca **ficticia** generada por
  `ScreenshotTool` (4 playlists, 2 de ellas sin portada propia) y muestran la app real.

**No probado todavía (declarado, no afirmado):**
- Reinicio con caché ya poblada, modo sin conexión, imagen corrupta y URL caída. El código las
  maneja en `resolve()`, pero no hay un test automatizado con esos cuatro casos.
- Comparación contra una biblioteca real de YouTube Music, porque requiere red y sesión.

## 8. Siguiente paso sugerido

Un test de instrumentación de `ArtworkStore` con cuatro fixtures en `build/tmp/`:
PNG válido, archivo truncado, URL inexistente y URL servida por un `HttpServer` local, comprobando
los estados, que la segunda lectura no toca la red y que el reinicio conserva la imagen.

# Cypher v1.0.5 — Descargas que suenan y listas limpias 🎧

Versión de estabilización: corrige la reproducción de las canciones descargadas
desde Cypher, el indicador Wave que aparecía en todas las filas y la importación
de playlists de YouTube Music, que se detenía en las primeras 100 canciones.

> **APK actualizados:** esta release conserva `versionName 1.0.5` y `versionCode 7`,
> pero sus APK incluyen la corrección de sincronización. Instálalos manualmente
> sobre la app existente: al no cambiar la versión, el actualizador interno no
> mostrará un aviso.

## 🔁 Playlists completas, sin límite de 100

**Causa:** la importación hacía una única petición `browse` a YouTube Music y no
seguía el token de continuación, así que solo entraba la primera página
(~100 pistas) de cada playlist.

- La obtención recorre todas las páginas con `continuation` hasta que la API deja
  de devolver token: sin límite fijo de canciones ni de peticiones.
- Cada página se reintenta antes de dar la sincronización por interrumpida; una
  playlist nunca se marca como completa si falta una página.
- Se conserva el orden original, se eliminan duplicados entre páginas y todo se
  guarda en una única transacción de Room.
- La biblioteca remota también se pagina: listas de playlists y «Tus Me Gusta».
- El progreso muestra canciones procesadas sobre el total real (`200 / 420`).
- Re-sincronizar actualiza la misma playlist (identidad `sourceId`) sin duplicar,
  también para las playlists creadas por versiones anteriores.
- Migración Room 4→5 no destructiva: conserva playlists, favoritos y ajustes.

**Comprobado:** recorrido real de una playlist pública de 200 pistas (2 páginas,
0 duplicados); pruebas con 50/100/150/420/1.000 pistas; migración aplicada en el
dispositivo con la playlist existente intacta.

## 🎵 Las descargas vuelven a sonar

**Causa:** al reproducir streaming, Cypher usa un portador silencioso en
ExoPlayer y baja su volumen a 0. Ese volumen solo se restauraba para pistas
`LOCAL`; una descarga conserva el tipo `YOUTUBE` aunque se reproduzca desde el
archivo local, así que se quedaba muda mientras la interfaz avanzaba.

- El volumen de ExoPlayer se restaura al entrar en una pista local u offline.
- Los cambios de volumen, el fade-in y el temporizador ahora también se aplican
  a las descargas (antes solo al IFrame).
- La decisión de motor es una política pura (`PlaybackOutputPolicy`) con pruebas.

**Comprobado en dispositivo:** descarga real con yt-dlp (M4A válido, 2,8 MB),
`Playing 'OJALA' via ExoPlayer (offlineVault=true)`, decodificador y pista de
audio activos, pausa/reanudación y salto de posición funcionando.

## 📊 Wave solo en la canción activa

- Se eliminó una rama que dibujaba las barras animadas en **cualquier** pista con
  duración; ahora las filas sin reproducción muestran su duración.
- El estado sale del reproductor real, no solo del ID seleccionado: al pausar, la
  pista sigue resaltada pero sin animación.
- Aplica a búsqueda, biblioteca, playlists y cualquier lista que use la fila.

## 🛡️ Descargas más seguras

- Se valida la firma del contenedor (M4A/MP3/FLAC/OGG/WAV/WebM/AAC) antes de dar
  una pista por descargada o reproducible, para no aceptar HTML/JSON con
  extensión de audio.
- Las descargas HTTP directas rechazan respuestas que no sean de audio.
- Borrar una descarga contempla también FLAC y WAV.

## 📦 Descargas

| Archivo | Tamaño | SHA-256 |
|---|---|---|
| `Cypher-v1.0.5-arm64-v8a-release.apk` | 60.519.071 B | `3ff685b87c038c7ac741648bc8f9e70b676f8f5fcb7f7d393aa7354289de99cb` |
| `Cypher-v1.0.5-universal-release.apk` | 104.018.503 B | `accc1a3f422b0e7541928645e648c733a8b73b812979385b18455aea39431fe8` |

Firmados con la clave original (`CN=Cypher`), instalables sobre v1.0.4 sin
perder playlists, favoritos, descargas ni ajustes.

## ✅ Pruebas

- Suites JVM: 15 archivos, 68 pruebas, 0 fallos (la prueba real de red es opt-in).
- Instalación sobre 1.0.4 (versionCode 6) conservando datos.
- Migración Room 4→5 aplicada en dispositivo: la playlist existente sigue intacta.
- Prueba real contra la API: playlist pública de 200 pistas recorrida en 2 páginas.
- Pruebas en dispositivo CPH2579: descarga, reproducción offline, pausa,
  reanudación, búsqueda, listas, mini-reproductor y segundo plano.

## ⚠️ Limitaciones conocidas

- La verificación de audio se hizo con el decodificador y la pista de audio
  activos, no con una grabación del altavoz.
- El APK universal sigue por encima de 99 MiB.
- El workflow de CI permanece en `ci/` hasta disponer de un token con scope
  `workflow`.

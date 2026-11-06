# Cypher v1.0.5 — Descargas que suenan y listas limpias 🎧

Versión de estabilización: corrige la reproducción de las canciones descargadas
desde Cypher y el indicador Wave que aparecía en todas las filas.

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
| `Cypher-v1.0.5-arm64-v8a-release.apk` | 60.504.291 B | `cea6f89d7309529d616c43f01612851de9d120d8d0697908dbbc76d50dd4c744` |
| `Cypher-v1.0.5-universal-release.apk` | 104.003.723 B | `8fa12d97044fa3169a3ebcabd113892a4770466cc3663f9e70f6e38ccdd67c39` |

Firmados con la clave original (`CN=Cypher`), instalables sobre v1.0.4 sin
perder playlists, favoritos, descargas ni ajustes.

## ✅ Pruebas

- Suites JVM: 12 archivos, 51 pruebas, 0 fallos.
- Instalación sobre 1.0.4 (versionCode 6) conservando datos.
- Pruebas en dispositivo CPH2579: descarga, reproducción offline, pausa,
  reanudación, búsqueda, listas, mini-reproductor y segundo plano.

## ⚠️ Limitaciones conocidas

- La verificación de audio se hizo con el decodificador y la pista de audio
  activos, no con una grabación del altavoz.
- El APK universal sigue por encima de 99 MiB.
- El workflow de CI permanece en `ci/` hasta disponer de un token con scope
  `workflow`.

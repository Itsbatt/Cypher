# Cypher v1.0.5 — Reproducción más resistente y estadísticas honestas 🎧

**En una frase:** esta versión evita cortes cuando YouTube caduca una URL, elimina las pausas fantasma al cambiar de canción, respeta tu pausa si la app tarda en cargar y define con reglas claras cuándo cuenta una reproducción.

**Versión:** `1.0.5` (`versionCode 6`) · Android 13+ (API 33-36)

---

## ✨ Qué cambia para ti

- **Menos cortes al reproducir.** Si la URL de streaming ha caducado, la app la renueva sola un par de veces antes de pasar al reproductor oficial de YouTube. La precarga descarta URLs viejas en lugar de reutilizarlas.
- **Adiós a las pausas fantasma.** Antes se ignoraban las pausas durante 3 segundos fijos al cambiar de pista; ahora se compara el vídeo real que está cargando el reproductor, así que una pausa tuya nunca se pierde y una pausa del vídeo anterior no detiene la música.
- **El watchdog ya no te quita la pausa.** Si pausas mientras una canción carga lenta, la app no la reanuda por su cuenta a los 4 segundos.
- **Conteo de reproducciones claro.** Una reproducción cuenta cuando has escuchado 30 segundos, o la mitad de una canción corta. Saltar en el primer segundo no cuenta; pausar y reanudar no duplica; repetir una canción sí suma otra reproducción.
- **Wrapped más fiable.** La ventana de diciembre y el recordatorio del 31 a las 20:00 se prueban con un reloj fijo, y la alarma solo se programa como exacta si el sistema lo permite (con plan B si no).
- **Integración continua.** Cada cambio ejecuta los tests y compila el APK; cada release recibe su `SHA256SUMS.txt` automáticamente.

---

## 📦 Descargas

| Archivo | Arquitectura | Tamaño | SHA-256 |
|---|---|---|---|
| **`Cypher-v1.0.5-arm64-v8a-release.apk`** | Solo 64 bits (recomendado) | 60,5 MB | `8edac2eedd815827237ed5f7d9d97a85bc9b0b19075536ba93c9473cc9e61410` |
| **`Cypher-v1.0.5-universal-release.apk`** | Universal (`arm64-v8a` + `armeabi-v7a`) | 104,0 MB | `39d2cb05612376ebdcd7f67285c842d1bd38d86954210b844c4e51f78ec048d2` |

Misma firma que 1.0.0–1.0.4: se instala encima **sin desinstalar** y conserva playlists y estadísticas.

---

<details>
<summary>👨‍💻 Para desarrolladores / Under the hood</summary>

- **Políticas puras y testeadas:** `StreamUrlPolicy` (frescura de 2 h, máximo 2 intentos, backoff lineal), `TrackTransitionGuard` (compara `loadedYouTubeVideoId` con `requestedYouTubeVideoId` vía `onVideoId`), `ResumePolicy` (el watchdog no fuerza play si el usuario pausó) y `PlayCountTracker` (30 s o 50 % de la duración, una vez por sesión).
- **Telemetría por pista:** `StreamingTelemetry.recordTrackResolution(videoId, tier)` guarda qué nivel (yt-dlp android/ios/tv, tv_embedded, InnerTube TVHTML5) resolvió cada pista, además de contadores agregados consultables con `tierUsageSnapshot()`.
- **Reintento 403:** el handler de ExoPlayer reintenta la resolución con backoff acotado, registra el tier que funcionó y solo después cae al IFrame oficial.
- **Wrapped:** `WrappedScheduler.plan(now, recapYear, canScheduleExact)` devuelve un plan puro (exacto, inexacto o ya pasado); `WrappedAvailability` acepta un `Clock` inyectado.
- **Tests:** 35 pruebas JVM (21 nuevas), 0 fallos.
- **CI:** `.github/workflows/android.yml` ejecuta tests y `assembleRelease`, sube los APK como artefacto y, en tags `v*`, calcula y adjunta `SHA256SUMS.txt` a la release.

</details>

---

<details>
<summary>🔐 Seguridad y verificación de la firma</summary>

- Clave de release del proyecto: `CN=Cypher, OU=Mobile, O=Cypher, L=Madrid, C=ES`.
- Huella SHA-256 del certificado: `54:41:5F:F1:75:1B:71:D0:D1:54:30:B9:0C:44:CF:04:B5:8F:46:52:F9:C6:83:8D:15:78:E9:29:2B:C1:77:20`.
- APK verificada con `apksigner`; coincide con las instalaciones 1.0.0–1.0.4.
- Se mantienen el límite de 300 MB del actualizador y la comprobación de firma antes de instalar.

</details>

---

## ⚠️ Limitaciones conocidas

- La copia de seguridad sigue exportando solo playlists y favoritos.
- El streaming depende de YouTube y de yt-dlp; esta versión mejora la recuperación, pero no puede garantizar que un vídeo siga disponible.
- El cálculo de ReplayGain usa el ajuste fijo de 600 mB; leer etiquetas por pista queda para la siguiente entrega.

## 🚫 Fuera de esta release

Estas tareas del bloque de funciones no entran en 1.0.5 y quedan para la siguiente:

- **Persistencia de la cola entre reinicios** y **exportar/importar M3U**: requieren rediseñar el guardado de la cola y una superficie nueva en la interfaz; se prefirió no mezclarlo con los arreglos de reproducción en la misma entrega.
- **Edición de etiquetas de archivos locales** (título, artista, álbum, carátula): escribir metadatos exige una librería de tagging nueva y permisos de escritura sobre SAF; necesita su propia justificación de dependencia y pruebas en dispositivo.
- **ReplayGain nativo por archivo**: `MediaMetadataRetriever` no expone esas etiquetas, así que hay que parsear ID3/Vorbis aparte. Se deja para evaluar junto a la edición de etiquetas.
- **Crossfade configurable**: ya existía y sigue igual (desactivado por defecto, 2–8 s en Ajustes); no se ha tocado en esta versión.

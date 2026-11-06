# Cypher v1.0.4 — Reproducción resistente y colores a tu medida 🎨🎧

**En una frase:** esta versión evita cortes cuando YouTube caduca una URL, elimina las pausas fantasma, hace que el conteo de reproducciones sea predecible y estrena un selector HEX para el fondo, el color distintivo y las tarjetas.

**Versión:** `1.0.4` (`versionCode 6`) · Android 13+ (API 33-36)

> Se mantiene el nombre 1.0.4 y se sube `versionCode` a 6 para que el actualizador pueda ofrecer esta build sobre la 1.0.4 anterior (código 5) sin desinstalar.

---

## ✨ Qué cambia para ti

- **Colores 100 % personalizados.** En Ajustes puedes escribir cualquier valor `#RRGGBB` (o `#AARRGGBB` con transparencia) para el **color distintivo**, el **fondo** y las **tarjetas**, con vista previa antes de aplicar. Ya no estás limitado a las paletas.
- **Menos cortes al reproducir.** Si la URL de streaming caducó, la app la renueva un par de veces antes de pasar al reproductor oficial; la precarga descarta URLs viejas.
- **Adiós a las pausas fantasma.** Se compara el vídeo que el reproductor tiene cargado con el que se pidió, en lugar de ignorar pausas durante 3 segundos fijos.
- **El watchdog respeta tu pausa.** Si pausas mientras carga, no se reanuda sola a los 4 segundos.
- **Reproducciones con reglas claras.** Cuenta al escuchar 30 s (o la mitad de una canción corta). Saltar en el primer segundo no cuenta; pausar y reanudar no duplica; repetir sí suma.
- **Wrapped más fiable.** Ventana estacional y recordatorio del 31 a las 20:00 con reloj inyectado y alarma exacta solo si el sistema lo permite.
- **CI.** Cada push ejecuta los tests y compila; cada tag recibe su `SHA256SUMS.txt`.

---

## 📦 Descargas

| Archivo | Arquitectura | Tamaño | SHA-256 |
|---|---|---|---|
| **`Cypher-v1.0.4-arm64-v8a-release.apk`** | Solo 64 bits (recomendado) | 60,5 MB | `e2afafa82e3cae7ea455aff2cf84fa0711684e5b947fcdb02cdf94ddd3528fcd` |
| **`Cypher-v1.0.4-universal-release.apk`** | Universal (`arm64-v8a` + `armeabi-v7a`) | 104,0 MB | `7f76b80ff532322929fdf4ed25bf3023b4de535e20b4863eef88fab7ac521db0` |

Misma firma que 1.0.0–1.0.4: se instala encima sin desinstalar y conserva playlists y estadísticas.

---

<details>
<summary>👨‍💻 Para desarrolladores / Under the hood</summary>

- **Colores:** `HexColor.parse` acepta 6 u 8 dígitos y devuelve ARGB; `HexColorPickerDialog` valida en vivo y solo aplica al confirmar. Los tres valores se guardan en las preferencias existentes (`accentColor`, `customBackground`, `customCard`), sin almacenamiento nuevo.
- **Políticas puras:** `StreamUrlPolicy` (frescura 2 h, 2 intentos, backoff lineal), `TrackTransitionGuard` (`loadedYouTubeVideoId` vs `requestedYouTubeVideoId`), `ResumePolicy` y `PlayCountTracker` (30 s o 50 %, una vez por sesión).
- **Telemetría:** `StreamingTelemetry.recordTrackResolution(videoId, tier)` registra el nivel que resolvió cada pista y contadores agregados.
- **Wrapped:** `WrappedScheduler.plan(now, recapYear, canScheduleExact)` es puro; `WrappedAvailability` acepta `Clock`.
- **Tests:** 39 pruebas JVM, 0 fallos.
- **CI:** `.github/workflows/android.yml` con tests, `assembleRelease`, artefacto y `SHA256SUMS.txt` en tags.

</details>

---

<details>
<summary>🔐 Seguridad y verificación de la firma</summary>

- Clave: `CN=Cypher, OU=Mobile, O=Cypher, L=Madrid, C=ES`.
- SHA-256 del certificado: `54:41:5F:F1:75:1B:71:D0:D1:54:30:B9:0C:44:CF:04:B5:8F:46:52:F9:C6:83:8D:15:78:E9:29:2B:C1:77:20`.
- Verificado con `apksigner`; se mantienen el límite de 300 MB y la comprobación de firma del actualizador.

</details>

---

## ⚠️ Limitaciones conocidas

- La copia de seguridad sigue exportando solo playlists y favoritos.
- El color de texto no es configurable; se calcula a partir del fondo para mantener contraste.
- ReplayGain usa el ajuste fijo de 600 mB; leer etiquetas por pista queda pendiente.

## 🚫 Fuera de esta release

- **Persistencia de la cola** y **exportar/importar M3U**: requieren rediseñar el guardado de la cola y una superficie nueva de interfaz.
- **Edición de etiquetas locales**: exige una librería de tagging nueva y permisos SAF.
- **ReplayGain nativo**: `MediaMetadataRetriever` no expone esas etiquetas.
- **Crossfade**: ya existía (0 por defecto, 2–8 s) y no se ha tocado.

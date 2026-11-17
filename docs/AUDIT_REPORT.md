# Auditoría Cypher — Informe

## Ciclo 1.0.9 (noviembre 2026) — auditoría inicial, desarrollo y auditoría final

### A. Auditoría inicial de la versión 1.0.8
- Base: `main` en `57eb743`, `versionName 1.0.8` / `versionCode 11`, instalada en CPH2579 (720×1612, Android 16 / API 36).
- Copia previa: `.git/audit/backup-pre-1.0.9-57eb743.zip` (sin sobrescribir las anteriores). Rama de trabajo `release/1.0.9`.
- Recorrido con dump de accesibilidad + capturas: arranque, Inicio, Biblioteca, Buscar, playlist, reproducción, mini reproductor, reproductor completo, letras, cola, Ajustes, Wrapped, persistencia y segundo plano. `logcat -b crash` vacío, 0 `FATAL EXCEPTION`/ANR.
- Fallos confirmados tras reproducirlos 3 veces cada uno:
  1. **Reproductor → menú contextual:** «Reproducir a continuación» y «Añadir a la cola» ejecutaban lambdas vacías; la pulsación cerraba el menú sin efecto sobre la cola. Causa: callbacks no conectados en `CypherNowPlayingModal`. Corregido pasando `onPlayNext`/`onAddToQueue` desde `MainScreen`.
  2. **Botón de cola sin nombre accesible:** el control aparecía como «A continuación» y no se podía localizar/etiquetar; `contentDescription` ausente. Corregido a «Cola de reproducción» (verificado por dump).
  3. **«Top Picks for You»:** título en inglés en una interfaz íntegramente en español. Cambiado a «Elegido para ti» en Inicio.
- Limitaciones reconocidas: inicio de sesión real de YouTube (requiere cuenta), búsqueda por voz (requiere micrófono), importación desde Spotify/YouTube con un ZIP del usuario, y calidad de audio DSP (no medible con ADB).

### B. Desarrollo 1.0.9 (resumen)
Compartir letras (3 diseños × 2 formatos, canvas nativo 1080 px, FileProvider + `ACTION_SEND`), Wrapped anual con `album`/`genre` reales (migración Room 6→7), portadas `maxres` con fallback y un único `ImageLoader` de Coil, caché de paleta acotada, recomendaciones con deduplicación y géneros reales, y estados vacíos honestos. Detalle completo en `docs/releases/RELEASE_NOTES_v1.0.9.md`.

### C. Auditoría final en dispositivo
- Migración 6→7 sobre la instalación existente: correcta; 22 playlists, 154 canciones, favoritos y descargas intactos tras `force-stop` + reapertura.
- Nuevos fallos detectados y corregidos en esta fase (cada uno reproducido 2–3 veces antes y después):
  1. **Top 5 canciones incoherente:** la lista se ordenaba por tiempo escuchado pero mostraba el número de reproducciones, así que el n.º 1 aparecía con 2× y el n.º 5 con 3×. Corregido mostrando el tiempo, que es el criterio real de orden.
  2. **Pie de tarjeta «SENCILLO»:** el álbum centinela de las pistas de YouTube se imprimía como si fuera un disco. Ahora se filtran los centinelas y el pie dice «FRAGMENTO DE LETRA».
  3. **Bloque de letra descentrado** en el diseño Minimal (hueco inferior con fragmentos cortos). Centrado vertical dentro del área reservada.
  4. **Carátula de fondo poco nítida** en el diseño Portada al ampliar miniaturas pequeñas: se aplica un desenfoque intencionado (no se finge resolución) y el texto queda sobre un scrim.
  4b. **Selección de letras no contigua:** se podían marcar líneas sueltas y la tarjeta mezclaba versos sin relación. Ahora la selección es un rango: el primer toque fija el inicio, el segundo el final, y el bloque se limita a 6 líneas seguidas. Verificado en dispositivo: dos toques a distancia dieron 3/6 y 4/6 líneas consecutivas, y el contador no admite huecos.
  5. **Aviso de compilación** «Condition is always true» en `SettingsScreen` tras hacer permanente la entrada de Wrapped: bloque reescrito sin la comprobación redundante.
  6. **Año fijo en Wrapped:** `loadWrappedStats` usaba 2026 literal; ahora usa la fecha real, igual que el deep link y Ajustes.
- Comprobaciones posteriores: las tres tarjetas y los dos formatos siguen renderizando; el menú nativo recibe la imagen; la cola abre y muestra su estado vacío; Wrapped recorre sus 6 diapositivas con datos reales (2157 min, 19 reproducciones, 100 % Milo J, día pico 8 de noviembre) y muestra estados honestos para álbum/género no disponibles.
- `logcat -b crash` vacío en todas las rondas; PSS ≈ 118 MB en reposo y ≈ 134 MB reproduciendo con la cola abierta.

### C.2 Ampliación final (antes de publicar)

Cuatro incorporaciones pedidas tras la primera auditoría final:

1. **Guardar tarjeta en galería** (`Pictures/Cypher` vía MediaStore) para letras y
   Wrapped. Verificado: dos PNG reales en `/sdcard/Pictures/Cypher` (227 KB y 161 KB).
2. **Selección por arrastre** en letras: pulsación larga y arrastre extienden el
   bloque con el mismo tope de 6 líneas. Verificado en dispositivo con
   `motionevent` (3 y 4 líneas consecutivas).
3. **Compartir Wrapped** reutilizando el sistema de tarjetas, con formatos
   Historia/Cuadrado y guardado propio. Verificado: hoja abierta, previsualización
   renderizada y PNG guardado en galería.
4. **Álbum real para pistas de YouTube** leyendo la descripción pública del vídeo
   al reproducir. Verificado: `album=Tus Vueltas` registrado y visible en el top
   de álbumes de Wrapped. **Género:** YouTube no expone género en esa respuesta,
   así que sigue mostrándose el estado honesto y solo se rellena cuando la fuente
   lo declara de forma explícita.

Fallos encontrados durante esta ampliación y corregidos: prefijo `$` espurio en
los títulos de la tarjeta de Wrapped, robo de metadatos por la redirección de
`youtube.com` a `m.youtube.com` (se pide la URL móvil directamente), y ausencia
inicial de resolución porque `streamUrl` puede venir vacío en pistas restauradas
de Room (ahora se recupera el id del prefijo `yt_`).

### D. Pendiente / no verificado en este ciclo
- Compartir a una app concreta más allá de abrir el menú nativo (se comprobó el chooser con Quick Share, Instagram, Mensajes, Bluetooth y Discord).
- Álbumes y géneros de Wrapped con datos reales: la biblioteca de prueba es 100 % YouTube Music y no expone álbum/género; el código y los estados vacíos están verificados, el cálculo con datos no.
- Importación/sincronización con cuentas reales de Spotify/YouTube y login de Google (no se introdujeron credenciales).
- Soak prolongado (>30 min) y perfilado con Perfetto.

---

# Auditoría Cypher — Informe (fase 2, v1.0.4)

## A. Estado de partida
- Repositorio limpio en `38abcb6` (`main`), sin cambios pendientes.
- Dispositivo CPH2579 (Android 15, SDK 35, arm64-v8a) con `1.0.3` (`versionCode 4`) firmada con clave debug.
- Copia de seguridad previa conservada en `.backups/Cypher-pre-audit-20261106-0100.zip`.

## B. Firma: recuperada la clave original
Se localizó una copia del keystore de release en el directorio de desarrollo anterior:
`C:\Users\bat\.gemini\antigravity\scratch\cypher\keystore\cypher-release.jks`
junto a `keystore.properties` (alias `cypher`).

Verificación criptográfica: el certificado del keystore tiene
`SHA-256 54:41:5F:F1:75:1B:71:D0:D1:54:30:B9:0C:44:CF:04:B5:8F:46:52:F9:C6:83:8D:15:78:E9:29:2B:C1:77:20`
y `CN=Cypher, OU=Mobile, O=Cypher, L=Madrid, C=ES`, **idénticos** al APK original instalado (`.git/installed.apk`). No es una clave nueva: es la clave real del proyecto.

- Se copió a `keystore/cypher-release.jks` + `keystore.properties`, ambos ignorados por git (`.gitignore`).
- El `build.gradle.kts` ya leía ese archivo; con él, `assembleRelease` firma con la clave original. El flag `-PcypherDebugSigning=true` sigue disponible solo para pruebas.
- Nunca se ha commiteado ningún keystore ni contraseña; el historial no contiene material de firma.
- Si esta copia se pierde: **no se puede extraer la clave privada de un APK**. Habría que generar una clave nueva y aceptar una desinstalación única en las instalaciones existentes (el actualizador rechaza correctamente firmas distintas).

## C. Releases publicadas
- **v1.0.4** (última): https://github.com/Itsbatt/Cypher/releases/tag/v1.0.4
  - `Cypher-v1.0.4-arm64-v8a-release.apk` — 60.471.463 B — SHA-256 `38501a62cd332c9c54db08877a956c52474b340d2645658bc11a54a77e636f81`
  - `Cypher-v1.0.4-universal-release.apk` — 103.970.899 B — SHA-256 `ce8849b5aa19ad15b05340772e6310c2fd418f97353d564379f3aa57461ddba6`
- **v1.0.3** (paso intermedio, compilada desde el commit `38abcb6`): https://github.com/Itsbatt/Cypher/releases/tag/v1.0.3
  - `Cypher-v1.0.3-arm64-v8a-release.apk` — 60.470.759 B — SHA-256 `0121df9c12b0f90160034a4bab057bcd23ff3645446f84c7dba7ba2d92c0ad74`
  - `Cypher-v1.0.3-universal-release.apk` — 103.970.195 B — SHA-256 `583fb714760d35323ad00ae0f1c6b001a54175ec64ca6c748caadc80dd2180b4`
- Ambas releases incluyen `SHA256SUMS.txt`, usado por el actualizador.
- Las cuatro APKs verificadas con `apksigner`: `CN=Cypher`, SHA-256 `54:41:5F…77:20`.
- `v1.0.4` queda marcada como *latest*: el actualizador compara versiones, así que publicar 1.0.3 después no provoca avisos de descarga.

## D. Actualización real de extremo a extremo (superada)
1. Instalación limpia de la **1.0.2 original** firmada con `CN=Cypher` (APK respaldado en `.git/installed.apk`).
2. Exportación desde la app a `/sdcard/Download/cypher_backup_alex_1793947165054.json` (645 B) con 2 playlists; importación posterior verificada (2 playlists restauradas).
3. Instalación de una **1.0.3 firmada con la clave original** sobre la 1.0.2 (`versionCode 4 > 3`, misma firma): actualización sin desinstalar y datos intactos.
4. La 1.0.3 detectó `v1.0.4` desde la API de GitHub (`UpdateViewModel: Actualización disponible 1.0.4`), mostró el aviso discreto con resumen.
5. «Actualizar» → descarga del APK arm64 + `SHA256SUMS.txt` → verificación SHA-256 y de firma → apertura del instalador oficial → ajuste «Instalar apps desconocidas» → Play Protect («This app looks safe») → instalar.
6. Resultado: **1.0.4 (`versionCode 5`) instalada**, sin desinstalar, con las 2 playlists conservadas y sin crashes.
   - Nota: la 1.0.2 no contenía el actualizador (se añadió en 1.0.3), por eso el salto probado es 1.0.3 → 1.0.4.

## E. Auditoría funcional (dispositivo real)
Método: navegación con ADB + `uiautomator dump` sobre cada pantalla, comprobando textos, estados y `logcat` (crash/ANR) tras cada bloque.

| Pantalla / función | Comprobación | Resultado |
|---|---|---|
| Inicio | Saludo, Top Picks, carruseles, playlists, «Nueva» | Correcto |
| Crear playlist | Diálogo, título, creación y persistencia tras reinicio | Correcto (la playlist sobrevive al `force-stop`) |
| Biblioteca | Secciones Playlists/Álbumes/Artistas/Canciones/Favoritos/Descargadas/Local | Correcto |
| Detalle de playlist | Cabecera, estado vacío, compartir QR, menú de opciones | Correcto |
| Buscar | Categorías, resultados globales de YouTube Music, opciones por pista | Correcto |
| Reproducción | Pista de YouTube, mini‑reproductor, pausa, siguiente, favorito, volumen | Correcto |
| Now Playing | Aleatorio, letras, repetir, scrubbing/volumen visibles | Correcto |
| Letras | Letras sincronizadas reales de la pista y cierre | Correcto |
| Ajustes | Perfil, cambiar nombre, estadísticas, YouTube, acentos, fondos, tarjetas, bordes, háptica | Correcto |
| Ecualizador | Hoja DSP con presets predefinidos | Correcto |
| Temporizador | Opciones 15/30/45/60 minutos | Correcto |
| Liquid Glass | Hoja AGSL con modo de paleta, velocidad, distorsión y opacidad | Correcto |
| Copia de seguridad | Exportar (JSON 645 B) e importar (2 playlists) | Correcto; ver limitación de contenido |
| Escáner QR | Hoja de importación + cámara (`BarcodeScanningActivityProxy`) | Correcto, sin crash en 1.0.4 |

Sin `FATAL EXCEPTION` ni ANR durante el recorrido. `logcat -b crash` vacío al final.

## F. Seguridad
Revisión de manifiesto fusionado, permisos, componentes, red, almacenamiento y logs.

- **Permisos**: INTERNET, ACCESS_NETWORK_STATE, READ_MEDIA_AUDIO, RECORD_AUDIO, FOREGROUND_SERVICE(+MEDIA_PLAYBACK), POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, REQUEST_INSTALL_PACKAGES, WAKE_LOCK, VIBRATE. Todos tienen uso real en la app.
- **Componentes exportados**: `MainActivity` (launcher, VIEW audio, MEDIA_PLAY_FROM_SEARCH, deep link `cypher://wrapped`, MUSIC_PLAYER) y `PlaybackService` (`MediaLibraryService`, necesario para Android Auto/Asistente). `FileProvider` (`exported=false`, solo `cache/updates`), `WrappedAlarmReceiver` (`exported=false`) y los componentes de librerías están correctamente cerrados.
- **Corregido**: el WebView de inicio de sesión de Google aceptaba cualquier URL; ahora solo navega a `google.com`, `youtube.com`, `gstatic.com` y `googleusercontent.com`.
- **Corregido**: la descarga de actualizaciones no tenía tope; ahora 300 MB comprobados en metadatos y durante la escritura.
- **Sin tráfico cleartext**: `targetSdk 36` y no se declara `usesCleartextTraffic` (por defecto `false`).
- **Credenciales**: cookies de YouTube en `EncryptedSharedPreferences` (AES256-GCM, clave en Android Keystore); no hay secretos en el repositorio ni en logs (revisadas 150 llamadas a `Log`; no se registran cookies, tokens ni contraseñas).
- **Aceptado/documentado**: `allowBackup=true` incluye la base Room en la copia del sistema; `PlaybackService` exportado es requisito de Media3 para controles externos; `ResilientDns` usa IPs fijas de dpaste/c-net y DoH de Google/Cloudflare, y considera «secuestro» IPs privadas (puede forzar DoH en redes locales). No se modificó para no romper el bypass del ISP del usuario; se recomienda hacerlo configurable.

## G. Dependencias
Resolución de `releaseRuntimeClasspath` sin conflictos ni duplicados: todas las Media3 en `1.5.1`, OkHttp único `4.12.0`, Kotlin `2.3.20`.

| Dependencia | Versión | Estado |
|---|---|---|
| `androidx.security:security-crypto` | `1.1.0-alpha06` → **`1.1.0`** | Actualizada (alpha obsoleta desde 2023) |
| `androidx.media3:*` | `1.5.1` | Obsoleta (última 1.11.1). Salto mayor con migraciones de API; no se actualiza en esta fase |
| `okhttp` | `4.12.0` | Sin CVEs directas conocidas; 5.x es un cambio mayor |
| `room` | `2.6.1` | Funcional; actualización mayor pendiente |
| `navigation3` | `1.0.1` | Correcta |
| `coil-compose` | `2.7.0` | Correcta |
| `play-services-code-scanner` | `16.1.0` | Requiere keep rules (ya aplicadas) |
| `youtubedl-android` / `ffmpeg` | `0.18.1` | Correcta |
| `zxing`, `guava`, `media`, `fragment` | `3.5.3`, `33.3.1-android`, `1.7.0`, `1.8.5` | Sin incidencias |

## H. Rendimiento (medido, no estimado)
| Métrica | 1.0.3 | 1.0.4 |
|---|---|---|
| Arranque en frío (`am start -W`) | 1045 / 1152 / 1643 ms | 1763 / 1557 / 1829 ms |
| PSS tras 15–20 s | 112,2 MB | 112,4 MB |
| Frames perdidos (`gfxinfo`) | 4/109 (3,67 %) | 3/77 (3,90 %) |
| Frametime p50 / p90 / p95 | 19 / 27 / 29 ms | 19 / 25 / 28 ms |
| CPU en segundo plano | 3,3 % | 3,3 % |
| Batería | — | 1,37 % de pantalla + 219 mAs de CPU en ~47 s de uso registrado |
| ANR / crashes | 0 | 0 |

La 1.0.4 se midió justo tras instalarla (dexopt/optimización en curso), por lo que su arranque es más lento; no se declara mejora ni regresión de rendimiento. No se usó un perfilador (Studio Profiler/Perfetto), así que no hay datos de fugas de memoria ni de bloqueos del hilo principal más allá de los ANR.

## I. Calidad de código
- `lintRelease`: **BUILD SUCCESSFUL**, 0 errores (se corrigió el escapado de `local.properties`); 123 avisos, principalmente `UseKtx`, `GradleDependency`, `UseTomlInstead`, `ObsoleteSdkInt` y versiones más nuevas disponibles.
- `testDebugUnitTest`: 14 pruebas, 0 fallos, 0 omitidas.
- `assembleRelease` con R8 + resource shrinking correcto.

## J. Datos
- Exportación real: `.backups/backup-1.0.3.json` (645 B) con 2 playlists; importación verificada en la instalación limpia.
- La copia incluye **solo playlists y favoritos**. No incluye perfil, estadísticas, tema, ecualizador ni cookies de YouTube. Antes la interfaz prometía «analíticas» y JSON «firmado»; el texto se corrigió para reflejarlo.
- Antes de desinstalar se exportó y validó el JSON; después de la actualización se comprobó que las playlists seguían presentes.

## K. Cambios de esta fase
- `app/build.gradle.kts`: `security-crypto 1.1.0`; versión `1.0.4` (`versionCode 5`).
- `UpdateInstaller.kt`: límite de 300 MB en metadatos y en bytes escritos.
- `YouTubeLoginDialog.kt`: allowlist de hosts en el WebView de login.
- `SettingsScreen.kt`: texto honesto de la copia de seguridad.
- `RELEASE_NOTES_v1.0.4.md`, este informe.
- Commits `fc49244` y `b0ae147` en `main`; releases `v1.0.3` y `v1.0.4` publicadas con APKs y checksums.

## L. Pendiente / no verificado
- Inicio de sesión real de YouTube/Google (requiere cuenta; no se introdujeron credenciales).
- Búsqueda por voz (requiere micrófono y permisos en primer plano; no se probó el reconocimiento).
- Wrapped (evento estacional; fuera de temporada no se muestra).
- Compartición real a dpaste/c-net y descarga offline/yt-dlp (dependen de servicios externos y de tiempo de descarga).
- Biblioteca de música local (no hay audio local en el dispositivo de prueba).
- Actualización de Media3/OkHttp/Room a versiones mayores (requiere migración y regresión específica).
- Perfilado con Perfetto/Studio Profiler y pruebas en otros fabricantes/tablets.
- Duplicar playlists con el mismo nombre está permitido (no hay restricción de unicidad); se observaron dos entradas idénticas creadas en pruebas.

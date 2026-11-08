# Informe de reparación — reproducción de música (Cypher 1.2.0)

Fecha: 8 de noviembre de 2026
Autor: auditoría técnica sobre dispositivos físicos vía ADB

---

## 1. Versión inicial comprobada

- Código del repositorio: rama local `release/1.0.9`, commits `d5d382d` y `ed2111d` (1.2.0), más los cambios de este informe.
- APK instalada: `versionCode 14`, `versionName 1.2.0`, minSdk 29, targetSdk 36.
- Tableta: **1.2.0** (versionCode 14).
- Móvil Samsung SM-S901E: inicialmente **1.1.0** (versionCode 13) y después **1.2.0** (versionCode 14).

Sí, la versión examinada corresponde a la 1.2.0 en desarrollo (no publicada).

## 2. Estado inicial: qué fallaba y qué ya funcionaba

Comprobado en dispositivo, no supuesto:

- **Móvil SM-S901E (Android 16, WebView 156)**: reproducción correcta. Posición y duración reales avanzando (0:42 → 1:35 en 48 s), siguiente/anterior correctos, pausa conservando 0:36 y reanudando. Sin reinicios ni saltos. Las pistas probadas estaban **descargadas** (`offlineVault=true` en el log), es decir, se reprodujeron por ExoPlayer.
- **Tableta SM-T580 (Android 10, WebView 81)**: **el fallo persiste**. Tres pistas de streaming distintas no llegaron a sonar por el motor de la app:
  - `Traición` → `IFrame error: INVALID_PARAMETER_IN_REQUEST` inmediato → salto de pista.
  - `Mañana` → `Playback did not start in 15000ms; retrying` → `playback did not start in 30000ms` → salto.
  - `Olvidame Y Pega La Vuelta` → mismo patrón.
  - La posición y la barra se quedaron en **0:00 / −3:44** y el estado osciló solo de `Pausar` a `Reproducir`.
- **Cola, controles y descargas**: siguen funcionando (verificado en el móvil: cola con 5 siguientes, vaciar con confirmación, anterior/siguiente).

Conclusión del estado inicial: el problema **no es universal**; depende del dispositivo. En el móvil moderno no se reproduce; en la tableta con WebView antiguo sí.

## 3. Causa raíz

Dos causas encadenadas, ambas en la gestión del motor de streaming:

1. **El motor IFrame de YouTube no arranca en WebView antiguos.**
   La app reproduce el streaming con el reproductor IFrame dentro de un WebView, construido con:
   `controls(0).autoplay(1).rel(0).origin("https://www.youtube-nocookie.com").privacyEnhancedMode(true)`.
   En la tableta (AOSP WebView 81.0.4044.117, de 2020) ese reproductor responde con
   `INVALID_PARAMETER_IN_REQUEST` (error 2 de la IFrame API) o se queda cargando sin emitir `PLAYING`.
   En el móvil (WebView 156) el mismo código funciona. De ahí que el fallo aparezca solo en algunos dispositivos.

2. **La lógica de recuperación convertía un arranque lento o fallido en un reinicio audible y un salto.**
   - El supervisor de arranque (`STREAM_START_DEADLINE_MS = 15000`) consideraba «no iniciada» cualquier pista que siguiera en `isBuffering`, y al vencer ejecutaba `youTubePlayer.loadVideo(videoId, 0f)`: **volvía a empezar desde el segundo 0** aunque el audio ya estuviera sonando.
   - Si a los 30 s seguía sin `PLAYING`, llamaba a `onTrackLoadFailed()` y saltaba a la siguiente pista, donde el ciclo se repetía.
   - Resultado observable: suena unos segundos, se corta, vuelve al principio, se repite y salta. Exactamente el síntoma descrito.
   - El vigilante de estancamiento agravaba el bucle: si el reloj del IFrame no avanza (`onCurrentSecond` no llega en WebView antiguos), a los ~20 s interpretaba «congelado» y recargaba la pista.

3. **Barra y duración a 0.** La posición dependía exclusivamente de `onCurrentSecond` y la duración de `onVideoDuration`. En la tableta esos callbacks no llegan (o llegan tarde), y no existía ningún reloj alternativo: la barra quedaba a 0 aunque la app creyera estar reproduciendo.

## 4. Archivos modificados

- `app/src/main/kotlin/com/cypher/music/ui/main/MainScreen.kt`
  - Eliminados `.origin("https://www.youtube-nocookie.com")` y `.privacyEnhancedMode(true)` del `IFramePlayerOptions`. Esa combinación (nocookie + origin) es la que provoca `INVALID_PARAMETER_IN_REQUEST` en clientes WebView antiguos. Se mantienen `controls(0)`, `autoplay(1)` y `rel(0)`.
- `app/src/main/kotlin/com/cypher/music/playback/MusicPlayerManager.kt`
  - `awaitTrackStart()`: una pista con posición > 1000 ms cuenta como iniciada, aunque el IFrame nunca emita `PLAYING`.
  - Supervisor: el reintento carga la pista **en la posición real** (`_playbackPositionMs / 1000`), nunca en 0.
  - El salto por arranque fallido solo se aplica si la posición sigue en 0: si hay audio avanzando, no se corta.
  - Nuevo reloj de respaldo (`lastSecondReportAt`): si `onCurrentSecond` no llega en 1,5 s y el estado es «reproduciendo sin buffering», la posición avanza con el tiempo real transcurrido y se actualiza la barra.

No se ha tocado la ruta de reproducción local/descargada (ExoPlayer), la cola, la sesión ni la base de datos.

## 5. Solución aplicada

- Se elimina el parámetro que rompía el IFrame en WebView antiguos, con lo que el reproductor vuelve a crearse con parámetros compatibles.
- Se rompe el bucle «reinicio desde 0 → salto»: un arranque lento ya no reinicia el audio ni descarta la pista si hay posición.
- Se independiza la barra de progreso del callback del IFrame mediante un reloj basado en tiempo transcurrido, de modo que posición y porcentaje avanzan aunque el WebView no informe.

Estado de la solución: **parcial y verificada solo en parte**. En la tableta, tras estos cambios, el reproductor ya abre y muestra la duración real (3:44) en lugar de 0:00, pero el audio del IFrame sigue sin arrancar; ver apartado 10.

## 6. Dispositivos utilizados

| Dispositivo | Modelo | Android | WebView | Versión probada |
|---|---|---|---|---|
| Tableta | Samsung SM-T580 (`3300b1a22e597563`) | 10 (API 29) | 81.0.4044.117 | 1.2.0 (14), antes y después de los cambios |
| Móvil | Samsung SM-S901E (`RZCT106THDK`) | 16 | 156.0.8078.50 | 1.1.0 (13) y 1.2.0 (14) |
| Móvil secundario | OPPO CPH2579 (`LNH6FY8T6PDEZHXC`) | 15 | — | 1.2.0 solo para cola y restauración, no para este informe de audio |

## 7. Pruebas realizadas

Método: uso real por ADB (`input tap`), lectura de la interfaz con `uiautomator dump` (posición, duración, estado) y `logcat` filtrado por PID.

**Móvil SM-S901E — 1.1.0**
- Pista `Un violinista en tu tejado`: 48 s continuos, 0:42 → 1:35, resto −3:00 → −1:56, estado `Pausar`.
- `Siguiente` → `Caminando por la vida`: 0:11 → 0:32.
- `Anterior` → vuelve y progresa 0:11 → 0:32.
- `Pausar`: posición retenida en 0:36, estado `Reproducir`; al reanudar continúa.
- Log: `Playing '...' via ExoPlayer (offlineVault=true)`.

**Móvil SM-S901E — 1.2.0**
- 48 s continuos con 0:42 → 1:35 y −2:49 → −1:56; siguiente 0:11 → −4:07.

**Tableta SM-T580 — 1.2.0 (antes de los cambios)**
- `Traición` → error IFrame; sin transporte, sin audio.
- Tres intentos desde Inicio: ningún mini reproductor, `Reproducir` en todas las tarjetas.

**Tableta SM-T580 — 1.2.0 (después de los cambios)**
- El reproductor ya abre; duración correcta: 3:44 / 3:35.
- Posición: 0:00 en las cinco muestras (8, 16, 24, 32, 40 s); estado pasa de `Pausar` a `Reproducir` a los 32 s.
- Log: `IFrame error: INVALID_PARAMETER_IN_REQUEST` en `W5YUWocIOgs`; `playback did not start in 30000ms` en `ell_Zda-4Fk` y `3A_S21MyUCA`.

**No realizadas** (no afirmadas): medición acústica del audio, prueba de 10+ minutos ininterrumpidos, cambio de canción manual repetido 20 veces, archivos locales (no hay música local en la tableta), ni streaming en el móvil con pistas no descargadas.

## 8. Resultados por dispositivo (antes / después)

**SM-S901E (Android 16)**
- Antes: reproducción correcta de pistas descargadas, posición y duración correctas, siguiente/anterior y pausa correctos.
- Después: idéntico, sin regresiones.

**SM-T580 (Android 10, WebView 81)**
- Antes: ninguna pista de streaming arrancaba; error IFrame o espera de 30 s; barra a 0.
- Después: la pantalla del reproductor y la duración son correctas, pero el audio del IFrame sigue sin arrancar y la posición no avanza porque el estado cae a pausa. **El fallo de reproducción en este dispositivo NO está resuelto.**

## 9. Regresiones

- Descargas: sin cambios en su ruta; la pista descargada del móvil se reproduce correctamente (verificado).
- Cola y controles: verificado que siguen operativos (5 siguientes, quitar actual, vaciar con confirmación, siguiente/anterior, pausa).
- Restauración de sesión, biblioteca y ajustes: no se tocaron en esta reparación.
- Compilación e instalación sobre la versión previa sin perder datos: `BUILD SUCCESSFUL`, `adb install -r` → `Success` en ambos dispositivos.

## 10. Problemas pendientes

1. **Streaming en la tableta (Android 10 / WebView 81): sigue sin reproducir.** El motor IFrame no arranca con ese WebView. El siguiente paso técnico, no aplicado aún por su alcance, es reproducir el streaming con **ExoPlayer usando la URL directa** (`YouTubeRepository.getStreamUrl`, ya usada para refrescar 403) y dejar el IFrame solo como respaldo. Eso eliminaría la dependencia del WebView y daría posición/duración nativas.
2. **Streaming en el móvil con pistas no descargadas**: no verificado; las pruebas reprodujeron descargas.
3. **Recomposición excesiva**: `MainScreen` se recompone ~1.000 veces en 40 s de reproducción (log `MainScreen entered composition`). No corta el audio, pero puede penalizar en la tableta y conviene separar el estado de posición del árbol completo.
4. La reproducción de archivos locales no se pudo probar (no hay música local almacenada en la tableta).

## 11. Conclusión

- La 1.2.0 **no está lista para publicar**: en la tableta Android 10 el streaming no funciona, y ese es el escenario que el informe original describía.
- En el móvil moderno la reproducción es estable en las pruebas realizadas (posición, duración, pausa, siguiente y anterior), pero principalmente con pistas descargadas.
- Los cambios aplicados eliminan dos causas reales del síntoma (reinicio desde el segundo 0 y salto con audio en curso) y desacoplan la barra del callback del IFrame, pero **no bastan** para la tableta: allí el problema de fondo es el motor IFrame sobre WebView 81.
- Recomendación: implementar el respaldo por URL directa con ExoPlayer antes de publicar, y repetir esta batería en ambos dispositivos.

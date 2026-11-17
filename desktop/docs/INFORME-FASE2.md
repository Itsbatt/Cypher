# Cypher Desktop · Informe de la segunda fase

Fecha: 13 de noviembre de 2026.
Alcance: SMTC real de Windows, integración con la app, nueva auditoría de interfaz y
reempaquetado. **Sin commits, tags, push, releases ni publicaciones.** No se modificó
Supabase, ni secretos, ni el contrato de Android 1.5.0, ni se tocaron el S22 o la SM-T580.

Los datos reales (`%APPDATA%\Cypher Desktop`) se mantuvieron intactos: **13 archivos,
90.421.208 bytes antes y después** de instalar/desinstalar el MSI. Todas las pruebas de
reproducción usaron un perfil aislado (`%LOCALAPPDATA%\Temp\cypher-smtc-audit`) con dos
WAV generados y una copia de `library.json`; la pista real de esa copia se sustituyó por
los WAV y nunca se escribió en el perfil real.

---

## 1. Estado de SMTC

**Implementado y verificado.** Cypher publica una sesión real en los controles multimedia
de Windows. No hay simulación interna: Windows enumera `cypher-smtc.exe` como aplicación
reproductora.

Arquitectura y protocolo: `desktop/docs/INTEGRACION-WINDOWS.md`.

| Componente | Archivo |
| --- | --- |
| Puente Kotlin (proceso, protocolo, estado) | `desktop/src/main/kotlin/com/cypher/desktop/player/SmtcBridge.kt` |
| Helper .NET (MediaPlayer + SMTC) | `desktop/native/SmtcHelper.cs` |
| Binario empaquetado | `desktop/src/main/resources/native/cypher-smtc.exe` |
| Compilación reproducible | `desktop/native/build-smtc.ps1` |
| Enganche a la app | `AppState.init` (callbacks y observación), `AudioPlayer.pause/resume/stopped`, `Main` (cierre) |

## 2. Pruebas reales de SMTC desde Windows

Método: app real (primero `./gradlew run`, después el portable y el MSI instalado),
reproducción iniciada con un clic real en «Reproducir todo», y consultas/acciones desde
PowerShell con `GlobalSystemMediaTransportControlsSessionManager`.

| # | Prueba | Resultado observado |
| --- | --- | --- |
| 1 | Reproducir desde Cypher | sesión `cypher-smtc.exe`, `Playing` |
| 2 | Windows detecta la app | `sessions=1`, `app=cypher-smtc.exe` |
| 3 | Título / artista / álbum | `SMTC probe local / Cypher / Audit album` |
| 3b | Portada | `thumb=True` con PNG local |
| 4 | Pausa desde Windows | `Paused`; la app queda pausada |
| 5 | Reanudar desde Windows | `Playing` |
| 6 | Siguiente | `SMTC probe second` |
| 7 | Anterior | vuelve a `SMTC probe local` |
| 8 | Estado en Cypher | el icono de la barra pasa a pausa/reproducción y el tiempo avanza |
| 9 | Cambio de pista | título y portada cambian con la pista |
| 10 | Fin de pista | salta sola a la siguiente; al terminar la cola queda `Stopped` con metadatos |
| 11 | Cerrar Cypher | el helper termina y la sesión desaparece |
| 12 | Posición / duración | `00:00:02 / 00:00:45`, avanzando |
| 13 | Seek desde Windows | `TryChangePlaybackPositionAsync` mueve la pista y provoca el fin |

Scripts de comprobación: `desktop/native/smtc-sessions.ps1`, `smtc-controls.ps1`,
`smtc-timeline.ps1`, `smtc-end.ps1`, `shot.ps1`, `click.ps1`.

## 3. Controles multimedia

Play, pausa, siguiente y anterior funcionan desde el sistema y desde la app; el seek
también. Los controles se mantienen sincronizados porque ambas vías modifican el mismo
`AudioPlayer`/cola. Windows muestra la sesión con carátula y barra de progreso.

## 4. Inicio de sesión de Google

**No probado.** Sigue faltando el `client_secret` de la aplicación Desktop y la audiencia
Desktop en el backend; no hay sesión válida. No se pidieron ni se pegaron secretos, no se
leyó ningún archivo de credenciales y no se modificó nada en Google Cloud ni en Supabase.

## 5. YouTube Music

**No probado con cuenta real.** La ruta de reproducción usa la caché de audio y comparte
el mismo `AudioPlayer`, por lo que SMTC la cubrirá igual que una pista local (mismo
`Track` con `path` local), pero no se pudo verificar sin sesión.

## 6. Playlists reales

**No probadas**: dependen del login y de YouTube Data API v3 con `youtube.readonly`. No se
importó nada.

## 7. Pull real de Supabase

**No realizado.** Sin sesión válida no se puede distinguir un pull real de un fixture; no
se presenta ninguno como sincronización.

## 8. Comparación con Android

No realizada por el mismo motivo. El contrato de Android 1.5.0 no se modificó.

## 9. Sincronización: problemas encontrados

Ninguno nuevo. El transporte de letras sigue siendo una propuesta
(`SINCRONIZACION-LETRAS-PROPUESTA.md`): no se creó la tabla `song_lyrics` ni la ruta
`/v1/sync/lyrics`.

## 10. Letras

Modelo intacto (`LyricDoc`). En la interfaz se comprobó el panel y su estado vacío con
«Añadir letra .lrc»; no se abrió el diálogo nativo de archivos en esta fase. La
persistencia y el resaltado siguen cubiertos por los tests existentes.

## 11. Problemas funcionales encontrados

1. **SMTC mostraba «Paused» tras un error de reproducción** (archivo inexistente). Se
   corrigió: un error se publica como `Stopped`.
2. **El seek de Windows no movía la reproducción**: faltaba el evento
   `PlaybackPositionChangeRequested`. Añadido y verificado.
3. **La portada nunca aparecía**: el tipo WinRT estaba en el espacio de nombres
   equivocado y además se dependía de `System.Runtime.WindowsRuntime`. Se resolvió con
   `RandomAccessStreamReference.CreateFromFile` y un `Await` por sondeo.
4. **Helper obsoleto en caché**: se validaba por tamaño y un binario distinto del mismo
   tamaño seguía usándose. Ahora se reescribe siempre.
5. **Al terminar la cola la sesión desaparecía** en lugar de quedar detenida. Ahora
   `Stopped` conserva metadatos y solo `Closed` (salir de la app) la retira.
6. Dos clics sintéticos no produjeron efecto (engranaje de Ajustes y X de la cola); ver
   punto 15.

## 12. Correcciones realizadas

| Archivo | Cambio |
| --- | --- |
| `player/AudioPlayer.kt` | `stopped` en `PlaybackState`; `pause()` y `resume()` explícitos; `stop()` marca detenido |
| `player/SmtcBridge.kt` (nuevo) | Proceso helper, protocolo, extracción, callbacks, timeline, cierre |
| `app/AppState.kt` | Puente SMTC, callbacks a la cola real, seek, `shutdown()` |
| `Main.kt` | Cierre ordenado de la sesión al cerrar la ventana |
| `native/SmtcHelper.cs` (nuevo) | MediaPlayer WinRT sin ventana, metadatos, portada, timeline, botones, seek |
| `native/build-smtc.ps1` (nuevo) | Compilación con `csc` y copia a recursos |
| `src/main/resources/native/cypher-smtc.exe` | Helper empaquetado |
| `.github/workflows/desktop.yml` | Recompila el helper antes de los tests |
| `docs/INTEGRACION-WINDOWS.md` | Documentación real de SMTC, protocolo y límites |

## 13. Botones y acciones identificados

El inventario de la primera fase sigue vigente: **123 puntos interactivos** en
`desktop/src/main/kotlin/com/cypher/desktop/ui` (34 en `App.kt`, 18 en `LibraryViews.kt`,
17 en `ImportView.kt`, 17 en `Components.kt`, 12 en `YouTubeView.kt`, 11 en
`SettingsAccount.kt`, 11 en `HomeFeed.kt`, 3 en `DownloadsView.kt`).

## 14. Probados realmente en esta segunda pasada

- Reproducir todo, pausa/reanudar, siguiente/anterior, seek (desde la app y desde Windows).
- Cola: abrir con el icono, ver las 2 pistas y la pista actual, cierre con la X mediante el
  icono (el panel se abrió correctamente; el cierre por X no se pudo confirmar con clic
  sintético, ver punto 15).
- Aleatorio: activado; `preferences.shuffle` pasó a `true` en disco.
- Volumen: el clic en la barra guardó `0.5484375` en `preferences.volume`.
- Favoritos: pantalla abierta y estado vacío correcto.
- Búsqueda: `Ctrl+K`, consulta «SMTC», resultados con las dos pistas.
- Panel de biblioteca: apertura con el icono, cierre con clic fuera.
- Navegación: Inicio, Playlists, detalle de playlist vacía.
- SMTC completo (13 comprobaciones del punto 2).
- Cierre de la app y desaparición de la sesión; cierre de procesos.

## 15. No probados y por qué

- **Ajustes y X de la cola**: los clics sintéticos sobre esos dos controles pequeños no
  produjeron efecto en varias posiciones; el resto del rail y los botones grandes sí
  responden. No se pudo determinar si es un límite de la automatización (hover/expansión
  del rail) o un fallo de área pulsable. **Requiere una comprobación manual con ratón.**
- **Renombrar/crear/eliminar playlists y quitar pistas**: no repetidos en esta pasada;
  cubiertos por tests y por la auditoría anterior.
- **Diálogos nativos** (CSV/ZIP, `.lrc`): no automatizables con clics sintéticos.
- **Canción anterior en mitad de la cola**: SMTC anterior se probó con dos pistas
  (`second` → `local`); el botón «anterior» de la app no se pulsó por separado.
- **YouTube / nube / sync real**: bloqueado por credenciales.
- **Maximizar/restaurar/arrastrar** en esta compilación: no repetido; el marco DWM sí se
  aplicó (`DWM dark caption applied`).

## 16. Problemas visuales encontrados

Ninguno nuevo en las pantallas revisadas. Se confirmó:

- El panel de biblioteca mantiene el scrim y se cierra al pulsar fuera.
- La cola se dibuja dentro de la ventana y no tapa el panel derecho.
- La portada del reproductor y la del panel derecho son la misma imagen, sin deformación.
- El estado vacío de letras y de playlist están bien presentados.
- La barra superior conserva el marco oscuro, sin marco blanco.

## 17. Correcciones visuales

Ninguna en esta fase; no se cambió el diseño. La única consecuencia visual es que la
sesión de Windows ahora muestra carátula y título correctos.

## 18. Pantallas revisadas

Inicio (con y sin reproducción), panel de biblioteca abierto/cerrado, Favoritos, búsqueda,
Playlists, detalle de playlist vacía, cola, ajustes (no se logró navegar por clic; ver
punto 15), reproductor, panel derecho de letras, ventana normal.

Capturas: `screenshots/audit2-home.png`, `audit2-library.png`,
`audit2-library-closed.png`, `audit2-favorites.png`, `audit2-search.png`,
`audit2-playing.png`, `audit2-queue4.png`, `audit2-rail-hover.png`,
`audit2-controls.png`.

## 19. Resultado de la segunda auditoría

Funcionalidad principal sin regresiones: biblioteca, búsqueda, favoritos, playlists
(apertura), reproducción, cola, aleatorio, volumen y SMTC. Quedan sin verificar
manualmente los controles citados en el punto 15; no se declara la app «100 % verificada».

## 20. Tests

`cd desktop && ./gradlew test --no-daemon --console=plain` → **BUILD SUCCESSFUL**
(128 tests, 0 fallos; informe en `desktop/build/test-results/test`).

## 21. Compilaciones

- Desktop: `test`, `createDistributable`, `packagePortable`, `packageMsi` → correctas.
- Android: `./gradlew :app:assembleRelease` → correcta (sin cambios; tareas en caché).

## 22. APK

- `app/build/outputs/apk/release/app-universal-release.apk` (104.980.611 bytes),
  `app-arm64-v8a-release.apk` (61.481.175) y `app-armeabi-v7a-release.apk` (54.828.041).
- `package com.cypher.music`, `versionCode 21`, `versionName 1.5.0`.
- Firmado con el keystore existente (SHA-256 del certificado
  `54415ff1…2bc17720`). No instalado ni publicado; S22 y SM-T580 intactos.

## 23. Portable

- `desktop/build/dist/Cypher-0.1.0-windows-portable.zip` (107.727.538 bytes).
- Extraído en `%LOCALAPPDATA%\Temp\CypherPortableTest2`, arrancado desde esa ruta con
  perfil aislado; el JAR contiene `native/cypher-smtc.exe` (12.800 bytes) y Windows
  mostró la sesión con título, artista, álbum y `Playing`.
- Cierre correcto: sin procesos `Cypher` ni `cypher-smtc` residuales.

## 24. MSI

- `desktop/build/compose/binaries/main/msi/Cypher-0.1.0.msi` (108.809.088 bytes, sin firma).
- Instalación silenciosa por usuario en `%LOCALAPPDATA%\Cypher`; arranque con SMTC
  (`Playing`, metadatos) y cierre correcto.
- Desinstalación: `program-dir=False` y biblioteca real intacta
  (**13 archivos, 90.421.208 bytes**).

## 25. GitHub Actions

`.github/workflows/desktop.yml` compila el helper desde fuente, ejecuta tests, genera
imagen, MSI y ZIP, y sube artefactos. No crea releases ni publica. **No se ha ejecutado en
GitHub** en esta fase; solo se validaron los mismos comandos en local.

## 26. Problemas conocidos pendientes

- Ajustes y cierre de cola no confirmados con clic sintético (punto 15).
- Login Google/YouTube y pull real de Supabase bloqueados por credenciales/audiencia.
- Portada SMTC solo para archivos locales; las carátulas de YouTube son URLs.
- MSI y helper sin firma de código → SmartScreen puede avisar.
- Versión de escritorio 0.1.0 frente a Android 1.5.0.
- Sin cobertura de clic para los ~78 controles restantes del inventario.

## 27. Riesgos antes de publicar

- Probar manualmente los controles pequeños citados antes de una beta pública.
- Confirmar Google Cloud (client secret Desktop, audiencia, YouTube Data API) y hacer un
  pull real antes de prometer sincronización.
- Firmar MSI/ejecutable para evitar avisos; conservar el keystore Android.
- Verificar SMTC en un Windows 10 real (aquí se probó en el equipo disponible) y con
  tarjetas de sonido/dispositivos de salida distintos.
- El repositorio sigue sin commit para `desktop/`; los workflows no se han ejecutado.

---

## Comandos ejecutados

```powershell
# Helper
powershell -ExecutionPolicy Bypass -File desktop/native/build-smtc.ps1

# Desktop
cd desktop
./gradlew test --no-daemon --console=plain
./gradlew createDistributable packagePortable packageMsi --no-daemon --console=plain

# Android
./gradlew :app:assembleRelease --no-daemon --console=plain
```

```powershell
# Sesión y controles
powershell -File desktop/native/smtc-sessions.ps1
powershell -File desktop/native/smtc-controls.ps1
powershell -File desktop/native/smtc-timeline.ps1
powershell -File desktop/native/smtc-end.ps1

# MSI
msiexec /i desktop\build\compose\binaries\main\msi\Cypher-0.1.0.msi /qn
msiexec /x desktop\build\compose\binaries\main\msi\Cypher-0.1.0.msi /qn
```

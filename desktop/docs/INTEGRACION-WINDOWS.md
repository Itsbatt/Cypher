# Integración con Windows

## Barra de título

La ventana sigue siendo **nativa y decorada**: arrastrar, ajustar, acoplar (snap),
maximizar y restaurar los gestiona Windows. Lo que cambia es el color: al mostrarse la
ventana se aplican atributos DWM (`DwmSetWindowAttribute`) para pintar el marco con la
paleta de Cypher.

- Implementación: `desktop/src/main/kotlin/com/cypher/desktop/ui/WindowsChrome.kt`.
- Dependencia: JNA (`net.java.dev.jna:jna` + `jna-platform`), ya declarada en
  `desktop/build.gradle.kts`.
- Atributos: `DWMWA_USE_IMMERSIVE_DARK_MODE` (20), `DWMWA_CAPTION_COLOR` (35),
  `DWMWA_TEXT_COLOR` (36) y `DWMWA_BORDER_COLOR` (34).
- Colores: fondo `#0D0B11`, texto `#F7F5FA`, borde `#2C2738`.
- Compatibilidad: Windows 11 pinta los colores exactos; Windows 10 1809+ aplica el modo
  oscuro e ignora los atributos de color que no conoce. En sistemas que no son Windows,
  o si la API falla, la ventana conserva el marco por defecto (no se rompe nada).
- Se reintenta durante ~2,4 s tras crear la ventana porque el handle nativo (HWND) no
  existe hasta que AWT crea el peer; en el log aparece `DWM dark caption applied`.

Se descartó una ventana sin bordes con botones propios: habría exigido reimplementar
arrastre, snap, doble clic, redimensionado en los cuatro bordes y sombras, con riesgo de
perder estabilidad. La barra oscura nativa consigue la integración visual sin ese riesgo.

## Logo

Fuente única: `app/src/main/res/drawable/app_logo.png` (512×512, el logo ya existente).

| Uso | Recurso |
| --- | --- |
| Ventana / barra de tareas | `desktop/src/main/resources/cypher.png` |
| Ejecutable, instalador, accesos directos, portable | `desktop/packaging/cypher.ico` (16, 24, 32, 48, 64, 128, 256 px) |

`Main.kt` pasa `icon = painterResource("cypher.png")` a `Window`, y
`nativeDistributions.windows.iconFile` apunta al `.ico`. El `.ico` se generó desde el PNG
con Pillow; no se creó ninguna identidad nueva.

## Empaquetado

```bash
cd desktop
./gradlew createDistributable   # imagen de app con runtime (build/compose/binaries/main/app/Cypher)
./gradlew packagePortable       # zip en build/dist
./gradlew packageMsi            # MSI en build/compose/binaries/main/msi
./gradlew packageMsi --no-daemon
```

- `packageVersion = "0.1.0"` (versión de la app de escritorio; la de Android sigue en 1.5.0).
- Instalación **por usuario** (`perUserInstall = true`): no pide administrador, instala en
  `%LOCALAPPDATA%\Cypher` y crea accesos directos en Inicio y Escritorio.
- `upgradeUuid` fijo para que una versión nueva sustituya a la anterior.
- La desinstalación elimina programa y accesos directos, **nunca** `%APPDATA%\Cypher Desktop`
  (biblioteca, playlists, estadísticas y letras).
- El MSI generado no está firmado con certificado de código; Windows SmartScreen avisará
  hasta que se firme con un certificado de distribución.

## GitHub Actions

`.github/workflows/desktop.yml` (Windows, JDK 17, WiX 3 para el MSI):

1. `./gradlew test`
2. `./gradlew createDistributable`
3. `./gradlew packageMsi`
4. `./gradlew packagePortable`
5. Sube `Cypher-Windows-portable` y `Cypher-Windows-installer` como artefactos.

No crea releases, tags ni publica nada. Solo se ejecuta a mano (`workflow_dispatch`) o al
empujar cambios en `desktop/**`; en ambos casos únicamente produce artefactos.

## System Media Transport Controls (SMTC)

**Implementado y probado desde Windows.** Cypher aparece en el widget multimedia de
Windows con título, artista, álbum, portada, estado, posición y duración, y acepta
play/pausa, siguiente, anterior y seek desde el sistema.

### Arquitectura

JavaFX no expone SMTC y llamar a COM/WinRT desde JNA exigía replicar a mano vtables de
`IInspectable`, con riesgo de cuelgue nativo. La solución es un **helper .NET Framework**
pequeño y sin dependencias que posee la sesión multimedia, hablado por tuberías:

| Pieza | Archivo |
| --- | --- |
| Puente Kotlin | `desktop/src/main/kotlin/com/cypher/desktop/player/SmtcBridge.kt` |
| Helper C# | `desktop/native/SmtcHelper.cs` |
| Binario empaquetado | `desktop/src/main/resources/native/cypher-smtc.exe` |
| Compilación | `desktop/native/build-smtc.ps1` |
| Integración con la app | `AppState` (callbacks y observación de `PlaybackState`), `AudioPlayer.pause/resume`, `Main` (cierre) |

El helper crea un `Windows.Media.Playback.MediaPlayer` sin ventana ni XAML, desactiva su
`CommandManager` y usa su `SystemMediaTransportControls`. Los tipos WinRT se resuelven por
nombre en tiempo de ejecución (`Type.GetType(..., ContentType=WindowsRuntime)`), así que
**no hace falta Windows SDK ni WinMD para compilar**: solo `csc.exe`, que viene con
.NET Framework 4.x (presente en Windows 10/11).

### Protocolo (UTF-8, una línea por mensaje)

```
meta|título|artista|álbum|rutaPortada
status|Playing|Paused|Stopped|Closed
timeline|posiciónMs|duraciónMs
quit

-> ready
-> button|Play|Pause|Next|Previous|Stop
-> position|milisegundos        (seek pedido desde Windows)
-> error|mensaje
```

- La app extrae el `.exe` a `%TEMP%\cypher-smtc\` en el primer uso y lo reescribe siempre
  (un binario viejo con el mismo tamaño fue un fallo real detectado en pruebas).
- La línea de tiempo se envía como máximo una vez por segundo; los metadatos y el estado
  solo cuando cambian.
- `Play`/`Pause`/`Next`/`Previous` llaman a `AudioPlayer.resume/pause` y a la cola real de
  `AppState`; el seek llama a `AudioPlayer.seek` con la duración conocida.
- Un error de reproducción se publica como `Stopped` (no «Paused»), para no fingir una
  pausa de algo que nunca sonó.
- Al terminar la cola la sesión queda en `Stopped` con sus metadatos; al cerrar Cypher se
  envía `quit`, el helper sale y Windows elimina la sesión.

### Limitaciones

- Solo Windows (en otros sistemas `SmtcBridge` queda inactivo y no afecta a la app).
- La portada se publica cuando `Track.artwork` es un archivo local. Las carátulas de
  YouTube son URLs y no se descargan solo para el widget; la sesión muestra el resto de
  metadatos.
- Requiere .NET Framework 4.8 (incluido en Windows 10 1903+ y Windows 11). Si el helper no
  arranca, la reproducción sigue funcionando sin integración y el error se escribe en
  `stderr` con el prefijo `SMTC:`.
- El helper no está firmado; se ejecuta como parte de Cypher y no abre puertos ni red.

### Cómo recompilarlo

```powershell
powershell -ExecutionPolicy Bypass -File desktop/native/build-smtc.ps1
```

El workflow de GitHub Actions ejecuta ese script antes de los tests, de modo que el
binario empaquetado siempre corresponde al fuente.

### Pruebas reales realizadas (13/11/2026)

Con la app real, perfil aislado y dos WAV de 45 s:

| Prueba | Resultado |
| --- | --- |
| Windows enumera la sesión | `app=cypher-smtc.exe` |
| Título/artista/álbum | `SMTC probe local / Cypher / Audit album` |
| Portada | `thumb=True` |
| Posición y duración | `00:00:02 / 00:00:45`, avanzando |
| Pausa desde Windows | `Paused`, y la app queda pausada |
| Reanudar desde Windows | `Playing` |
| Siguiente | cambia a `SMTC probe second` |
| Anterior | vuelve a `SMTC probe local` |
| Seek desde Windows | salta y termina la pista |
| Cambio automático al terminar | pasa a la segunda pista sin intervención |
| Final de la cola | `Stopped` conservando título |
| Cerrar Cypher | el helper termina y la sesión desaparece |

Scripts de comprobación usados: `desktop/native/smtc-sessions.ps1`,
`smtc-controls.ps1`, `smtc-timeline.ps1`, `smtc-end.ps1`, `click.ps1`.

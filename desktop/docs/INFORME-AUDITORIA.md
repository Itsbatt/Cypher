# Cypher Desktop · Informe de la fase autónoma

Fecha: 13 de noviembre de 2026.
Alcance: auditoría funcional y visual, correcciones, integración con Windows, logo,
empaquetado y workflows. **No se ha hecho commit, tag, push, release ni publicación.**
No se ha tocado Supabase, ni secretos, ni el contrato de Android 1.5.0, ni la biblioteca
real (`%APPDATA%\Cypher Desktop`, 13 archivos, 87 MB, intacta y comprobada tras las pruebas).

Las pruebas de interfaz se hicieron sobre un perfil aislado
(`%LOCALAPPDATA%\Temp\cypher-audit`) sembrado con 4 MP3 de prueba, 2 playlists, favoritos,
estadísticas y una letra LRC. Ningún dato personal se usó en capturas ni artefactos.

---

## 1. Inventario de elementos interactivos

Se localizaron **123 puntos interactivos** en el código de la UI
(`grep` de `onClick`, `clickable`, `onValueChange`, `onDismissRequest`, `onCheckedChange`
en `desktop/src/main/kotlin/com/cypher/desktop/ui`):

| Archivo | Puntos |
| --- | --- |
| App.kt (rail, top bar, reproductor, cola, panel biblioteca, panel derecho) | 34 |
| LibraryViews.kt (biblioteca, filtros, favoritos, playlists, detalle, stats) | 18 |
| ImportView.kt (Spotify, YouTube, cuenta) | 17 |
| Components.kt (filas, botones, campos, menús) | 17 |
| YouTubeView.kt | 12 |
| SettingsAccount.kt | 11 |
| HomeFeed.kt | 11 |
| DownloadsView.kt | 3 |

El total incluye elementos repetidos por pantalla (cada fila de pista, cada tarjeta).

## 2. Probados realmente con clic

**45 acciones pulsadas en la aplicación real** (no solo tests): navegación completa del
rail (12 destinos), logo/volver, panel de biblioteca (abrir, fijar, cerrar por clic fuera y
por icono), buscador (escribir, resultados, reproducir), Reproducir todo, play/pausa,
siguiente, anterior, barra de progreso, cola (abrir, cerrar), corazón del panel derecho,
corazón de fila, crear playlist, editar nombre y guardar, eliminar con confirmación y
cancelar, abrir playlist, reproducir playlist, menú de fila (añadir a playlist, quitar de
la playlist), abrir tarjetas, "Ver todas", y controles nativos de ventana
(maximizar, restaurar, arrastrar).

Además se verificaron por API de Windows minimizar/restaurar (`ShowWindow`), porque los
clics automatizados sobre el botón nativo no eran fiables.

## 3. Botones que funcionaban

Reproducción local, pausa/reanudar, siguiente, seek, favoritos, búsqueda y resultados,
navegación del rail, scroll de Inicio, apertura de playlists, edición y guardado de nombre,
creación de playlist, borrado con confirmación, añadir/quitar pistas, menú de fila, cola,
panel de letras (visualización), importación (vista), ajustes y cuenta (visual), cierre del
panel de biblioteca (icono y clic fuera).

## 4. Botones que fallaban

1. **Crear playlist** (nueva, vacía) → diálogo «Fallo no controlado» y playlist no creada.
2. **Previsualización de importación** (YouTube y Spotify) → mismo fallo latente en cuanto
   el plan pasaba de nulo a no nulo (no llegó a reproducirse en la sesión por falta de
   credenciales, pero el patrón era idéntico y se corrigió).
3. **Botón de cola** → no mostraba nada.
4. **Panel de biblioteca fijado** → no tenía cierre visible; el clic fuera tampoco lo
   cerraba.
5. **Acento invisible** en algunos controles (alfa 0 en el color guardado).
6. **Icono de volver** era la misma casa que el icono de Inicio del rail.
7. **Wrapped** mostraba «0 horas» con 6 minutos escuchados.
8. Marco de ventana blanco y sin icono (no era un botón, pero rompía la integración).

## 5. Causa de cada fallo

1 y 2. `return` temprano dentro de un `Column` de Compose: al cambiar la condición se
desbalanceaba la pila de grupos del compositor (`Index -2 out of bounds`,
`ComposerImpl.end`). Afectaba a `PlaylistDetail`, `PlaylistsView` y a las dos ramas de
`ImportView`.
3. `DropdownMenu` anclado al borde inferior: el menú se abría fuera de la ventana y
   parecía un botón muerto.
4. El panel se fijaba con `pinned` y solo se cerraba con el icono del rail; no había
   scrim ni botón de cierre.
5. El acento se guardaba como entero con alfa 0 (`0x00RRGGBB`) y Compose lo interpretaba
   como transparente.
6. Se reutilizaba `Icons.Rounded.Home` para dos acciones distintas.
7. División entera de milisegundos entre 3.600.000 sin caso «menos de una hora».
8. Faltaba `iconFile`, icono de ventana y los atributos DWM.

## 6. Correcciones realizadas

| Archivo | Cambio |
| --- | --- |
| `core/Models.kt` | `normalizeAccent`: fuerza alfa opaco en carga y sync. |
| `app/AppState.kt`, `sync/SyncCodec.kt` | Uso de `normalizeAccent` al iniciar y al aplicar preferencias. |
| `ui/LibraryViews.kt` | `PlaylistDetail` y `PlaylistsView` sin `return` dentro de `Column`; ramas `if/else`. |
| `ui/ImportView.kt` | Previsualizaciones YouTube/Spotify en ramas `if/else` (sin retorno temprano). |
| `ui/App.kt` | Cola como panel dentro de la ventana; scrim para cerrar el panel de biblioteca al pulsar fuera; flecha de volver en pantallas distintas de Inicio; icono de cola con estado activo. |
| `ui/WindowsChrome.kt` (nuevo) | Marco oscuro DWM con reintentos y `FindWindow`. |
| `Main.kt` | Icono de ventana (`cypher.png`), tamaño mínimo 1120×720, aplicación del marco oscuro. |
| `ui/WrappedView.kt` | Horas o minutos según corresponda. |
| `build.gradle.kts` | JNA, `iconFile`, `perUserInstall`, `upgradeUuid`, tarea `packagePortable`. |
| `desktop/src/main/resources/cypher.png` | Copia del logo oficial para la ventana. |
| `desktop/packaging/cypher.ico` | Icono multi-resolución generado desde el logo oficial. |
| `.github/workflows/desktop.yml` | Workflow de artefactos Windows (sin publicar). |

## 7. Resultado de las pruebas posteriores

- Crear playlist: creación correcta, detalle vacío sin excepción, persistida en
  `library.json` (`Lista auditoria`).
- Renombrar: `pl-1` pasó de «Noche de prueba» a «Noche editada» en disco.
- Eliminar: confirmación visible; al confirmar desaparece del JSON; el resto de playlists
  y favoritos se conserva.
- Añadir/quitar pistas: `pl-2` pasó de 0 a 1 pista y `pl-1` se borró sin tocar las demás.
- Favorito: `['audit-1','audit-3']` → `['audit-3']` tras pulsar el corazón.
- Cola: panel visible con 4 pistas, cierre con el mismo botón.
- Panel de biblioteca: cierre con el icono del rail y por clic fuera, verificados con una
  única instancia (`single-library-closed.png`, `single-library-outside-close.png`).
- Buscar «olimpo» y pulsar el resultado: reproduce y actualiza portada/tiempo.
- Seek: 0:04 → 0:11 tras pulsar la barra.
- Ventana: maximizar 1936×1048, restaurar 1320×860, arrastrar a 96,96, minimizar/restaurar
  por API (`IsIconic=True` → `False`). Sin cierres ni excepciones.
- `stderr` de la sesión final: 0 excepciones.

## 8. No probado y por qué

- **Login Google / YouTube Music**: falta `client_secret` de Desktop y la audiencia
  Desktop en el backend; la sonda real devuelve `api_status=401 reason=authError`.
- **Listar/importar playlists de YouTube**: depende de lo anterior y de habilitar YouTube
  Data API.
- **Importación por selector de archivos** (CSV/ZIP y `.lrc`): el diálogo nativo no se
  puede automatizar con clics sintéticos; la lógica está cubierta por tests
  (BOM, `;`/tab, comillas, cp1252, ZIP slip, duplicados, cancelación).
- **Ajustes**: se revisaron visualmente; no se pulsaron los conmutadores ni los swatches
  (habrían cambiado el perfil de prueba, reversible, pero se priorizaron correcciones).
- **Volumen, shuffle, anterior y reproducir-à-continuación**: no pulsados en esta sesión.
- **Vista Biblioteca completa**: se revisó el panel lateral, el scroll de Inicio y las
  tarjetas, pero no se abrió la vista filtrada de álbum/artista (el clic de prueba no
  navegó y no se insistió).
- **Sincronización Android → Supabase → Desktop**: no verificada; no hay pull real.
- **Android en dispositivo**: APK compilado y firmado, no instalado (S22 y SM-T580 intactos).
- **SMTC**: no implementado (ver punto 10).

## 9. Estado de la barra superior

Integrada con DWM: fondo `#0D0B11`, texto claro, borde `#2C2738`; botones nativos de
minimizar, maximizar/restaurar y cerrar; arrastre y snap intactos. Verificado maximizado y
restaurado, con el logo en la barra de título y en el ejecutable. El icono de la barra de
tareas no se inspeccionó visualmente. Minimizar/restaurar se comprobó por API de Windows
(`IsIconic`), no con un clic fiable en el botón nativo. En Windows 10 anterior a 1809 el
marco seguirá claro (limitación documentada). No se usó una ventana sin bordes para no
perder estabilidad.

## 10. Controles multimedia nativos de Windows

**Implementados en la segunda fase** mediante un helper .NET (`cypher-smtc.exe`) que posee
una sesión real de `SystemMediaTransportControls` y habla con la app por tuberías. Windows
muestra título, artista, álbum, portada, estado, posición y duración, y acepta
play/pausa, siguiente, anterior y seek. Verificado de extremo a extremo; detalle,
protocolo y límites en `desktop/docs/INTEGRACION-WINDOWS.md` y resultados completos en
`desktop/docs/INFORME-FASE2.md`.

## 11. Estado del logo

Un único origen (`app_logo.png`). Presente en la ventana, el ejecutable, el instalador, los
accesos directos y el portable (`.ico` 16–256 px generado desde el PNG). Verificado
extrayendo el icono del `.exe` empaquetado.

## 12. Reproducción local y YouTube

- Local: 4 pistas de prueba, play/pausa, siguiente/anterior, seek y cola correctos.
- YouTube: la reproducción remota usa la caché de audio; **no verificada** en esta sesión
  por falta de sesión válida. El código de reproducción remota no se modificó.

## 13. Importaciones

Importadores Spotify (CSV/ZIP) y YouTube (enlaces, orden, no disponibles, duplicados)
cubiertos por tests; la vista previa se corrigió para no romper la composición. No se
pulsó «Importar» con datos reales ni se usó ninguna cuenta.

## 14. Estado real de la sincronización

No verificada. Sin client secret ni audiencia Desktop no se puede completar el login ni el
pull. No se ha modificado ni desplegado Supabase. La propuesta de letras sigue siendo una
propuesta (`SINCRONIZACION-LETRAS-PROPUESTA.md`).

## 15. Tests

`cd desktop && ./gradlew test --no-daemon --console=plain`
→ **128 tests, 0 fallos** (informe XML en `desktop/build/test-results/test`).

## 16. Compilaciones

- Desktop: `classes`, `test`, `createDistributable`, `packagePortable`, `packageMsi` →
  correctas.
- Android: `./gradlew :app:assembleRelease` → correcta.
  - `app-universal-release.apk` (104,980,611 bytes), `app-arm64-v8a-release.apk`,
    `app-armeabi-v7a-release.apk`.
  - `package com.cypher.music`, `versionCode 21`, `versionName 1.5.0`.
  - Firmado con el keystore existente: `CN=Cypher, OU=Mobile, O=Cypher, L=Madrid, C=ES`,
    SHA-256 `54415ff1751b71d0d15430b90c44cf04b58f4652f9c6838d1578e9292bc17720`.
  - No se instaló ni publicó; la versión publicada no se sustituyó.

## 17. Versión portable

- Imagen: `desktop/build/compose/binaries/main/app/Cypher` (167 MB, runtime incluido).
- ZIP: `desktop/build/dist/Cypher-0.1.0-windows-portable.zip` (107.710.418 bytes).
- Probada **desde otra ruta** (`%LOCALAPPDATA%\Temp\CypherPortableTest`) con perfil
  aislado; arranca sin Java instalado, ventana 1320×860, marco oscuro y logo correctos.

## 18. Instalador

- `desktop/build/compose/binaries/main/msi/Cypher-0.1.0.msi` (108.796.800 bytes, sin firma
  de código).
- Instalación por usuario verificada en silencio: `%LOCALAPPDATA%\Cypher`, accesos en
  Inicio y Escritorio, sin UAC.
- Desinstalación verificada: elimina programa y accesos; **conserva**
  `%APPDATA%\Cypher Desktop` (13 archivos, 87 MB antes y después).
- SmartScreen avisará hasta firmar el MSI con un certificado.

## 19. GitHub Actions

- `.github/workflows/desktop.yml` (nuevo): tests, imagen, MSI, ZIP y subida de artefactos
  `Cypher-Windows-portable` y `Cypher-Windows-installer`. Sin releases ni publicación.
- `.github/workflows/android.yml` se mantiene sin cambios.
- Ninguno se ha ejecutado en GitHub en esta fase.

## 20. Problemas conocidos pendientes

- SMTC implementado; pendiente de probar en otro equipo Windows y con carátulas de
  YouTube (punto 10 y `INFORME-FASE2.md`).
- Login Google/YouTube pendiente de credenciales y audiencia Desktop.
- El estado vacío del panel derecho muestra una portada degradada grande que puede
  confundirse con una carátula rota; candidato a simplificar.
- Las tarjetas de favoritos de Inicio quedan cortadas por el reproductor hasta hacer
  scroll (es un viewport con scroll, pero no hay indicador).
- Se permiten dos playlists con el mismo nombre (los menús las listan duplicadas).
- El panel de biblioteca fijado ocupa 342 px del contenido hasta que se cierra; ahora se
  cierra con clic fuera y con el icono del rail (no tiene botón X propio).
- Iconos de la cabecera de Importar y del rail Descargas son similares (descarga), aunque
  con significados relacionados.
- Versión de escritorio `0.1.0` frente a Android `1.5.0`: no se ha unificado.

## 21. Riesgos antes de publicar

- MSI sin firma de código → avisos de SmartScreen y de antivirus.
- APK firmado con el keystore existente: **no debe perderse ni cambiarse** o Android
  rechazará actualizaciones.
- SMTC ya funciona, pero la portada solo se publica para carátulas locales (las URLs de
  YouTube no se descargan para el widget).
- Sin login no se puede prometer paridad de YouTube Music ni sincronización.
- El contrato de sync de Android 1.5.0 no se ha tocado, pero tampoco se ha validado un
  pull real.
- El repositorio sigue sin commit para `desktop/`; los workflows no se han ejecutado.

---

## Auditoría visual

Pantallas revisadas (capturas en `desktop/screenshots/`): Inicio, Biblioteca, Favoritos,
Playlists y detalle/edición/confirmación, Estadísticas, Wrapped, Descargas, YouTube,
Importar, Ajustes, Cuenta, buscador, cola, panel de letras y panel de biblioteca; ventana
normal, maximizada y arrastrada.

Corregido: acento transparente, icono de volver duplicado, cola invisible, cierre del panel
de biblioteca, «0 horas», marco blanco, icono genérico, botón de cola sin estado.

Sin corregir (documentado): portada vacía grande en el panel derecho, recorte de favoritos
en Inicio sin indicador de scroll, nombres de playlist duplicados y ligera similitud entre
iconos de descarga.

Capturas destacadas:

- `screenshots/final2-wrapped.png` — Wrapped con minutos/horas.
- `screenshots/func-settings.png` — Ajustes.
- `screenshots/lib-account.png` — Cuenta.
- `screenshots/audit6-chrome-home.png` y `screenshots/close-rail-toggle.png` — marco oscuro.
- `screenshots/audit12-queue-sweep.png` — cola.
- `screenshots/audit7-detail-empty.png` — playlist vacía sin fallo.
- `screenshots/portable-running.png` — portable desde otra ruta.
- `screenshots/window-state.png` — escritorio tras cerrar (prueba de cierre).

## Comandos ejecutados

```bash
cd desktop
./gradlew test --no-daemon --console=plain
./gradlew createDistributable packagePortable --no-daemon --console=plain
./gradlew packageMsi --no-daemon --console=plain

# APK
./gradlew :app:assembleRelease --no-daemon --console=plain
```

Instalación/prueba del MSI (sin privilegios):

```powershell
msiexec /i desktop\build\compose\binaries\main\msi\Cypher-0.1.0.msi /qn
msiexec /x desktop\build\compose\binaries\main\msi\Cypher-0.1.0.msi /qn
```

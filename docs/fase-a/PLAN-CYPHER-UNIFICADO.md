# Cypher unificado — FASE A (auditoría y diseño)

> Documento de trabajo. **No se ha modificado código, ni ramas, ni datos, ni configuración.**
> Solo se añade este informe y las capturas de referencia en `docs/fase-a/referencia-pdf/`.
> Fecha: 2026-11-16. Autor: auditoría técnica sobre `meloflow` @ `2fa2cd7`.

---

## 0. Resumen ejecutivo

| Punto | Conclusión |
|---|---|
| Base recomendada | **`fcab005`** (última versión 1.4.0, `versionCode 19`, sin nada de Google). No la rama actual. |
| Google como cuenta | Se elimina por completo en Android y Desktop (Credential Manager + OAuth de escritorio + Supabase). |
| YouTube Music como *importador* | Se conserva, pero con **OAuth oficial** (`youtube.readonly`). Eso obliga a cambiar el método actual de Android (cookies de WebView) y a decidir sobre verificación de Google. |
| Desktop | La distribución del PDF **ya existe en un 70 %** (`App.kt`): rail de 78 dp, flyout de biblioteca al hover, buscador centrado con historial, panel derecho y reproductor flotante. Falta la **vista ampliada** y falta **reordenar** la jerarquía (hoy la música local manda). |
| ZIP | Se diseña un formato v1 versionado, con hashes, contenido direccionado por hash para portadas y letras, e importación transaccional con copia previa. |
| Letras | **Lyricsify no tiene API y su disclaimer restringe la reutilización.** No se raspa. La fuente primaria debe ser **LRCLIB** (ya integrada en Android, API oficial sin clave), con `lyrics.ovh` como secundaria y LRC manual como siempre disponible. |
| Riesgo mayor | Que no hay dispositivo Android: la exportación se valida con tests + ZIPs generados, **no** con una prueba física. Se declara así en todos los informes. |

---

## 1. Análisis visual del PDF (`Añade algo de texto.pdf`, 5 páginas)

Revisado página por página rasterizando el propio PDF (capturas en `docs/fase-a/referencia-pdf/`).
El PDF es un **prototipo de bloques grises** sobre una captura real de reproducción: los grises son
marcadores de posición, no estilo final. Lo que sí es normativo es la **topología**: qué existe, dónde
está y cómo se comporta. Los iconos y el reproductor sí están dibujados de verdad.

### Página 1 — Inicio con rail colapsado
- Lienzo 16:9 (1920×1080): es un escritorio, no una tablet.
- Columna izquierda ≈ 78 px: contenedor redondeado con **9 círculos** (iconos) + un bloque destacado
  arriba (logotipo). El rail es una píldora flotante, separada del borde superior.
- Arriba, **buscador centrado** (píldora ancha, ≈ 40 % del ancho) y nada más: no hay barra de título
  ni botones de ventana dibujados.
- Centro: **6 tarjetas horizontales** en 2 filas × 3 columnas (formato "fila de lista/carrusel ancho"), y
  debajo **5 tarjetas cuadradas grandes** en una sola fila. Jerarquía: dos bloques, no cinco.
- Abajo: **reproductor flotante** de ≈ 88 px de alto, ancho ≈ 90 %, esquinas muy redondeadas, flotando
  sobre el contenido con margen inferior. Contiene: portada 1:1, título + "Vídeo musical · Melendi",
  aleatorio, anterior, play (círculo blanco), siguiente, repetir, barra de progreso con tiempos
  (3:13 / 3:48) y, en un bloque oscuro a la derecha, **cola**, **volumen** y **pantalla completa**.

### Página 2 — Biblioteca desplegada
- El rail sigue visible a la izquierda; a su derecha aparece un **panel de biblioteca ancho**
  (≈ 300–340 px) que **se superpone** al contenido (no lo empuja: las tarjetas quedan debajo).
- Cada fila: **círculo/portada** + nombre en una sola línea + espacio para metadatos. Sin agrupaciones
  ni filtros visibles en el prototipo.
- El cursor está sobre el panel (esquinas inferiores redondeadas): confirma que la apertura es **por
  hover** y que el panel se cierra al salir.

### Página 3 — Rail colapsado (repetida)
- Idéntica a la 1 salvo el cursor, que está sobre el icono de **pantalla completa** del reproductor.
  Es la demostración explícita del tercer estado: rail cerrado + intención de abrir la vista ampliada.

### Página 4 — Vista ampliada (la referencia de la vista inmersiva)
- Pantalla completa, **fondo generado a partir de la portada**: enormes manchas desenfocadas del arte
  (rojo/crema/verde), oscurecidas lo suficiente para leer.
- Mitad izquierda: **portada grande** con esquinas redondeadas y sombra, controles superpuestos tenues
  (anterior/play/siguiente) sobre la portada, barra de progreso fina debajo con tiempos, y **título +
  artista centrados** bajo la portada.
- Mitad derecha: **letras** a gran tamaño, varias líneas visibles, **la línea actual en blanco y en
  negrita**, las demás atenuadas al ~50 %. Sin tarjeta contenedora: el texto va directo sobre el fondo.
- Abajo a la derecha: dos botones circulares pequeños (compartir/ajustes de letras).
- Arriba a la izquierda: tres puntos (menú). Arriba a la derecha: overlay de diagnóstico FPS/GPU ajeno
  al diseño (es del equipo que grabó el prototipo) — **se ignora**.
- Conclusión de estilo: esta vista **no** es una tarjeta modal centrada; es un modo de ventana completa.

### Página 5 — Panel derecho "En reproducción"
- Panel lateral **derecho** (≈ 300–320 px) que se superpone al contenido, con la misma familia visual
  que el flyout izquierdo: portada grande arriba (1:1, esquinas redondeadas), píldora
  "Cambiar a vídeo", **título** en grande y artista debajo, iconos de cerrar y de menú a la derecha,
  y una **tarjeta de letras** translúcida con líneas atenuadas y el texto actual resaltado.
- Debajo de las letras: miniaturas ("a continuación" / relacionadas).
- **No hay controles de reproducción en el panel**: el prototipo respeta la regla de no duplicar el
  reproductor inferior. Esto se mantiene como requisito duro.

### Lo que el PDF *no* dice (y hay que decidir con criterio)
1. Qué hay exactamente en cada tarjeta horizontal (¿recientes? ¿recomendaciones?).
2. Si el panel derecho es fijo o superpuesto; en la página 5 se superpone y el reproductor se recorta
   debajo, lo que en una app real es un defecto: se resolverá con el reproductor **por encima** del
   panel y el panel terminando justo encima del reproductor.
3. Tipografía, radios exactos y colores (los grises son marcadores).
4. Comportamiento con ventana estrecha.

---

## 2. Propuesta de diseño — Cypher Desktop

### 2.1 Principio rector
El PDF ya está implementado en lo esencial; el rediseño es **reordenar la jerarquía y completar los
dos estados que faltan** (vista ampliada y panel derecho definitivo), no reescribir la cáscara.
Se conserva el reproductor flotante actual (es el "característico de Cypher" que pides mantener).

### 2.2 Estructura (medidas concretas)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  ●  [ Inicio ]            ⌕ ¿Qué quieres reproducir?  Ctrl K        (CY) ▾    │  72 dp
├────┬───────────────────────────────────────────────────────────────┬─────────┤
│    │                                                               │         │
│ ▣  │   CONTINUAR ESCUCHANDO                                        │ PANEL   │
│ ▣  │   ┌─────────────────────┐ ┌─────────────────────┐             │ DERECHO│
│ ▣  │   │ portada  nombre  ▸  │ │ portada  nombre  ▸  │             │ 320 dp  │
│    │   └─────────────────────┘ └─────────────────────┘             │         │
│ ＋ │   TUS PLAYLISTS RECIENTES                                     │ portada │
│ ▣  │   ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐                          │ título  │
│ ▣  │   └────┘ └────┘ └────┘ └────┘ └────┘                          │ artista │
│ ▣  │   FAVORITOS                                                   │ álbum ♥ │
│ ▣  │   ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐                          │ letras  │
│    │   └────┘ └────┘ └────┘ └────┘ └────┘                          │ info    │
├────┴───────────────────────────────────────────────────────────────┴─────────┤
│  ▣  Título                  ⇄ ◀ ▶ ▶ ▷ ▷  ⤨   3:13 ──────── 3:48   ☰ 🔊 ▣    │ 88 dp
└──────────────────────────────────────────────────────────────────────────────┘
   78 dp                        (reproductor flotante 90 % de ancho, 16 dp de margen)
```

**Rail (78 dp)** — 8 destinos, un bloque destacado arriba y ajustes abajo:
`Inicio · Biblioteca (abre flyout) · ＋ (crear lista) · Favoritos · Estadísticas · Wrapped · Importar · Ajustes`.
Salen del rail: **Descargas**, **YouTube Music** y **Cuenta** (ver 2.7).
Cada icono: 44×44 dp, radio 12, fondo `accent 18 %` si está activo, `Pill 60 %` al hover.

**Flyout de biblioteca (340 dp)** — hover con retardo de 120 ms, fijado por clic en el icono, cerrado por
`Esc`, por clic fuera o al elegir una lista. Tres pestañas dentro del panel: `Listas · Álbumes · Artistas`
(el prototipo solo muestra listas; las otras dos son la misma lista con otra fuente). Cada fila: portada
48 dp, nombre 14 sp semibold, "Lista · N canciones" 11 sp, y contador a la derecha. Animación:
`fadeIn + slideInHorizontally(-18dp)` en 160 ms, `fadeOut` en 120 ms; con *movimiento reducido* aparece
sin animación (ya soportado).

**Barra superior (72 dp)** — solo tres zonas: identidad + miga de pan a la izquierda (220 dp), buscador
centrado (máx. 560 dp, altura 44), chip de perfil a la derecha (220 dp). El buscador abre un panel con
historial (consultas y entidades abiertas, ya persistido en `RecentSearch`), resultados mixtos
biblioteca + YouTube con icono de procedencia, y `Ctrl K` / `Esc`. Sin duplicar: el buscador **no**
repite iconos del rail; el chip de perfil ya no dice "Cuenta" ni email.

**Zona central** — Inicio por defecto, con este orden (configurable, ver §6):
1. **Continuar escuchando** — 1 tarjeta ancha con la última sesión (portada, título, posición).
2. **Tus playlists recientes** — 5 tarjetas 1:1 de 168 dp, con nombre y nº de canciones.
3. **Favoritos** — 5 tarjetas 1:1 + "Ver todos".
4. **Recomendado para ti** — tarjetas 2:1 más anchas, derivadas de historial local (misma lógica que
   `dynamicRecommendations` de Android; nunca inventadas).
5. **Tus artistas** — tarjetas circulares.
Con ventana ancha (≥ 1240) se ven las 5 columnas; con 1040–1240, 4; por debajo de 1040, 3 y el panel
derecho se colapsa a una pestaña lateral de 44 dp (ya existe `CollapsedRightRail`).

**Panel derecho (320 dp)** — exactamente lo pedido, sin controles de transporte:
portada 1:1 con sombra, título (2 líneas), artista, álbum clicable, corazón, y dos pestañas:
`Letras` (por defecto, con la línea actual resaltada y salto al tocar) e `Información`
(reproducciones, añadida, origen, formato, ruta relativa solo si es local, fuente de la letra y fecha).
Debajo, "A continuación" con 3 miniaturas. Ancho se colapsa con el chevron; el estado se recuerda.

**Reproductor inferior (88 dp)** — se conserva tal cual está hoy: portada 50, título/artista,
aleatorio, anterior, play, siguiente, repetir, tiempos, progreso, cola, volumen y **nuevo icono de
vista ampliada** (el que el PDF señala con el cursor en la página 3). La cola sigue siendo el panel
en ventana (no un `DropdownMenu`, que ya se corrigió por recorte).

**Vista ampliada (página 4)** — overlay a pantalla completa dentro de la ventana:
- Fondo: la portada actual ampliada + `blur 64 dp` + velo negro 55 % + viñeta vertical. Se reutiliza
  `CypherBackground` en modo `IMAGE` apuntando a la portada (no al fondo del usuario).
- Izquierda 44 %: portada grande (máx. 420 dp) con controles tenues al hover, progreso fino con
  tiempos, título 26 sp, artista 14 sp.
- Derecha 56 %: letras centradas verticalmente en el 40 % superior, tamaño según preferencia del
  usuario, línea actual en `TextPrimary` + bold, resto al 45 %; desplazamiento automático suave;
  si no hay letras: estado honesto "Sin letras para esta canción" + "Buscar" / "Añadir .lrc".
- Esquinas: menú (⋮) arriba izquierda, cerrar (Esc) arriba derecha, y dos botones circulares abajo
  derecha (compartir / preferencias de letras).
- Transición: `fadeIn` 180 ms desde el reproductor; con movimiento reducido, corte seco.

### 2.3 Tokens visuales (identidad Cypher, no los grises del prototipo)
| Token | Valor | Uso |
|---|---|---|
| `Background` | `#0D0B11` | lienzo |
| `Surface` | `#16141D` (α 0.92 con imagen de fondo) | rail, paneles, reproductor |
| `Card` | `#1E1B27` | tarjetas, filas |
| `Pill` | `#282334` | chips, hover, botones secundarios |
| Acento | `#9D7BB0` (lavanda) + 9 presets + HEX libre | selección, progreso, corazón |
| Radio | 20 dp tarjetas / 16 dp filas / 999 píldoras | coherente con Android |
| Sombra | 26–30 dp, 8 % negro | paneles flotantes |
| Tipografía | sistema; 34/28/22/20/17/15/13/12/11/10 sp | misma escala que `CypherTypography` |
| Espaciado | rejilla de 4 dp; márgenes 22 dp | |
| Separadores | `#2C2738` 1 dp, solo si el usuario los activa | |

Los tokens se exponen como **ajustes reales** (§6), no como constantes: cada uno debe cambiar la
interfaz o no se muestra.

### 2.4 Estados vacíos y de error (explícitos, nunca inventados)
- Biblioteca vacía → "Cypher no tiene contenido todavía" + 3 acciones: Importar de YouTube Music,
  Importar ZIP de Cypher, Activar biblioteca local.
- Sin letras → mensaje + botones; jamás letras de relleno.
- Cola vacía → texto, no lista fantasma.
- Canción remota sin audio → fila marcada "No disponible" con reintento, sin desaparecer de la lista.

### 2.5 Qué se reutiliza del código actual
`CypherApp` (shell), `CompactRail`, `LibraryFlyout`, `AppTopBar`, `SearchPanel`, `RightInfoPanel`,
`FloatingPlayer`, `Artwork`, `CypherBackground`, `Theme.kt`, `Appearance.kt`. Se **añade**
`ExpandedPlayer.kt` y se **reescribe** `HomeFeed.kt` (hoy está ordenado alrededor de la biblioteca
local). Ver §2.7.

### 2.6 Animaciones
Duración 120–180 ms, `easeOut`, sin rebotes salvo en portada al cambiar de pista (que ya existe en
Android como `artworkSpringPhysics`). Todas gobernadas por `reduceMotion`. Ninguna animación bloquea
entrada: el hover abre el flyout aunque las animaciones estén desactivadas.

### 2.7 Cambios de navegación concretos (esto es lo que "reorganiza la experiencia")
| Hoy (`Screen`) | Después | Motivo |
|---|---|---|
| `HOME` | `HOME` (rediseñado) | inicio personalizado |
| `LIBRARY` (canciones del disco) | **fuera** del rail | requisito 4 |
| `PLAYLISTS` | dentro del flyout + acción ＋ | el PDF pone las listas en la biblioteca |
| `FAVORITES` | `FAVORITES` | conservar |
| `STATS` | `STATS` | conservar |
| `WRAPPED` | `WRAPPED` | conservar |
| `DOWNLOADS` | **Ajustes → Descargas** | es caché de streaming, no contenido principal |
| `ONLINE` (YouTube Music) | **Ajustes/Importar → "Explorar y buscar en YouTube Music"** | ya no es sección principal |
| `IMPORT` | rail (icono de importar) | es una acción de primer nivel |
| `SETTINGS` | rail, abajo | |
| `ACCOUNT` | **eliminada** | no hay cuenta |

---

## 3. Base recomendada: comparación 1.4.0 vs "última anterior a Google"

### 3.1 Evidencia del historial
```
2fa2cd7  Cypher 1.5.0 (WIP): cuentas de Google, sincronización en la nube…   ← HEAD, rama actual
fcab005  feat(sueño): fade-out de 30 s al terminar el temporizador            ← última SIN Google
786c0cd  feat(atajos): favoritas y última sesión desde el icono
a068178  feat(ajustes): mostrar espacio de descargas y borrar todas
e8a847f  feat(playlists): portadas propias elegidas de la galería           ← tag v1.4.0
```
Comprobaciones hechas sobre el árbol de cada commit:

| | `v1.4.0` (e8a847f) | `fcab005` (recomendado) | `2fa2cd7` (HEAD) |
|---|---|---|---|
| `versionName` | 1.4.0 | **1.4.0** | 1.5.0 |
| `versionCode` | 19 | **19** | 21 |
| Directorio `app/.../sync/` | 0 archivos | **0 archivos** | 15 archivos |
| `androidx.credentials` / `googleid` | no | **no** | sí |
| Tablas `sync_outbox` / `sync_state` | no | **no** | sí (migración 7→8) |
| `desktop/` | no existe | **no existe** (está sin commit) | sí (untracked) |
| `experimental/supabase/` | no | **no** | 23 archivos |
| Post-1.4.0 útiles | — | **+3 commits** (atajos, espacio de descargas, fade del temporizador) | igual |

**`fcab005` es literalmente "Cypher 1.4.0 con tres arreglos", sin una línea de Google ni de nube.**
El tag `v1.4.0` es el mismo producto tres commits antes.

### 3.2 Recomendación
- Base de Android: **`fcab005`**, conservando su `versionName` 1.4.0 como punto de partida y subiendo a
  **`1.5.0` con `versionCode 21`** en la nueva rama (mismo par que ya usa el WIP, así una instalación
  del WIP actualiza sin conflicto de firma ni de nombre).
- Nombre de rama propuesto: **`feature/cypher-unificado`**, creada desde `fcab005`. No se toca `main`,
  ni `release/*`, ni el tag, ni se borra nada.
- **Desktop no está en git**: 49 archivos, 12 652 líneas (≈ 10,4 k de código + 2,2 k de tests), todo
  untracked. Formará parte del primer commit de la fase B **después** de retirar su código de Google,
  para que la rama nueva no nazca con la nube dentro.
- Los 9 archivos modificados sin commit en la rama actual (`PlaylistDao`, `AccountRepository`,
  `GoogleAuthGateway`, `JwtClaims`, `LocalSyncSource`, `SessionStore`, `SyncEngine`, `SyncModels`,
  `AccountComponents`, un SQL de Supabase) y los 9 untracked (`LocalTrackRestore`, `OutboxBatch`,
  `PlaylistTrackCodec`, `RestoredPlaylistTracks`, `SyncAuthPolicy` y sus tests) **se quedan donde
  están**: son de la etapa 1.5. No se borran; simplemente no viajan a la rama nueva.

### 3.3 Comparación funcional (lo que cambia entre las dos bases)
| Función | 1.4.0 tag | fcab005 | Comentario |
|---|---|---|---|
| Playlists, cola, orden, descargas por playlist | sí | sí | idéntico |
| Portadas de playlist desde galería | sí | sí | el tag es justamente ese commit |
| Atajos de icono (favoritas / continuar) | no | **sí** | 2 archivos nuevos de recursos + plan |
| Espacio de descargas y borrar todo | no | **sí** | ajustes |
| Fade-out de 30 s del temporizador | no | **sí** | 1 política + test |
| Google / nube | no | no | ninguno de los dos |
| Wrapped, letras LRCLIB, EQ, Spotify import, QR | sí | sí | igual |

No hay ninguna función de `fcab005` que quieras eliminar, y las tres añadidas son locales y estables.
Por eso `fcab005` gana sin discusión.

---

## 4. Qué se conserva y qué se elimina

### 4.1 Se conserva (Android)
Reproductor Media3 + cola + sesión + notificación; descargas con `.nomedia`; orden y filtros de
biblioteca; playlists y portadas propias; favoritos; historial; estadísticas diarias y Wrapped; EQ de
5 bandas + bass boost + normalización; temporizador con fade; crossfade; búsqueda (local + YouTube
InnerTube/yt-dlp); importación Spotify por CSV/ZIP/enlace; importación YouTube por enlace; letras
LRCLIB + tarjetas compartibles; QR de playlist; tema y personalización completos; atajos de icono;
exportación JSON actual (base del ZIP); diagnóstico; actualizaciones in-app.

### 4.2 Se elimina (Android)
| Elemento | Archivos / líneas |
|---|---|
| Cuenta Google (Credential Manager) | `sync/GoogleAuthGateway.kt` (102), `sync/JwtClaims.kt` (32), `ui/account/AccountComponents.kt` (412) |
| Sesión y almacenamiento de cuenta | `sync/SessionStore.kt` (152), `sync/AccountRepository.kt` (365) |
| Motor de sincronización | `sync/SyncEngine.kt` (353), `SyncBackend.kt` (184), `SyncQueue.kt`, `SyncMerge.kt`, `SyncPolicy.kt`, `SyncModels.kt`, `LocalSyncSource.kt`, `OutboxBatch.kt`, `PlaylistTrackCodec.kt`, `RestoredPlaylistTracks.kt`, `SyncAuthPolicy.kt` (~1 900 líneas) |
| Estado de sync en la UI | estados `syncStatusMsg`, `accountState`, `linkOfferVisible` y sus bloques en `MainScreen`/`MainViewModel` (~250 líneas), `SyncPhase`/`AccountInfo` en Desktop |
| Ajustes de nube | sección "Sincronización YouTube Music (Google)" de `SettingsScreen` (líneas 584–728): **se sustituye** por "Importar de una cuenta", no se borra el concepto |
| Dependencias | `androidx.credentials`, `credentials-play-services-auth`, `googleid` (`libs.versions.toml`) |
| BuildConfig | `GOOGLE_WEB_CLIENT_ID`, `SYNC_BASE_URL` y su lectura de `local.properties` en `app/build.gradle.kts` |
| Room | tablas `sync_outbox` y `sync_state` (ver §5) |
| Documentación | `docs/backend-sync-supabase.md`, `docs/runbooks/fase-b-despliegue-rollback.md` → se marcan obsoletos, no se borran |

### 4.3 Se elimina (Desktop)
`sync/GoogleAuth.kt` (258, pero **se recicla** su parte PKCE para el importador), `SyncService.kt` (174),
`SyncClient.kt` (156), `SyncCodec.kt` (329), `SyncMerge.kt` (71), `sync/PlaylistTrackCodec.kt` (88),
`screens` `ACCOUNT`, `ui/SettingsAccount.kt` en su parte de cuenta (líneas 260–466), campos
`SyncState`, `OutboxRow`, `cloudTracks`, `remoteThemeJson` de `LibraryData`, `AccountInfo`, `SyncPhase`,
tests `SyncEngineTest`, `SyncCodecTest`, `SyncCoreTest`, `SyncDiagnosticsTest`, `SyncFixtureTest`,
`OAuthFlowTest`, `LoopbackLoginTest` (≈ 1 000 líneas de test), y la mención de "misma cuenta" del
`README.md`.

### 4.4 Qué **no** se elimina y conviene vigilar
- **La importación de YouTube Music no es la cuenta de Cypher.** Hoy comparten infraestructura
  (`GoogleAuth` con `includeYouTubeScope`, y en Android `YouTubeAccountManager` con cookies). Se separan:
  el importador queda con su propio cliente OAuth, su propio scope y su propio estado, y **nunca** se
  dispara al abrir la app.
- El perfil local (nombre visible) se conserva; pierde el email.
- Los datos locales no se tocan: ni JSON de Desktop, ni Room, ni preferencias.

---

## 5. Migraciones necesarias

### 5.1 Android — Room (versión 8 → 9)
- **Impacto en datos de usuario: ninguno.** `playlists`, `tracks`, `playlist_track` (con `position`),
  `recently_played`, `playback_analytics`, `playback_daily` y `search_queries` no se modifican.
- `MIGRATION_8_9`: `DROP TABLE IF EXISTS sync_outbox; DROP TABLE IF EXISTS sync_state;` y quitar los
  DAO/entidades de la lista. Ambas tablas **solo** contienen bookkeeping de sincronización, que deja de
  existir. Es transaccional: si falla, Room revierte.
- Riesgo único: **downgrade**. Quien tenga instalado el 1.5.0 WIP (v8) y vuelva a un APK v8 no notará
  nada; quien instale el nuevo v9 no podrá volver a instalar el 1.5.0 WIP sin desinstalar (Room no baja
  versiones). Se documenta; no afecta a la 1.4.0 pública, que ya tiene `versionCode` menor.
- Alternativa conservadora (si prefieres cero cambios de esquema): mantener v8 con las tablas vacías y
  eliminar solo el código. Coste: dos tablas muertas para siempre. **Recomiendo el v9.**
- `app/schemas/*.json` se regenera; el test `MigrationTest` (instrumentado) cubre 8→9 en emulador.

### 5.2 Android — SharedPreferences
| Fichero | Acción |
|---|---|
| `cypher_theme_prefs` | intacto |
| `cypher_view_prefs` | intacto |
| `lyrics_display_settings` | intacto (se amplía con proveedor, ver §9) |
| `cypher_motion_prefs` | intacto |
| `yt_secure_account_*` (EncryptedSharedPreferences) | **borrado único** al desinstalar la cuenta de Cypher: deja de tener sentido con el nuevo importador OAuth. Se avisa en el changelog: el usuario tendrá que reconectar su cuenta de YouTube la primera vez que importe. |
| `yt_account_*` (legado en claro) | borrado igualmente |

### 5.3 Desktop — `library.json`
- Hoy `LibraryData.version = 2`, en `%APPDATA%\Cypher Desktop\library.json`, escritura atómica y
  `Storage.backup("pre-…")` ya implementados.
- Nueva **versión 3** del documento sin `sync`, `outbox`, `cloudTracks`, `remoteThemeJson`.
- Lector tolerante: `Json { ignoreUnknownKeys = true }` **ya** ignora los campos eliminados, así que un
  `library.json` v2 se abre sin migración explícita. Aun así se escribe una migración `2→3` que:
  1. copia el fichero a `library-v2-<ts>.json.bak` antes de tocar nada;
  2. convierte cada `cloudTracks[i]` en `RemoteTrack` si tiene `streamUrl`/id de YouTube válido
     (así un favorito de otro dispositivo sigue siendo reproducible);
  3. descarta `outbox` y `sync`;
  4. deja `version = 3`.
- **Decisión pendiente (D3)**: descartar los `cloudTracks` que no se puedan convertir, o conservarlos
  como "referencia sin reproducción".

### 5.4 Android → Desktop (ZIP)
Migraciones del formato: registro `migrations: Map<Int, (JsonObject) -> JsonObject>`; el lector acepta
`formatVersion` 1..N y aplica en cadena. v1 es la que se implementa ahora; el diseño deja sitio a v2
(audio incluido, letras por canción en fichero propio, campos nuevos).

### 5.5 Riesgos de compatibilidad
| Riesgo | Probabilidad | Mitigación |
|---|---|---|
| Usuario con el 1.5.0 WIP al actualizar | media | mismo `versionCode` 21 y misma firma; el v9 se aplica sobre v8 sin pérdida |
| Pérdida de la sesión de YouTube al cambiar el método | alta (esperada) | mensaje claro "reconecta para importar"; no afecta a la biblioteca |
| `library.json` v2 con `cloudTracks` | media | migración con copia previa + conversión a `RemoteTrack` |
| Playlists importadas con ids de origen distintos (`yt_…` vs `yt:…`) | media | normalizador único en el formato (§7) y test cruzado |
| ZIP de una versión futura leído por un Desktop viejo | baja | `minReaderVersion` en el manifiesto → error claro, nunca importación parcial |
| Descargas de audio referenciadas por ruta antigua | media | el formato **no** guarda rutas absolutas; cada dispositivo resuelve su propio archivo |

---

## 6. Inventario de personalización de Android y su equivalente en Desktop

Todo lo que sigue **existe hoy** en Android (verificado en código). "Desktop" indica si ya está, si se
implementa o si se descarta con motivo.

### 6.1 Tema y superficies — `ThemePreferences`
| Ajuste | Android (rango) | Desktop |
|---|---|---|
| Color de acento | 10 presets + HEX libre | ✅ `accent` (falta el selector HEX y los presets) |
| Estilo de superficie | OLED puro / Carbón / Grafito / Claro | ➕ `surfaceStyle` (mismos 4 nombres) |
| Color de fondo base | 5 tonos + HEX | ➕ `backgroundColor` |
| Tono de tarjetas | 5 tonos + HEX | ➕ `cardColor` |
| Bordes de 1 dp | on/off | ➕ `borders` |
| Radio de esquinas | 8–32 dp | ➕ `cornerRadius` |
| Respuesta háptica | on/off | ❌ no aplica (sin motor háptico); se oculta |
| Física de portada (spring) | on/off | ➕ animación del cambio de pista |
| Glow ambiental | on/off + intensidad 0–1 | ✅ equivalente en modo `GLASS`; ➕ intensidad |
| Tinte del cristal | 0–0.3 | ➕ `glassTint` |
| Shader AGSL Liquid Glass | on/off, 3 colores, velocidad, distorsión, opacidad | ➕ versión aproximada (blobs difuminados; Compose Desktop no tiene AGSL). Se documenta como "aproximación", no se finge paridad |
| Crossfade | 0/2/4/6/8 s | ⏳ requiere fundido en `AudioPlayer` (hoy no existe) — se implementa o se oculta; **no** se deja un control muerto |
| Movimiento reducido | on/off, sigue al sistema | ✅ `reduceMotion` |

### 6.2 Fondo
| Ajuste | Android | Desktop |
|---|---|---|
| Fondo original / imagen del usuario | ✅ | ✅ `backgroundMode` (ORIGINAL / GLASS / IMAGE) |
| Desenfoque | ✅ | ✅ `backgroundBlur` |
| Oscurecimiento | ✅ | ✅ `backgroundDim` |
| Opacidad | ✅ | ✅ `backgroundOpacity` |
| Recorte y posición | ✅ (crop/fit) | ✅ `backgroundFit` (falta "posición") ➕ `backgroundAnchor` |

### 6.3 Inicio y biblioteca — `UserViewPreferences`
| Ajuste | Android | Desktop |
|---|---|---|
| Secciones de inicio (6 tipos) con orden y on/off: Recomendaciones, Recientes, Favoritos, Playlists, Playlists favoritas, Accesos rápidos | ✅ | ➕ mismas 6 con los mismos nombres |
| Orden de biblioteca | Fecha / Artista / Álbum / Nombre, asc/desc | ✅ `SortMode` (+ falta fecha, que ya existe como `addedAt`) |
| Playlists favoritas | ✅ | ➕ `favoritePlaylists` |
| Fecha de añadido por pista | ✅ (se registra sola) | ✅ `addedAt` en `Track` |
| Pantalla inicial | — | ✅ `libraryStart` |

### 6.4 Letras — `LyricsDisplaySettings`
| Ajuste | Android | Desktop |
|---|---|---|
| Tamaño | Pequeño / Mediano / Grande / Extra (20/26/32/38 sp) | ➕ mismos 4 |
| Alineación | Izquierda / Centro | ➕ |
| Opacidad de versos inactivos | 0.2–0.9 | ➕ |
| Desenfoque de versos inactivos | 0–6 dp | ➕ |
| Color de la línea activa | Acento / paleta de portada | ➕ |

### 6.5 Reproductor y audio
| Ajuste | Android | Desktop |
|---|---|---|
| Ecualizador 5 bandas + presets + bass boost | ✅ | ❌ sin equivalente viable en JavaFX Media → **se oculta** en Desktop (documentado) |
| Normalización −14 LUFS | ✅ | ❌ igual |
| Temporizador de apagado con fade | ✅ | ➕ temporizador simple (pausa + fade de 5 s; el fade de 30 s es de Android) |
| Crossfade | ✅ | ver 6.1 |
| Volumen inicial | ✅ | ✅ |
| Aleatorio / repetición | ✅ | ✅ |
| Cola | ✅ | ✅ |
| Modo expandido | ✅ | ➕ **nuevo** (página 4) |

### 6.6 Nuevas opciones que pides y no existen aún en ninguno
`Apariencia del reproductor` (flotante / barra pegada al borde), `transparencias y efectos`,
`diseño de tarjetas` (plana / elevada / con borde), `densidad` (compacta / cómoda / amplia),
`paneles visibles` (biblioteca, panel derecho), `animaciones y transiciones` (on/off global + duración),
`configuración de inicio` (qué pantalla y qué bloques).
Todas se implementan como preferencias que **cambian la interfaz de verdad**, con test de persistencia
(cerrar y reabrir) por cada una.

### 6.7 Nombres compartidos
Se adopta un único vocabulario en ambos productos: `accent`, `surfaceStyle`, `backgroundColor`,
`cardColor`, `borders`, `cornerRadius`, `backgroundMode/Blur/Dim/Opacity/Fit`, `reduceMotion`,
`homeSections`, `librarySort`, `lyricsFontSize/Alignment/InactiveOpacity/InactiveBlur/LyricsColor`,
`playerStyle`, `cardStyle`, `density`, `panels`, `animations`. Android los mapea a sus enums actuales;
Desktop los añade a `Preferences`. Así el ZIP puede transportarlos sin traducción.

---

## 7. Diseño técnico del ZIP (`cypher-library.zip`)

### 7.1 Estructura
```
cypher-library.zip
├── manifest.json                 # obligatorio, primero en el zip
├── library.json                  # obligatorio: perfil, preferencias, pistas, playlists, favoritos, stats, índice de letras
├── playlists/
│   └── <playlistId>.json         # opcional, mismo contenido que library.playlists[i] (import parcial / futuro Desktop→Android)
├── artwork/
│   └── <sha256[:2]>/<sha256>.jpg # contenido direccionado por hash → sin duplicados
├── lyrics/
│   └── <trackKey>.lrc            # texto exacto, nunca reformateado
├── metadata/
│   ├── checksums.sha256          # lista plana verificable con sha256sum -c
│   └── README.txt                # explicación legible del contenido
└── audio/                        # SOLO si el usuario lo activa
    ├── index.json
    └── <sha256>.<ext>
```

### 7.2 `manifest.json`
```json
{
  "format": "cypher-library",
  "formatVersion": 1,
  "minReaderVersion": 1,
  "generator": { "app": "android", "versionName": "1.5.0", "versionCode": 21, "buildId": "a1b2c3d4" },
  "exportedAt": "2026-11-16T10:42:11Z",
  "exportedAtMs": 1763289731000,
  "content": { "audio": false, "artwork": true, "lyrics": true, "stats": true, "preferences": true },
  "counts": { "playlists": 14, "tracks": 963, "uniqueTracks": 903, "favorites": 212,
              "artwork": 87, "lyrics": 41, "statsDays": 310, "audioFiles": 0 },
  "source": { "platform": "android", "sdk": 34, "appVersion": "1.5.0" },
  "files": [
    { "path": "library.json", "role": "library", "bytes": 812344,
      "sha256": "9f2c…" },
    { "path": "artwork/ab/ab12….jpg", "role": "artwork", "bytes": 184221, "sha256": "ab12…" },
    { "path": "lyrics/yt_dQw4w9WgXcQ.lrc", "role": "lyrics", "bytes": 2103, "sha256": "77aa…" }
  ],
  "totals": { "bytes": 12409887, "files": 129 },
  "notes": "Exportado por el usuario desde Ajustes > Exportar biblioteca. Sin audio protegido."
}
```
Reglas:
- **Nunca rutas absolutas** ni nombres de dispositivo, ni `content://`, ni `file://`. Las rutas del zip
  son siempre relativas, con `/`, y solo de la lista blanca de carpetas.
- `files[].sha256` = SHA-256 del contenido exacto del entry. El manifiesto **no** se lista a sí mismo
  (se verifica con `metadata/checksums.sha256`, que sí incluye `manifest.json`).
- `bytes` sirve para detectar truncamientos sin leer el contenido.

### 7.3 `library.json` (v1)
```jsonc
{
  "schema": 1,
  "profile": { "displayName": "bat" },
  "preferences": { /* §6, mismas claves en ambos productos */ },
  "tracks": [{
    "key": "yt_dQw4w9WgXcQ",            // identidad estable y única
    "id": "yt_dQw4w9WgXcQ",             // id local original (se conserva)
    "source": { "type": "YOUTUBE", "externalId": "dQw4w9WgXcQ", "isrc": null },
    "title": "Con la luna llena", "artist": "Melendi", "album": "…",
    "durationMs": 228000, "genre": null,
    "artwork": { "kind": "FILE", "ref": "artwork/ab/ab12….jpg" },
    "addedAt": 1700000000000, "lastPlayedAt": 1763000000000,
    "audio": null                        // o { "kind":"FILE", "ref":"audio/…" } si se incluyó
  }],
  "playlists": [{
    "id": "p_8f1c", "name": "Road trip", "description": "",
    "cover": { "kind": "FILE", "ref": "artwork/cd/cd34….jpg" },
    "origin": "YOUTUBE", "sourceUrl": "yt:PL…",
    "createdAt": 0, "updatedAt": 0, "favorite": true,
    "items": [
      { "key": "yt_dQw4w9WgXcQ", "position": 0, "addedAt": 0 },
      { "key": "yt_9bZkp7q19f0", "position": 1, "addedAt": 0 }
    ]
  }],
  "favorites": [ { "key": "yt_dQw4w9WgXcQ", "addedAt": 0 } ],
  "stats": [ { "day": "2026-11-15", "key": "yt_dQw4w9WgXcQ", "listenDurationMs": 210000,
               "playCount": 1, "title": "…", "artist": "…", "album": "…", "lastListenedAt": 0 } ],
  "lyrics": [ { "key": "yt_dQw4w9WgXcQ", "file": "lyrics/yt_dQw4w9WgXcQ.lrc",
                "synced": true, "source": "LRCLIB", "providerId": "lrclib",
                "sourceUrl": "https://lrclib.net/api/get/123", "fetchedAt": 0,
                "confidence": 0.93, "language": "es" } ]
}
```
- **Orden**: `playlists[].items[].position` es autoridad. El importador ordena por `position` y valida
  que no haya huecos ni duplicados (si los hay: se normaliza 0..n-1 y se informa).
- **Relaciones**: solo por `key`. Un `key` ausente en `tracks` se importa como "referencia huérfana"
  con los metadatos mínimos que traiga el item; nunca se rompe la playlist.
- **Portadas**: `kind` ∈ `FILE` (dentro del zip) | `URL` (remota, se guarda la URL) | `NONE`.
  Se deduplican por hash de **bytes**: la misma portada de álbum en 40 pistas ocupa un archivo.

### 7.4 Identidad de pista (`key`)
1. Si hay id de servicio: `yt_<videoId>`, `sp_<hash>`, `am_<id>`.
2. Si es local: `local_<sha256(rutaRelativa + tamaño)[:16]>` (la ruta relativa es informativa; nunca se
   usa para abrir nada en otro equipo).
3. Si no hay nada: `meta_<sha256(normalize(title)|normalize(artist)|album|round(durationMs/2000))[:16]>`.
`normalize()` = minúsculas, sin acentos, sin `feat./ft.`, sin `(Remastered)`, sin corchetes de vídeo.
La misma función se implementa en Android y Desktop y **se prueba con los mismos vectores** (test
cruzado), o los favoritos no se reconciliarán.

### 7.5 Validación de importación (orden exacto)
1. **Abrir**: límite 2 GiB y 5 000 entradas (configurable). Leer solo el directorio central.
2. **Zip Slip**: por cada entry rechazar si `name` empieza por `/`, contiene `\`, `:` de unidad, o tras
   normalizar (`Path.of(name).normalize()`) sale del staging (`!resolved.startsWith(root)`), o es un
   symlink. Cualquier violación → **rechazo total**, no importación parcial.
3. **Manifiesto**: `format == "cypher-library"`, `formatVersion` entero ≤ soportado,
   `minReaderVersion` ≤ versión del lector; si no, error con mensaje ("Este ZIP es de una versión más
   nueva de Cypher").
4. **Estructura**: `library.json` presente; rutas de `files[]` dentro de la lista blanca; todo entry del
   zip está declarado en el manifiesto y viceversa.
5. **Integridad**: SHA-256 de cada archivo contra el manifiesto. Un solo fallo → abortar e informar qué
   archivo falló. Se informa también el caso "bytes declarados ≠ bytes reales".
6. **Bombas**: ratio de compresión > 200:1 por entry y total descomprimido > 2× lo declarado → abortar.
7. **Esquema**: `library.json` parseado a un modelo; se rechazan claves desconocidas *críticas*
   (`schema` > 1) pero se toleran las nuevas dentro de v1.
8. **Vista previa** (sin escribir nada): nº de playlists, canciones únicas, favoritos, portadas, letras,
   días de estadísticas, conflictos detectados y bytes a escribir.
9. **Resolución de conflictos** (por el usuario, por defecto "conservar ambos"):
   - Playlist existente: misma `sourceUrl` → actualizar / duplicar / omitir. Sin `sourceUrl` y mismo
     nombre → las tres opciones + "fusionar".
   - Pista existente: por `key` → reutilizar metadatos del existente (no sobrescribir) / sobrescribir.
   - Favoritos y estadísticas: unión por defecto (las stats se suman por `(day,key)`).
10. **Aplicar**: copia de seguridad (`library-pre-import-<ts>.json` en Desktop; copia de Room a
    `cypher_backup_pre_import.zip` vía `VACUUM INTO` en Android si algún día importa), escribir en
    staging, y **commit atómico**:
    - Desktop: nuevo `LibraryData` en memoria → `Storage.save` (tmp + `ATOMIC_MOVE`).
    - Android: transacción Room única.
11. **Reintento**: staging se borra siempre; si el proceso muere, el `library.json` previo sigue intacto
    y el staging queda en `.cypher-import-<uuid>.tmp` para limpieza en el siguiente arranque.
12. **Informe**: resumen con lo importado, lo omitido y los motivos.

### 7.6 Audio en el ZIP
- Por defecto **fuera**. Interruptor explícito "Incluir archivos de audio locales (N archivos, X MB)"
  con espacio libre comprobado (necesita X + 10 %) y solo archivos que el usuario ya posee
  (`MediaStore`/carpeta elegida).
- Nunca audio de YouTube/streaming: de esas pistas se exporta `source.externalId` y metadatos.
- Si el espacio no alcanza: se ofrece exportar sin audio, o solo una selección.
- Con audio incluido, `content.audio = true`, `audio/index.json` relaciona `key` → archivo + hash.

### 7.7 Pruebas exigidas
| Test | Plataforma | Qué comprueba |
|---|---|---|
| Export pequeña / mediana / grande (5 / 500 / 5 000 pistas) | Android (JVM) | estructura, orden, hashes, tamaño, tiempo |
| Export sin audio, sin portadas, sin letras | Android | degradación limpia, manifiesto coherente |
| Import ZIP válido → misma biblioteca | Desktop | round-trip exacto (orden, relaciones, portadas, favoritos, stats) |
| ZIP corrupto (byte alterado) | Desktop | rechazo + archivo señalado |
| Hash incorrecto en manifiesto | Desktop | rechazo |
| Zip Slip (`../../evil`, `C:\evil`, `/abs`) | Desktop | rechazo total, nada escrito |
| Bomba zip (10 KB → 1 GB) | Desktop | rechazo por ratio |
| Orden con `position` duplicados/huecos | Desktop | normalización + aviso |
| Duplicados (misma playlist y misma pista) | Desktop | las 3 políticas |
| Import interrumpido (kill antes del commit) | Desktop | biblioteca previa intacta, sin staging huérfano |
| Formato antiguo (v0 del JSON actual) | Desktop | migración y conservación |
| Formato futuro (`formatVersion: 9`) | Desktop | rechazo con mensaje |
| Ajustes transportados | Desktop | los 12 grupos de §6 llegan y persisten |
| Vectores de `normalize()` idénticos | ambos | mismo resultado para 20 casos |

"Pruebas cruzadas": el test de Android genera ZIPs reales en `desktop/src/test/resources/`, y el test de
Desktop los importa. Ese par es la garantía que sustituye al móvil físico.

---

## 8. Importación mediante cuenta (independiente de Cypher)

### 8.1 Matriz de servicios
| Servicio | Vía oficial | Playlists privadas | Audio | Veredicto |
|---|---|---|---|---|
| **YouTube Music** | YouTube Data API v3 + OAuth 2.0 `youtube.readonly` (`playlists.list?mine=true`, `playlistItems.list`) | **sí**, con `mine=true` y consentimiento | no (solo metadatos) | **Se implementa** en ambos |
| Spotify | CSV/ZIP exportado por el usuario (ya existe) | sí (en el export) | no | se conserva tal cual; la Web API exigiría app propia y modo desarrollador de 25 usuarios |
| Apple Music | MusicKit + developer token (Apple Developer Program, 99 $/año) | sí | no | fuera de alcance por coste; se puede añadir import CSV |
| Deezer / Tidal | OAuth + API, sin catálogo de biblioteca completo sin partnership | limitado | no | no |

### 8.2 Hechos que condicionan el diseño (verificados en documentación oficial)
- `playlists.list` cuesta **1 unidad** de cuota y `playlistItems.list` **1 unidad**; la cuota por
  defecto son **10 000 unidades/día** por proyecto. Importar 40 listas de 200 canciones ≈ 40 + 200
  llamadas paginadas ≈ muy por debajo del límite.
- `youtube.readonly` es un **scope sensible**: una app pública debe pasar la verificación de Google; sin
  ella, la pantalla de consentimiento avisa y el número de usuarios de prueba es limitado. Durante la
  beta casera (tu propio cliente OAuth, tu cuenta) funciona sin publicar nada.
- La **biblioteca de YouTube Music** se expone como playlists de YouTube. No se expone "Me gusta" como
  lista canónica ni las listas automáticas ("Supermix"), así que **no se promete** acceso a ellas.
- **Android**: `androidx.credentials` (Credential Manager) sirve solo para *autenticación*; para pedir
  un access token de un scope hay que usar **`AuthorizationClient`** (`play-services-auth`) y separar los
  dos flujos. Esto invalida el enfoque actual de la app (cookies de WebView + `SAPISIDHASH`), que además
  choca con la restricción de no copiar cookies.
- **Desktop**: el flujo PKCE + loopback ya está implementado (`GoogleAuth`) y Google lo documenta para
  apps instaladas; se recicla sin el secret embebido.

### 8.3 Experiencia (idéntica en ambos, adaptada)
`Ajustes → Importar de una cuenta` (Android) / `Importar → YouTube Music` (Desktop):
1. Tarjeta con el servicio, texto: "Cypher no crea ninguna cuenta. Solo pide permiso para leer tus
   listas durante esta importación."
2. Botón "Conectar con YouTube Music" → consentimiento de Google con el scope mínimo.
3. Lista de playlists con portada, nombre, nº de canciones y visibilidad (pública/privada).
4. "Vista previa" → canciones con estado (disponible / no disponible / ya en tu biblioteca), totales y
   qué se va a crear. **Nada se escribe hasta confirmar.**
5. Confirmar → progreso por páginas, cancelable; al terminar, resumen y "Desconectar" siempre visible.
6. Al cerrar la app **no** se reconecta sola; el token se guarda cifrado (Android:
   `EncryptedSharedPreferences`; Desktop: nunca en disco, solo memoria de la sesión).

### 8.4 Credenciales y configuración
- Desktop: cliente OAuth de tipo **Desktop app** + PKCE; el `client_id` puede vivir en el binario; el
  `client_secret` (si Google lo exige) **no** se incrusta: variable de entorno o fichero del usuario
  (ya soportado por `GoogleAuth.secretFile()`).
- Android: cliente de tipo **Android** con SHA-1 de la firma; `client_id` en `local.properties`
  (ignorado por git), nunca en el repo.
- Los scopes de importación **no** se piden al arrancar; solo al pulsar "Conectar".

### 8.5 Lo que no se hará
- No leer cookies del navegador ni de un WebView para autenticar.
- No descargar audio de YouTube.
- No pedir `youtube` (escritura) ni `youtube.force-ssl`.
- No prometer listas privadas si el permiso no las devuelve: se muestra el motivo real de Google.

---

## 9. Letras automáticas

### 9.1 Investigación de fuentes
| Fuente | API oficial | Estado | Veredicto |
|---|---|---|---|
| **Lyricsify** (`lyricsify.com`) | **no existe API pública** | sitio con letras aportadas por usuarios, descarga de `.lrc`, login; disclaimer: "All lyrics are property and copyright of their owners… educational purposes and personal use only" | **No se integra por scraping**: sin API ni permiso de acceso automatizado, raspar incumpliría sus condiciones. Queda como **importador manual/pegado** y como proveedor futuro *si* conceden permiso por escrito |
| **LRCLIB** (`lrclib.net`) | **sí**, gratis, sin clave, LRC sincronizado | ya integrada en Android (`LrcLibService`); exige `User-Agent` identificable, límites ~45/min sin identificar y ~90/min identificado, honrar `Retry-After`, espaciar 200–500 ms en lotes | **proveedor primario** |
| **lyrics.ovh** | sí (comunitario, sin clave) | solo texto plano, sin tiempos, uptime irregular (~87–92 %) | **secundario**, solo si LRCLIB no tiene nada |
| Musixmatch | sí, pero **pago** (desde 49 $/mes), licencia no comercial, caché solo en Enterprise | — | descartado |
| Genius | API solo metadatos/fragmentos; uso comercial prohibido sin licencia | — | descartado |
| NetEase / Kugou | APIs no oficiales | — | descartado por estabilidad y legalidad |

**Licencia de las letras:** todas siguen siendo propiedad de sus autores. Cypher muestra letras al
usuario que ya posee la canción, guarda la fuente y la fecha, y **no** las redistribuye ni las incluye
en un ZIP por defecto. (Decisión D7: ¿incluir letras en el ZIP? Propongo **sí**, porque es tu biblioteca
personal, pero es un punto que debes aprobar.)

### 9.2 Diseño: proveedores intercambiables
```kotlin
data class LyricsQuery(title, artist, album, durationMs, isrc?, videoId?)
data class LyricsCandidate(providerId, providerTrackId, title, artist, album,
                           durationMs, synced: Boolean, confidence: Double,
                           sourceUrl: String?, language: String?)
interface LyricsProvider {
    val id: String; val displayName: String
    val capabilities: Set<Capability>   // SYNCED, PLAIN, SEARCH, FETCH
    suspend fun search(q: LyricsQuery): List<LyricsCandidate>
    suspend fun fetch(c: LyricsCandidate): String   // LRC o texto
}
```
Cadena por defecto: `LRCLIB → lyrics.ovh → almacenado local → manual`.
Cada proveedor: timeout 8 s, reintentos 2 con backoff, `User-Agent: Cypher/<version> (<repo>)`,
respeto de `Retry-After`, caché en memoria + persistida (`LyricDoc` con `providerId`, `sourceUrl`,
`fetchedAt`, `confidence`). Un fallo de red nunca borra una letra guardada.

### 9.3 Coincidencia (evitar la versión equivocada)
1. Normalizar título y artista (misma función que §7.4).
2. Puntuar: título (0.45) + artista (0.35) + duración ±3 s (0.15) + álbum (0.05).
3. Filtros duros: si la diferencia de duración > 10 s → descartar; si el artista no comparte ningún
   token → descartar; si el título es de otra canción (similitud < 0.55) → descartar.
4. `confidence ≥ 0.85` → aplicar automáticamente y marcar "auto". `0.6–0.85` → preguntar mostrando las
   3 mejores opciones con duración y álbum. `< 0.6` → no aplicar y decir "no encontrada".
5. Preferir siempre `synced = true`.
6. Corrección manual: "Cambiar de coincidencia", "Buscar otra", "Pegar letra", "Importar .lrc",
   "Borrar letra". Se guarda `matchState = AUTO | MANUAL | IMPORTED`.

### 9.4 UI compartida
Misma jerarquía visual en ambos: línea actual en `TextPrimary` + bold, resto al `inactiveOpacity`, sin
caja contenedora, scroll automático suave centrado, salto al tocar. En Desktop dentro del panel derecho
y en la vista ampliada; en Android en la hoja actual (`CypherLyricsSheet`), que se ajusta a los mismos
tokens. Sin letras: mensaje + acciones, nunca texto inventado.

---

## 10. Plan por fases

| Fase | Alcance | Entregable | Verificación |
|---|---|---|---|
| **A** | esta auditoría | este documento + capturas | — |
| **B** | rama `feature/cypher-unificado` desde `fcab005`; retirada de Google/Supabase en Android y Desktop; migraciones 8→9 y `library.json` v2→v3; limpieza de deps, textos y estados | diff + `docs/MIGRACION-1.5.md` | `:app:assembleDebug` + `testDebugUnitTest`; `:desktop:test`; informe de migración |
| **C** | nuevo shell Desktop: rail definitivo, flyout con pestañas, inicio reordenado, panel derecho con pestañas, **vista ampliada**, biblioteca local fuera de la experiencia principal (a Ajustes), descargas y YouTube fuera del rail | capturas + app ejecutable | ejecución real en Windows (interfaz, navegación, reproductor, cola, persistencia de paneles) |
| **D** | ZIP: exportador Android + validador/importador Desktop + formato documentado + tests cruzados | `docs/FORMATO-CYPHER-LIBRARY.md`, ZIPs de prueba | tests §7.7; importación de ZIPs generados por Android en Desktop |
| **E** | importación por cuenta: `AuthorizationClient` en Android, OAuth loopback en Desktop, pantalla unificada | flujo completo | Desktop con cuenta real; Android solo compilación + tests (sin dispositivo) |
| **F** | letras (proveedores, matching, almacenamiento, UI) + personalización completa Desktop y coherencia Android | ajustes persistidos | Desktop real; Android tests |
| **G** | pruebas completas, APK, portable y MSI, informe de limitaciones, preparación de beta | artefactos locales | §11 |

**Puertas**: B no empieza sin aprobar §11 (D1–D4). D no se da por buena sin el test cruzado verde.
E no se da por buena sin una importación real autorizada por ti. G no publica nada.

### 10.1 Cómo se verifica cada plataforma (realidad, no promesas)
- **Android**: compilación (`assembleDebug`), tests unitarios JVM (exportación, normalización, migración
  de esquema vía `room-testing` en instrumentado **si** hay emulador), `lint`. **No** hay dispositivo:
  no se afirmará nada sobre tacto, rendimiento real ni aspecto en pantalla física.
- **Desktop**: ejecución real en este PC — interfaz, navegación, reproductor, cola, búsqueda, ZIP,
  personalización (cerrar/abrir), letras, importación por cuenta, instaladores.
- **Integración**: ZIPs generados por los tests de Android, importados por el importador real de Desktop.
- **Pendiente**: todo lo que requiera un teléfono (permiso de MediaStore, exportación real desde la app,
  compartir, notificación, atajos, háptica).

---

## 11. Riesgos y decisiones que necesitan tu aprobación

| # | Decisión | Opciones | Recomendación |
|---|---|---|---|
| **D1** | Base | `fcab005` / tag `v1.4.0` | **`fcab005`** (1.4.0 + 3 arreglos, cero Google) |
| **D2** | Nombres de rama | `feature/cypher-unificado` | sí, desde `fcab005`; no se toca nada existente |
| **D3** | `cloudTracks` de Desktop (favoritos venidos de la nube) | descartar / convertir a `RemoteTrack` | **convertir** los que tengan id de YouTube; descartar el resto con informe |
| **D4** | Room 8→9 | mantener tablas muertas / `DROP` de las dos tablas de bookkeeping | **DROP** (no contienen datos de usuario) |
| **D5** | Navegación | quitar del rail Biblioteca-local, Descargas, YouTube y Cuenta | **sí**, según §2.7; "Cuenta" desaparece |
| **D6** | Biblioteca local | solo en Ajustes; al activarla, ¿sus canciones vuelven a la Biblioteca general? | **no**: se ven en Ajustes > Biblioteca local y se pueden añadir a playlists; la Biblioteca general solo muestra playlists/álbumes/artistas de Cypher |
| **D7** | Letras en el ZIP | incluir / excluir por defecto | **incluir** (es tu biblioteca personal), con interruptor para excluir |
| **D8** | Audio en el ZIP | por defecto fuera | **sí, fuera por defecto**, con opción explícita |
| **D9** | ZIP: ¿Desktop→Android ya? | implementar lector en Android ahora / solo formato preparado | **solo formato** ahora; lector en una fase posterior |
| **D10** | Lyricsify | scraping / no integrar / pedir permiso | **no integrar**; LRCLIB primario + `.lrc` manual + pegado. Si quieres Lyricsify, hay que pedir permiso al sitio |
| **D11** | YouTube en Android | mantener cookies WebView / migrar a `AuthorizationClient` | **migrar**: es lo único compatible con "OAuth oficial, sin copiar cookies". Implica reconectar la cuenta una vez |
| **D12** | Verificación de Google | beta privada con tu cliente OAuth / publicar y pasar verificación de scope sensible | **beta privada** primero; la verificación es trámite aparte y puede tardar semanas |
| **D13** | Paridad de audio Desktop | implementar crossfade y temporizador / ocultar EQ y normalización | ocultar lo que no se pueda hacer bien; **crossfade y temporizador sí** |
| **D14** | Versión pública | `1.5.0` `versionCode 21` al terminar B, o reservar el 21 y usar 1.6.0 | **1.5.0 / 21** reutilizando el par ya definido, para que el WIP actualice a esta rama |

### Riesgos abiertos (sin decisión tuya, solo vigilancia)
1. **Sin móvil**: la exportación puede fallar en un dispositivo real por permisos de MediaStore,
   `content://` de portadas o espacio. Mitigación: tests + ZIPs generados + un informe explícito de lo
   no validado. No se declarará "estable" hasta que lo pruebes.
2. **Scope sensible de Google**: si publicas, la verificación puede rechazar el uso; el importador debe
   degradar a "solo enlace público" sin romper.
3. **Aproximación del cristal en Desktop**: Compose Desktop no tiene AGSL; el resultado será una
   aproximación honesta, etiquetada como tal en Ajustes.
4. **Pérdida aparente de datos al quitar la nube**: quien tenga el 1.5.0 WIP con datos sincronizados verá
   desaparecer la sección de cuenta; sus playlists locales siguen ahí. Se avisa en el changelog.
5. **`desktop/` sin versionar**: si el disco falla antes de B, se pierde. Sugerencia: commit local en una
   rama aparte antes de empezar B (lo haría solo con tu OK).

---

## 12. Restricciones registradas (se respetan en todo el plan)

Sin push, sin releases, sin APK/instaladores publicados, sin tags, sin despliegues ni cambios en
Supabase, sin borrar datos remotos ni bibliotecas, sin modificar secretos, sin importaciones reales, sin
sobrescribir instalaciones o datos personales, sin reemplazar la versión estable y sin borrar código
antiguo hasta que la nueva implementación y la migración estén verificadas.

Ramas locales, prototipos, tests y builds locales: permitidos.

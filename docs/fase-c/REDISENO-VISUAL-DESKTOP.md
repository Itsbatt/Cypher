# Rediseño visual de Cypher Desktop

Fecha: 16 de noviembre de 2026 · Referencia: PDF original de Cypher (5 páginas, en
`docs/fase-a/referencia-pdf/`).

Capturas reales de la aplicación ejecutándose en Windows: `docs/fase-c/capturas-desktop/`.
Capturas anteriores para comparar: `docs/fase-c/capturas/`.

---

## 1. Qué se ha reconstruido

### 1.1 Cabecera: tres zonas (PDF pág. 1)

| | Antes | Ahora |
| --- | --- | --- |
| Izquierda | Botón que era Inicio y flecha "volver" según la pantalla, con la etiqueta del lugar | **Un círculo con el engranaje. Solo Ajustes, siempre el mismo significado** |
| Centro | Buscador de 560 dp | Buscador centrado, misma anchura, atajo `Ctrl K` visible |
| Derecha | Avatar + nombre, y al pulsar abría Ajustes | **Avatar con iniciales + nombre + subtítulo "Perfil local" + menú** (Cambiar nombre / Ajustes) |

Archivo: `ui/App.kt`, `AppTopBar`.

### 1.2 Panel izquierdo: solo playlists (PDF pág. 2)

Antes: "Tu biblioteca" con una píldora "Listas" y un botón "Crear", más una columna de portadas
suelta en el rail con la etiqueta LISTAS.

Ahora:

- Título **Playlists** y un **"+" circular** arriba a la derecha.
- Solo la lista de playlists: portada, nombre y "N canciones".
- Sin favoritos, estadísticas, descargas, historial, música local ni YouTube.
- Se despliega **superpuesto** (el Inicio no se mueve), se cierra al salir y tiene su propia
  animación de entrada y salida.
- El rail perdió la tira de portadas: ahora hay **un** botón "Playlists" que abre el panel.

El panel es más opaco que el resto a propósito (`opacity 0.92`, `density 1.0`): es un menú sobre
contenido ocupado y el texto tiene que ganar. Con 0.74 el "Hola, Cypher" de detrás se leía a través
de la lista (se vio en la captura intermedia y se corrigió).

### 1.3 Dock inferior: flotante

Antes: el contenido reservaba 104 dp abajo y el dock se apoyaba en esa franja del fondo de la
ventana; se leía como una barra.

Ahora: el contenido **ocupa toda la altura y pasa por debajo** del dock; la única superficie es la
del propio dock, con su radio y su cristal. No hay franja, ni rectángulo negro, ni contenedor de
ancho completo. Comparar `07-reproductor-flotante.png` y `11-ventana-estrecha.png`.

Transparencia al escritorio: **no implementada**, es una decisión de arquitectura de ventana.
Opciones y coste en `docs/liquid-glass/INFORME-LIQUID-GLASS-DESKTOP.md` §8.

### 1.4 Perfil local y primer inicio

- Diálogo de bienvenida cuando `profileSetupDone` es falso: nombre, previsualización del avatar con
  iniciales, "Ahora no" y "Guardar".
- Se guarda en el documento local (`profile.displayName`, `profileSetupDone`). Sin cuenta, sin
  Supabase, sin red.
- Cambiar el nombre después: menú del perfil → "Cambiar nombre" (mismo diálogo).
- Marcar `profileSetupDone` aunque se elija "Ahora no" evita que la bienvenida reaparezca en cada
  arranque.

Captura: `00-bienvenida.png`.

### 1.5 Portadas

Ver `AUDITORIA-PORTADAS.md`. Resumen: caché persistente en disco con escritura atómica, estados
explícitos (`LOADING/AVAILABLE/MISSING/CORRUPT/OFFLINE`), una sola descarga por fuente, y mosaico
2×2 para las playlists sin portada propia.

## 2. Comparación con el PDF

| Página del PDF | Elemento | Estado |
| --- | --- | --- |
| 1 | Círculo arriba a la izquierda | Hecho (es Ajustes) |
| 1 | Buscador centrado | Hecho |
| 1 | Rail estrecho con iconos circulares | Hecho, sin la tira de portadas |
| 1 | Contenido con hero + filas de tarjetas | Hecho (hero, playlists recientes, favoritos) |
| 1 | Dock flotante con portada, título, transporte, progreso, volumen | Hecho |
| 2 | Panel izquierdo desplegado solo con playlists | Hecho |
| 2 | El panel no empuja el contenido | Hecho (superpuesto) |
| 3 | Rail colapsado con el contenido a ancho completo | Hecho |
| 4 | Vista ampliada: fondo de la portada, portada grande, letras | Hecho en fase C (`06b-vista-ampliada.png`) |
| 5 | Panel derecho con LETRAS / COLA | Hecho (`05-panel-derecho.png`, `06a-panel-cola.png`) |

Diferencias que siguen en pie: el PDF dibuja dos filas de tres píldoras donde Cypher muestra las
playlists reales del usuario; se ha respetado la jerarquía (filas + tarjetas grandes), no el
número exacto de huecos vacíos que el mock usa para rellenar.

## 3. Pruebas realizadas

| Prueba | Cómo | Resultado |
| --- | --- | --- |
| Compilación | `./gradlew compileKotlin` | Correcta |
| Suite de pruebas | `./gradlew test` | `BUILD SUCCESSFUL` |
| Capturas reales | `./gradlew run --args="--screenshots <dir>"` con `CYPHER_DATA_DIR` temporal | 13 capturas, revisadas a ojo |
| Rendimiento del cristal | `--glass-bench 240 1440 900` | 5.6 ms de media, p95 7.1 ms, `shader=on` |
| Ventana estrecha | captura a 1120×760 | Dock y panel derecho se adaptan |

Datos usados: biblioteca **ficticia** de `ScreenshotTool` en un directorio temporal. La biblioteca
personal no se ha tocado.

## 4. Problemas pendientes

1. **Transparencia real de la ventana** (§1.3): falta decidir entre ventana sin decorar, ventana por
   capas de Win32 o quedarse con el fondo de la app.
2. **Resto del rail**: sigue habiendo iconos de funciones que existen (estadísticas, Wrapped,
   descargas, YouTube, importar, favoritos). No se han quitado porque son funciones en uso; si el
   PDF manda que el rail tenga solo tres o cuatro, hay que decidir cuáles.
3. **Pruebas de portadas con casos difíciles** (corrupta, URL caída, sin conexión, reinicio): el
   código los trata, no hay test automatizado todavía.
4. **Comparación física con Android**: no hay dispositivo ni emulador, así que la comparación es
   contra el shader y sus renders, no contra una captura del teléfono.

## 5. Archivos modificados

- `desktop/src/main/kotlin/com/cypher/desktop/ui/App.kt` — cabecera en tres zonas, panel de
  playlists, rail, dock sin franja, diálogo de bienvenida.
- `desktop/src/main/kotlin/com/cypher/desktop/ui/ProfileSetup.kt` — nuevo: bienvenida y renombrado.
- `desktop/src/main/kotlin/com/cypher/desktop/core/ArtworkStore.kt` — nuevo: caché persistente.
- `desktop/src/main/kotlin/com/cypher/desktop/core/Models.kt` — `profileSetupDone`.
- `desktop/src/main/kotlin/com/cypher/desktop/app/AppState.kt` — `profileSetup`, `saveProfileName`,
  `openProfileSetup`, `closeProfileSetup`.
- `desktop/src/main/kotlin/com/cypher/desktop/ui/Components.kt` — `rememberArtworkState`,
  `PlaylistArtwork`, `Artwork` con estados.
- `desktop/src/main/kotlin/com/cypher/desktop/ScreenshotTool.kt` — captura `00-bienvenida`.
- `desktop/src/main/kotlin/com/cypher/desktop/GlassBench.kt` — modo y fondo por argumentos.

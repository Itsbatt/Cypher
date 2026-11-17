# FASE C — Rediseño del Desktop según el PDF

Fecha: 16 de noviembre de 2026
Referencia: `docs/fase-a/referencia-pdf/04-vista-ampliada-letras.png` y `05-panel-derecho.png`.
Capturas de lo implementado: `docs/fase-c/capturas/`.

---

## 1. Vista ampliada (referencia, página 4)

Archivo nuevo: `desktop/src/main/kotlin/com/cypher/desktop/ui/ExpandedPlayer.kt`.

- Fondo: copia de la carátula escalada 1,3× y desenfocada 80 dp, con degradado oscuro
  encima, para que el texto siempre tenga contraste.
- Izquierda: carátula de 356 dp con sombra, barra de progreso con tiempos, título,
  artista y los controles (aleatorio, anterior, reproducir/pausar, siguiente,
  repetición).
- Derecha: las letras a tamaño grande; la línea que suena va en blanco y negrita, el
  resto al 42 % de opacidad. Se desplaza sola y respeta `reduceMotion` porque usa el
  mismo `animateScrollToItem` que el panel.
- Se abre desde el icono de pantalla completa del panel derecho
  (`state.expandPlayer()`) y se cierra con la flecha o con **Esc**.
- **No toca la reproducción**: es la misma instancia del reproductor, solo se pinta
  más grande. Cerrarla no pausa nada.

## 2. Panel derecho con pestañas (referencia, página 5)

`RightInfoPanel` en `ui/App.kt`:

- Cabecera «EN REPRODUCCIÓN» con dos acciones: **vista ampliada** y **contraer panel**.
- Pestañas **LETRAS** y **COLA**, con el mismo lenguaje visual que el resto (píldora
  teñida con el acento).
- **COLA** lista la cola real: carátula, título, artista y un ecualizador en la pista
  que está sonando; pulsar una fila salta a ella. Distingue la pista actual
  (`queueIndex`) de la que suena en el reproductor.
- La pestaña **LETRAS** conserva todo lo anterior: procedencia del documento, texto
  sincronizado desplazándose solo, y el botón de importar `.lrc` cuando no hay letras.
- Cualquier cambio de pestaña reabre el panel si estaba contraído, para que el clic
  nunca parezca no hacer nada.

Estado nuevo en `UiState`: `rightPanelTab` y `playerExpanded`, con
`setRightPanelTab`, `expandPlayer`, `collapsePlayer` y `toggleExpandedPlayer`.

## 3. Biblioteca local en Ajustes

La sección **BIBLIOTECA** pasa a llamarse **BIBLIOTECA LOCAL** y empieza con el
recuento real (`N pistas · N álbumes · N listas en este equipo`). Debajo siguen, como
estaban, la pantalla inicial de la biblioteca, la carpeta elegida con
«Elegir carpeta» y «Volver a escanear», y la lista de formatos. La reproducción no
cambia: los mismos archivos, el mismo reproductor.

## 4. Inicio

El orden de secciones ya coincide con el del prototipo (continuar → playlists
recientes → favoritos → artistas → recientes) y el bloque «DE TU CUENTA» desapareció
en la FASE B. **No he reescrito el hero**: el boceto de la página 5 es un wireframe de
filas de placeholders y no da suficiente información para justificar tirar el diseño
actual. Queda anotado por si quieres que lo cambie con una indicación más concreta.

## 5. Verificación

| Comprobación | Resultado |
| --- | --- |
| `./gradlew compileKotlin` | BUILD SUCCESSFUL |
| `./gradlew test` | **85 tests, 0 fallos** (incluye el nuevo test de estado de pestañas y vista ampliada) |
| `--screenshots` con `CYPHER_DATA_DIR` temporal | 14 capturas generadas, sin excepciones |
| Inspección visual | `capturas/vista-ampliada.png`, `capturas/panel-letras.png`, `capturas/panel-cola.png` |

El test nuevo comprueba que abrir una pestaña no cierra el panel, que elegir pestaña
lo reabre, que la vista ampliada se abre, se cierra y se alterna, y que nada de eso
altera la biblioteca.

Nota honesta: en la captura de **COLA** la lista aparece vacía porque el generador de
capturas usa `player.preview()` para no reproducir audio y no siembra cola. La pestaña
sí pinta la cola real cuando existe; lo que no he hecho es una captura con cola
poblada.

## 6. Pendiente dentro de la FASE C

- Captura de la pestaña COLA con una cola real.
- Decisión sobre el hero de Inicio (mantener el actual o replicar el wireframe).
- Las pestañas «relacionado»/«vídeo» del prototipo no se han añadido: el panel tiene
  dos, que son las que tienen datos detrás hoy.

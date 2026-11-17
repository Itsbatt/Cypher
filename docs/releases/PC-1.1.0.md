# Cypher PC 1.1.0

La versión de escritorio se acerca a la del móvil: el fondo de las letras es el mismo
cristal líquido, los controles viven sobre la carátula y YouTube se reproduce con un iframe
en vez de descargar la canción.

## Letras

- **Fondo líquido animado** con los colores de la portada: tres campos que se cruzan y se
  pliegan, filos de vidrio, cáusticas y un reflejo que barre la pantalla. El movimiento ya
  no se detiene cuando nada más se está animando.
- **Color de la letra**: acento, **portada** (el tono dominante de la carátula) o
  **personalizado**, con selector de color propio.
- **Efectos nuevos**: *Líquido* y *Neón*, además de los que ya había. Todos animan.
- La vista previa de Ajustes › LETRAS usa la portada de la canción que está sonando, así que
  lo que se elige es lo que se ve después.

## Pantalla ampliada

- Carátula más grande.
- Los controles y la barra de duración aparecen **encima de la carátula** al pasar el ratón,
  no debajo.
- Clic en la carátula para play/pausa.

## YouTube

- Las canciones de YouTube se reproducen con **Chromium embebido (JCEF)**: la canción se
  emite, no se descarga. La app conserva título, carátula, progreso y letra sincronizada.
- El vídeo se muestra en una ventana propia del reproductor («YouTube · canción»): Chromium
  pinta su propia superficie y no puede dibujarse dentro de la ventana de la app.
- Si YouTube no permite insertar el vídeo (privado, con restricción de edad o con la
  inserción desactivada), la app lo detecta y vuelve sola al modo anterior: descarga y
  reproduce el archivo.
- **La primera vez** que se abre YouTube, Chromium descarga sus binarios (unos 150 MB) en
  `%APPDATA%\Cypher Desktop\cef`. A partir de ahí arranca al instante y sin conexión a
  GitHub.

## Instalación

- `Cypher-1.1.0.msi`: instalador por usuario, sin permisos de administrador. No toca tu
  biblioteca (`%APPDATA%\Cypher Desktop`).
- `Cypher-1.1.0-windows-portable.zip`: misma app sin instalar, solo descomprimir.

Sin cuentas ni nube: la biblioteca, las letras y los ajustes siguen siendo tuyos y locales.

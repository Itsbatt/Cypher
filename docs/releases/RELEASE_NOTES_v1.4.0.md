# Cypher 1.4.0

## Reproducción
- Al cambiar a una canción que no está descargada, la reproducción ya no se queda en pausa: el motor de YouTube avisa de una pausa falsa mientras carga el vídeo nuevo y eso se confundía con una pausa real. Ahora solo cuenta la pausa de la canción pedida o la que pides tú.
- Si esa pausa falsa llega igualmente, el reproductor vuelve a arrancar el vídeo (como máximo dos veces por canción) en lugar de dejar la cola parada.
- **Las canciones sin descargar ya suenan.** El vídeo incrustado de YouTube se quedaba cargando para siempre en varios dispositivos, así que la app mostraba la canción avanzando mientras solo se reproducía una pista muda de relleno. Ahora el audio sale del reproductor nativo con la URL directa del tema, que es la misma vía que usan las descargas; el vídeo incrustado queda solo como respaldo si esa URL no se puede obtener.
- El volumen, el fundido, el ecualizador y los controles (pausa, siguiente, barra de progreso, notificación y pantalla de bloqueo) siguen al motor que esté sonando de verdad.
- Vuelven los botones de la notificación en todos los móviles: la sesión no anunciaba las órdenes de play/pausa ni de salto, así que Samsung dibujaba el panel multimedia sin controles. Ahora la notificación la genera el propio reproductor y la sesión declara esas órdenes.
- La música ya no se para al acabar una canción con la pantalla apagada o con otra app delante: mientras hay cola sonando se mantiene despierta la CPU y la conexión, así que al terminar un tema arranca el siguiente en vez de morir el proceso.

## Proyecto
- Workflow de GitHub Actions en `ci/android.yml`: tests JVM, compilación de los APK y publicación firmada al empujar un tag `v*`. Queda activado en cuanto un push con scope `workflow` lo mueva a `.github/workflows/`.

## Notas
- `versionCode` 19, `versionName` 1.4.0.
- Pendiente de probar un día completo en el móvil antes de publicar la release.

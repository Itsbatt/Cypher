# Cypher 1.3.1

## Interfaz
- El mini reproductor vuelve a verse transparente: el contenido se desplaza por detrás y se ve alrededor de la píldora, como antes, sin la franja muerta que dejaba el arreglo anterior.
- Cada lista reserva su propio espacio al final, así que el dock nunca tapa el último título.

## Reproducción
- Una canción que no puede sonar ya no desaparece en silencio: se reintenta una vez y se avisa antes de pasar a la siguiente.
- «Anterior» se detiene en la primera canción cuando la repetición está desactivada, igual que «Siguiente» al final de la cola.
- Al desactivar el aleatorio, la cola vuelve al orden original de la playlist.
- La notificación ya no hereda la carátula de la canción anterior y sus acciones están en español.

## Descargas
- Las descargas rechazadas ya no dejan archivos basura en el almacenamiento.
- Eliminar una descarga revisa todos los volúmenes y avisa cuando no se pudo borrar.
- La comprobación de descargas ya no recorre el disco al dibujar cada fila.

## Búsqueda e idioma
- La búsqueda por voz o asistente (`MEDIA_PLAY_FROM_SEARCH`) abre Buscar con la consulta.
- Las duraciones usan dígitos latinos en cualquier idioma.

## Librerías
- media3 1.11.1 (exoplayer, session, ui, datasource, database) y sin la dependencia heredada `androidx.media`.

## Notas
- `versionCode` 18, `versionName` 1.3.1.
- Es la 1.3.0 con el aspecto del mini reproductor restaurado; usa esta versión para actualizar desde la propia app.

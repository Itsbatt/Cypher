# Cypher 1.3.0

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

## Interfaz
- El contenido vuelve a desplazarse por detrás del mini reproductor, que conserva su aspecto transparente, y cada lista reserva espacio al final para que el dock no tape el último título.
- La pantalla principal deja de recomponerse con cada tick de progreso; solo se actualiza la barra.

## Librerías
- media3 1.11.1 (exoplayer, session, ui, datasource, database) y sin la dependencia heredada `androidx.media`.

## Notas
- `versionCode` 17, `versionName` 1.3.0.
- Probado en CPH2579 y en la tablet SM-T580.

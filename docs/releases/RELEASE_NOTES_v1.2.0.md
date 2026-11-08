# Cypher 1.2.0

## Cola de reproducción inteligente
- Reordenar canciones desde la cola, con asas visibles y orden sincronizado con el reproductor.
- Eliminar una canción o la que está sonando (salta a la siguiente sin errores).
- Vaciar la cola con confirmación; la biblioteca, las playlists y las descargas no se tocan.
- Añadir como siguiente o al final, con control de duplicados por identidad de pista.

## Biblioteca ordenable
- Orden por fecha de añadido, artista, álbum o título, ascendente y descendente.
- El criterio se conserva al navegar y no altera playlists ni cola.

## El reproductor recuerda dónde estabas
- Se restauran canción, posición, cola, orden, aleatorio y repetición.
- Siempre en pausa: la pista se carga al pulsar play, sin audio inesperado.

## Descargas más claras
- Agrupadas por playlist descargada, con las canciones individuales en su propio bloque.
- Progreso y estado reales, errores con motivo y reintento, sin duplicar archivos.
- Las descargas antiguas se conservan: no hay migración destructiva ni cambios de esquema.

## Inicio personalizable
- Activa, desactiva y reordena las secciones de inicio; incluye favoritas, recientes y accesos rápidos.
- Playlists favoritas con estrella. Las preferencias persisten entre sesiones.

## Reproducción
- La barra de progreso ya no se queda a 0 cuando el WebView no entrega los eventos del reproductor: la posición avanza con el tiempo real y se resincroniza cuando llegan los datos reales.
- El supervisor de arranque ya no reinicia la pista en el segundo 0 ni la salta cuando no recibe eventos, evitando cortes sobre música que está sonando.
- Se eliminan los parámetros del IFrame que provocaban el error 2 en WebView antiguos.

## Comprobado en dispositivo
- Samsung SM-T580 (Android 10): reproducción con posición avanzando (0:21 → 0:37) y duración correcta.
- Samsung SM-S901E (Android 16): reproducción de descargas, siguiente/anterior y pausa sin regresiones.
- Cola, orden de biblioteca, restauración de sesión y personalización del inicio probadas en dispositivo real.

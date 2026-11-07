# Cypher 1.0.8

Versión de mantenimiento centrada en el control de la reproducción, el versionado y la documentación. Mantiene todo lo de la 1.0.7: gestos para cambiar de canción e importación de Spotify sin cuenta.

**Build 10** · `versionCode 10` · `versionName 1.0.8`

## Gestos más naturales

- La portada del reproductor y el contenido del MiniPlayer siguen ligeramente el dedo mientras arrastras (32 % del recorrido, con tope de 56 dp y 40 dp).
- Si no superas el umbral de 56 dp, la portada vuelve a su sitio con un muelle.
- Al cambiar de canción se mantiene el deslizamiento corto de entrada y salida en la dirección del gesto.
- El gesto vertical de cerrar, el toque para abrir letras y los botones siguen funcionando igual.

## Cola de reproducción

- Buscar en la cola por título o artista, con contador de resultados y opción de limpiar.
- Reordenar arrastrando: mantén pulsado el asa de una pista y arrástrala. La pista en reproducción no se mueve y el índice actual se conserva.
- Se mantienen el modo aleatorio, la repetición y la eliminación individual.

## Actualizaciones y versionado

- Cada compilación publicada sube su `versionCode` (esta es la 10), así que dos APK de la misma versión ya no se confunden.
- El aviso de recompilación de una misma versión deja de aparecer sobre Inicio: solo se muestra dentro de Ajustes.
- Las notas indican el número de build para identificar el APK exacto.

## Documentación

- README actualizado al estado real, con los nombres de APK correctos y enlace a Releases.
- Notas anteriores archivadas en `docs/releases/`.
- Ficha técnica actualizada con versión, ruta y dispositivo de pruebas actuales.

## Instalación

- `app-arm64-v8a-release.apk`: móviles ARM de 64 bits.
- `app-universal-release.apk`: cualquier dispositivo compatible.
- `SHA256SUMS.txt`: hashes para comprobar la descarga.

Tus playlists, favoritos, descargas y ajustes se conservan al actualizar.

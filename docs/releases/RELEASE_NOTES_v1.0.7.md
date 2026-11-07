# Cypher 1.0.7

Esta versión añade gestos para cambiar de canción sin abrir menús, y mantiene todas las mejoras de importación de Spotify de la 1.0.6.

## Gestos para cambiar de canción

- **Reproductor a pantalla completa**: desliza el dedo sobre la portada del álbum. Hacia la **izquierda** pasa a la **siguiente** canción; hacia la **derecha** vuelve a la **anterior**.
- **MiniPlayer**: el mismo gesto funciona directamente sobre la píldora de reproducción, sin necesidad de abrir el reproductor.
- Umbral de **56 dp** para que un toque o un pequeño temblor sobre los botones no cambie de canción por accidente.
- El gesto horizontal no interfiere con los controles de reproducción, pausa, favorito, cola ni con el gesto vertical para expandir o cerrar el reproductor. Se respeta la cola, el historial y el modo aleatorio/repetir.
- Respuesta háptica al cambiar de canción si tienes la vibración activada.
- La portada del reproductor y el contenido del MiniPlayer se deslizan brevemente en la dirección del gesto al entrar la canción nueva, para que el cambio se sienta inmediato y natural.

## Importación desde Spotify (heredado de 1.0.6)

- Importa toda tu biblioteca **sin cuenta y sin Premium**: ZIP con todas las playlists, o un único CSV/JSON exportado gratis desde Spotify. Incluye playlists privadas y «Me gusta».
- El ZIP se lee sin extraerlo: detecta todas las playlists, conserva sus nombres y aguanta cientos de playlists y miles de canciones. Un archivo dañado se indica con su nombre y el motivo sin cancelar el resto.
- Eliges qué playlists transferir, con progreso en tiempo real y opción de cancelar. Al terminar hay un resumen con las canciones no encontradas y botones para copiar o compartir la lista.
- Sin duplicados: reimportar actualiza la playlist existente y añade solo lo que falta; si ya existe una con el mismo nombre, se fusiona conservando tus canciones, portada y descripción.
- También puedes importar una playlist pública pegando su enlace.

## Otros cambios

- Eliminada por completo la conexión con cuenta de Spotify: ya no hay OAuth, Client ID ni pantalla de «Conectar con Spotify».
- La app mantiene tus playlists, favoritos, descargas y ajustes existentes al actualizar.

## Instalación

Descarga el APK correspondiente a tu dispositivo:

- `app-arm64-v8a-release.apk`: móviles ARM de 64 bits (la mayoría).
- `app-universal-release.apk`: cualquier dispositivo, incluye todas las arquitecturas.
- `SHA256SUMS.txt`: hashes para comprobar la descarga.

Comprueba el archivo con `sha256sum -c SHA256SUMS.txt` si quieres verificar la integridad antes de instalar.

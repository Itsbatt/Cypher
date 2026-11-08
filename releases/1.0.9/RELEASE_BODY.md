## CYPHER 1.0.9

**Compartir fragmentos de letras**
- Selección de hasta **6 líneas consecutivas**: toca la primera y la última, o mantén pulsada una línea y arrastra. Nunca se mezclan versos sueltos.
- Tres diseños propios (Minimal, Portada, Tipografía) y formatos Historia 1080×1920 y Cuadrado 1080×1080, renderizados a resolución final y con previsualización WYSIWYG.
- Envío por el menú nativo de Android.

**Cypher Wrapped**
- Minutos totales, top 5 canciones, artistas con porcentaje de escucha, top 5 álbumes, géneros, canción más repetida y día pico.
- El álbum de las pistas de YouTube se lee de la descripción pública del vídeo; si la fuente no lo publica, se muestra el estado honesto en lugar de inventarlo.
- Nuevo botón para **compartir el Wrapped** como imagen, con los mismos formatos.

**Guardar en galería**
- Las tarjetas de letras y de Wrapped se guardan en `Pictures/Cypher` mediante MediaStore, además de poder compartirse.

**Portadas y recomendaciones**
- Miniaturas `maxresdefault` (1280×720) con retroceso automático a `hqdefault` cuando el vídeo no tiene maxres.
- Un único `ImageLoader` de Coil y caché de paleta acotada: menos memoria y sin tirones al cambiar de pista.
- Recomendaciones con deduplicación, exclusión de lo recién escuchado y géneros reales, con estado vacío cuando no hay historial.

**Reparaciones**
- «Reproducir a continuación» y «Añadir a la cola» vuelven a actuar sobre la cola.
- El botón de cola se anuncia como «Cola de reproducción».
- «Top Picks for You» pasa a «Elegido para ti».

Requiere Android 13 o superior. Usa el APK `arm64-v8a` en la mayoría de móviles y el `universal` si tu dispositivo no es ARM64. Incluye `SHA256SUMS.txt` para verificar la descarga.

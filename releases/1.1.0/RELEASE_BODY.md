## CYPHER 1.1.0

**Ahora funciona desde Android 10**
- Requisito mínimo bajado de Android 13 a **Android 10 (API 29)**: permisos de audio y notificaciones adaptados por versión, alarma exacta solo donde existe y arranque sin llamadas a APIs posteriores.
- Liquid Glass con degradado propio en Android 10–12 y el efecto completo en Android 13+, sin cierres al abrir el reproductor.
- Diseño adaptativo real para **tablet**: anchos compacto, medio y expandido en lugar de una sola columna estirada.

**Portadas que antes no se veían**
- Las miniaturas del proveedor apuntaban a un host que respondía error; ahora se usa el fotograma real de YouTube derivado del id de la pista, con respaldo `maxresdefault → hqdefault`.
- Las portadas se piden a 1080 px en lugar de 60 px, así que dejan de verse borrosas en pantallas grandes.
- Un único `ImageLoader` con caché de disco y DNS resiliente: menos memoria, sin recargas al cambiar de pista.

**Reproductor**
- El arranque se supervisa de verdad: espera al estado real de reproducción, reintenta a los 15 s y salta de pista si falla, en vez de fingir que suena.
- Recuperación cuando el reloj de reproducción se queda congelado.
- Al terminar la cola el estado deja de decir «reproduciendo».

**Cypher Wrapped**
- Disponible solo del **15 de diciembre al 10 de enero**; fuera de ese periodo no aparece ni se puede abrir por accesos internos.
- Cuatro diapositivas: minutos, top de canciones, artistas y resumen.
- Sin álbumes ni géneros inventados: solo lo que la fuente publica de verdad.

Usa el APK `arm64-v8a` en la mayoría de móviles y el `universal` si tu dispositivo no es ARM64. Incluye `SHA256SUMS.txt` para verificar la descarga.

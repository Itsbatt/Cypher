# Cypher PC 1.3.0 — Cypher Connect, Menú Contextual en Cascada, Ecualizador 10 Bandas, Karaoke F11 y Formato .cypherpack

Cypher PC 1.3.0 es una actualización mayor que introduce conectividad inalámbrica con el móvil, herramientas de audio profesional, pantalla completa inmersiva, un nuevo menú de contexto con submenús en cascada y soporte nativo para el formato de respaldo universal multiplataforma.

## Novedades Principales

### 1. Menú Contextual Compuesto con Submenú en Cascada y Bordes Redondeados
- **Llistas ordenadas y limpias:** Las playlists ya no se acumulan en el menú principal. Al pasar el cursor o hacer clic sobre **«Añadir a lista»**, se despliega a la izquierda un submenú lateral conectado.
- **Buscador en tiempo real:** Incluye el campo **«Busca una lista»** para filtrar al instante cualquier playlist de tu biblioteca.
- **Creación inmediata («+ Nueva lista»):** Crea una playlist y añade la pista seleccionada con un solo clic.
- **Indicador de pertenencia:** Muestra un visto (`✓`) junto a las listas que ya contienen dicha canción.
- **Estética fluida:** Esquinas redondeadas (`16.dp` exterior, `8.dp` interior) con bordes finos idénticos al selector de archivos (`FilePicker`).
- **Clic derecho Win32:** El menú se abre tanto pulsando los 3 puntos como haciendo clic derecho directamente en cualquier pista.

### 2. Cypher Connect (Control Remoto Wi-Fi / LAN)
- **Servidor local WebSocket / HTTP:** Controla la reproducción de Cypher PC desde tu móvil u otro dispositivo en tu red local.
- **Emparejamiento instantáneo con código QR:** Pulsa el botón de Connect en la barra de control para mostrar un QR y escanearlo desde la app móvil de Cypher o cualquier navegador web.
- **Sincronización en tiempo real:** Reproduce, pausa, salta canciones, visualiza letras y ajusta el volumen desde cualquier rincón de la casa.

### 3. Ecualizador Nativo de 10 Bandas & Presets
- Ajuste acústico preciso con 10 bandas de frecuencia (31 Hz a 16 kHz) y control de ganancia.
- Presets optimizados incluidos: *Flat, Bass Boost, Treble Boost, Rock, Pop, Vocal, Acoustic y Electronic*.

### 4. Radio Infinita Dinámica (Start Radio / Mix)
- Al pulsar el botón de Radio en cualquier canción, Cypher genera automáticamente una cola interminable de pistas similares y recomendaciones dinámicas utilizando el motor de InnerTube.

### 5. Modo Pantalla Completa Inmersivo / Karaoke (F11)
- Pulsa `F11` para transformar la interfaz en una experiencia inmersiva limpia: carátula envolvente, fondo ambiental fluido reactivo y letras sincronizadas gigantes ideales para monitores secundarios, proyectores o televisores.

### 6. Temporizador de Apagado (Sleep Timer) con Fundido Suave
- Programa el apagado de la música tras 15, 30, 45, 60 minutos o al finalizar la canción actual, reduciendo gradualmente el volumen antes de pausar para no interrumpir el descanso.

### 7. Exportación de Tarjetas de Letras (Lyric Share Cards)
- Selecciona estrofas de la letra sincronizada y expórtalas al instante como imágenes estéticas a alta resolución (1080p) o cópialas al portapapeles para compartirlas en Discord o redes sociales.

### 8. Transición Suave entre Canciones (Crossfade & Gapless)
- Mezcla progresiva ajustable entre pistas para eliminar silencios bruscos y mantener un flujo musical continuo.

### 9. Formato de Respaldo Universal `.cypherpack` y Asociación en Windows
- Nuevo estándar unificado de respaldo para biblioteca, playlists, letras, favoritos e historial.
- Doble clic en Windows para importar automáticamente cualquier archivo `.cypherpack` creado en PC o en Android.

### 10. Aislamiento Estricto del Canal de Actualizaciones
- El cliente de actualización de escritorio ahora consulta exclusivamente los releases específicos de PC (`pc-v*`), exige la presencia de instaladores `.msi` y gestiona el orden de versiones semver limpiamente, sin colisiones con los releases de Android.

## Instalación

- **`CypherMusic-1.3.0.msi`**: Instalador recomendado para Windows (instalación limpia por usuario, sin necesidad de permisos de administrador).
- **`CypherMusic-1.3.0-windows-portable.zip`**: Versión portable lista para usar; descomprimir y ejecutar directamente en cualquier carpeta o memoria USB sin instalar nada.

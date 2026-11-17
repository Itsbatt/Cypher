# Cypher PC 1.2.0 — Discord Rich Presence, Minimizar a Bandeja, Atajos Globales y Normalización

Cypher PC 1.2.0 es una gran actualización enfocada en integración de sistema, presencia social y calidad de escucha en Windows.

## Novedades

### 1. Discord Rich Presence (RPC) Nativo
- **Integración directa:** Cypher se conecta localmente al cliente de Discord a través de la tubería Win32 (`\\.\pipe\discord-ipc-0`..`9`) sin depender de librerías DLL externas.
- **Información en tiempo real:** Muestra en tu perfil de Discord la canción que estás escuchando, el artista, el álbum, la carátula en alta resolución y la barra de progreso en vivo.
- **Botón interactivo:** Permite a tus amigos abrir y escuchar la canción directamente en YouTube Music.
- **Configurable:** Activable o desactivable desde la pestaña de *Ajustes*, con soporte para indicar un Client ID personalizado.

### 2. Minimizar a la Bandeja del Sistema (System Tray)
- **Segundo plano limpio:** Cypher añade un icono nativo junto al reloj de Windows.
- **Menú contextual rápido:** Controla la música con clic derecho (*Reproducir / Pausa*, *Siguiente*, *Anterior*, *Mostrar Cypher* o *Salir*).
- **Minimizar al cerrar:** Al pulsar la `X`, la ventana se oculta en la bandeja para seguir disfrutando de la música sin ocupar espacio en la barra de tareas.

### 3. Atajos de Teclado Globales (Win32)
- Controla Cypher desde cualquier videojuego, editor o aplicación a pantalla completa:
  - `Ctrl + Alt + Espacio`: Reproducir / Pausar.
  - `Ctrl + Alt + Flecha Derecha`: Siguiente pista.
  - `Ctrl + Alt + Flecha Izquierda`: Pista anterior.
  - `Ctrl + Alt + Flecha Arriba`: Subir volumen (+5%).
  - `Ctrl + Alt + Flecha Abajo`: Bajar volumen (-5%).

### 4. Normalización de Volumen Dinámica
- Nivelación suave de ganancia para evitar sobresaltos de volumen entre pistas con diferente masterización o canciones de YouTube.
- Conmutable con un clic desde *Ajustes*.

### 5. Cero Fricción con Descargas Offline (`yt-dlp`)
- Si decides descargar playlists enteras para escucharlas offline y tu equipo no tiene `yt-dlp.exe`, Cypher lo descarga e inicializa silenciosamente en segundo plano sin errores ni configuración manual.

## Instalación

- **`CypherMusic-1.2.0.msi`**: Instalador por usuario de Windows (sin permisos de administrador). Ejecutable renombrado a `CypherMusic.exe` para evitar falsos positivos con el catálogo de juegos de Discord y asegurar una integración limpia.
- **`CypherMusic-1.2.0-windows-portable.zip`**: Versión portable completa con runtime incluido; descomprimir y ejecutar en cualquier PC o memoria USB.

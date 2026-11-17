# Paridad funcional Android 1.5.0 → Desktop 0.2

Inventario sacado del código Android (`ui/screens`, `sync`, `data/room`, `SettingsScreen`),
no de una lista de deseos. "Implementado" significa que hay código y prueba o verificación;
"verificado" indica además cómo se comprobó.

| Función de Android | Estado en Desktop | Cómo se comprobó |
| --- | --- | --- |
| Biblioteca: canciones | Implementado | `AppStateTest`, `UiSmokeTest` |
| Biblioteca: álbumes con portada | Implementado (agrupado por etiqueta `album`) | `AppStateTest.albums`, render |
| Biblioteca: artistas | Implementado | `AppStateTest.albums and artists`, render |
| Búsqueda por canción/artista/álbum | Implementado | `AppStateTest.search` |
| Ordenación (título/artista/álbum/añadido/duración) | Implementado | `AppStateTest.sorting` |
| Reproducir / pausar | Implementado, verificado con audio real | `--self-test` (posición 260 ms) |
| Anterior / siguiente | Implementado (cola) | Revisión de código; sin prueba de audio continua |
| Barra de progreso y búsqueda en la pista | Implementado | `--self-test` + UI |
| Volumen y persistencia del volumen | Implementado | `PlaybackTest` (setVolume), `StorageTest` |
| Aleatorio | Implementado (reordena la cola) | Revisión de código |
| Repetición (off/toda/una) | Implementado, se guarda en preferencias | Revisión de código |
| Cola de reproducción visible | Implementado (menú de cola) | Render + código |
| Errores de lectura de archivo | Implementado (archivo ausente y formato no soportado) | `AudioPlayer` estado `error` |
| Playlists: crear/editar/eliminar | Implementado, con descripción | `AppStateTest.playlist lifecycle` |
| Playlists: añadir/quitar canciones | Implementado | `AppStateTest` |
| Playlists: cambiar orden | Implementado (subir/bajar), actualiza `updatedAt` | `AppStateTest.reordering` |
| Playlists: reproducir completa | Implementado | Código |
| Playlists: portada compuesta | Implementado (mosaico con carátulas reales) | Render |
| Favoritos: marcar/desmarcar | Implementado, con tombstone al quitar | `AppStateTest.favoriting` |
| Favoritos de otros dispositivos sin archivo local | Implementado (se muestran como "solo en tu cuenta") | `SyncCodecTest` |
| Preferencia: acento | Implementado, en vivo | Render |
| Preferencia: sección inicial de biblioteca | Implementado | Código |
| Preferencia: reducir animaciones | Implementado (se guarda; la UI aún no anima) | Código |
| Perfil: nombre | Implementado | `AppStateTest` |
| Estadísticas: tiempo, reproducciones, top artistas/canciones, últimos días | Implementado desde datos reales (nunca inventados) | Render con datos sintéticos |
| Cuenta: login Google, cierre de sesión sin borrar datos | Implementado; el login real necesita tu clic | `LoopbackLoginTest`, `OAuthFlowTest` |
| Sincronización PROFILE/PLAYLIST/FAVORITE/PREFERENCE/STATS | Implementado con el contrato exacto de Android | `SyncEngineTest` (9 pruebas) |
| Estados de sync (sin sesión, sync, error, sin conexión, token caducado) | Implementado | `AccountView` + `SyncOutcome` |
| Migración/descarga de YouTube Music | **No implementado** | Fuera del alcance local-first |
| Descargas | Implementado para carpetas locales: elige una carpeta y añade sus archivos a la biblioteca sin moverlos | Render + `Scanner.read` |
| YouTube Music | Implementado: búsqueda, playlists/álbumes por enlace, cola de streaming, descarga a la biblioteca y guardado como playlist local con portada, mediante yt-dlp (se instala desde la propia pantalla) | `YouTubeMusic`, `YouTubeView`, 8 pruebas + autopruebas reales |
| Favoritos del móvil con `streamUrl` | Se reproducen en streaming; sin URL válida, error explícito | `AudioPlayer` |
| Letras y su personalización | **No implementado** | Pendiente
| Wrapped | Implementado (año, horas, top artistas con porcentaje, top canciones, más repetida y día récord) | `WrappedTest` (7), render | Compartir / QR | **No implementado** | Pendiente
| Audio espacial | **No implementado** | Depende de la app móvil
| Migrar desde Spotify | **No implementado** | Pendiente
| Secciones de inicio configurables | **No implementado** | Pendiente

## Diferencias conscientes

- Desktop no descarga audio por sí mismo: "Descargas" apunta a una carpeta que el usuario ya
  tiene y añade sus archivos a la biblioteca sin moverlos. Un favorito del móvil con
  `streamUrl` se reproduce en streaming; si la URL caducó, el reproductor lo dice.
- La preferencia de tema se sincroniza **solo en el acento**. Android guarda un documento
  de tema completo; Desktop lo lee, aplica el acento y, si el usuario lo cambia aquí,
  reescribe únicamente `accentColor` conservando el resto de claves.
- El perfil local (`@bat`) y el nombre visible siguen siendo independientes del correo de
  Google, igual que en Android.

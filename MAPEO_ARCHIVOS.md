# Mapeo de Archivos del Proyecto - Cypher
> **Índice Ligero de Contexto:** Referencia ultra-concisa (1-2 líneas por archivo) de los componentes, pantallas, ViewModels, servicios y modelos de datos de Cypher (`com.cypher.music`) ubicado en `C:\Users\bat\.gemini\antigravity\scratch\cypher`.

---

## 1. Núcleo, Aplicación y Navegación
- `MainActivity.kt`: Punto de entrada de la actividad principal con Compose `setContent`, manejo de intents de audio y solicitud de permisos en runtime (API 33+).
- `CypherApplication.kt`: Clase Application de Android; inicializa de forma asíncrona los binarios nativos de yt-dlp y FFmpeg y sincroniza descargas.
- `Navigation.kt`: Enrutamiento raíz que monta la vista principal `MainScreen`.
- `AndroidManifest.xml`: Manifiesto del sistema con permisos (audio, internet, servicios en primer plano) y servicios de Media3 y Android Auto.

---

## 2. Base de Datos y Persistencia (Room Nativo 100% Kotlin)
- `CypherDatabase.kt`: Base de datos Room abstracta (versión 3) con soporte Singleton thread-safe, migraciones y registro de los 6 DAOs y entidades.
- `Converters.kt`: `TypeConverters` de Room para serialización y persistencia de marcas de tiempo y fechas.
- `TrackEntities.kt`: Entidad `TrackEntity` y DAO `TrackDao`; almacena canciones (id, título, artista, álbum, streamUrl, sourceType, favoritos y descargas).
- `PlaylistEntities.kt`: Entidades `PlaylistEntity`, `PlaylistTrackCrossRef`, modelo relacional `PlaylistWithTracks` (@Relation) y DAO `PlaylistDao`.
- `RecentlyPlayedEntities.kt`: Entidad `RecentlyPlayedEntity` y DAO `RecentlyPlayedDao`; historial reactivo de reproducciones recientes.
- `PlaybackAnalyticsEntities.kt`: Entidad `PlaybackAnalyticsEntity` y DAO `PlaybackAnalyticsDao`; métricas acumuladas y agregaciones de tiempo y reproducciones.
- `PlaybackDailyEntities.kt`: Entidad `PlaybackDailyEntity` y DAO `PlaybackDailyDao`; métricas diarias para estadísticas avanzadas y Wrapped.
- `SearchQueryEntities.kt`: Entidad `SearchQueryEntity` y DAO `SearchQueryDao`; historial de búsquedas recientes.
- `RoomMappers.kt`: Funciones de extensión bidireccionales para mapear entidades y relaciones de Room a los modelos de dominio.

---

## 3. Capa de Datos, Repositorios y Modelos
- `PlaylistRepository.kt`: Repositorio principal de listas de reproducción; gestiona CRUD, relaciones en Room y búsquedas locales.
- `FavoritesManager.kt`: Gestor de pistas favoritas; maneja estado reactivo de IDs favoritos y alternancia (like/unlike) en Room.
- `MusicRepository.kt`: Orquestador de música unificada; combina catálogo local de MediaStore, playlists editoriales y tendencias.
- `LocalMusicRepository.kt`: Repositorio de lectura del MediaStore del dispositivo para escanear y cargar MP3/FLAC/WAV locales.
- `Track.kt`: Modelo de dominio fundamental que representa una pista de audio (título, artista, álbum, URLs, tipo de fuente).
- `PlaylistModel.kt`: Modelos de datos para `Playlist`, `RecentlyPlayedItem`, `UnifiedSearchResults` y `BackupImportSummary`.
- `PlaybackStatsModel.kt`: Modelos de estadísticas de escucha (`UserListeningStats`, `TopArtistStat`, `TopTrackStat`).
- `SharedPlaylistModel.kt`: Estructuras de carga útil (`SharedPlaylistPayload`) para compartir e importar playlists mediante QR o códigos.
- `ArtistNormalizer.kt`: Normalizador y limpiador de nombres de artistas para evitar duplicados causados por metadatos dispares.
- `RecommendationModel.kt`: Modelos para secciones de recomendaciones inteligentes basadas en artistas y géneros.
- `SearchCategory.kt`: Modelo de categorías y géneros temáticos de búsqueda y exploración.
- `SearchHistoryManager.kt`: Gestor reactivo de términos de búsqueda recientes persistidos en base de datos.
- `UserModel.kt`: Modelo simplificado del perfil personal de usuario local de Cypher.
- `LiquidGlassConfig.kt`: Data class de configuración del shader AGSL Liquid Glass (colores, velocidad, distorsión, opacidad).
- `LyricsDisplaySettings.kt`: Modelos y preferencias para la sincronización y renderizado tipográfico de letras de canciones.
- `ThemePreferences.kt`: Preferencias de tema visual (acento Lavanda `#9D7BB0`, fondo OLED, estilo de superficie, haptics).

---

## 4. Red y Resiliencia de Streaming (InnerTube / yt-dlp)
- `YouTubeRepository.kt`: Repositorio de streaming y búsqueda en YouTube/YTMusic con resolución multi-tier (yt-dlp, InnerTube TVHTML5 y parsing HTML).
- `StreamingTelemetry.kt`: Monitor de telemetría pasiva y resiliencia; detecta roturas de API (errores HTTP 403, protocolo SABR) y conmuta fallbacks.
- `YouTubeAccountManager.kt`: Gestor de credenciales y cookies de cuenta YouTube mediante `EncryptedSharedPreferences` para sincronizar likes y playlists.
- `ResilientDns.kt`: Implementación DNS resiliente (DoH Cloudflare) que elude bloqueos de resolución en CDNs de streaming de Google.
- `LrcLibService.kt`: Cliente de red para la API de LRCLIB; obtiene letras de canciones sincronizadas (.lrc) o en texto plano.
- `PlaylistShareService.kt`: Servicio de red para resolver códigos cortos y compartir playlists en la nube.

---

## 5. Reproducción y Caché de Audio (Media3 / ExoPlayer / DSP)
- `MusicPlayerManager.kt`: Orquestador central de audio (Singleton); gestiona ExoPlayer, IFrame YouTube Player, cola de reproducción, pre-caching gapless, normalización de volumen (-14 LUFS con LoudnessEnhancer) y MediaLibrarySession.
- `MediaCacheManager.kt`: Gestor de caché LRU y Bóveda Offline en Scoped Storage (con soporte MicroSD y archivo `.nomedia`); ejecuta descargas nativas con yt-dlp.
- `AudioVolumeManager.kt`: Gestor de volumen y atenuación de audio del sistema.
- `EqualizerManager.kt`: Controlador de efectos de audio DSP en tiempo real (ecualizador gráfico de bandas y BassBoost).
- `PlaybackService.kt`: `MediaLibraryService` en primer plano; mantiene la reproducción activa en background y enlaza notificaciones multimedia del sistema.

---

## 6. Interfaz de Usuario: Pantallas (Screens)
- `MainScreen.kt`: Pantalla contenedora principal; alberga la barra de navegación, mini-reproductor dockeado, vistas y el WebView de YouTube oculto.
- `ListenNowScreen.kt`: Pantalla de inicio con carruseles de pistas recomendadas, playlists destacadas y accesos directos.
- `BrowseScreen.kt`: Pantalla de exploración por categorías musicales, estados de ánimo y listas editoriales.
- `LibraryScreen.kt`: Pantalla de biblioteca con pestañas para Playlists, Favoritos, Descargas offline y Música local.
- `SearchScreen.kt`: Pantalla de búsqueda unificada (local + YouTube), búsqueda por voz e historial reciente.
- `PlaylistDetailScreen.kt`: Vista detallada de una lista de reproducción con cabecera dinámica, edición y lista de pistas reordenable.
- `SettingsScreen.kt`: Pantalla de ajustes de personalización (Color Picker lavanda, ROMs custom, shaders, ecualizador, backups y cuenta YouTube).

---

## 8. Interfaz de Usuario: ViewModels
- `MainViewModel.kt`: ViewModel principal de la aplicación; unifica flujos de reproducción, búsqueda, tema, backups y navegación.
- `PlayerViewModel.kt`: ViewModel enfocado en controles del reproductor, volumen, ecualizador y cola de reproducción.
- `LibraryViewModel.kt`: ViewModel para la gestión de colecciones locales, playlists, favoritos y descargas.
- `YouTubeViewModel.kt`: ViewModel para la integración de cuenta de YouTube Music y sincronización de música guardada.
- `ShareViewModel.kt`: ViewModel para exportación, generación e importación de códigos QR y enlaces de playlists.

---

## 9. Interfaz de Usuario: Componentes Reutilizables
- `CypherMiniPlayer.kt`: Mini-reproductor flotante sobre la barra de navegación con gestos de deslizamiento y controles rápidos.
- `CypherNowPlayingModal.kt`: Modal de pantalla completa "En Reproducción" con arte de tapa dinámico, scrubber elástico y controles de cola.
- `CypherNavBar.kt`: Barra de navegación inferior flotante con selección de pestañas e indicadores visuales.
- `CypherTrackRow.kt`: Fila de canción con menú contextual, estado de descarga, indicador de favorito y duración.
- `CypherMediaCard.kt`: Tarjeta visual para playlists, álbumes y colecciones editoriales.
- `CypherSectionHeader.kt`: Encabezado de sección con título en negrita y acción opcional "Ver todo".
- `CypherLyricsSheet.kt`: Hoja inferior deslizable con letras de canciones sincronizadas con resaltado línea por línea.
- `CypherQueueSheet.kt`: Hoja deslizante con la cola de reproducción actual y soporte de reordenamiento de pistas.
- `CypherEqualizerSheet.kt`: Hoja de control del ecualizador gráfico y realce de graves.
- `CypherSleepTimerSheet.kt`: Temporizador de apagado automático de reproducción (15m, 30m, 1h, fin de pista).
- `CypherContextMenu.kt`: Menú contextual flotante para acciones de pistas (añadir a playlist, descargar, compartir, ver artista).
- `AddToPlaylistSheet.kt`: Hoja modal para añadir una pista específica a cualquiera de las playlists creadas.
- `CreatePlaylistDialog.kt`: Diálogo emergente para crear una nueva lista de reproducción con título y descripción.
- `SharePlaylistDialog.kt`: Diálogo de compartición con previsualización de código QR y exportación de enlaces.
- `QrScannerSheet.kt`: Escáner de cámara integrado mediante Google Code Scanner para importar playlists P2P.
- `PlaylistImportPreviewDialog.kt`: Vista previa de canciones antes de confirmar la importación de una playlist externa.
- `PlaylistMosaicCover.kt`: Generador automático de portada en cuadrícula 2x2 a partir de las portadas de sus pistas.
- `YouTubeLoginDialog.kt`: Diálogo modal con WebView para inicio de sesión en YouTube Music y captura de cookies de sesión.
- `CypherVoiceSearchModal.kt`: Modal interactivo de reconocimiento de voz para búsqueda por comandos hablados.
- `ArtworkPlaceholder.kt`: Placeholder estilizado con degradado e isotipo musical cuando una pista carece de portada.
- `LiquidGlassSettingsSheet.kt`: Hoja de ajustes avanzados en vivo para calibrar el shader AGSL (velocidad, distorsión, opacidad).

---

## 10. Shaders AGSL, Estilos y Utilidades
- `LiquidGlass.kt`: Shader AGSL (`RuntimeShader`) 100% acelerado por GPU con mallas fluidas orgánicas, dispersión óptica y aberración cromática nativa (API 33+).
- `RealLiquidGlass.kt`: Modificador de superficies sólidas y limpias para estilo minimalista de alto contraste OLED.
- `Color.kt`: Paleta de colores oficial de Cypher (acento Lavanda `#9D7BB0`, fondo Dark `#0D0B11`, superficies `#16141D`).
- `Theme.kt`: Definición del `CypherTheme` con esquemas Material 3 oscuros/claros y barras de sistema translúcidas.
- `Type.kt`: Configuración tipográfica moderna optimizada para interfaces de audio.
- `PaletteExtractor.kt`: Extractor dinámico de colores dominantes de la carátula usando AndroidX Palette para el fondo ambiental.
- `ThemeManager.kt`: Resolvedor dinámico de colores de tema según las preferencias activas del usuario.
- `AppHaptics.kt`: Controlador de respuesta háptica con vibraciones táctiles sutiles para interacciones táctiles y scrubbers.
- `QrCodeGenerator.kt`: Utilidad de generación de mapas de bits QR mediante la librería ZXing.

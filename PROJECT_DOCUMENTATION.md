# Cypher — Documentación Técnica Integral y Arquitectura del Proyecto

> **Nombre Oficial:** Cypher  
> **Identificador de Paquete (Package Name):** `com.cypher.music`  
> **Versión Actual Android:** **1.5.0** (`versionCode 21`)  
> **Versión Actual Cypher PC:** **1.1.0** (`Cypher-1.1.0.msi` / Portable)  
> **Licencia:** Código Abierto (GNU General Public License v3.0 — GPL-3.0)  
> **Compatibilidad Android:** Android 10+ (API 29 a API 36) | `minSdk = 29`, `targetSdk = 36`, `compileSdk = 36`  
> **Compatibilidad PC:** Windows 10 / 11 (64-bit)  
> **Runtimes y Lenguajes:** Kotlin 2.3.20 (Android) / Kotlin 2.0.21 (Desktop), Java 17 toolchain, Jetpack Compose, Material 3, AndroidX Media3, AGSL/SkSL Shaders, Room v9  

---

## 1. Visión General y Filosofía de Diseño

**Cypher** es un reproductor de música de alta fidelidad y cliente de streaming híbrido y resiliente para **Android** y **Windows**.
El proyecto nace con el objetivo de unificar en una experiencia visualmente cuidada, fluida y orientada a la privacidad tanto la biblioteca de música local del usuario como el catálogo de **YouTube Music / InnerTube**.

### Principios Fundamentales
1. **Streaming Híbrido Resiliente:** Resolución directa de audio en streaming mediante yt-dlp y llamadas nativas a InnerTube TVHTML5, respaldado por telemetría reactiva ante bloqueos HTTP 403 o anomalías del protocolo SABR, con fallback automático al reproductor iframe oficial embebido.
2. **Sin Cuentas Obligatorias ni Nube Externa:** Funcionalidad completa fuera de línea; sin registro forzado ni almacenamiento de datos privados en servidores de terceros.
3. **Copia de Seguridad Portátil (ZIP con Letras):** Exportación completa de listas de reproducción, favoritos, configuración y letras descargadas en un único archivo ZIP en el almacenamiento local, compatible directamente con Cypher PC para migración inmediata entre plataformas.
4. **Almacenamiento Local Protegido (Scoped Storage):** Descargas en memoria interna o tarjetas MicroSD extraíbles con protección mediante archivo `.nomedia`, manteniendo los archivos fuera del índice de la galería y reproductores estándar del sistema.
5. **Aceleración por GPU (Liquid Glass AGSL / SkSL):** Shaders de cristal líquido dinámicos procesados en tiempo real por la GPU, calibrados según la paleta cromática de la carátula activa y respetuosos con las opciones de accesibilidad de reducción de movimiento (*Reduced Motion*).
6. **Letras Sincronizadas y Estudio de Tarjetas:** Consulta dinámica con LRCLIB, almacenamiento en caché persistente (`LyricsStore`) y editor integrado para generar tarjetas estilizadas en 1080 px (Historia 1080×1920 o Cuadrado 1080×1080).
7. **Integración con Sistemas de Automoción:** Soporte nativo para Android Auto a través de `MediaLibraryService` en AndroidX Media3.
8. **Cypher PC Multiplataforma:** Aplicación de escritorio moderna construida sobre Jetpack Compose for Desktop, con reproducción de YouTube mediante Chromium embebido (JCEF), códecs nativos mediante JavaFX Media, shaders SkSL y personalización completa de superficies y colores.

---

## 2. Historial Evolutivo y Fases del Proyecto

### Fase 1: Génesis e Interfaz Inicial
- Creación del prototipo con exploración de pestañas (Inicio, Explorar, Biblioteca, Buscar).
- Uso inicial de base de datos SQLite manual y compatibilidad retroactiva con gradientes estáticos en dispositivos sin soporte de shaders nativos.

### Fase 2: Motor de Streaming y Servicios Externos
- Integración de binarios nativos de `yt-dlp` y `ffmpeg` mediante `youtubedl-android`.
- Incorporación de letras sincronizadas con LRCLIB (`LrcLibService`), generación/lectura de códigos QR para compartir listas de reproducción sin servidores y servicio de reproducción en segundo plano `PlaybackService`.

### Fase 3: Reestructuración Arquitectónica y Rebranding (Cypher)
- Reorientación completa a aplicación personal monousuario, simplificando la gestión de estado y persistencia.
- Migración definitiva a **Room Database** con relaciones tipadas `@Relation` / `@Junction`, conversores de tipo y mapeadores idiomáticos entre entidades de Room y modelos de dominio.

### Fase 4: Descargas Ocultas en Almacenamiento Aislado (Scoped Storage)
- Implementación de `MediaCacheManager` utilizando `ContextCompat.getExternalFilesDirs`.
- Detección automática y priorización de tarjetas MicroSD extraíbles para formatos de audio de alta resolución (FLAC/WAV).
- Creación garantizada del archivo `.nomedia`.

### Fase 5: Resiliencia de Streaming y Telemetría en Red
- Implementación de `StreamingTelemetry` para supervisión en vivo del estado de la conexión ante errores HTTP 403 o cortes SABR.
- Sistema de resolución en 4 niveles (Tier 1: clientes móviles yt-dlp; Tier 2: clientes alternativos; Tier 3: InnerTube TVHTML5 JSON directo; Tier 4: conmutación transparente al iframe oficial de YouTube).

### Fase 6: Shaders Nativos AGSL y Optimización Háptica
- Implementación de `LiquidGlass.kt` mediante `RuntimeShader` de GPU con aberración cromática, refracción cáustica y cálculo adaptativo de color.
- Throttling háptico a 45 ms (`fireThrottledHaptic`) en el control de posición (*scrubber*) para evitar sobrecarga del hilo principal y de los actuadores de vibración.

### Fase 7: Identidad Visual Oficial (Lavanda Cypher)
- Definición de la paleta oficial: acento **Lavanda Cypher (`#9D7BB0`)**, fondo OLED puro (`#0D0B11`) y tarjetas (`#16141D`).
- Soporte para personalización de paleta en Ajustes (acentos, fondos y estilos de superficie).

### Fase 8: Sincronización de Progreso y Limpieza de Pistas
- Corrección de desincronización entre el reproductor de YouTube y el rastreo de progreso local de ExoPlayer, evitando saltos de la barra de reproducción y desplazamientos erráticos de letras.
- Limpieza de datos heredados y purga de pruebas en base de datos.

### Fase 9: Consolidación Open Source
- Publicación bajo licencia GPL-3.0 en GitHub (`https://github.com/Itsbatt/Cypher`).
- Configuración de pipelines de integración continua en GitHub Actions.

### Fase 10: Cypher Wrapped Anual Estilo Historias
- Módulo de resumen anual disponible estacionalmente (diciembre-enero) y accesible bajo demanda.
- Experiencia interactiva de diapositivas estilo historias: minutos totales, podio Top 5 canciones, artistas con porcentaje de tiempo, álbumes y géneros reales.
- Exportación en tarjeta visual de alta resolución para compartir en redes o guardar en la galería.

### Fase 11: Suite DSP, Normalización -14 LUFS y Gapless Pre-caching
- Normalización de volumen mediante `LoudnessEnhancer` fijado a -14 LUFS.
- Ecualizador gráfico de 5 bandas interactivas con presets calibrados y realce de graves (*Bass Boost*).
- Pre-caching gapless: resolución anticipada de la URL de streaming en los últimos 25 segundos de la pista en curso.
- Estabilización del compilador de Room en AGP 9 mediante clases base Java y adaptadores Kotlin.

### Fase 12: Estabilización de Descargas e Indicador de Onda
- Centralización de decisiones de salida en `PlaybackOutputPolicy`: restauración de volumen al alternar entre pistas en streaming y archivos descargados sin conexión.
- Indicador animado de onda sonora restringido exclusivamente a la canción activa en reproducción.
- Validación de firmas binarias de audio (MP4/M4A, MP3, FLAC, OGG, WAV, WebM, AAC) para garantizar descargas íntegras.

### Fase 13: Compartición de Letras, Portadas Maxres y Recomendaciones
- Estudio de tarjetas de letras: selección de hasta 6 versos, 3 plantillas tipográficas, renderizado en Canvas nativo a 1080 px y exportación directa a `Pictures/Cypher` o envío vía `ACTION_SEND`.
- Portadas en alta resolución con `YouTubeArtwork` solicitando `maxresdefault` (1280×720) y fallback automático a `hqdefault`, con instancia compartida de `ImageLoader` de Coil y caché acotada de paletas.
- Motor de recomendaciones local basado en historial real de reproducción sin servicios externos.

### Fase 14: Cypher 1.5.0 — Respaldo ZIP con Letras, Atajos y Temporizador con Fade
- **Respaldo Integral en ZIP:** Generación de un archivo comprimido en `Descargas/Cypher` conteniendo la base de datos de listas, canciones favoritas, configuraciones y la totalidad de letras sincronizadas descargadas (`LyricsStore`).
- **Base de Datos Room v9:** Migración declarada y segura desde versiones anteriores, eliminando dependencias de contabilidad externas y asegurando el orden correcto de canciones en listas mediante tabla de cruce relacional.
- **Temporizador de Sueño con Fundido:** Desvanecimiento progresivo del volumen durante los últimos 30 segundos del temporizador antes de la detención completa.
- **Atajos de Lanzador:** Acceso rápido en el icono de la aplicación (Favoritas y Última sesión activa).
- **Portadas Propias de Playlist:** Selector nativo de imágenes desde la galería del dispositivo para personalizar carátulas de listas de reproducción.
- **Gestión de Almacenamiento en Ajustes:** Indicador del espacio ocupado por descargas locales y opción de borrado masivo.

### Fase 15: Cypher PC 1.0.0 y PC 1.1.0 — Llega la Versión de Escritorio
- **Arquitectura de Escritorio:** Aplicación nativa para Windows con Compose for Desktop, Java 17 y Kotlin 2.0.21.
- **Fondo Líquido Animado (SkSL):** Traslado de la experiencia visual de cristal líquido a PC con shaders SkSL de tres campos ondulantes, reflejos cáusticos y tonos derivados de la carátula.
- **Streaming de YouTube con Chromium Embebido (JCEF 122.1.10):** Las canciones de YouTube se transmiten de forma nativa mediante un reproductor iframe en una ventana sincronizada, conservando barra de tiempo, carátula y letras en vivo sin necesidad de descargar el archivo. Fallback transparente a descarga en caso de restricciones de inserción.
- **Letras Personalizables:** Selección de color adaptable (acento del tema, dominante de portada o selector libre HSV/Hex) y efectos visuales animados (*Líquido* y *Neón*).
- **Vista Ampliada con Controles Superpuestos:** Presentación de carátula de gran tamaño con barra de tiempo y botones de reproducción flotantes al pasar el puntero, además de pausa/reanudación mediante clic directo en la imagen.
- **Personalización Integral de Paleta:** Edición en vivo de colores de fondo, paneles, tarjetas, bordes, píldoras y textos.
- **Empaquetado Nativo:** Distribución mediante instalador por usuario MSI (`Cypher-1.1.0.msi`) y paquete portable sin requisitos previos (`Cypher-1.1.0-windows-portable.zip`).

---

## 3. Arquitectura del Sistema

```
===================================================================================
                               CYPHER ARQUITECTURA
===================================================================================

[ ANDROID ]                                              [ DESKTOP / PC ]
--------------------------------------------------      ---------------------------
Jetpack Compose + Material 3 + AGSL Shaders              Compose for Desktop (SkSL)
Navigation 3 (Runtime & UI)                              Ventana con DWM Oscuro Nativo
--------------------------------------------------      ---------------------------
                       │                                              │
                       ▼                                              ▼
┌──────────────────────────────────────────────┐        ┌───────────────────────────┐
│              CAPA DE VIEWMODELS              │        │       DESKTOP STATE       │
│ MainViewModel · PlayerViewModel              │        │ PlayerState · ThemeState  │
│ LibraryViewModel · YouTubeViewModel          │        │ LibraryState · LyricsState│
└──────────────────────┬───────────────────────┘        └─────────────┬─────────────┘
                       │                                              │
                       ▼                                              ▼
┌──────────────────────────────────────────────┐        ┌───────────────────────────┐
│         MOTOR MULTIMEDIA Y REPRODUCCIÓN      │        │      MOTOR DE AUDIO PC    │
│ MusicPlayerManager (Singleton)               │        │ DesktopPlayerManager      │
│ - AndroidX Media3 (ExoPlayer) para Local     │        │ - JavaFX Media (Local)    │
│ - IFrame Embebido + Streaming Resiliente     │        │ - JCEF Chromium (YouTube) │
│ - PlaybackService (Android Auto / Notif.)    │        │ - Sincronizador de Letras │
│ - Suite DSP: -14 LUFS, Ecualizador, Fade     │        │                           │
└──────────────────────┬───────────────────────┘        └─────────────┬─────────────┘
                       │                                              │
                       ▼                                              ▼
┌──────────────────────────────────────────────┐        ┌───────────────────────────┐
│                CAPA DE DATOS                 │        │      DATOS DE PC          │
│ - Room Database v9 (cypher_room.db)          │        │ - %APPDATA%\Cypher Desktop│
│ - Scoped Storage (MicroSD / .nomedia)        │        │   library.json (Local)    │
│ - LyricsStore (LRCLIB caché persistente)     │        │ - Importador ZIP Android  │
│ - YouTubeRepository (InnerTube, yt-dlp, DoH) │        │ - Generador QR (ZXing)    │
└──────────────────────────────────────────────┘        └───────────────────────────┘
```

---

## 4. Instrucciones de Compilación y Entorno

### Requisitos del Sistema
- **Java:** JDK 17 o superior (Adoptium Temurin 17 recomendado).
- **Android SDK:** `compileSdk 36`, `targetSdk 36`, `minSdk 29`.
- **Windows:** Windows 10/11 para compilar o ejecutar Cypher PC.

### Compilar Android (APK)
```powershell
# Compilación de la variante release
.\gradlew.bat :app:assembleRelease

# Compilación de depuración
.\gradlew.bat :app:assembleDebug

# Despliegue en dispositivo por ADB
adb install -r app\build\outputs\apk\release\app-arm64-v8a-release.apk
```

### Compilar Cypher PC (Desktop)
```powershell
cd desktop

# Ejecutar localmente
.\gradlew.bat run

# Ejecutar tests unitarios
.\gradlew.bat test

# Generar instalador MSI
.\gradlew.bat packageMsi

# Generar versión portable (build\dist\Cypher-1.1.0-windows-portable.zip)
.\gradlew.bat packagePortable
```

---

## 5. Licencia

Cypher es software libre publicado bajo la **GNU General Public License v3.0**. Consulta el archivo `LICENSE` para los términos legales completos.

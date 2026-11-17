# Cypher 🎵

> **Cypher** es un reproductor de música moderno de alta fidelidad y cliente de streaming híbrido para **Android** (Android 10+, API 29-36) y **Windows**.
> Construido con **Jetpack Compose**, **Material 3**, **AndroidX Media3**, shaders nativos **AGSL/SkSL acelerados por GPU**, **Room** en Android y **Compose for Desktop** en PC.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2010%2B%20%7C%20Windows-green.svg)](https://github.com/Itsbatt/Cypher)
[![Android Version](https://img.shields.io/badge/Android-v1.5.0-purple.svg)](https://github.com/Itsbatt/Cypher/releases/tag/v1.5.0)
[![PC Version](https://img.shields.io/badge/PC-v1.1.0-informational.svg)](https://github.com/Itsbatt/Cypher/releases/tag/pc-v1.1.0)
[![Language](https://img.shields.io/badge/Language-Kotlin%20%7C%20Java%2017-orange.svg)](https://kotlinlang.org)

---

## ✨ Características Principales

### 📱 Experiencia en Android (Cypher v1.5.0)
- **Motor híbrido de streaming y local:** Reproducción de audio local de alta definición sin pérdidas (FLAC, WAV, ALAC, MP3, AAC, Ogg Opus) y streaming de YouTube Music mediante InnerTube / yt-dlp con mecanismos automáticos de resiliencia ante cortes (fallback HTTP 403 / SABR).
- **Suite de Audio DSP:** Normalización de volumen calibrada a -14 LUFS (`LoudnessEnhancer`), ecualizador gráfico interactivo de 5 bandas con preajustes afinados y amplificación de graves (*Bass Boost*).
- **Temporizador de sueño con fundido suave:** Apagado inteligente programable con rampa de desvanecimiento (*fade-out*) progresiva durante los últimos 30 segundos en lugar de cortes abruptos.
- **Acceso rápido desde el lanzador:** Atajos dinámicos en el icono de la aplicación para iniciar instantáneamente canciones favoritas y la última sesión activa.
- **Portadas personalizadas:** Elección libre de carátulas para listas de reproducción directamente desde la galería del dispositivo.
- **Descargas privadas y organizadas:** Almacenamiento local (en memoria interna o tarjetas MicroSD extraíbles) protegido con marcador `.nomedia` para evitar la indexación en galerías del sistema. Panel en Ajustes con desglose de espacio y opción de purga completa.
- **Copia de seguridad en ZIP (con letras incluidas):** Exporta e importa toda tu biblioteca (playlists, favoritos, ajustes y letras descargadas) en un archivo ZIP local en `Descargas/Cypher`. Compatible al 100% con Cypher PC para transferir tu biblioteca sin cuentas ni servidores en la nube.
- **Gestos táctiles y cola fluida:** Desliza horizontalmente la carátula o el MiniPlayer para alternar de canción. Búsqueda en vivo dentro de la cola y reordenación de pistas mediante arrastre prolongado (*long-press drag*).
- **Interfaz líquida (AGSL Liquid Glass):** Shaders orgánicos en tiempo real ejecutados en la GPU con dispersión cromática calculada a partir de la paleta de la carátula, con compatibilidad nativa para el modo de reducción de movimiento (*Reduced Motion*).
- **Letras en vivo y estudio para compartir:** Sincronización precisa línea a línea desde LRCLIB con guardado local persistente (`LyricsStore`). Estudio integrado de tarjetas: selecciona hasta 6 versos, elige entre 3 estilos (Minimal, Portada o Tipografía) y exporta en 1080 px (Historia 1080×1920 o Cuadrado 1080×1080) para compartir o guardar en `Pictures/Cypher`.
- **Cypher Wrapped Anual:** Resumen anual interactivo estilo historias (minutos reproducidos, top 5 canciones, artistas con porcentaje de escucha, géneros y álbumes reales) con tarjeta compartible.
- **Migración libre desde Spotify:** Importación rápida mediante archivo ZIP, listas CSV/JSON o enlace público, sin necesidad de credenciales, cuentas de desarrollador ni suscripciones de pago.
- **Android Auto y segundo plano:** Servicio multimedia en primer plano (`MediaLibraryService`) con navegación por biblioteca en sistemas de infoentretenimiento de vehículos.
- **Actualizaciones integradas:** Verificación directa con GitHub Releases con comprobación de firmas criptográficas e instalación segura.

### 💻 Experiencia en Escritorio (Cypher PC v1.1.0)
- **Fondo líquido animado (SkSL Liquid Glass):** Efectos visuales de cristal líquido acelerados por GPU con tres campos fluidos, filos cáusticos y refracción reactiva a la portada activa.
- **Streaming de YouTube con Chromium embebido (JCEF):** Reproducción directa mediante iframe embebido, conservando metadatos, barra de progreso y letras sincronizadas sin requerir descargas previas a disco.
- **Letras totalmente personalizables:** Selección de color adaptativa (tono de acento, dominante de portada o selector libre HSV/Hex) y efectos visuales animados (*Líquido*, *Neón*).
- **Vista ampliada renovada:** Carátula a gran escala con controles flotantes y barra de scrubbing integrados directamente sobre la portada al posicionar el cursor, con pausa/reanudación táctil o por clic.
- **Personalización total de paleta:** Control minucioso y en vivo de cada superficie y nivel tipográfico (fondos, paneles laterales, tarjetas, bordes, píldoras y textos primarios/secundarios).
- **Reproducción local de alta fidelidad:** Soporte nativo para formatos de audio mediante JavaFX Media y lectura de metadatos con jaudiotagger.
- **Integración con Windows:** Estilizado nativo de barra de título oscura mediante llamadas a Windows DWM (JNA).
- **Privacidad y portabilidad:** Configuración y biblioteca local almacenada en `%APPDATA%\Cypher Desktop\library.json`, sin rastreadores ni servicios externos.

---

## 📦 Descargas

Descarga las últimas versiones oficiales desde la sección de **[Releases](https://github.com/Itsbatt/Cypher/releases)**:

### Android (v1.5.0)
- **[app-arm64-v8a-release.apk](https://github.com/Itsbatt/Cypher/releases/latest)** — Para dispositivos móviles de 64 bits (recomendado para la gran mayoría de teléfonos y tablets).
- **[app-universal-release.apk](https://github.com/Itsbatt/Cypher/releases/latest)** — Compatible con todas las arquitecturas ABI admitidas.
- **`SHA256SUMS.txt`** — Sumas de verificación criptográfica para verificar la integridad del APK.

Para comprobar la integridad del paquete descargado:
```bash
sha256sum -c SHA256SUMS.txt
```

> **Versión actual de Android:** **1.5.0** (`versionCode 21`). Compatible con Android 10 (API 29) hasta Android 16 (API 36).

### Cypher PC (v1.1.0)
- **[Cypher-1.1.0.msi](https://github.com/Itsbatt/Cypher/releases/tag/pc-v1.1.0)** — Instalador por usuario de Windows (no requiere permisos de administrador).
- **[Cypher-1.1.0-windows-portable.zip](https://github.com/Itsbatt/Cypher/releases/tag/pc-v1.1.0)** — Versión portable empaquetada lista para descomprimir y ejecutar, sin necesidad de tener Java instalado en el sistema.

> Consulta las notas completas de la versión de escritorio en [`docs/releases/PC-1.1.0.md`](docs/releases/PC-1.1.0.md).

---

## 🚀 Compilación desde el Código Fuente

### Requisitos Previos
- **Java Development Kit (JDK):** JDK 17 o superior (el proyecto utiliza `jvmToolchain(17)`).
- **Git** instalado.

### Compilar Cypher para Android
Requiere Android SDK con `compileSdk = 36` y `minSdk = 29`.

```bash
# Clonar el repositorio
git clone https://github.com/Itsbatt/Cypher.git
cd Cypher

# En Windows (PowerShell / CMD)
.\gradlew.bat :app:assembleRelease

# En Linux / macOS
./gradlew :app:assembleRelease

# Instalar en un dispositivo conectado mediante ADB
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

*Nota:* Las compilaciones de release se firman mediante el keystore configurado en `keystore.properties` (ignorado por git). Para compilar una variante de depuración sin credenciales de firma de producción, usa `assembleDebug`.

### Compilar Cypher PC (Windows)
No requiere Android SDK. El módulo de escritorio (`desktop`) contiene su propio entorno Gradle independiente:

```bash
cd desktop

# Ejecutar la aplicación en modo desarrollo
.\gradlew.bat run

# Ejecutar la batería de tests unitarios
.\gradlew.bat test

# Generar el instalador MSI nativo de Windows
.\gradlew.bat packageMsi

# Generar el archivo ZIP portable (en build/dist)
.\gradlew.bat packagePortable
```

---

## 📖 Documentación del Proyecto

- [`PROJECT_DOCUMENTATION.md`](PROJECT_DOCUMENTATION.md) — Documentación técnica exhaustiva, evolución histórica y arquitectura profunda del sistema.
- [`docs/releases/`](docs/releases/) — Notas de versión detalladas de cada lanzamiento publicado (Android y PC).
- [`docs/liquid-glass/`](docs/liquid-glass/) — Especificaciones técnicas de los shaders AGSL/SkSL.

---

## 📄 Licencia

Este proyecto está publicado bajo la licencia **GNU General Public License v3.0** — consulta el archivo [`LICENSE`](LICENSE) para más detalles.

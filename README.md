# Cypher 🎵

> **Cypher** es un reproductor de música moderno de alta fidelidad y cliente de streaming híbrido para **Android** (Android 10+, API 29-36) y **Windows**.
> Construido con **Jetpack Compose**, **Material 3**, **AndroidX Media3**, shaders nativos **AGSL/SkSL acelerados por GPU**, **Room** en Android y **Compose for Desktop** en PC.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Owner: itsbatt](https://img.shields.io/badge/Owner-itsbatt-7952b3.svg)](https://github.com/Itsbatt)
[![Platform](https://img.shields.io/badge/Platform-Android%2010%2B%20%7C%20Windows-green.svg)](https://github.com/Itsbatt/Cypher)
[![Android Version](https://img.shields.io/badge/Android-v1.5.1-purple.svg)](https://github.com/Itsbatt/Cypher/releases/tag/v1.5.1)
[![PC Version](https://img.shields.io/badge/PC-v1.3.0-informational.svg)](https://github.com/Itsbatt/Cypher/releases/tag/pc-v1.3.0)
[![Language](https://img.shields.io/badge/Language-Kotlin%20%7C%20Java%2017-orange.svg)](https://kotlinlang.org)

---

## ✨ Características Principales

### 📱 Experiencia en Android (Cypher v1.5.1)
- **Motor híbrido de streaming y local:** Reproducción de audio local de alta definición sin pérdidas (FLAC, WAV, ALAC, MP3, AAC, Ogg Opus) y streaming de YouTube Music mediante arquitectura desacoplada (`StreamResolver`) con InnerTube, DoH y fallback de contingencia mediante proxies (Piped / Invidious) ante bloqueos de red o 403.
- **Suite de Audio DSP:** Normalización de volumen calibrada a -14 LUFS (`LoudnessEnhancer`), ecualizador gráfico interactivo de 5 bandas con preajustes afinados y amplificación de graves (*Bass Boost*).
- **Temporizador de sueño con fundido suave:** Apagado inteligente programable con rampa de desvanecimiento (*fade-out*) progresiva durante los últimos 30 segundos en lugar de cortes abruptos.
- **Acceso rápido desde el lanzador:** Atajos dinámicos en el icono de la aplicación para iniciar instantáneamente canciones favoritas y la última sesión activa.
- **Portadas personalizadas:** Elección libre de carátulas para listas de reproducción directamente desde la galería del dispositivo.
- **Descargas privadas y organizadas:** Almacenamiento local (en memoria interna o tarjetas MicroSD extraíbles) protegido con marcador `.nomedia` para evitar la indexación en galerías del sistema. Panel en Ajustes con desglose de espacio y opción de purga completa.
- **Copia de seguridad en ZIP (con letras y ajustes):** Exporta e importa toda tu biblioteca (playlists, favoritos, ajustes de streaming/proxy y letras descargadas) en un archivo ZIP local en `Descargas/Cypher`. Compatible al 100% con Cypher PC para transferir tu biblioteca sin cuentas ni servidores en la nube.
- **Base de datos moderna:** Room migrado al 100% a Kotlin con KSP (`com.google.devtools.ksp`), garantizando máxima velocidad y estabilidad.
- **Gestos táctiles y cola fluida:** Desliza horizontalmente la carátula o el MiniPlayer para alternar de canción. Búsqueda en vivo dentro de la cola y reordenación de pistas mediante arrastre prolongado (*long-press drag*).
- **Interfaz líquida (AGSL Liquid Glass):** Shaders orgánicos en tiempo real ejecutados en la GPU con dispersión cromática calculada a partir de la paleta de la carátula, con compatibilidad nativa para el modo de reducción de movimiento (*Reduced Motion*).
- **Letras en vivo y estudio para compartir:** Sincronización precisa línea a línea desde LRCLIB con guardado local persistente (`LyricsStore`). Estudio integrado de tarjetas: selecciona hasta 6 versos, elige entre 3 estilos (Minimal, Portada o Tipografía) y exporta en 1080 px (Historia 1080×1920 o Cuadrado 1080×1080) para compartir o guardar en `Pictures/Cypher`.
- **Cypher Wrapped Anual:** Resumen anual interactivo estilo historias (minutos reproducidos, top 5 canciones, artistas con porcentaje de escucha, géneros y álbumes reales) con tarjeta compartible.
- **Migración libre desde Spotify:** Importación rápida mediante archivo ZIP, listas CSV/JSON o enlace público, sin necesidad de credenciales, cuentas de desarrollador ni suscripciones de pago.
- **Android Auto y segundo plano:** Servicio multimedia en primer plano (`MediaLibraryService`) con navegación por biblioteca en sistemas de infoentretenimiento de vehículos.
- **Actualizaciones integradas:** Verificación directa con GitHub Releases con comprobación de firmas criptográficas e instalación segura.

### 💻 Experiencia en Escritorio (Cypher PC v1.3.0)
- **Cypher Connect (Control Remoto Wi-Fi / LAN):** Controla la reproducción de Cypher PC desde el móvil o navegador mediante emparejamiento con código QR o detección automática local.
- **Menú contextual compuesto con submenú en cascada:** Menú limpio para cada pista con buscador en tiempo real («Busca una lista»), botón rápido «+ Nueva lista», indicador de canciones ya añadidas, estética con bordes redondeados y soporte para clic derecho.
- **Ecualizador nativo de 10 bandas y perfiles DSP:** Ajuste de frecuencias de audio con presets afinados (*Bass Boost, Rock, Pop, Vocal, Acoustic, Flat*).
- **Radio infinita dinámica (Mix / Start Radio):** Generación automática de cola continua con recomendaciones basadas en la canción actual aprovechando el motor de InnerTube.
- **Modo Pantalla Completa Inmersivo / Karaoke (F11):** Fondo líquido fluido reactivo a la carátula con letras gigantes sincronizadas en tiempo real.
- **Temporizador de apagado con fundido suave:** Programable (15, 30, 45, 60 min o al terminar la canción actual) con atenuación progresiva de volumen.
- **Exportación de tarjetas de letras:** Estudio para exportar fragmentos de letras sincronizadas a 1080p o copiarlas directamente al portapapeles.
- **Transición suave (Crossfade & Gapless):** Mezcla progresiva ajustable entre canciones para eliminar silencios bruscos.
- **Formato universal `.cypherpack` y doble clic:** Respaldo completo de biblioteca en un solo archivo con asociación en Windows para importación directa con doble clic.
- **Discord Rich Presence nativo (Win32 RPC):** Muestra la música, artista, álbum, progreso y carátula en tu perfil de Discord sin dependencias DLL externas.
- **Minimizar a la bandeja del sistema (System Tray):** Segundo plano limpio junto al reloj con menú contextual rápido y atajos globales de teclado.
- **Streaming de YouTube con Chromium embebido (JCEF):** Reproducción directa con Chromium integrado, metadatos y letras sincronizadas offline y online.
- **Fondo líquido animado (SkSL Liquid Glass):** Efectos visuales de cristal líquido acelerados por GPU con tres campos fluidos y refracción reactiva.

---

## 📦 Descargas

Descarga las últimas versiones oficiales desde la sección de **[Releases](https://github.com/Itsbatt/Cypher/releases)**:

### Android (v1.5.1)
- **[app-arm64-v8a-release.apk](https://github.com/Itsbatt/Cypher/releases/latest)** — Para dispositivos móviles de 64 bits (recomendado para la gran mayoría de teléfonos y tablets).
- **[app-universal-release.apk](https://github.com/Itsbatt/Cypher/releases/latest)** — Compatible con todas las arquitecturas ABI admitidas.
- **`SHA256SUMS.txt`** — Sumas de verificación criptográfica para verificar la integridad del APK.

Para comprobar la integridad del paquete descargado:
```bash
sha256sum -c SHA256SUMS.txt
```

> **Versión actual de Android:** **1.5.1** (`versionCode 22`). Compatible con Android 10 (API 29) hasta Android 16 (API 36).

### Cypher PC (v1.3.0)
- **[CypherMusic-1.3.0.msi](https://github.com/Itsbatt/Cypher/releases/tag/pc-v1.3.0)** — Instalador por usuario de Windows (no requiere permisos de administrador).
- **[CypherMusic-1.3.0-windows-portable.zip](https://github.com/Itsbatt/Cypher/releases/tag/pc-v1.3.0)** — Versión portable empaquetada lista para descomprimir y ejecutar, con Java y Chromium preinstalados para funcionamiento 100% offline.

> Consulta las notas completas de la versión de escritorio en [`docs/releases/PC-1.3.0.md`](docs/releases/PC-1.3.0.md).

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

Toda la documentación técnica se encuentra organizada en la carpeta [`docs/`](docs/):

- [`docs/PROJECT_DOCUMENTATION.md`](docs/PROJECT_DOCUMENTATION.md) — Documentación técnica exhaustiva, evolución histórica y arquitectura profunda del sistema.
- [`docs/PROYECTO_COMPLETO.md`](docs/PROYECTO_COMPLETO.md) — Ficha maestra de componentes, dependencias, persistencia y estado funcional.
- [`docs/MAPEO_ARCHIVOS.md`](docs/MAPEO_ARCHIVOS.md) — Índice y mapa rápido de archivos fuente y módulos del proyecto.
- [`docs/AUDIT_REPORT.md`](docs/AUDIT_REPORT.md) — Informes de auditoría y pruebas de estabilidad en dispositivos reales.
- [`docs/INFORME_REPARACION_REPRODUCCION.md`](docs/INFORME_REPARACION_REPRODUCCION.md) — Informe técnico sobre la resiliencia del motor de streaming.
- [`docs/releases/`](docs/releases/) — Notas de versión detalladas de cada lanzamiento publicado (Android y PC).
- [`docs/liquid-glass/`](docs/liquid-glass/) — Especificaciones técnicas de los shaders AGSL/SkSL.

### Código congelado

- [`experimental/supabase/`](experimental/supabase/README.md) — Backend de sincronización en
  Supabase, retirado del producto en 1.5.0. No forma parte de ningún build; se conserva con su
  hoja de ruta por si la nube vuelve como función opcional.

---

## 🤝 Comunidad y seguridad

- [`CONTRIBUTING.md`](CONTRIBUTING.md) — Cómo compilar, ejecutar los tests, estilo y flujo de ramas.
- [`SECURITY.md`](SECURITY.md) — Cómo reportar una vulnerabilidad de forma privada.
- Plantillas de issue para fallos, sugerencias y Pull Requests en [`.github/`](.github/).

---

## 📄 Licencia y Autoría

- **Propietario / Autor:** **itsbatt** ([@Itsbatt](https://github.com/Itsbatt))
- **Licencia:** Distribuido bajo los términos de la licencia de software libre **GNU General Public License v3.0** — consulta el archivo [`LICENSE`](LICENSE) para más detalles.

# Cypher v1.0.2 — Wrapped Anual, Suite de Audio DSP & Migración 100% Kotlin 🎵✨

**Cypher** es un reproductor de música de alta fidelidad para Android 13+ (API 33-36) que combina tu biblioteca local (FLAC, WAV, ALAC, MP3, AAC) con streaming global de YouTube Music, construido con **Jetpack Compose**, **Material 3**, **AndroidX Media3**, shaders **AGSL Liquid Glass** y **Room v3**.

La versión **1.0.2** (`versionCode 3`) es una actualización mayor que introduce el evento anual **Cypher Wrapped** en formato Stories interactivas, una **Suite de Procesamiento de Audio DSP** (normalización -14 LUFS y ecualizador con presets), **Pre-caching Gapless**, la **migración completa a Kotlin nativo** de la base de datos Room y la solución definitiva al bug de pausa al saltar de canción.

---

## ✨ Novedades Principales

### 🎚️ Suite de Audio DSP y Normalización de Volumen
- **Normalización de Volumen (-14 LUFS / ReplayGain):** Integración nativa con `LoudnessEnhancer` enlazado a la sesión de audio del reproductor (`600 mB` de ganancia objetivo). Elimina los molestos saltos de volumen entre canciones acústicas y producciones modernas comprimidas.
- **Ecualizador Gráfico Paramétrico:** Hoja de control interactiva (`CypherEqualizerSheet`) con 5 bandas de frecuencia calibrables en tiempo real y selector de 8 presets (*Plano, Refuerzo de Graves, Rock, Pop, Electrónica, Vocal, Acústica, Jazz*).
- **Refuerzo de Graves (BassBoost):** Potenciador de sub-frecuencias dedicado con control deslizante porcentual.
- **Pre-caching Inteligente (Gapless Playback):** Resolución proactiva en segundo plano de la URL de streaming de la siguiente canción cuando restan 25 segundos o al comenzar una pista. El salto entre canciones de streaming ocurre ahora a velocidad instantánea, sin silencios ni rueda de carga.

### 🎁 Cypher Wrapped: Resumen Anual Estacional (Estilo Stories)
- **Ventana de Disponibilidad Estacional:** El resumen anual solo está activo del **1 de diciembre al 7 de enero**. Fuera de estas fechas, la interfaz se mantiene limpia y libre de distracciones.
- **Pop-up Automático:** Tras completar el onboarding, si tienes minutos escuchados durante el año, el Wrapped se despliega de forma automática al iniciar la app.
- **Control de Frecuencia:** Se guarda un registro anual en preferencias para que no sea intrusivo en arranques posteriores una vez visto.
- **Historias Interactivas (Animaciones estilo Spotify):**
  - Fondo dinámico oscuro OLED con halos de luz animados en rotación continua.
  - Top 5 de canciones con animaciones de entrada escalonada y podio destacado.
  - Artistas más escuchados con barras porcentuales y tarjeta del **Día pico de escucha**.
  - Tarjeta final consolidada con métricas globales, lista para compartir.
  - Gestos táctiles fluidos (tocar laterales para avanzar/retroceder, mantener pulsado para pausar) con respuesta háptica en cada diapositiva.
- **Recordatorio del 31 de Diciembre:** Alarma exacta del sistema (`AlarmManager`) programada automáticamente para el 31 de diciembre a las 20:00 para recordar revivir el año musical.

---

## 🛠️ Corrección de Bugs Críticos

### ⏸️ Solución al Bug de Auto-Pausa al Cambiar de Canción
- **Diagnóstico:** El reproductor web IFrame emite un estado transitorio `PAUSED` (código 2) mientras descarga el vídeo previo y carga el siguiente. El listener de eventos lo interpretaba como una pausa intencionada del usuario, cambiando `isPlaying = false` y desincronizando la cola con ExoPlayer.
- **Corrección:** 
  - Se implementó una ventana de transición segura (`isTransitioningTrack` de 3000 ms) que filtra y descarta eventos de pausa intermedios durante el salto de pista.
  - Se previno la retroalimentación en `onPlayWhenReadyChanged`.
  - Se redujo el watchdog de recuperación de buffering de 8s a 4s con orden activa de auto-reanudación (`.play()`).

---

## 🏗️ Refactorización y Arquitectura

### ⚡ Persistencia Room Nativa Optimizada para AGP 9
- Arquitectura Room v3 totalmente probada y validada en hardware real (Samsung Galaxy S22 5G con Android 16).
- Generación de `CypherDatabase_Impl` mediante el procesador de anotaciones de Room en compilador nativo Java con helpers y mapeadores 100% idiomáticos en Kotlin (`CypherDatabase.kt`, `RoomMappers.kt`).
- Estabilidad total en tiempo de ejecución, eliminando el fallo de inicio `CypherDatabase_Impl does not exist`.

### 🧹 Limpieza y Modernización
- **Scrobbling desestimado:** Eliminado el paquete `com.cypher.music.scrobble` y sus llamadas en el ciclo de vida del reproductor para mantener la app ligera y centrada en la experiencia local.
- **Material 3 & Edge-to-Edge:** Reemplazados componentes obsoletos (`Divider` por `HorizontalDivider`, iconografía `AutoMirrored`) y eliminadas manipulaciones obsoletas de colores de la barra de navegación en `Theme.kt` para compatibilidad nativa con Android 15/16.

---

## 📦 Binarios y Descargas de la Release

| Archivo | Arquitectura / Tipo | Tamaño | Checksum SHA-256 |
|---|---|---|---|
| **`Cypher-v1.0.2-release.apk`** | Universal (`arm64-v8a` + `armeabi-v7a`) | ~98.8 MB | `3322697C2DA7C4016D155469A791FAA47FA55A61A112F5005CF4CCA884B452E9` |
| **`Cypher-v1.0.2-arm64-v8a-release.apk`** | Solo 64-bit (`arm64-v8a`) | ~57.3 MB | `3D11FD842802A95ECF0E7DC77B197ED428B961F121C832DFF649169A06BF33CA` |
| **`Cypher-v1.0.2-debug.apk`** | Universal Debug con logs | ~173.3 MB | `2B02D0B82C830CEF2795555C092FFE7AA70107616392C8EC0DFA747FF49D9198` |

> **Nota:** La versión recomendada para el 99% de dispositivos Android modernos (Samsung Galaxy, Xiaomi, Pixel, OnePlus, etc.) es **`Cypher-v1.0.2-arm64-v8a-release.apk`** o el paquete universal **`Cypher-v1.0.2-release.apk`**. Ambas versiones están optimizadas con Proguard/R8 y firmadas con la clave de release del proyecto.

---

## 📋 Requisitos del Sistema
- **Sistema Operativo:** Android 13 o superior (`minSdkVersion 33`, `targetSdkVersion 36`).
- **Dispositivo probado y verificado:** Samsung Galaxy S22 5G (Android 16 / SDK 36), emulador x86_64 Android 14+.
- **Permisos requeridos:** Notificaciones (`POST_NOTIFICATIONS`), Alarma exacta (`SCHEDULE_EXACT_ALARM`), Reproducción en segundo plano (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`).

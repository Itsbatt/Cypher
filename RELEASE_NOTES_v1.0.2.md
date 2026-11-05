# Cypher v1.0.2 — Wrapped Anual y Rediseño Visual Estilo Stories 🎵✨

**Cypher** es un reproductor de música de alta fidelidad para Android 13+ que combina tu biblioteca local (FLAC, WAV, ALAC, MP3, AAC) con streaming de YouTube Music, construido con **Jetpack Compose**, **Material 3**, **AndroidX Media3**, **Room v3** y shaders **AGSL Liquid Glass**.

La versión **1.0.2** introduce el evento anual **Cypher Wrapped** completamente rediseñado con animaciones fluidas estilo historias interactivas, disponibilidad estacional inteligente y notificación automática programada.

---

## ✨ Novedades de la versión 1.0.2

### 🎁 Cypher Wrapped: Resumen Anual Estacional
- **Ventana de disponibilidad:** El resumen anual solo está activo del **1 de diciembre al 7 de enero** (en enero resume el año recién cerrado). Fuera de este periodo, la experiencia se mantiene limpia y libre de distracciones.
- **Pop-up automático una vez al año:** Al iniciar la app dentro de temporada (y tras completar el onboarding inicial), si tienes minutos escuchados, el Wrapped se despliega automáticamente a los 1,2 segundos.
- **Control de repetición:** Se guarda una clave por año en `SharedPreferences` para que **no vuelva a aparecer de forma intrusiva** en los siguientes arranques una vez visto.
- **Acceso manual en Ajustes:** La fila para revivir el Wrapped aparece en Ajustes únicamente durante la temporada activa.

### 🎨 Rediseño Visual Interactivo (Estilo Spotify Stories)
- **Fondo cinemático dinámico:** Fondos oscuros OLED con halos de luz animados en rotación suave, tipografía de gran escala con degradados vibrantes.
- **Top 5 Canciones:** Entrada escalonada de abajo a arriba (puestos 5 al 1) con insignias de podio, carátula y el Top 1 resaltado.
- **Artistas Más Escuchados:** Barras de porcentaje animadas, tiempo de escucha por artista y tarjeta especial para el **Día pico de escucha**.
- **Diapositiva final de resumen:** Tarjeta consolidada y limpia con los minutos totales, reproducciones, canción y artista favoritos, lista para capturar y compartir.
- **Controles táctiles y háptica:** Toca a la izquierda/derecha para retroceder/avanzar, mantén pulsado para pausar la historia y siente una vibración suave en cada cambio de diapositiva.

### ⏰ Recordatorio Automático del 31 de Diciembre
- `WrappedScheduler` programa una alarma exacta del sistema (`AlarmManager`) para el **31 de diciembre a las 20:00** notificando al usuario que su resumen del año está listo para revivirlo con un solo toque.

---

## 📦 Descargas de la Release

| Archivo | Arquitectura / Tipo | Descripción |
|---|---|---|
| **`Cypher-v1.0.2-release.apk`** | Universal (arm64-v8a + armeabi-v7a) | **Versión recomendada:** Release firmada con optimización R8/Proguard habilitada. |
| **`Cypher-v1.0.2-arm64-v8a-release.apk`** | Solo arm64-v8a | Versión ligera optimizada para dispositivos modernos de 64 bits (Samsung, Pixel, etc.). |
| **`Cypher-v1.0.2-debug.apk`** | Universal | Versión de desarrollo con depuración y logs activos. |

> **Requisitos:** Android 13 o superior (`minSdk 33`, `targetSdk 36`).

---

*Código fuente y documentación: [README.md](README.md) · [PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md) · [MAPEO_ARCHIVOS.md](MAPEO_ARCHIVOS.md)*

# Cypher v1.0.1 — Nombre de usuario y pausa al desconectar auriculares

**Cypher** es un reproductor de música de alta fidelidad para Android 13+ que combina tu biblioteca local (FLAC, WAV, ALAC, MP3, AAC) con streaming de YouTube Music. Está construido con **Jetpack Compose**, **Material 3**, **AndroidX Media3**, **Room v3** y shaders **AGSL** para la interfaz Liquid Glass.

Esta actualización de parche (**1.0.1**, `versionCode 2`) no rediseña nada: añade dos mejoras incrementales sobre la arquitectura existente y elimina una acción de ajustes que no hacía nada.

---

## ✨ Novedades

### 👤 Sistema de nombre de usuario
- En el primer arranque, Cypher muestra un diálogo de bienvenida para preguntarte cómo quieres que te llame.
- El nombre se guarda localmente en `SharedPreferences` (`cypher_user_prefs`) y **no vuelve a pedirse** en los siguientes arranques.
- El saludo de Inicio y el perfil de Ajustes se actualizan **al instante**, sin reiniciar la aplicación.
- En **Ajustes → Perfil y Sesión Local → "Cambiar nombre"** puedes modificarlo en cualquier momento.
- El `@usuario` se genera automáticamente a partir del nombre (sin acentos, en minúsculas) y se conserva el color de avatar.

### 🎧 Pausa automática al desconectar los auriculares
- Se atiende `ACTION_AUDIO_BECOMING_NOISY` en el servicio de reproducción.
- Al desconectar auriculares con cable o Bluetooth, la reproducción **se pausa de inmediato**.
- La música nunca continúa sonando por el altavoz del dispositivo.
- ExoPlayer ya cubría el audio local/offline con `setHandleAudioBecomingNoisy(true)`; el nuevo receptor cubre el motor IFrame de YouTube, cuyo audio no pasa por ExoPlayer.
- Sin pausas duplicadas ni listeners de más: la notificación, la pantalla de bloqueo y los controles externos siguen funcionando exactamente igual.

### 🧹 Ajustes más limpios
- Eliminado el botón **"Cambiar de cuenta / Cerrar sesión"**, que no realizaba ninguna acción.

---

## 📦 Descargas

| Archivo | Descripción | Recomendado para |
|---|---|---|
| **`Cypher-v1.0.1-release.apk`** | Build de release, optimizada, firmada con la keystore de debug para instalación directa. | Todos los usuarios |
| **`Cypher-v1.0.1-debug.apk`** | Build de depuración con logging y diagnósticos extendidos. | Desarrollo y reporte de errores |

> **Requisitos:** Android 13 (API 33) o superior. `minSdk 33`, `targetSdk 36`.

---

## 🔧 Detalles técnicos

- **Sin cambios de base de datos ni migraciones:** el nombre del perfil vive en `SharedPreferences`, igual que las preferencias de tema.
- **Sin dependencias nuevas:** se reutiliza la arquitectura y el estilo de los diálogos existentes.
- **Un único diálogo compartido** para el onboarding y la edición del nombre, evitando duplicar UI.
- Compilado con `./gradlew clean assembleDebug assembleRelease` y verificado en un **Samsung Galaxy S22 5G** (Android 16 / SDK 36).

---

## ⚠️ Nota de actualización

La build de release usa la misma keystore de debug que la v1.0.0, por lo que se instala como actualización normal conservando playlists, favoritos y descargas. La primera vez que abras esta versión se pedirá el nombre de usuario (una sola vez).

*Documentación completa: [README.md](README.md) · [PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md) · [MAPEO_ARCHIVOS.md](MAPEO_ARCHIVOS.md)*

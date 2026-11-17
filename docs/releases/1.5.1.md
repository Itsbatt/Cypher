# Cypher 1.5.1 — Streaming desacoplado, fallback de contingencia y Room en KSP

`versionName` **1.5.1** · `versionCode` **22** · `applicationId` sin cambios (`com.cypher.music`).

## Qué trae

- **Arquitectura de streaming desacoplada (`StreamResolver`):**
  - El motor de resolución de audio de YouTube se independizó de la capa de datos. Ahora reside en una interfaz limpia `StreamResolver` (`YouTubeStreamResolver`, `InnertubeStreamApi`, `InnertubeStreamParser` y un cliente `YouTubeHttp` unificado con DoH y timeouts consistentes).
  - Si un proveedor o formato falla, no interrumpe la reproducción ni lanza excepciones: pasa de forma segura y telemetrizada al siguiente nivel.

- **Mecanismo de contingencia con Proxies (Piped / Invidious):**
  - Se añade `ProxiedStreamResolver` como Tier 4 automático de resolución ante bloqueos de IP, throttles o restricciones de formato por parte de YouTube.
  - **Configuración en Ajustes › Audio y Reproducción**: Permite activar o desactivar el fallback de proxy y configurar la instancia preferida (soporta Piped e Invidious).
  - La configuración de proxy se incluye de forma automática en los respaldos ZIP de la biblioteca.

- **Migración integral de Room a Kotlin con KSP:**
  - Se completó la migración de las 16 clases de base de datos de Java a Kotlin puro usando KSP (`com.google.devtools.ksp`).
  - El esquema v9 y su `identityHash` se mantuvieron 100% idénticos: actualización limpia y sin riesgo de migración destructiva para bibliotecas existentes.

- **Higiene comunitaria y gobernanza abierta:**
  - Código experimental de Supabase formalmente archivado en `experimental/supabase/`.
  - Incorporación de directrices de contribución (`CONTRIBUTING.md`), política de seguridad (`SECURITY.md`) y plantillas de issues/PRs.

## Requisitos

- Android 10 o superior (API 29+), 64 bits y 32 bits ARM (arm64-v8a, armeabi-v7a). Compatible hasta Android 16 (API 36).
- Instalación directa sobre cualquier versión anterior (1.4.x o 1.5.0) sin pérdida de biblioteca, favoritos ni descargas.

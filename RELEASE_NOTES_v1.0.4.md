# Cypher v1.0.4 — Auditoría de seguridad, rendimiento y actualizaciones verificadas 🔍

**Versión:** `1.0.4` (`versionCode 5`) · Android 13+ (API 33-36)
**Firma:** clave de release del proyecto (`CN=Cypher`, SHA-256 `54:41:5F…77:20`), compatible con las instalaciones 1.0.0–1.0.2.

## 🔐 Seguridad
- El WebView de inicio de sesión de Google queda restringido a dominios de Google y YouTube: una redirección a cualquier otro sitio ya no se carga dentro de la sesión.
- La descarga de actualizaciones tiene un límite duro de 300 MB (también aplicado al tamaño real escrito en disco), evitando llenar el almacenamiento con un recurso manipulado.
- Dependencia `androidx.security:security-crypto` actualizada de `1.1.0-alpha06` a **1.1.0 estable**.
- Revisión completa de componentes exportados: solo `MainActivity` (launcher/intents públicos) y `PlaybackService` (MediaLibraryService para Android Auto/Asistente) están exportados; `FileProvider` y el receptor de Wrapped no lo están.

## 🧾 Datos
- El texto de la copia de seguridad ahora describe exactamente lo que exporta: playlists y favoritos en JSON local. El perfil, las estadísticas y los ajustes **no** se incluyen; antes se prometían «analíticas» y un formato «firmado» que no existían.

## ⚙️ Actualizaciones
- Actualización real de extremo a extremo sobre una instalación 1.0.2 firmada con la clave original: detección, aviso, descarga, checksum SHA-256, verificación de firma, instalador oficial y conservación de datos.
- Sin cambios en el comportamiento visible del aviso.

## ✅ Verificación
- 14 pruebas JVM sin fallos.
- Release minificado firmado y verificado con `apksigner`.
- Recorrido funcional de Inicio, Biblioteca, Buscar, Ajustes, diálogos, ecualizador, temporizador y copia de seguridad en CPH2579 (Android 15).

## ⚠️ Limitaciones
- Las instalaciones firmadas con una clave distinta (por ejemplo, el APK debug de la auditoría 1.0.3) requieren una desinstalación única.
- La copia de seguridad no incluye perfil, estadísticas ni preferencias.

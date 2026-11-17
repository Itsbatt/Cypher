# Política de seguridad

Cypher reproduce audio local y de streaming, descarga medios, habla con servicios de terceros,
ejecuta binarios nativos (yt-dlp/FFmpeg en Android, Chromium embebido y el asistente SMTC en
Windows) y se actualiza a sí mismo desde GitHub Releases. Por eso los fallos de seguridad se
tratan como prioritarios.

## Versiones con soporte

| Plataforma | Versión | Soporte |
|---|---|---|
| Android | 1.5.x | ✅ Recibe arreglos de seguridad |
| Android | 1.4.x y anteriores | ❌ Solo se documentan, no se parchean |
| Cypher PC (Windows) | 1.1.x | ✅ Recibe arreglos de seguridad |
| Cypher PC (Windows) | 1.0.x y anteriores | ❌ Solo se documentan, no se parchean |

## Cómo reportar una vulnerabilidad

**No abras un issue público.** Usa el aviso privado de GitHub:

1. Entra en la pestaña **Security** del repositorio.
2. Pulsa **Report a vulnerability**.
3. Describe el impacto, los pasos para reproducirlo y la versión afectada.

También puedes escribir a **itsbatt** a través del perfil de GitHub si el formulario no está
disponible.

Incluye, cuando puedas:

- Plataforma y versión exacta (`1.5.0`, `versionCode 21`, `PC 1.1.0`).
- Si afecta a reproducción local, streaming, descargas, importación (ZIP/CSV/JSON/enlace),
  letras (LRCLIB), actualizaciones o el backend de sincronización archivado.
- Una prueba de concepto mínima, sin datos personales ni contenido con derechos de autor.

## Qué esperar

- Confirmación de recepción en un plazo razonable (proyecto mantenido por una sola persona,
  sin SLA formal).
- Un aviso conjunto antes de publicar los detalles, y crédito en las notas del release si lo
  deseas.
- Si el fallo está en una dependencia de terceros (yt-dlp, Media3, JCEF/Chromium, jaudiotagger),
  se coordina el reporte con su proyecto.

## Alcance

Dentro del alcance:

- Ejecución de código o escalada de privilegios a través de metadatos, carátulas, letras,
  archivos ZIP de copia de seguridad o listas importadas.
- Fuga de datos locales (biblioteca, historial, estadísticas) o de credenciales de sesión.
- Suplantación del canal de actualizaciones o validación insuficiente de la firma del APK.
- SSRF o inyección en las llamadas a InnerTube, LRCLIB y demás servicios externos.

Fuera del alcance:

- Bloqueos o cambios de servicio de YouTube/Google que solo rompen la reproducción
  (son incidencias funcionales: ábrelas como *bug*).
- Ataques que requieren un dispositivo con root o un atacante con acceso físico ya
  comprometido.
- Avisos automáticos de dependencias sin una ruta de explotación demostrable.

## Prácticas del repositorio

- Ningún secreto se versiona: `keystore.properties`, `local.properties` y los archivos de firma
  están fuera de git.
- Las releases de Android se firman; un tag `v*` sin secretos de firma falla en CI a propósito.
- Las dependencias y los binarios nativos se fijan por versión en `gradle/libs.versions.toml`
  y en `desktop/build.gradle.kts`.

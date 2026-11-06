# Auditoría Cypher — Informe

## A. Resumen
- **Estado inicial:** v1.0.2 instalada en CPH2579 (Android 15, arm64). El escáner QR cerraba la app; el proyecto no estaba en el workspace y se clonó desde GitHub.
- **Trabajo realizado:** reproducción y corrección del crash QR con stack desofuscado (mapping R8), sistema de actualizaciones GitHub Releases, auditoría de seguridad básica, build release, instalación real y pruebas.
- **Estado final:** v1.0.3 instalada y funcionando en el teléfono; QR operativo; actualizador implementado y probado contra la release real.

## B. Funcionalidades
| Funcionalidad | Estado | Prueba | Resultado |
|---|---|---|---|
| Escáner QR (cámara) | Corregido | CPH2579 + emulador | Abre ActivityProxy de ML Kit y procesa fotogramas |
| Importación manual por código | Correcto | Código inválido `ZZZZZ` | Error en español, sin cierre |
| Abrir/cerrar escáner repetidamente | Correcto | 2 ciclos | Mismo proceso, sin crash |
| Aviso de actualización | Nuevo | Emulador contra GitHub v1.0.2 | Aviso, descarga y rechazo de firma |
| Descarte persistente | Nuevo | Preferencias + relanzado | No vuelve a avisar para 1.0.2 |
| Reproducción / biblioteca / ajustes | Smoke test | Navegación real | Sin crashes en el recorrido realizado |

## C. Errores
1. **QR (prioritario):** NPE en `mlkit_code_scanner.zzoi` solo con R8. Causa: consumer rules ausentes en `play-services-code-scanner:16.1.0`. Solución: keep rules. Verificado en dispositivo físico.
2. **Instalación:** `INSTALL_FAILED_UPDATE_INCOMPATIBLE` al cambiar de firma; `INSTALL_FAILED_VERSION_DOWNGRADE` por versionCode. Se resolvió con desinstalación completa y build 1.0.3 (versionCode 4).
3. **Entorno:** caché de transforms de Gradle corrupta y `local.properties` ausente; limpiada/creada, sin cambios de código.

## D. Seguridad
- Revisión de manifiesto: permisos justificados; se añadió `REQUEST_INSTALL_PACKAGES` solo para el instalador.
- `FileProvider` no exportado y restringido a `cache/updates`.
- Actualizador: HTTPS, límite de checksums, verificación de paquete/versión/firma, sin instalación silenciosa.
- `PlaybackService` sigue exportado para integración con el sistema multimedia (Android Auto/Asistente); revisar si se restringe en el futuro.
- No se encontraron secretos en el código ni claves en el repositorio.

## E. Rendimiento
- Sin regresiones observadas en arranque, navegación ni escáner.
- La comprobación de actualizaciones es asíncrona, con timeout y cooldown; no bloquea la UI.
- No se realizó perfilado profundo de CPU/RAM.

## F. GitHub
- Archivos: nuevo paquete `update/`, `UpdateBanner.kt`, `file_paths.xml`, reglas ProGuard, manifiesto, `MainScreen`, tests y documentación.
- Commit y push: ver estado al final de la sesión.

## G. Pruebas
- Dispositivo: CPH2579, Android 15 (SDK 35), arm64-v8a.
- 14 pruebas unitarias JVM, 0 fallos.
- Release minificado compilado; instalación ADB verificada.
- Escáner QR validado en hardware real (cámara abierta y frames procesados).
- Actualizador validado en emulador contra la API real de GitHub.

## H. Pendientes / limitaciones
- Barrido funcional exhaustivo de todas las pantallas y ajustes (no completado al 100%).
- Auditoría de dependencias y vulnerabilidades no automatizada.
- Firma: falta el keystore de release; el APK entregado usa firma debug.
- Instalación de una actualización real no probada de extremo a extremo por incompatibilidad de firma del entorno.
- Publicación de una GitHub Release v1.0.3 pendiente (no se creó ninguna release).

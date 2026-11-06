# Cypher v1.0.3 — Escáner QR estable y actualizaciones desde GitHub 🚀

**Versión:** `1.0.3` (`versionCode 4`) · Android 13+ (API 33-36)

## 🐞 Corrección crítica: cierre al escanear QR
- **Causa raíz:** `play-services-code-scanner:16.1.0` no incluye reglas ProGuard de consumidor. Con R8 full mode (AGP 9) se eliminaban/reescribían clases internas y `GmsBarcodeScanning.getClient()` lanzaba `NullPointerException` dentro de `mlkit_code_scanner.zzoi`.
- **Solución:** reglas `-keep` específicas para `com.google.mlkit.**` y `com.google.android.gms.internal.mlkit_code_scanner.**`.
- **Verificado en dispositivo físico** (CPH2579, Android 15): la hoja de importación abre, el escáner de cámara de Play Services se lanza y procesa fotogramas, se puede entrar/salir repetidamente y el flujo manual muestra errores sin cerrar la app.

## 🔄 Sistema de actualizaciones (GitHub Releases)
- Consulta `Itsbatt/Cypher` en segundo plano al abrir la app, con espera de 15 minutos entre comprobaciones y sin bloquear la interfaz.
- Incluye versiones **estables y prerelease**; comparación SemVer real (`1.0.0-rc.2 < 1.0.0`, numéricos y alfanuméricos).
- Aviso compacto y descartable con versión, resumen de novedades, progreso y errores. El descarte se guarda por versión y una versión posterior vuelve a avisar.
- Selección del APK compatible por ABI (nunca builds `debug` ni de otra arquitectura), descarga con progreso, verificación opcional SHA-256 del release, comprobación de `packageName`, versión y **firma instalada**.
- Instalación únicamente a través del instalador oficial de Android, con flujo para activar «Instalar apps desconocidas». Nunca instala en silencio.

## 🔒 Seguridad
- `FileProvider` limitado a `cache/updates`; sin exposición de datos de usuario.
- Descargas solo por HTTPS y con límites de tamaño para checksums.
- Se rechaza cualquier APK con firma distinta a la instalada.

## ✅ Pruebas
- 14 pruebas JVM (versiones, releases, selección de APK, checksums) sin fallos.
- Release minificado generado y verificado; instalación por ADB en CPH2579.
- Actualizador probado contra la release real v1.0.2: detectó la versión, descargó el APK y rechazó correctamente la firma incompatible del entorno de prueba.

## ⚠️ Limitaciones conocidas
- El keystore de release no está en el repositorio: el APK de esta auditoría está firmado con la clave debug. Instalarlo sobre la 1.0.2 firmada con la clave del proyecto exige desinstalar (Android no permite actualizar entre firmas distintas).
- La copia exportada antes de la reinstalación estaba vacía (`playlists: []`, `favorites: []`); el perfil y las estadísticas locales se reiniciaron.
- La auditoría funcional exhaustiva de todas las pantallas y el análisis de dependencias/rendimiento quedan documentados en `AUDIT_REPORT.md`.

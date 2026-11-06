# Cypher v1.0.3 — Escáner QR estable y actualizaciones desde GitHub 🚀

**Versión:** `1.0.3` (`versionCode 4`) · Android 13+ (API 33-36)
**Firma:** clave de release del proyecto (`CN=Cypher`, SHA-256 `54:41:5F…77:20`), compatible con 1.0.0–1.0.2.

## 🐞 Corrección crítica: cierre al escanear QR
- **Causa raíz:** `play-services-code-scanner:16.1.0` no incluye reglas ProGuard de consumidor. Con R8 full mode (AGP 9) se eliminaban/reescribían clases internas y `GmsBarcodeScanning.getClient()` lanzaba `NullPointerException` dentro de `mlkit_code_scanner.zzoi`.
- **Solución:** reglas `-keep` específicas para `com.google.mlkit.**` y `com.google.android.gms.internal.mlkit_code_scanner.**`.
- **Verificado en dispositivo físico** (CPH2579, Android 15): la hoja de importación abre, el escáner de cámara de Play Services se lanza, se puede entrar/salir repetidamente y el flujo manual muestra errores sin cerrar la app.

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
- Instalación real sobre 1.0.2 **sin desinstalar** y con datos conservados (misma firma, `versionCode 4`).
- Actualizador probado contra la release real v1.0.2 y, después, salto real 1.0.3 → 1.0.4.

## ⚠️ Limitaciones conocidas
- Requiere Android 13 o superior (API 33+) y arquitectura ARM; el APK universal incluye arm64-v8a y armeabi-v7a.
- Las instalaciones firmadas con otra clave (por ejemplo, un APK debug) necesitan una desinstalación única.
- La copia de seguridad exporta playlists y favoritos; no incluye perfil, estadísticas ni ajustes.

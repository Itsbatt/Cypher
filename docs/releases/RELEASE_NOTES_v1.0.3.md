# Cypher v1.0.3 — El escáner QR deja de cerrarse y la app ya sabe actualizarse 🚀

**En una frase:** se corrige el cierre inesperado al abrir el escáner de códigos QR y la app estrena su propio sistema de actualizaciones desde GitHub, sin pasar por una tienda.

**Versión:** `1.0.3` (`versionCode 4`) · Android 13+ (API 33-36)

---

## ✨ Qué cambia para ti

- **El escáner QR ya no cierra la app.** El fallo venía de cómo se comprimía el código al generar la versión de release: se estaban eliminando piezas internas del escáner de Google. Ahora se conservan y la cámara abre de forma estable, incluso entrando y saliendo varias veces.
- **Actualizaciones desde la propia app.** Al abrir Cypher se comprueba en segundo plano si hay una versión nueva en GitHub. Si la hay, aparece un aviso discreto con las novedades; puedes descartarlo y no volverá a molestar hasta la siguiente versión.
- **Descarga e instalación guiadas.** La app elige el APK de tu arquitectura, muestra el progreso, verifica el archivo y abre el instalador oficial de Android. Nunca instala en silencio ni acepta un APK con otra firma.
- **Todo lo demás sigue igual:** misma interfaz, mismas playlists, misma reproducción.

---

## 📦 Descargas

| Archivo | Arquitectura | Tamaño | SHA-256 |
|---|---|---|---|
| **`Cypher-v1.0.3-arm64-v8a-release.apk`** | Solo 64 bits (recomendado) | 60,5 MB | `0121df9c12b0f90160034a4bab057bcd23ff3645446f84c7dba7ba2d92c0ad74` |
| **`Cypher-v1.0.3-universal-release.apk`** | Universal (`arm64-v8a` + `armeabi-v7a`) | 104,0 MB | `583fb714760d35323ad00ae0f1c6b001a54175ec64ca6c748caadc80dd2180b4` |

Usa el **arm64-v8a** salvo que tu móvil sea de 32 bits o no sepas cuál te corresponde.

> **Recomendación:** si puedes, instala directamente la **1.0.4**, que incluye esta misma base más los arreglos de seguridad del inicio de sesión y del actualizador. Esta 1.0.3 existe para que el salto 1.0.2 → 1.0.3 → 1.0.4 sea posible sin desinstalar.

---

## 🧭 Cómo actualizar

1. Descarga el APK e instálalo encima: misma firma, **sin desinstalar** y conservando playlists.
2. La próxima vez que abras la app, el actualizador te ofrecerá la 1.0.4.
3. Si vienes de una compilación de prueba con otra firma, Android pedirá desinstalar una vez.

---

<details>
<summary>👨‍💻 Para desarrolladores / Under the hood</summary>

- **Causa del crash del QR:** `play-services-code-scanner:16.1.0` no publica reglas de consumidor para R8; con R8 full mode (AGP 9) se eliminaban clases internas del escáner y `GmsBarcodeScanning.getClient()` fallaba al instanciarse. Se añadieron reglas `-keep` para `com.google.mlkit.**` y `com.google.android.gms.internal.mlkit_code_scanner.**`.
- **Actualizador:** consulta la API de releases de `Itsbatt/Cypher` (estables y prerelease), comparación SemVer real, caché de 15 minutos, descarte por versión, selección de APK por ABI descartando builds `debug`, verificación opcional de `SHA256SUMS.txt`, comprobación de `packageName`, `versionCode` y firma instalada antes de delegar en el instalador del sistema.
- **Seguridad:** `FileProvider` limitado a `cache/updates`, descargas solo HTTPS, límite de tamaño para checksums y rechazo de cualquier APK con firma distinta a la instalada.
- **Build:** release minificado con R8; pruebas unitarias de versiones, selección de APK y checksums en verde.

</details>

---

<details>
<summary>🔐 Seguridad y verificación de la firma</summary>

- Clave de release del proyecto: `CN=Cypher, OU=Mobile, O=Cypher, L=Madrid, C=ES`.
- Huella SHA-256 del certificado: `54:41:5F:F1:75:1B:71:D0:D1:54:30:B9:0C:44:CF:04:B5:8F:46:52:F9:C6:83:8D:15:78:E9:29:2B:C1:77:20`.
- APKs verificadas con `apksigner`; la huella coincide con 1.0.0–1.0.2, así que la actualización se instala encima sin desinstalar.
- Comprueba el archivo con `sha256sum` usando los valores de la tabla.

</details>

---

## ⚠️ Limitaciones conocidas

- La copia de seguridad exporta playlists y favoritos; no incluye perfil, estadísticas ni ajustes.
- Esta versión no incorpora todavía los arreglos de seguridad de la 1.0.4.
- El inicio de sesión con una cuenta real de Google y las descargas offline no se validaron en esta auditoría.

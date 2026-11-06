# Cypher v1.0.4 — Más segura al actualizar y al iniciar sesión 🔐

**En una frase:** esta versión cierra un hueco en el inicio de sesión con Google, limita el tamaño de las actualizaciones que descarga la app y deja claro qué guarda la copia de seguridad. No hay cambios visuales.

**Versión:** `1.0.4` (`versionCode 5`) · Android 13+ (API 33-36)

---

## ✨ Qué cambia para ti

- **Inicio de sesión más seguro.** La ventana de Google/YouTube ya no puede acabar navegando a un sitio ajeno; si un enlace intenta salir de esos dominios, se bloquea.
- **Actualizaciones con límite.** La app no descargará un archivo de actualización de más de 300 MB, ni siquiera si el servidor miente sobre su tamaño. Así una descarga manipulada no puede llenarte el almacenamiento.
- **Copia de seguridad honesta.** El texto ahora dice exactamente qué se guarda: **playlists y favoritos**. El perfil, las estadísticas y los ajustes no se incluyen (antes se insinuaba lo contrario).
- **Actualización probada de verdad.** Se instaló una versión anterior firmada con la clave del proyecto y se actualizó desde la propia app: aviso, descarga, verificación, instalador de Android y **datos conservados**.
- **Sin cambios en la interfaz.** Misma apariencia, mismos ajustes, misma reproducción.

---

## 📦 Descargas

| Archivo | Arquitectura | Tamaño | SHA-256 |
|---|---|---|---|
| **`Cypher-v1.0.4-arm64-v8a-release.apk`** | Solo 64 bits (recomendado) | 60,5 MB | `38501a62cd332c9c54db08877a956c52474b340d2645658bc11a54a77e636f81` |
| **`Cypher-v1.0.4-universal-release.apk`** | Universal (`arm64-v8a` + `armeabi-v7a`) | 104,0 MB | `ce8849b5aa19ad15b05340772e6310c2fd418f97353d564379f3aa57461ddba6` |

Para prácticamente cualquier móvil de los últimos años, usa el **arm64-v8a**: ocupa menos y rinde igual. El universal es para equipos de 32 bits o si no sabes cuál te toca.

> `SHA256SUMS.txt` acompaña a los APKs y es el archivo que usa el actualizador para comprobar la descarga.

---

## 🧭 Cómo actualizar

1. Descarga el APK (o abre la app y acepta el aviso de nueva versión).
2. Android pedirá permiso para «Instalar apps desconocidas» la primera vez; es el instalador oficial, no una instalación silenciosa.
3. Instala encima: **no hay que desinstalar** y tus playlists se conservan.

Si vienes de una compilación de prueba firmada con otra clave, Android exigirá desinstalar una vez. Las versiones 1.0.0–1.0.3 oficiales usan la misma firma que esta.

---

<details>
<summary>👨‍💻 Para desarrolladores / Under the hood</summary>

- **Login WebView:** `shouldOverrideUrlLoading` ahora aplica una allowlist (`google.com`, `youtube.com`, `gstatic.com`, `googleusercontent.com`); cualquier otra URL se descarta en lugar de cargarse con la sesión activa.
- **Actualizador:** límite `MAX_APK_BYTES = 300 MB` comprobado en los metadatos del asset y al escribir bytes en disco, para que un `Content-Length` falso no sirva de nada.
- **Dependencias:** `androidx.security:security-crypto` pasa de `1.1.0-alpha06` a **`1.1.0`** estable (AES256-GCM para las cookies de YouTube).
- **Superficie expuesta:** revisado el manifiesto fusionado; solo `MainActivity` (launcher, VIEW audio, `MEDIA_PLAY_FROM_SEARCH`, deep link `cypher://wrapped`) y `PlaybackService` (`MediaLibraryService`, necesario para Android Auto/Asistente) están exportados. `FileProvider` (limitado a `cache/updates`) y el receptor de Wrapped quedan cerrados.
- **Red:** `targetSdk 36`, sin `usesCleartextTraffic`, así que todo el tráfico es TLS.
- **Build:** R8 + resource shrinking; `lintRelease` sin errores y pruebas unitarias de versiones, selección de APK y checksums en verde.

</details>

---

<details>
<summary>🔐 Seguridad y verificación de la firma</summary>

- Clave de release del proyecto: `CN=Cypher, OU=Mobile, O=Cypher, L=Madrid, C=ES`.
- Huella SHA-256 del certificado: `54:41:5F:F1:75:1B:71:D0:D1:54:30:B9:0C:44:CF:04:B5:8F:46:52:F9:C6:83:8D:15:78:E9:29:2B:C1:77:20`.
- Ambas APKs verificadas con `apksigner`; la huella coincide con las instalaciones 1.0.0–1.0.3.
- Verifica el archivo descargado con `sha256sum` usando los valores de la tabla de arriba.

</details>

---

## ⚠️ Limitaciones conocidas

- La copia de seguridad exporta playlists y favoritos; **no** incluye perfil, estadísticas ni preferencias.
- Sigue pendiente actualizar Media3 (1.5.1), OkHttp (4.12) y Room (2.6.1) a versiones mayores: son migraciones con regresión específica y no se abordan aquí.
- El inicio de sesión real con una cuenta de Google, la búsqueda por voz y las descargas offline dependen de servicios externos y no se validaron en esta auditoría.

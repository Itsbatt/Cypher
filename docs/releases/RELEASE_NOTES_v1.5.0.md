# Cypher 1.5.0 — Cuentas de Google y sincronización en la nube

`versionName` **1.5.0** · `versionCode` **21** · `applicationId` sin cambios (`com.cypher.music`).

## Qué trae

- **Cuenta opcional con Google** mediante Credential Manager (`androidx.credentials` +
  `googleid`). El ID token se guarda cifrado con `EncryptedSharedPreferences`; no se
  almacenan contraseñas ni refresh tokens propios.
- **Cypher sigue funcionando sin cuenta y sin conexión.** Toda la app local no cambia:
  reproducción, descargas y biblioteca quedan intactas.
- **Popup de vinculación** para quien ya tenía datos locales: «Tu Cypher, en todas
  partes.», con «Conectar con Google» y «Ahora no». Espera a que Room entregue la
  biblioteca, no bloquea la app, y recuerda la decisión (se vuelve a ofrecer a los 30
  días o nunca más si se descarta desde Ajustes).
- **Sección «CUENTA Y NUBE» en Ajustes**: estado de la cuenta, nombre y correo, estado de
  sincronización, última sincronización correcta, sincronizar ahora y desconectar.
  Desconectar no borra nada local.
- **Sincronización offline-first**: outbox en Room, cursor incremental por cuenta,
  reintentos con backoff exponencial, tombstones para borrados, LWW determinista por
  `updatedAt` y unión de pistas de playlist para no perder canciones, y contadores de
  estadísticas idempotentes (`max`) para no duplicar reproducciones ni Wrapped.
- **Arquitectura compartida con el futuro Cypher PC**: `sync/` no depende de Android
  salvo por el almacén de sesión; los modelos son `Map<String, Any?>` canónicos y el
  protocolo es HTTP+JSON (`POST {base}/v1/sync/pull` y `/v1/sync/push`, cabecera
  `Authorization: Bearer <id token>`).
- **Room 8** con migración aditiva `7 → 8`: solo crea `sync_outbox` y `sync_state`.

## Lo que nunca sale del dispositivo

Audio descargado, cachés, cookies de YouTube, credenciales o tokens, cola de descargas y
datos temporales de dispositivo. La sincronización solo transporta playlists, favoritos,
perfil/preferencias y el rollup diario de estadísticas.

## Configuración necesaria (acción del usuario)

1. ~~Web client ID de OAuth~~ **hecho**: el proyecto ya usa
   `764777457932-djot894fcm28vs15mhptks3p2cql56o6.apps.googleusercontent.com` desde
   `local.properties` (fichero ignorado por git). Se puede sobrescribir con
   `-Pcypher.googleClientId=…` o `CYPHER_GOOGLE_CLIENT_ID`.
2. **Cliente OAuth de tipo Android** (registrado, id
   `764777457932-op2aasnvnqb9d6mrv9q3dtr2221ek36j.apps.googleusercontent.com`):
   Google Cloud Console → *Credenciales* → *Crear credenciales* → *ID de cliente de
   OAuth* → **Android**, en el **mismo proyecto** que el cliente web, con
   - Nombre del paquete: `com.cypher.music`
   - SHA-1 de la firma release:
     `90:06:2A:5D:3A:6A:2A:A0:A6:4F:73:6F:17:0E:5D:61:57:09:42:F8`
     (SHA-256 del certificado: `54415ff1751b71d0d15430b90c44cf04b58f4652f9c6838d1578e9292bc17720`)
   - Si se prueban compilaciones debug, añadir también el SHA-1 de depuración:
     `54:7C:2A:95:32:3D:9E:A1:E1:0E:CF:D2:61:B8:FD:DA:53:A4:C9:4B`
   Este cliente no se usa en el código: el `serverClientId` sigue siendo el cliente
   **web**, como exige Credential Manager. Si se borra, el login deja de funcionar y
   Google Play Services responde «sin credenciales» aunque haya cuentas.
3. **Backend de sincronización** que implemente el contrato de `SyncBackend.kt`.
   Apuntarlo en `cypher.syncBaseUrl` (`CYPHER_SYNC_BASE_URL`). Sin él, la UI muestra
   «sincronización no configurada» y la cola local se conserva.

## Pruebas ejecutadas (SM-S901E, Android 16, API 36)

| Prueba | Resultado |
| --- | --- |
| Instalación sobre 1.4.1 (vc20 → 21), datos intactos | Pasa |
| Arranque de 1.5.0 sin crash | Pasa |
| Popup con datos locales tras cargar la biblioteca | Pasa |
| «Ahora no» cierra el popup y no reaparece al reabrir | Pasa |
| Sección «CUENTA Y NUBE» en Ajustes | Pasa |
| Conectar con client id de prueba → error controlado de Credential Manager, sin crash | Pasa |
| Reproducción tras los cambios (MediaSession en PLAYING) | Pasa |
| Tests unitarios: 130, 0 fallos | Pasa |
| Client id leído por Gradle → `BuildConfig.GOOGLE_WEB_CLIENT_ID` | Pasa (`764777457932-djot…56o6.apps.googleusercontent.com`) |
| Instalación en tablet SM-T580 sobre 1.4.1, arranque y datos | Pasa |
| Conectar con Google con el client id real: se abre el flujo de Credential Manager | Pasa |
| Selector de cuentas de Google | Pasa: «Choose a saved sign-in for Cypher» con la cuenta del dispositivo |
| Login real con la primera cuenta (S22) | Pasa: consentimiento «Continue as Aleix» y ID token aceptado por Cypher |
| Nombre y correo mostrados tras el login | Pasa: «Aleix Gonzalez» / «alejop333pro@gmail.com» |
| Sesión persistente tras cerrar y reabrir la app | Pasa |
| Cerrar sesión conserva datos locales | Pasa: «Sesión cerrada. Tus datos siguen en este dispositivo.» y 7h 22m intactos |
| Login en la tablet SM-T580 | Bloqueado: la tablet no tiene red (`IOException: NetworkError`, `BeginSignInOperation` 28433) |
| Persistencia de sesión, migración a la nube, sync entre dispositivos, offline + sync posterior, cierre de sesión | Bloqueado: requieren el login real y un backend |

## Correcciones hechas durante las pruebas

- Migración 7→8: `kind`/`entityId` se declararon `@NonNull` para que el esquema creado
  por el SQL coincida con la entidad (el crash de arranque era esta validación de Room).
- El estado de cuenta ya no puede tumbar la app: las lecturas de Room van envueltas y
  degradan a modo local.
- El popup espera a la biblioteca real y consulta Room fuera del hilo principal (antes
  Room rechazaba la lectura y nunca había «datos locales»).

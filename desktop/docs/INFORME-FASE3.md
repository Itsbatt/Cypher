# Cypher Desktop · Informe de la tercera fase

Fecha: 13 de noviembre de 2026.
Alcance: cierre de los dos controles pequeños, revisión de OAuth Desktop, validación local
de GitHub Actions y auditoría de seguridad de los artefactos.
**Sin commits, tags, push, releases ni publicaciones. Sin tocar Supabase, secretos, datos
reales, S22 ni SM-T580.**

---

## 1. Causa del problema de Ajustes

**No era un fallo de la interfaz.** El botón está bien dibujado, tiene 44 dp y su `onClick`
llama a `state.select(Screen.SETTINGS)`.

La causa fue el arnés de pruebas:

- `Process.MainWindowHandle` devolvió en varias ejecuciones un rectángulo erróneo (se
  llegaron a calcular clics en `y = 1455`, fuera de pantalla, y una captura mostró otra
  zona del escritorio). La ventana además se movió entre pruebas (de `(48,48)` a `(51,111)`
  y luego a `(-8,-8)` maximizada).
- Esos clics se enviaban fuera del control o a un botón vecino.

Para confirmarlo se instrumentó temporalmente `RailButton` con un `println` del destino y
se comprobó que el clic sobre Favoritos llegaba y el de la coordenada estimada del
engranaje no. Tras localizar su centro real, el log mostró `RAILCLICK Ajustes` y la captura
`audit3-settings-ok.png` muestra la pantalla de Ajustes.

## 2. Causa del problema de la X de la cola

La misma. La X es un `IconButton` de 24 dp con `onClick = { queueOpen = false }`; con la
coordenada calibrada el log mostró `QUEUECLOSE` y el panel desapareció
(`audit3-queue-close-try.png`, `audit3-max-queue-closed.png`).

No se amplió ninguna zona pulsable ni se cambió el layout: no había nada que corregir en el
producto.

## 3. Pruebas con entrada real de ratón

Se usó `mouse_event`/`SetCursorPos` de Windows (entrada real del sistema, no un clic
interno de Compose), con ventana en primer plano. **No hubo un ratón físico**: el agente
no puede manipular hardware; esta es la limitación honesta de la prueba.

| Prueba | 1320×860 | Maximizada 1936×1048 |
| --- | --- | --- |
| Ajustes abre | Sí (`audit3-settings-ok`) | Sí (`audit3-max-settings2`) |
| X de la cola cierra | Sí (`audit3-queue-close-try`) | Sí (`audit3-max-queue-closed`) |
| Cola abre | Sí (`audit3-queue-open`) | Sí (`audit3-max-queue`) |
| Favoritos / Inicio | Sí | Sí |
| Shuffle y volumen | Sí (`shuffle=true`, `volume=0.548`) | No repetido |

El arnés quedó corregido: `desktop/native/cypher-window.ps1` enumera ventanas visibles y
elige la de mayor área, en lugar de confiar en `MainWindowHandle`.

## 4. Otros controles pequeños revisados

- Icono de cola (abre/cierra el panel).
- Volumen (slider y persistencia en `preferences.volume`).
- Aleatorio (persistencia en `preferences.shuffle`).
- Icono de biblioteca del rail (abre el panel; clic fuera lo cierra).
- Botones de la barra del reproductor (play/pausa, siguiente, anterior) ya cubiertos por
  SMTC y por la auditoría anterior.
- No se revisaron en esta pasada los iconos de las tarjetas, los swatches de acento ni los
  botones de los diálogos nativos.

## 5. Estado de OAuth Desktop

Implementado (PKCE + loopback, `sync/GoogleAuth.kt`) pero **no probado con cuenta real**.
Este equipo no tiene secreto:

- `%APPDATA%\Cypher Desktop\desktop-client-secret.txt` → **no existe**.
- `CYPHER_DESKTOP_CLIENT_SECRET` → **no definida**.
- El cliente Desktop del proyecto es
  `764777457932-goan61rtqc8g3u6t7o7if2pdm6pkkc97.apps.googleusercontent.com` (público, en
  el código).

## 6. Configuración que falta

1. **Client secret** del cliente Desktop (solo lo puede copiar el propietario).
2. Guardarlo desde **Cuenta → CLIENTE OAUTH DESKTOP → Guardar secreto** (o la variable de
   entorno), nunca en el repositorio ni en el chat.
3. **YouTube Data API v3** habilitada en el proyecto.
4. Ámbito `youtube.readonly` en la pantalla de consentimiento y cuenta como usuario de
   prueba si la app está en modo Prueba.
5. `GOOGLE_DESKTOP_CLIENT_ID` como secreto de la Edge Function (no se puede leer su valor
   desde aquí; no se ha modificado).

Pasos exactos en `desktop/docs/GOOGLE-OAUTH-DESKTOP.md`.

## 7. Resultado del inicio de sesión de Google

**No ejecutado.** Sin secreto, Google rechaza el intercambio (`client_secret is missing`);
la app ya lo explica con esa instrucción. No se abrió ninguna ventana de consentimiento ni
se usaron cookies del navegador.

## 8. Resultado de carga de playlists de YouTube

**No probado.** Depende del login y de YouTube Data API. No se importó nada ni se descargó
audio.

## 9. Resultado de importación

**No realizada**: requiere autorización expresa y login. No se tocó la biblioteca real
(13 archivos, 90.421.208 bytes, comprobados antes y después de las pruebas de MSI).

## 10. Resultado del pull real de Supabase

**No realizado** (sin sesión válida). Sí se verificó que el backend está desplegado y
protege las rutas:

| Petición | Resultado |
| --- | --- |
| `GET …/sync/health` | 200 `{"ok":true}` |
| `POST …/v1/sync/pull` sin token | 401 `missing_token` |
| `POST …/v1/sync/pull` con `a.b.c` | 401 `invalid_token` |

No se enviaron datos ni se usaron fixtures para simular un pull.

## 11. Validación de GitHub Actions

- `.github/workflows/desktop.yml` y `.github/workflows/android.yml` **parsean correctamente**
  (SnakeYAML 2.0, el que trae Gradle).
- Triggers: `workflow_dispatch` y push a `main` con rutas relevantes; Android además en PR
  y tags.
- Permisos: Desktop añade `contents: read` (solo artefactos); Android mantiene
  `contents: write` porque publica APKs al crear un tag (comportamiento existente, no
  ejecutado).
- Acciones en versiones actuales: `checkout@v4`, `setup-java@v4` (Temurin 17),
  `setup-gradle@v4`, `upload-artifact@v4`.
- El workflow Desktop recompila el helper SMTC desde fuente
  (`powershell -File native/build-smtc.ps1`), ejecuta tests, imagen, MSI, ZIP y sube
  `Cypher-Windows-portable` y `Cypher-Windows-installer`.
- **Todos los pasos se reprodujeron en local** con éxito: helper, `test`, `createDistributable`,
  `packageMsi`, `packagePortable`; Android: `testDebugUnitTest` y `assembleRelease`.
- **No se ejecutó en GitHub**: hace falta un push o un PR, y eso está fuera de la
  autorización actual.

## 12. Resultado de las compilaciones

| Elemento | Resultado |
| --- | --- |
| Tests Desktop | **128, 0 fallos, 0 errores** |
| Tests Android | `testDebugUnitTest` correcto |
| Desktop distributable | correcto |
| Portable ZIP | 107.727.566 bytes (13/11 08:28) |
| MSI | 108.813.182 bytes (13/11 08:29) |
| APK universal | 104.980.611 bytes, `com.cypher.music`, versionCode 21, 1.5.0 |
| Helper SMTC dentro del JAR | `native/cypher-smtc.exe`, 12.800 bytes |

El helper se recompiló dos veces con el mismo resultado; el binario empaquetado corresponde
al fuente.

## 13. Revisión de seguridad de los artefactos

Se extrajeron los 47 JAR del portable y se buscaron cadenas sensibles (texto y binario):

- Claves privadas (`BEGIN … PRIVATE KEY`): **ninguna**.
- Tokens (`eyJhbGciOi…`), `refresh_token`, `storePassword`, `CYPHER_KEYSTORE`: **ninguno**.
- Claves de API (`AIza…`): **ninguna**.
- Rutas personales (`C:\Users\bat`): **ninguna**.
- `library.json`, `desktop-client-secret.txt`, audio: **no incluidos**.
- Única aparición de `client_secret`: nombres de campo y mensajes en el código de
  `GoogleAuth`/Ajustes, no un valor.
- APK: sin `storePassword`, claves privadas ni claves de API embebidas.
- El portable solo contiene el runtime, la app y el helper; el MSI empaqueta la misma
  imagen.

## 14. Problemas pendientes

- Login Google/YouTube y pull real: bloqueados por el client secret y la audiencia.
- SMTC probado en un solo equipo Windows; falta Windows 10/N otras salidas de audio.
- La portada SMTC solo se publica con carátulas locales (las URL de YouTube no se bajan).
- MSI y helper sin firma de código.
- Cobertura de clics: siguen sin probarse en esta fase los diálogos nativos, el CRUD
  completo de playlists, swatches de acento y varios iconos de tarjeta.
- El arnés de clics necesita calibración manual por elemento; no es un fallo del producto.

## 15. Riesgos antes de la beta

- Sin login probado no se puede prometer YouTube Music ni sincronización.
- El APK no se ha instalado en un dispositivo; el keystore debe conservarse intacto.
- SmartScreen avisará del MSI y del ejecutable sin firmar.
- GitHub Actions no se ha ejecutado remotamente: la primera ejecución puede revelar
  diferencias del runner (WiX, rutas, caché).
- Antes de publicar conviene una pasada manual con ratón físico de los controles pequeños y
  de los diálogos nativos.

---

## Evidencia y comandos

Capturas nuevas: `screenshots/audit3-settings-ok.png`,
`audit3-max-settings2.png`, `audit3-queue-open.png`, `audit3-queue-close-try.png`,
`audit3-max-queue.png`, `audit3-max-queue-closed.png`, `audit3-max.png`.

```bash
# Validación de workflows
javac -cp snakeyaml-2.0.jar YamlCheck.java
java -cp ".;snakeyaml-2.0.jar" YamlCheck .github/workflows/desktop.yml .github/workflows/android.yml

# Recompilar helper y paquetes
powershell -File desktop/native/build-smtc.ps1
cd desktop && ./gradlew test createDistributable packagePortable packageMsi --no-daemon

# Android
./gradlew :app:testDebugUnitTest :app:assembleRelease --no-daemon
```

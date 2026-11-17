# Cypher Desktop · Informe de la cuarta fase (OAuth real y YouTube)

Fecha: 13 de noviembre de 2026.
Perfil usado: aislado (`%LOCALAPPDATA%\Temp\cypher-smtc-audit`, 2 pistas de prueba).
La biblioteca real no se abrió ni se modificó.

---

## 1. Login real de Google

**Conseguido.** Con el cliente Desktop, PKCE y loopback:

- Cuenta: `alejopp333pro@gmail.com`.
- Identidad con `sub` y email; `aud` verificado contra el client ID en el código (un
  `aud` distinto habría lanzado error).
- Sesión válida hasta las **11:53** del mismo día (el `exp` del token y `expires_in`).
- Mensaje de la app: «Sesión iniciada. Pulsa Sincronizar para traer y enviar cambios».
- **No se sincronizó nada**: el login no lanza pull ni push; el token vive solo en memoria.

Evidencia: `screenshots/audit4-wait2.png` (Cuenta con email y caducidad) y la pestaña de
callback `127.0.0.1:<puerto>/oauth2callback` que abrió el navegador.

## 2. Lectura del secreto sin mostrarlo

Se escribió el secreto únicamente en
`%LOCALAPPDATA%\Temp\cypher-smtc-audit\desktop-client-secret.txt` (perfil aislado), se
comprobó su presencia y tamaño (35 bytes) y **se borró al terminar**. No se mostró su valor
en la app, la terminal, las capturas ni este informe. El perfil real nunca lo contuvo.

## 3. YouTube Music

**Bloqueado por Google.** Al pedir `youtube.readonly` con la misma cuenta, Google respondió
en el navegador:

> Acceso bloqueado: Cypher no ha completado el proceso de verificación de Google…
> solo pueden acceder los testers aprobados… Error 403: access_denied

Es configuración de la pantalla de consentimiento, no un fallo del código:

1. La app está en estado **Prueba** y `alejopp333pro@gmail.com` no está en **Usuarios de
   prueba**, o
2. el scope `https://www.googleapis.com/auth/youtube.readonly` no está añadido en
   **Acceso a datos**, o
3. el proyecto exige verificación para ese scope sensible.

Solución (solo el propietario): Google Cloud → **Pantalla de consentimiento de OAuth** →
**Usuarios de prueba** → añadir esa cuenta; y en **Acceso a datos** añadir
`youtube.readonly`. Después, repetir «Iniciar sesión con Google» en Importar.

## 4. Playlists encontradas

**Ninguna: la consulta no llegó a ejecutarse.** No se llamó a YouTube Data API, no se
descargó audio y no se importó nada. No se presenta un cero como resultado de la API.

## 5. Backend

| Comprobación | Resultado |
| --- | --- |
| `GET {base}/health` | **200** `{"ok":true}` |
| `POST {base}/v1/sync/pull` sin token | **401** `{"error":"missing_token"}` |
| `POST …` con `Bearer a.b.c` | **401** `{"error":"invalid_token"}` |
| `GET …/pull` | **405** |
| `OPTIONS …/pull` | **204** |

`base = https://mdwjcphgidagmasehtsg.supabase.co/functions/v1/sync`.

**`GOOGLE_DESKTOP_CLIENT_ID`**: no se puede leer (los secretos de Supabase no son
consultables y no se ha tocado nada). El login fue contra Google, no contra el backend; la
primera sincronización real revelará si la audiencia Desktop está configurada
(`invalid_audience` si falta).

## 6. Pull

**No ejecutado.** `SyncService` aplica los registros remotos automáticamente (merge LWW)
antes de empujar el outbox; no existe un modo de solo lectura en la GUI. Ejecutarlo habría
modificado la biblioteca, así que se dejó pendiente de autorización y de un perfil aislado
con datos desechables.

## 7. Errores encontrados y corregidos

1. **`jdk.httpserver` ausente** en el runtime de jpackage: el listener loopback fallaba con
   `com.sun.net.httpserver/HttpServer`. Añadido `modules("jdk.httpserver")`.
2. **`java.net.http` ausente**: el intercambio fallaba con `java/net/http/HttpRequest`.
   Añadido al mismo `modules(...)`.
3. **Host equivocado en una comprobación previa** (`mdwjcphtjidgamashehg` en vez de
   `mdwjcphgidagmasehtsg`): corregido; era un error de la auditoría, no del producto.
4. **Secreto antiguo en disco**: `tools/oauth-audit/.client_secret` (ignorado por Git, nunca
   commiteado) eliminado.
5. **403 `access_denied`** en YouTube: pendiente de configuración en Google Cloud.

Los dos módulos se añadieron en `desktop/build.gradle.kts`; sin ellos, el login nunca
funciona en el portable ni en el MSI.

## 8. Estado del proyecto y datos

- Biblioteca real: **13 archivos, 90.421.208 bytes**, sin cambios.
- Sin commits, tags, push, releases ni despliegues.
- Sin cambios en Supabase, Google Cloud, tablas, políticas, RPC ni secretos de la nube.
- El secreto usado se borró del perfil aislado; el valor sigue expuesto en el chat y debe
  restablecerse en Google Cloud.
- Paquetes regenerados con el arreglo: portable **108.181.313 bytes**, MSI **109.273.261
  bytes**; tests **128, 0 fallos**. Runtime con `java.net.http` y `jdk.httpserver`.

## Pendiente

1. Restablecer el client secret (está expuesto) y guardarlo en el perfil real desde Cypher.
2. Añadir la cuenta como usuario de prueba y el scope `youtube.readonly`.
3. Repetir el login de YouTube y contar/describir playlists sin importar.
4. Autorizar una prueba de pull en perfil aislado (o implementar un modo de solo lectura).
5. Verificar `GOOGLE_DESKTOP_CLIENT_ID` con un token Desktop real.

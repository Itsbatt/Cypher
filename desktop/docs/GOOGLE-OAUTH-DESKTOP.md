# Google OAuth Desktop: qué falta y cómo completarlo

Estado comprobado el 13/11/2026. **No hay credenciales guardadas en este equipo**: ni
`%APPDATA%\Cypher Desktop\desktop-client-secret.txt` ni `CYPHER_DESKTOP_CLIENT_SECRET`.
Por eso no se pudo iniciar sesión ni probar YouTube Music o el pull real.

El flujo ya está implementado y no necesita cambios: Authorization Code + PKCE con
redirección loopback (`http://127.0.0.1:<puerto libre>/oauth2callback`), sin secreto
incrustado en el binario (`sync/GoogleAuth.kt`).

## Lo que solo puedes hacer tú

### 1. Copiar el client secret del cliente Desktop

1. Google Cloud Console → proyecto del cliente
   `764777457932-goan61rtqc8g3u6t7o7if2pdm6pkkc97`.
2. **APIs y servicios → Credenciales → Clientes OAuth 2.0**.
3. Abre el cliente de tipo **Aplicación de escritorio** y copia su *Client secret*.

No lo pegues en el chat ni en un archivo del repositorio.

> Si un secreto se ha pegado en un chat, un informe o un archivo compartido, considéralo
> comprometido: restablécelo en Google Cloud (Credenciales → cliente Desktop → restablecer
> secreto) antes de usarlo. Google puede revocarlo automáticamente al detectar exposición.

### 2. Guardarlo en Cypher (mecanismo local existente)

En Cypher Desktop → **Cuenta → CLIENTE OAUTH DESKTOP** → pega el secreto → **Guardar
secreto**.

- Se escribe en `%APPDATA%\Cypher Desktop\desktop-client-secret.txt`, fuera del proyecto,
  y no se muestra de nuevo ni se registra en logs.
- Alternativa para una sesión de pruebas: variable de entorno
  `CYPHER_DESKTOP_CLIENT_SECRET` (tiene prioridad y no se escribe en disco).
- El archivo debe seguir fuera de Git; ya está fuera por estar en AppData.

### 3. Habilitar YouTube Data API v3

Google Cloud Console → **APIs y servicios → Biblioteca** → *YouTube Data API v3* →
**Habilitar**. Sin esto, el consentimiento de `youtube.readonly` falla al listar playlists.

### 4. Revisar la pantalla de consentimiento

**APIs y servicios → Pantalla de consentimiento de OAuth**:

- Ámbitos: `openid`, `email`, `profile` y
  `https://www.googleapis.com/auth/youtube.readonly`.
- Si la app está en modo **Prueba**, añade tu cuenta como *usuario de prueba*.
- No hace falta registrar la URI de redirección: los clientes Desktop admiten loopback
  con cualquier puerto.

### 5. Audiencia del backend

Supabase Dashboard → **Edge Functions → Secrets**: debe existir
`GOOGLE_DESKTOP_CLIENT_ID` con el mismo client ID Desktop
(`764777457932-goan61rtqc8g3u6t7o7if2pdm6pkkc97.apps.googleusercontent.com`).

No se puede leer el valor desde aquí (los secretos no son consultables) y no se ha
modificado nada. Si falta, el backend responde `invalid_audience` al sincronizar; la app
ya muestra ese mensaje con la instrucción.

## Verificación hecha sin credenciales

- `GET /functions/v1/sync/health` → **200** `{"ok":true}` (función desplegada).
- `POST /v1/sync/pull` sin token → **401** `{"error":"missing_token"}`.
- `POST /v1/sync/pull` con token falso → **401** `{"error":"invalid_token"}`.
- La app muestra el campo de secreto y el botón **Guardar secreto** (captura
  `screenshots/audit3-settings2.png` / `audit3-max-settings2.png`).
- Pruebas automatizadas del intercambio (PKCE, `state`, errores de Google, secreto
  opcional, almacenamiento fuera del proyecto) siguen en verde.

## Qué se probará cuando esté configurado

1. **Conectar con Google** y consentimiento con `youtube.readonly`.
2. Carga de playlists y paginación (sin descargar audio).
3. Metadatos, portadas, orden y canciones no disponibles.
4. Vista previa y cancelación sin tocar la biblioteca.
5. Importación **solo con autorización expresa**.
6. Pull real de Supabase y comparación con Android.

Nada de esto se ha ejecutado todavía; no se presenta como verificado.

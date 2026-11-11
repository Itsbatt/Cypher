# Auditoría con un Google ID token real (cliente Desktop + PKCE)

Herramienta **local**, fuera de la app Android. Su único objetivo es ejecutar las tres
pruebas de la fase 3 que no se pudieron hacer sin un token válido: `200` con identidad
real, `403` con `accountId` ajeno y demostración de que ese `403` no escribe.

No forma parte de Cypher: no se compila en el APK, no se despliega y no toca Supabase.

## Qué garantiza

- **Authorization Code + PKCE S256** con `code_verifier` de 64 bytes en memoria.
- **`state` aleatorio** de 32 bytes, comparado en el callback con tiempo constante.
- **`nonce` aleatorio**, enviado en la autorización y exigido en el claim del ID token.
- **Loopback solo en `127.0.0.1`**, puerto efímero, un único callback y cierre inmediato.
- **Validación local estricta**: `aud` debe ser una sola y coincidir con el client ID,
  `azp` coherente cuando venga, `iss` de Google, `exp` vigente y `sub` exactamente igual
  al que ya usa Android (`100305844804415678846`). Si el `sub` no coincide, **el script
  aborta**: no hay ninguna asociación por correo electrónico.
- **El token, el `code` y el `code_verifier` no se escriben nunca** en disco, variables de
  entorno, argumentos de línea de comandos, logs ni en este repositorio.

## Ficheros de configuración (locales, ignorados por git)

| Fichero | Contenido | ¿Secreto? |
| --- | --- | --- |
| `tools/oauth-audit/.client_id` | Client ID del cliente Desktop | No (es público) |
| `tools/oauth-audit/.client_secret` | Solo si Google lo exige en el canje | Sí, nunca por chat |

Ambos están en `tools/oauth-audit/.gitignore`.

## Pasos en Google Cloud (Fase A)

1. Entra en **Google Cloud Console → APIs y servicios → Credenciales** del proyecto que
   ya contiene el cliente web (`764777457932-…`) y el de Android.
2. **Crear credenciales → ID de cliente de OAuth**.
3. Tipo de aplicación: **Aplicación de escritorio** (*Desktop app*).
4. Nombre: `Cypher PC – auditoría`.
5. **No hay que añadir redirect URIs**: el loopback `http://127.0.0.1:PUERTO` está
   permitido por defecto en este tipo de cliente (la deprecación del loopback afecta a
   clientes Android/iOS/Chrome, no a Desktop).
6. Guarda el **Client ID** en `tools/oauth-audit/.client_id` (una sola línea, sin comillas).
7. Si la pantalla de consentimiento sigue en modo *Testing*, añade tu cuenta como
   *test user*. Los scopes usados son `openid email profile` (no sensibles).
8. Si el canje falla con `invalid_request: client_secret is missing`, copia el
   **Client secret** a `tools/oauth-audit/.client_secret`. No lo pegues en el chat.

## Comprobaciones locales (sin red, sin token)

```bash
node tools/oauth-audit/audit.mjs --self-test
node tools/oauth-audit/audit.mjs --help
```

El self-test valida PKCE (longitud y derivación del challenge), la comparación de `state`
y `nonce`, y que el digest sea estable ante el orden y cambie cuando cambia un registro.

## Ejecución de la auditoría

```bash
node tools/oauth-audit/audit.mjs
```

1. Imprime la URL de Google (e intenta abrir el navegador del sistema).
2. Elige **la misma cuenta** que usas en Cypher. El script comprueba el `sub`.
3. Tras el callback, ejecuta contra `cypher.syncBaseUrl`:

| Prueba | Esperado |
| --- | --- |
| `GET /health` | `200 {"ok":true}` |
| `pull` con token | `200`, registros, cursor y páginas |
| `push` idempotente (mismo registro con `updatedAt=0`) | `200` con `accepted`, **sin modificar nada** (LWW lo descarta por antiguo) |
| `pull` con `accountId` ajeno | `403 account_mismatch` |
| `push` con `accountId` ajeno y `kind` inválido | `403`, sin `accepted` |
| Integridad antes/después | mismo recuento, mismo cursor, misma revisión máxima y **mismo hash** de registros |

Si el backend aún no acepta la audiencia del cliente Desktop, el `pull` devolverá
`401 invalid_audience` y el script lo dirá explícitamente: el token es válido y no se ha
escrito nada. Eso es la **Fase B** (`GOOGLE_ALLOWED_AUDIENCES`), que requiere tu
autorización tras revisar el diff y los tests.

## Qué NO hace

- No lee `EncryptedSharedPreferences` ni datos de la app Android.
- No pide ni guarda credenciales de Google.
- No modifica los 208 registros: el único `push` que envía repite un registro existente
  con `updatedAt=0`, que el LWW rechaza; el resto de peticiones son lecturas o `403`.
- No hace commits, ni despliegues, ni cambios de secretos.

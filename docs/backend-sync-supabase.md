# Backend de sincronización de Cypher (Supabase)

> **OBSOLETO.** La versión 1.5.0 retiró las cuentas y la nube (la biblioteca viaja en un ZIP
> local), así que la app actual no consume este servidor. El código se conserva **congelado** en
> [`experimental/supabase/`](../experimental/supabase/README.md) y este documento describe el
> contrato histórico, no el producto vigente. Se mantiene solo como referencia.

Implementación del servidor que consume la app 1.5.0. Respeta **exactamente** el
contrato de `app/src/main/kotlin/com/cypher/music/sync/SyncBackend.kt`; no requiere
ningún cambio en el cliente Android salvo apuntar `cypher.syncBaseUrl`.

## Rutas reales

La URL base que se configurará en Android es:

```
https://mdwjcphgidagmasehtsg.supabase.co/functions/v1/sync
```

y el cliente añade sus rutas habituales, de modo que las llamadas quedan:

| Cliente | URL efectiva |
| --- | --- |
| `POST {base}/v1/sync/pull` | `https://mdwjcphgidagmasehtsg.supabase.co/functions/v1/sync/v1/sync/pull` |
| `POST {base}/v1/sync/push` | `https://mdwjcphgidagmasehtsg.supabase.co/functions/v1/sync/v1/sync/push` |

El handler enruta con `endsWith("/v1/sync/pull")`, así que funciona tanto con el
prefijo de Supabase como detrás de un dominio propio. `GET …/health` existe solo para
comprobaciones manuales.

## Contrato

```
POST /v1/sync/pull
  Authorization: Bearer <Google ID token>
  { "accountId": "<sub>", "cursor": "42" | null, "limit": 100 }
  -> 200 { "records": [ { "kind", "id", "updatedAt", "deleted", "payload" } ],
           "cursor": "77", "hasMore": true }

POST /v1/sync/push
  Authorization: Bearer <Google ID token>
  { "accountId": "<sub>", "records": [ … ] }
  -> 200 { "accepted": ["PLAYLIST:p1", "STATS:2026-11-11|track"] }
```

- `payload` viaja siempre como `clave → texto`, que es lo que el cliente decodifica
  con `optString`.
- `kind ∈ {PROFILE, PLAYLIST, FAVORITE, PREFERENCE, STATS}`.
- Máximo 200 registros por lote y 16 KB por payload.
- `cursor` es la última `revision` entregada; el cliente lo trata como opaco.

## Autenticación

El backend acepta una **lista cerrada** de audiencias: `GOOGLE_WEB_CLIENT_ID` (cliente
web `764777457932-djot894fcm28vs15mhptks3p2cql56o6…`, el mismo que usa Credential
Manager como `serverClientId`) y, opcionalmente, `GOOGLE_DESKTOP_CLIENT_ID` (cliente
Desktop con Authorization Code + PKCE). `GOOGLE_ALLOWED_AUDIENCES` permite declarar la
lista completa separada por comas. Nunca hay comodines: una `aud` fuera de la lista se
rechaza. El backend valida:

- firma **RS256** contra el JWKS de Google (`https://www.googleapis.com/oauth2/v3/certs`,
  con caché de una hora y refresco ante un `kid` nuevo),
- `alg` estrictamente RS256 (rechaza `none` y HS*),
- `iss` en `accounts.google.com` / `https://accounts.google.com`,
- `aud` exactamente igual a una de las audiencias permitidas (un `aud` con varias
  entradas se rechaza aunque una de ellas esté permitida),
- `azp`, si está presente, igual a la `aud` del token **o al cliente Android**
  (`GOOGLE_ANDROID_CLIENT_ID`): Credential Manager emite `aud` = cliente web y
  `azp` = cliente Android. Cualquier otro valor, o un `azp` Android sin configurar,
  se rechaza con `invalid_azp`,
- `exp` vigente e `iat` no futuro (60 s de tolerancia),
- `sub` no vacío.

El identificador de cuenta **sale siempre del `sub` del token**. El `accountId` del
cuerpo solo se comprueba: si no coincide, la petición se rechaza con 403 y no toca la
base de datos.

Códigos: `401` token inválido/caducado, `403` cuenta confundida, `400` contrato roto,
`503` JWKS inaccesible, `502` fallo de base de datos (sin filtrar detalles internos).

## Base de datos

`experimental/supabase/migrations/20261111120000_sync_records.sql` (idéntico a
`experimental/supabase/dashboard/sync_records.sql`) crea:

- `public.sync_records` con PK `(user_id, kind, entity_id)`, `revision` monótona,
  checks de tipo/longitud, índice `(user_id, revision)` para el pull.
- RLS **habilitado sin políticas** y `revoke all` para `anon` y `authenticated`: la
  tabla solo es accesible por la service role y por las funciones `SECURITY DEFINER`.
- `sync_pull(p_user, p_cursor, p_limit)`: pagina con `revision > cursor`, devuelve
  `limit + 1` para calcular `hasMore` y el cursor de la última fila entregada.
- `sync_push(p_user, p_records)`: upsert con LWW
  (`updatedAt` mayor gana; en empate, un registro vivo gana a un tombstone; en empate
  total se conserva el valor guardado para que dos dispositivos no se reescriban en
  bucle). Cada escritura recibe una revisión nueva y la respuesta `accepted` incluye
  todas las claves procesadas, de modo que la cola del cliente se vacía.
- `revoke` de ambas funciones para `public`/`anon`/`authenticated` y `grant` solo a
  `service_role`.

No se guarda audio, caché, cookies, credenciales ni la cola de descargas: el cliente
solo envía los cinco tipos de registro de arriba.

## Despliegue (Dashboard)

### 1. Migración

1. Supabase Dashboard → **SQL Editor** → *New query*.
2. Pega el contenido completo de `experimental/supabase/dashboard/sync_records.sql`.
3. **Run**. Es idempotente: repetirlo no rompe nada.
4. Comprobación opcional:
   ```sql
   select count(*) as registros, count(distinct user_id) as cuentas
   from public.sync_records;
   ```

### 2. Secretos

Dashboard → **Edge Functions** → **Secrets** (o *Project Settings → Edge Functions →
Secrets*). Añade:

| Nombre | Valor | Origen |
| --- | --- | --- |
| `GOOGLE_WEB_CLIENT_ID` | `764777457932-djot894fcm28vs15mhptks3p2cql56o6.apps.googleusercontent.com` | Cliente **web** de Google Cloud (no el de Android) |
| `GOOGLE_DESKTOP_CLIENT_ID` | `764777457932-goan61rtqc8g3u6t7o7if2pdm6pkkc97.apps.googleusercontent.com` | Cliente **Desktop** (opcional; solo si se usa Cypher PC o la auditoría OAuth) |
| `GOOGLE_ANDROID_CLIENT_ID` | el cliente **Android** del proyecto (Console → Credenciales) | Opcional, pero necesario para que Android sincronice: solo se acepta como `azp`, nunca como `aud` |
| `GOOGLE_ALLOWED_AUDIENCES` | lista separada por comas | Opcional; sustituye a las dos anteriores cuando se declara sola |

`SUPABASE_URL` y `SUPABASE_SERVICE_ROLE_KEY` los inyecta Supabase automáticamente en
cada Edge Function: **no hay que crearlos ni copiarlos**, y nunca deben aparecer en
Android, en el repositorio ni en el chat. No hace falta la contraseña de la base de
datos ni el access token para que la función funcione.

### 3. Función

El Dashboard no permite subir varios archivos, así que la función se despliega con la
CLI (una sola vez, en tu máquina):

```bash
npx supabase login                    # abre el navegador; el token se queda en tu equipo
npx supabase link --project-ref mdwjcphgidagmasehtsg
npx supabase functions deploy sync    # usa experimental/supabase/config.toml (verify_jwt = false)
```

`verify_jwt = false` es imprescindible: la plataforma no debe validar el token de
Supabase porque el token es de Google y lo valida la función.

### 4. Comprobaciones

```bash
# 200 con {"ok":true}, sin token
curl -s https://mdwjcphgidagmasehtsg.supabase.co/functions/v1/sync/health

# 401: token ausente
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  https://mdwjcphgidagmasehtsg.supabase.co/functions/v1/sync/v1/sync/pull \
  -H 'content-type: application/json' -d '{}'

# 401: token manipulado
curl -s -X POST \
  https://mdwjcphgidagmasehtsg.supabase.co/functions/v1/sync/v1/sync/pull \
  -H 'authorization: Bearer a.b.c' -H 'content-type: application/json' -d '{}'
```

La prueba real de pull/push se hace desde la app (fase 3): login ya existente →
«Sincronizar ahora» → la cola de 208 registros debe bajar a 0.

## Tests locales

No necesitan red, ni Docker, ni Supabase: generan un par RSA efímero y simulan el
JWKS. Node 24 ejecuta TypeScript directamente.

```bash
node --test experimental/supabase/functions/sync/verify.test.ts \
            experimental/supabase/functions/sync/handler.test.ts \
            experimental/supabase/functions/sync/validation.test.ts
```

Cubren: firma válida/alterada, payload manipulado, `alg=none`, `aud`/`iss` erróneos,
caducidad, `iat` futuro, `sub` vacío, rotación de `kid`, JWKS caído (503), enrutado y
métodos, 401/403/400/502, aislamiento por `sub`, paginación por cursor, límites de
lote, normalización del payload, deduplicación y cursores inválidos.

Las claves de los tests se generan en memoria en cada ejecución: no hay ninguna clave
privada en el repositorio.

## Notas de seguridad

- El cliente Android nunca recibe la service role key ni habla con PostgREST.
- No se registran tokens ni payloads: los logs solo contienen el evento y la ruta.
- RLS sin políticas + `revoke` dejan la tabla cerrada a la API pública.
- El aislamiento depende del `sub` verificado; un `accountId` distinto se rechaza.
- Los ID tokens caducan en ~1 h; el cliente ya guarda la edad del token para renovarlo.

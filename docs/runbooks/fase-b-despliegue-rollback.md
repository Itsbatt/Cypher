# Runbook · Fase B (OAuth Desktop): despliegue y rollback

Estado en el momento de escribir este documento:

| Elemento | Valor |
| --- | --- |
| Edge Function `sync` | **ACTIVE v3**, `updated_at` `1794436335825` (2026-11-11T22:32:15.825Z) |
| Código desplegado | idéntico al local de la 1.5.0 (5 módulos, byte a byte) |
| Fase B local | 52/52 tests PASS, **sin desplegar** |
| Registros reales | **208** (`sub` `100305844804415678846`) |
| Secretos | `GOOGLE_WEB_CLIENT_ID` + los de plataforma. Ninguno nuevo |

Nada de este documento se ha ejecutado: es preparación.

---

## 0. Revisión técnica previa (resultado)

| Comprobación | Resultado | Evidencia |
| --- | --- | --- |
| `audiences.ts` es lista cerrada | OK | trim, dedupe, máx. 8, sufijo `.apps.googleusercontent.com` obligatorio |
| `verify.ts` valida `aud` | OK | `singleAudience()`; una sola entrada; pertenencia exacta a la lista |
| `verify.ts` valida `azp` | OK | si existe, debe ser texto e igual a `aud`; si falta, se acepta |
| `iss` | OK | solo `accounts.google.com` y `https://accounts.google.com` |
| `exp` / `iat` | OK | caducado ⇒ `token_expired`; futuro >60 s ⇒ `invalid_iat` |
| `sub` | OK | obligatorio y no vacío; **única** fuente de identidad |
| Firma | OK | RS256 contra JWKS de Google, `alg` distinto ⇒ `invalid_algorithm` |
| `accountId` vs `sub` | OK | distinto ⇒ 403 `account_mismatch`, sin tocar la BD |
| Desktop Client ID del código | **coincide exactamente** con `tools/oauth-audit/.client_id` | comparación de cadenas completas |
| `GOOGLE_WEB_CLIENT_ID` sin cambios | **verificado por digest** | SHA-256 del valor documentado = `1bfcbd7e324507e6f2ceb44ae93e5f28d36953ae595d5047b2474875724f057d`, idéntico al digest que muestra `secrets list` |
| `GOOGLE_ALLOWED_AUDIENCES` | sin conflicto, pero **aditivo** | la implementación **une** lista + web + desktop; nunca puede *quitar* una audiencia |
| Android usa la audiencia web | OK | `GoogleAuthGateway` lee `BuildConfig.GOOGLE_WEB_CLIENT_ID` ← `cypher.googleClientId` (cliente web) |
| Rollback v3 utilizable | OK | `supabase/rollback/v3/` tiene los 5 módulos, es autocontenido (no importa `audiences.ts`) y usa `audience: string` |

**Único matiz a tener en cuenta:** `GOOGLE_ALLOWED_AUDIENCES` no sustituye a las otras
dos variables a efectos de código: se suman. Si algún día hay que **reducir** la lista,
hay que vaciar el secreto que sobre, no basta con omitirlo en la lista.

---

## 1. Integridad previa (solo lectura)

Abre **SQL Editor** en el Dashboard y ejecuta el **BLOQUE A** de
`supabase/dashboard/phase4_integrity_readonly.sql`:

```sql
select
  count(*)                         as registros,
  max(revision)                    as revision_maxima,
  max(updated_at)                  as updated_maximo,
  count(*) filter (where deleted)  as tombstones
from public.sync_records
where user_id = '100305844804415678846';

select md5(string_agg(
         kind || ':' || entity_id || ':' || updated_at::text || ':' ||
         deleted::text || ':' || payload::text,
         E'\n' order by kind, entity_id
       )) as hash_registros
from public.sync_records
where user_id = '100305844804415678846';
```

Esperado: `registros = 208`. **Copia los cuatro valores** (registros, `revision_maxima`,
`updated_maximo`, `hash_registros`) en un fichero local o en tu libreta. Son la
referencia que debe repetirse al final.

> `revision_maxima` **es** el cursor máximo del pull: es la última revisión entregada.
> No hay una consulta distinta para el cursor.

**Parada:** si `registros ≠ 208`, detente y no continúes.

---

## 2. Rotar el client secret Desktop (recomendado, opcional)

Motivo: al diagnosticar el formato se mostraron 26 de sus 35 caracteres. Con PKCE y un
cliente público eso no permite falsificar tokens, pero el secreto debe considerarse
expuesto.

1. Google Cloud Console → **Credenciales** → tu cliente *Desktop* → **Añadir secreto**.
2. Descarga el JSON nuevo.
3. Guarda **solo** el `client_secret` en `tools/oauth-audit/.client_secret`
   (una línea, sin comillas). Está ignorado por git.
4. Repite el paso de auditoría (§7) con el secreto nuevo.
5. Cuando la auditoría pase, **borra el secreto antiguo** en la consola.

Nunca pegues el secreto en el chat, en un commit, en `local.properties` ni en
`supabase/`. Rotar el secreto **no invalida** los ID tokens ya emitidos: solo afecta al
canje del `authorization_code`.

---

## 3. Configurar `GOOGLE_DESKTOP_CLIENT_ID`

Sin tocar `GOOGLE_WEB_CLIENT_ID`:

```bash
cd "C:/Users/bat/.gemini/antigravity/scratch/meloflow"
npx --yes supabase@latest secrets set \
  GOOGLE_DESKTOP_CLIENT_ID=764777457932-goan61rtqc8g3u6t7o7if2pdm6pkkc97.apps.googleusercontent.com \
  --project-ref mdwjcphgidagmasehtsg --yes
```

El `--yes` es obligatorio en la CLI actual; sin él el comando no aplica el cambio.

---

## 4. Verificar los secretos sin revelar valores

`secrets list` muestra un **digest** por secreto. Compara con el SHA-256 esperado del
valor público:

```bash
printf '%s' '764777457932-djot894fcm28vs15mhptks3p2cql56o6.apps.googleusercontent.com' | sha256sum
# esperado: 1bfcbd7e324507e6f2ceb44ae93e5f28d36953ae595d5047b2474875724f057d

printf '%s' '764777457932-goan61rtqc8g3u6t7o7if2pdm6pkkc97.apps.googleusercontent.com' | sha256sum
# esperado: 37c7f6e1a9c2feff215fafd95c04efdbb5d339bbff98d052c473c51821d35cd8

npx --yes supabase@latest secrets list --project-ref mdwjcphgidagmasehtsg
```

Criterio de éxito: `GOOGLE_WEB_CLIENT_ID` conserva
`1bfcbd7e324507e6f2ceb44ae93e5f28d36953ae595d5047b2474875724f057d` y
`GOOGLE_DESKTOP_CLIENT_ID` aparece con
`37c7f6e1a9c2feff215fafd95c04efdbb5d339bbff98d052c473c51821d35cd8`. Si el digest web
cambia, **detente**: significa que se ha alterado la audiencia de Android.

> Si Supabase cambiara el algoritmo del digest, el comando sigue siendo útil como
> comparación relativa: anota el digest antes y después.

---

## 5. Desplegar

```bash
npx --yes supabase@latest functions deploy sync \
  --project-ref mdwjcphgidagmasehtsg
```

`supabase/config.toml` aporta `verify_jwt = false`, imprescindible: la función valida
los tokens de Google por su cuenta.

El despliegue sube `index.ts`, `handler.ts`, `verify.ts`, `validation.ts`, `rpc.ts` y el
nuevo `audiences.ts` (se resuelve por el `import` de `index.ts`).

---

## 6. Confirmar la versión desplegada

```bash
npx --yes supabase@latest functions list --project-ref mdwjcphgidagmasehtsg --output json \
  | grep -E '"(status|version|updated_at|verify_jwt)"'
```

Esperado: `"status": "ACTIVE"`, `"version": 4`, `"verify_jwt": false`, y un
`updated_at` posterior al de v3 (`1794436335825`).

**Parada:** si `version` no es 4 o `status` no es ACTIVE, no sigas con las pruebas.

---

## 7. Pruebas HTTP sin token (lectura, sin datos)

```bash
BASE=https://mdwjcphgidagmasehtsg.supabase.co/functions/v1/sync

curl -s -o /dev/null -w 'health %{http_code}\n' "$BASE/health"
curl -s -o /dev/null -w 'sin token %{http_code}\n' -X POST "$BASE/v1/sync/pull" \
  -H 'content-type: application/json' -d '{}'
curl -s -o /dev/null -w 'token falso %{http_code}\n' -X POST "$BASE/v1/sync/pull" \
  -H 'authorization: Bearer no.es.un.jwt' -H 'content-type: application/json' -d '{}'
curl -s -o /dev/null -w 'ruta mala %{http_code}\n' -X POST "$BASE/v1/sync/otra"
curl -s -o /dev/null -w 'GET %{http_code}\n' "$BASE/v1/sync/pull"
curl -s -o /dev/null -w 'OPTIONS %{http_code}\n' -X OPTIONS "$BASE/v1/sync/pull"
```

| Petición | Esperado | Cuerpo esperado |
| --- | --- | --- |
| `GET /health` | 200 | `{"ok":true}` |
| `POST /pull` sin `Authorization` | 401 | `{"error":"missing_token"}` |
| `POST /pull` con `Bearer no.es.un.jwt` | 401 | `{"error":"invalid_token"}` |
| `POST /sync/otra` | 404 | `{"error":"not_found"}` |
| `GET /pull` | 405 | `{"error":"method_not_allowed"}` |
| `OPTIONS /pull` | 204 | sin cuerpo, `access-control-allow-origin: *` |

Ninguna de estas peticiones llega a la base de datos ni requiere token válido.

---

## 8. Pull autenticado con el token Desktop (modo lectura)

```bash
node tools/oauth-audit/audit.mjs --read-only
```

`--read-only` recorre el flujo OAuth completo pero **no envía ningún push**: omite el
push idempotente y el push con `accountId` ajeno, y lo marca como «omitido» en la tabla.
Sí ejecuta el `pull`, el `pull` con `accountId` ajeno (403) y el digest antes/después.

Esperado:

| Prueba | Esperado | Real |
| --- | --- | --- |
| `state`, `nonce`, `aud` única, `aud` = client ID, `azp`, `iss`, `exp`, `sub` | PASS | PASS |
| `sub` = `100305844804415678846` | PASS | PASS |
| `GET /health` | 200 | 200 |
| `pull` con token Desktop | **200** | 200 con 208 registros |
| `push` idempotente | — | omitido (`--read-only`) |
| `pull` con `accountId` ajeno | 403 `account_mismatch` | 403 |
| `push` con `accountId` ajeno | — | omitido (`--read-only`) |
| Integridad (recuento, cursor, revisión máxima, hash) | idéntico | idéntico |

Si sale `401 invalid_audience`, el secreto no llegó a la función o el valor no es
exacto: revisa §4 y vuelve a desplegar (§5). **No** cambies el código para «arreglarlo».

---

## 9. Pull autenticado con Android

No se puede extraer el token del S22 sin sacarlo de `EncryptedSharedPreferences`, y eso
está prohibido en este proyecto. La comprobación es funcional:

1. Abre Cypher en el S22 (`RZCT106THDK`).
2. Comprueba que la sesión de Google sigue activa (cuenta `alejop333pro@gmail.com`).
3. Fuerza una sincronización desde la app.
4. Verifica: no hay error de autenticación, no se pierde ningún dato local y el
   contador de la biblioteca sigue siendo el mismo.

Criterio: si Android recibiera `401 invalid_audience`, la sincronización fallaría de
forma visible; que complete sin errores es la prueba de que la audiencia web sigue
aceptada.

---

## 10. Demostrar que no hubo escrituras

1. El modo `--read-only` no emite ningún `POST /v1/sync/push`. No depende solo del
   flujo: `callApi()` tiene una guarda que **lanza un error** si la ruta contiene
   `/push` mientras el flag está activo, así que ni una llamada futura ni un camino de
   error pueden escribir en modo lectura.
2. Vuelve a ejecutar el **BLOQUE B** del SQL (idéntico al A) y compara los cuatro
   valores con los del §1.
3. `hash_registros` debe ser **exactamente** el mismo. `revision_maxima` no debe subir.

Si el hash cambia tras un despliegue que solo tocó `verify.ts`/`index.ts`/`audiences.ts`,
algo ajeno al despliegue escribió en la tabla (por ejemplo la app sincronizando en ese
momento): repite la medición con la app cerrada antes de concluir nada.

---

## 11. Criterios de éxito

- [ ] `functions list`: ACTIVE, `version 4`, `verify_jwt false`
- [ ] health 200, sin token 401, token falso 401, ruta mala 404, GET 405, OPTIONS 204
- [ ] Desktop: `pull` 200 con `--read-only`
- [ ] Android: sincroniza sin errores
- [ ] Ambos tokens producen el mismo `sub` (`100305844804415678846`)
- [ ] `accountId` ajeno ⇒ 403 `account_mismatch`
- [ ] Cero pushes durante la verificación
- [ ] 208 registros, `revision_maxima` y hash idénticos al §1
- [ ] Ningún token, secreto ni `state`/`nonce` en logs o respuestas

Solo después, y con autorización aparte, se puede probar un push real (idempotente,
`updatedAt=0`) y repetir el hash.

---

## 12. Rollback a v3

### 12.1 Qué es reversible y qué no

| Acción | Reversible |
| --- | --- |
| Desplegar v4 | Sí: volver a desplegar v3 |
| Añadir `GOOGLE_DESKTOP_CLIENT_ID` | Sí: `secrets unset` (no hace falta para v3) |
| Rotar el client secret | **No**: el secreto antiguo no se puede recuperar, solo generar otro |
| Escrituras en datos | **No** en general; en este runbook no hay ninguna |
| Número de versión | **No**: el contador no baja (v3 → v4 → v5); v5 será el código de v3 |

### 12.2 Procedimiento

**Paso previo · verificar la copia de v3** (debe hacerlo alguien distinto de quien la
generó, o al menos en otro momento):

```bash
sha256sum supabase/rollback/v3/*.ts
```

Esperado, exactamente:

```
5f33b1937e7c1c625156648135faab9c9655c2cda3895ab8fee01670074b8949  handler.ts
f1e1c21e2abfd79372f461be9c399e1be140befbeda96e2697f197006676f6cf  index.ts
60b5d17dad041a36570dceb9b00c2dc3eaefc8b80ace6f0d2369b7b34954c0c7  rpc.ts
358461314e28df9376cecf13c5cbf70f95267ed27c2bfcc9a1e7f139e6c00ca6  validation.ts
cf164c4405758cac218240cecdc0218a00b54281b983926c0f17f3923f22467c  verify.ts
```

Y que la copia sea realmente la versión sin multi-audiencia:

```bash
grep -c "audiences" supabase/rollback/v3/index.ts   # esperado: 0
grep -n "audience: string" supabase/rollback/v3/verify.ts  # esperado: 1 línea
```

Estos cinco ficheros se descargaron de la función desplegada (v3) y se compararon byte
a byte con el código activo: son idénticos. Guarda además una copia de
`supabase/rollback/v3/` **fuera del repositorio** (por ejemplo en tu unidad de
backup): el directorio está ignorado por git y un `git clean -xdf` lo borraría.

1. **Guardar la Fase B antes de sobrescribirla:**

   ```bash
   mkdir -p supabase/rollback/fase-b
   cp supabase/functions/sync/*.ts supabase/rollback/fase-b/
   ```

2. **Restaurar v3** (los 5 ficheros; `audiences.ts` puede quedarse, v3 no lo importa):

   ```bash
   cp supabase/rollback/v3/*.ts supabase/functions/sync/
   ```

   Esto deja `audiences.ts` y `audiences.test.ts` en la carpeta. Son inocuos: el
   despliegue empaqueta lo que importa `index.ts`, y `index.ts` de v3 no los importa.
   Si prefieres limpieza total, muévelos a `supabase/rollback/fase-b/`.

3. **Desplegar:**

   ```bash
   npx --yes supabase@latest functions deploy sync --project-ref mdwjcphgidagmasehtsg
   ```

4. **Verificar:** `functions list` ⇒ ACTIVE, `version 5` (o la siguiente), y
   `updated_at` nuevo.

5. **Secretos:** deja `GOOGLE_DESKTOP_CLIENT_ID` puesto. v3 solo lee
   `GOOGLE_WEB_CLIENT_ID`, así que el secreto extra no tiene efecto. Retíralo solo si
   quieres dejar el proyecto como estaba: `npx supabase secrets unset GOOGLE_DESKTOP_CLIENT_ID --yes`
   (no afecta a v3).

6. **Smoke tests:** §7 completo. Con v3, un token Desktop vuelve a dar
   `401 invalid_audience`: es el comportamiento anterior correcto, no un fallo.

7. **Android:** repetir §9. Debe sincronizar igual que antes.

8. **Datos:** ejecutar el BLOQUE B y comparar con §1. Debe coincidir exactamente.

### 12.3 Rollback de emergencia sin copia local

Solo es válido **antes** de desplegar la Fase B: en ese momento lo desplegado sigue
siendo v3 y se puede descargar:

```bash
mkdir -p build-tmp/v3/supabase && cp supabase/config.toml build-tmp/v3/supabase/
cd build-tmp/v3 && npx --yes supabase@latest functions download sync --project-ref mdwjcphgidagmasehtsg --yes
cd ../.. && sha256sum build-tmp/v3/supabase/functions/sync/*.ts
```

Compara esos hashes con los del paso previo. `functions download` entrega una sola
versión: la **activa en ese instante**, no una histórica.

**Después de desplegar v4 este atajo ya no sirve**: descargaría v4, no v3. Supabase no
expone historial de versiones, así que la única garantía es la copia verificada de
`supabase/rollback/v3/` (y su duplicado fuera del repositorio). Si se perdió, la
recuperación pasa por reescribir los cinco módulos a mano desde este runbook y
`git`/backup del proyecto; no hay comando que devuelva v3 desde la plataforma.

---

## 13. Riesgos residuales

1. **Error de configuración = caída total.** Una errata en `GOOGLE_DESKTOP_CLIENT_ID` o
   en `GOOGLE_ALLOWED_AUDIENCES` hace que `index.ts` arranque en `server_misconfigured`
   y **todos** los clientes reciban 500. Mitigación: §4 antes de desplegar y
   verificación inmediata del `pull` Android.
2. **La lista es aditiva.** No se puede reducir quitando una variable de la lista.
3. **`azp` opcional.** Si Google no lo envía, no se exige. El token real de la auditoría
   no lo traía.
4. **`aud` múltiple rechazado** (cambio respecto a v3, que aceptaba una lista que
   contuviera la audiencia). Ningún cliente de Cypher lo emite.
5. **Rotación del secreto irreversible**: hay que repetir la auditoría con el nuevo.
6. **El rollback sube el número de versión** y deja `audiences.ts` en el árbol local.
7. **La prueba Android es funcional, no criptográfica**: no vemos su token, así que un
   401 se detecta por el fallo de sincronización, no por el código de error.

---

## 14. Autorizaciones que hacen falta

1. Ejecutar el SQL de integridad (§1) — solo lectura.
2. Crear/rotar el client secret en Google Cloud (§2) — acción tuya en la consola.
3. `secrets set GOOGLE_DESKTOP_CLIENT_ID` (§3) — cambia configuración de producción.
4. `functions deploy` (§5) — publica código en producción.
5. Ejecutar la auditoría OAuth contra el backend desplegado (§8).
6. Cualquier push real, aunque sea idempotente: **no** está cubierto por este runbook.

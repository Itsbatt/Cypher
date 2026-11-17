# Sincronización Android ↔ Desktop

## Contrato (el mismo que usa Android, sin cambios en el backend)

```
POST {base}/v1/sync/pull   {"accountId":"…","cursor":"opaco|null","limit":100}
  -> {"records":[record],"cursor":"opaco","hasMore":false}
POST {base}/v1/sync/push   {"accountId":"…","records":[record]}
  -> {"accepted":["KIND:id", …]}

record = {"kind":"PLAYLIST","id":"…","updatedAt":123,"deleted":false,"payload":{"k":"v"}}
Authorization: Bearer <Google ID token>
```

Base por defecto: `https://mdwjcphgidagmasehtsg.supabase.co/functions/v1/sync` (v7 desplegada,
sin tocar). Se puede apuntar a otro sitio con `CYPHER_SYNC_BASE_URL`.

### Payloads exactos

| Kind | id | Campos |
| --- | --- | --- |
| PROFILE | `profile` | `displayName`, `avatarColor` |
| PLAYLIST | id de la playlist | `name`, `description`, `createdAt`, `updatedAt`, `isPrivate`, `trackIds` (**array JSON en texto**, `["a","b"]`) |
| FAVORITE | id de la canción | `title`, `artist`, `album`, `durationMs`, `artworkUrl`, `streamUrl`, `sourceType`, `path`, `updatedAt`; al desmarcar, tombstone |
| STATS | `día\|trackId` | `day`, `trackId`, `title`, `artist`, `artworkUrl`, `album`, `genre`, `listenDurationMs`, `playCount`, `lastListenedAt` |
| PREFERENCE | `theme` | `json` (documento de tema de Android) |

El `trackIds` con comas que tenía la primera versión de Desktop era incompatible: Android
usa `RemoteRecordCodec.encodeTrackIds`, que serializa un array JSON. Corregido y cubierto
por `SyncCodecTest`.

## Reglas de fusión (idénticas a `SyncMerge.kt` de Android)

1. Gana el `updatedAt` mayor.
2. Empate: el registro vivo gana al tombstone.
3. Empate total: gana el id menor (convergencia determinista).
4. Playlist: primero el orden remoto y después las pistas que solo tiene este equipo.
5. Estadísticas: máximo por contador, nunca suma (evita inflar minutos al repetir sync).

## Garantías de seguridad implementadas

- **Nada se escribe hasta que el pull completo termina.** Un fallo a mitad de paginación no
  deja la biblioteca a medias.
- **Solo se envía el outbox.** Desktop nunca manda un snapshot completo; solo los registros
  que el usuario cambió aquí y que siguen siendo más nuevos que la copia remota
  (`SyncMerge.needsPush`).
- **Solo se vacía lo aceptado.** El outbox se limpia con la lista `accepted` del servidor;
  si el push falla tras el corte, las filas siguen pendientes.
- **Tombstones reales.** Solo se generan al eliminar una playlist o quitar un favorito.
- **Preferencias de solo lectura** salvo el acento, y solo si antes se recibió el documento
  del móvil; nunca se inventa un tema.
- **Cierre de sesión** limpia el token en memoria y no toca ningún dato local.
- **Errores diferenciados:** 401/403 → reconexión (y `token_expired` si el cuerpo lo dice);
  5xx o red → sin conexión, reintentable; 4xx de otro tipo → rechazo, sin reintento.
- El token **no se escribe en disco ni en logs**: vive en memoria de la sesión.

## Estados que ve el usuario

| Estado | Mensaje |
| --- | --- |
| Sin sesión | «Sin sesión: todo se guarda en este equipo.» |
| Conectando | «Esperando a Google…» |
| Sincronizando | «Sincronizando con tu cuenta…» |
| Sincronizado | «Sincronizado: N registros leídos, M aplicados, K enviados.» |
| Sin conexión | «Sin conexión con el servidor. Tus cambios siguen guardados…» |
| Token caducado | «Tu sesión de Google caducó: vuelve a conectar la cuenta.» |
| Error del servidor | «El servidor no aceptó la sincronización: …» |

## Qué está probado y qué no

**Probado (automatizado, sin red ni cuenta):**

- 9 pruebas del motor contra un servidor local que habla este contrato: paginación, LWW
  local más nuevo, LWW remoto más nuevo, unión de pistas, tombstones, limpieza solo de lo
  aceptado, 401 con `token_expired`, 503 con reintentos y servidor inalcanzable.
- 11 pruebas del códec: formato de `trackIds`, metadatos de favoritos, stats, tema.
- 3 pruebas del intercambio OAuth (PKCE, audiencia, caducidad) y 2 del ciclo loopback
  completo (state correcto y state falsificado).

**No probado todavía:**

- **Login real con Google**: requiere que aceptes el consentimiento en el navegador.
- **Pull/push contra el backend v7** con tu cuenta: no se ha ejecutado ninguna petición real,
  para no escribir en tus datos sin supervisión.
- **Intercambio Android ↔ Desktop real** (ida y vuelta con el móvil).

## Cómo completar la prueba real (5 minutos)

1. Abre Cypher Desktop (`Cypher.exe` o `./gradlew run`).
2. `Cuenta y nube` → **Conectar con Google**. Se abrirá el navegador en el cliente de
   escritorio `764777457932-goan61rtqc8g3u6t7o7if2pdm6pkkc97`.
3. Elige la misma cuenta que usa el móvil y acepta. La pestaña mostrará «Listo».
4. Vuelve a la app: verás el correo y la validez de la sesión. Pulsa **Sincronizar ahora**.
   El primer pull solo lee; no escribe nada.
5. Revisa el mensaje: cuántos registros leyó y cuántos aplicó. Después mira en el móvil que
   todo sigue igual.
6. Si quieres probar el envío, crea una playlist de prueba en Desktop (`Playlists → Crear`),
   pulsa **Sincronizar** y comprueba que aparece en el móvil.

Si Google responde que el cliente requiere secreto, define `CYPHER_DESKTOP_CLIENT_SECRET`
antes de abrir la app (nunca se guarda en el repositorio). El mensaje de error de la app
dice exactamente qué respondió Google.

## Diagnóstico de errores

| Síntoma | Causa probable | Qué hacer |
| --- | --- | --- |
| «No se pudo iniciar sesión: … 400» | redirect no registrado o cliente mal tipado | revisar el cliente de escritorio en Google Cloud |
| «El token es para otro cliente» | ID token de otro cliente OAuth | usar el cliente de Desktop |
| «Google rechazó la sesión» | token caducado o revocado | Reconectar con Google |
| «Sin conexión con el servidor» | red o 5xx | reintentar; el outbox se conserva |
| «El servidor no aceptó el envío: 403» | `azp`/audiencia no permitida | revisar los secretos WEB/DESKTOP del backend (no modificados aquí) |

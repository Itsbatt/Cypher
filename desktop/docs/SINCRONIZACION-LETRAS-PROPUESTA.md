# Letras sincronizadas — propuesta técnica (sin desplegar)

Estado actual: Desktop guarda letras en la biblioteca local (`LyricDoc`, atadas a un id
estable de canción) y sigue leyendo el sidecar `.lrc`. El protocolo de sync actual
**no puede** transportarlas, y este documento explica por qué y qué habría que cambiar.

## 1. Por qué no funcionan hoy

`experimental/supabase/functions/sync/validation.ts` solo acepta estos tipos:

```ts
export const SYNC_KINDS = ["PROFILE", "PLAYLIST", "FAVORITE", "PREFERENCE", "STATS"] as const;
```

Un `LYRICS` se rechaza con `invalid_kind` antes de llegar a la tabla. Además:

- el payload se limita a **16 KB** (`DEFAULT_MAX_PAYLOAD_BYTES`), así que una letra
  completa con timestamps puede superarlo;
- la tabla `sync_records` guarda un `payload` por registro y una revisión por escritura;
- el pull es por cursor de revisión, sin filtro por tipo.

Meter la letra dentro del registro `FAVORITE` o `PLAYLIST` no sirve: rompe el contrato
que Android 1.5.0 ya parsea (`SyncCodecTest` fija exactamente sus claves) y haría que una
letra modificada reescribiera la playlist entera.

## 2. Cambios de esquema (propuesta)

Nueva tabla, separada de la biblioteca para no inflar los registros existentes:

```sql
create table public.song_lyrics (
  user_id uuid not null references auth.users(id) on delete cascade,
  track_id text not null,              -- id estable de la canción, no una ruta
  lrc text not null,                   -- LRC original, timestamps intactos
  language text,
  source text not null default 'LOCAL' check (source in ('LOCAL','SYNCED','IMPORTED')),
  synced boolean not null default false,
  revision bigint not null default 1,  -- se incrementa en cada escritura aceptada
  updated_at timestamptz not null default now(),
  updated_by uuid,
  deleted boolean not null default false,
  primary key (user_id, track_id)
);

create index song_lyrics_revision_idx on public.song_lyrics (user_id, revision);
```

- `revision` funciona como cursor estable para el pull incremental.
- `deleted` conserva tombstones: borrar una letra no la resucita desde otro dispositivo.
- RLS: `user_id = auth.uid()` en `select/insert/update`; nada de acceso por email.

Límite: `lrc` de hasta 64 KB (una letra LRC normal no pasa de 20 KB). El payload de la
RPC no crece porque las letras no viajan dentro de `sync_records`.

## 3. Cambios de backend

1. `validation.ts`: nuevo tipo `LYRICS` con su validador (`trackId`, `lrc`, `language`,
   `source`, `updatedAt`, `deleted`), sin tocar los cinco tipos existentes.
2. RPC nueva `sync_lyrics_push(records jsonb)` y `sync_lyrics_pull(since_revision, limit)`
   con la misma forma de respuesta que `/v1/sync/pull` (`records`, `cursor`, `hasMore`),
   para que el cliente reutilice el bucle de paginación.
3. Regla LWW en SQL: se acepta la escritura si `updated_at` es mayor, o si es igual y el
   registro entrante es un tombstone. Nunca se sobrescribe con una revisión menor.
4. La Edge Function actual gana la ruta `/v1/sync/lyrics`, sin cambiar el contrato de
   `/v1/sync/pull` ni `/v1/sync/push`.
5. No se despliega nada hasta que el usuario lo autorice.

## 4. Cambios de Android (mínimos)

- `SyncModels.kt`: añadir `SYNC_LYRICS` y el DTO `LyricsRecord`.
- `LocalSyncSource.kt`: enviar las letras que ya tenga asociadas a la canción.
- `SyncEngine.kt`: un pull de letras por cursor propio, sin mezclarlo con el de la
  biblioteca; aplicar con LWW y conservar la letra local si la remota es más antigua.
- UI: mostrar la letra igual que hoy, con el indicador de sincronizada.

## 5. Cambios ya hechos en Desktop

- `LyricDoc` en `library.json`: `trackId`, `lrc`, `source`, `synced`, `language`,
  `updatedAt`.
- `Lyrics.parseText` / `Lyrics.fromText`: LRC con timestamps, sin reescribirlos.
- `AppState.saveLyrics`: reemplaza por `trackId`, nunca duplica, descarta textos vacíos.
- Panel derecho: resalta la línea actual y hace scroll con `positionMs`; si la letra no
  tiene tiempos se muestra sin resaltado; el badge distingue Guardada / Importada /
  Sincronizada.
- Importación de `.lrc` desde el propio panel.
- Falta únicamente el transporte: cuando exista la ruta `/v1/sync/lyrics`, `LyricDoc`
  se mapea 1:1 a `song_lyrics` y el cursor `/v1/sync/pull` no cambia.

## 6. Compatibilidad con datos antiguos

- Clientes viejos: ignoran `LYRICS` (no lo piden) y siguen funcionando igual.
- Filas existentes: `library.json` sin `lyrics` se lee como lista vacía; no hay migración.
- Letras con `trackId` de otra cuenta: RLS las oculta; no se mezclan bibliotecas.
- Sidecar `.lrc`: sigue siendo el fallback para archivos locales sin letra en la nube.
- Si una letra remota llega sin timestamps, se guarda y se muestra sin resaltado.

## 7. Riesgos

| Riesgo | Mitigación |
| --- | --- |
| Payload grande / abuso | límite 64 KB por letra y 200 registros por push |
| Letra con copyright | solo se sincroniza lo que el usuario ya tenía; no se descarga de terceros |
| Escritura antigua pisa una nueva | LWW por `updated_at` + revisión monótona |
| Duplicados | clave primaria `(user_id, track_id)` |
| Tombstone resucitado | `deleted` gana en empate y viaja en el pull |

## 8. Pruebas que acompañan al cambio

- `SyncFixtureTest`: LRC con timestamps, texto sin tiempos, reemplazo por id, sin
  duplicados, persistencia tras reiniciar.
- Añadir al aprobar el backend: push/pull de letras, LWW en ambos sentidos, tombstone,
  paginación de más de 200 letras y aislamiento entre dos cuentas.

Sin autorización para tocar Supabase, esto queda como propuesta; el Desktop ya está
preparado para consumirla.

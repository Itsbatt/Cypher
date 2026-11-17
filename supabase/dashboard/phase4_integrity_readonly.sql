-- ============================================================================
-- Cypher · integridad de sync_records · SOLO LECTURA
--
-- Ninguna sentencia de este fichero escribe, borra ni modifica nada.
-- Todas son `select` sobre public.sync_records.
--
-- DOS MÉTRICAS DISTINTAS, no intercambiables:
--   · revision     contador monótono de versiones; su máximo es el cursor que
--                  devuelve /v1/sync/pull. Sube con cada escritura aplicada y
--                  también consume números en los upserts que el guard LWW
--                  descarta, así que NO mide "cuántos registros hay".
--   · updated_at   marca de tiempo del último cambio real del registro (ms).
--                  Es la que decide conflictos (last-writer-wins), no la versión.
--
-- Referencias conocidas:
--   antes de la reconciliación  208 registros · revision 208 · updated_at 1796656145091 · 0 tombstones
--   después de la reconciliación (12 nov, sync del S22)
--                               208 registros · revision 486 · 0 tombstones
--                               hash distinto al previo (0403824a… ya no aplica)
--
-- Interpretación tras una reconciliación:
--   · registros y tombstones deben seguir iguales (nada se pierde ni se duplica);
--   · revision puede subir;
--   · updated_at solo sube si algún registro local era más nuevo (el guard LWW
--     `excluded.updated_at > t.updated_at` impide pisar datos remotos más nuevos);
--   · hash_registros cambia solo si cambió el contenido de algún registro.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- BLOQUE B · Estado actual (solo lectura)
-- Esperado: registros 208 · tombstones 0 · revision_maxima 486 (o mayor)
--           updated_maximo >= 1796656145091
-- ---------------------------------------------------------------------------
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
-- Esperado: un hash estable entre ejecuciones seguidas. Distinto de 0403824a…
-- (la reconciliación actualizó registros). Anota el valor nuevo como referencia.

-- ---------------------------------------------------------------------------
-- BLOQUE C · Detalle por registro (solo lectura)
-- Sirve para comprobar un registro concreto y para comparar con el cliente.
-- ---------------------------------------------------------------------------
select kind, entity_id, updated_at, deleted, revision, md5(payload::text) as payload_md5
from public.sync_records
where user_id = '100305844804415678846'
order by kind, entity_id;

-- Recuento por tipo: debe sumar 208 y no aparecer ningún kind desconocido.
select kind, count(*) as registros, max(revision) as revision_maxima
from public.sync_records
where user_id = '100305844804415678846'
group by kind
order by kind;

-- Cuántos registros cambiaron de verdad en la reconciliación:
-- los que tienen updated_at posterior a la referencia anterior.
select count(*) as actualizados_tras_referencia
from public.sync_records
where user_id = '100305844804415678846'
  and updated_at > 1796656145091;

-- Duplicados: no debe devolver ninguna fila (la clave es única por usuario).
select kind, entity_id, count(*) as veces
from public.sync_records
where user_id = '100305844804415678846'
group by kind, entity_id
having count(*) > 1;

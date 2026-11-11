-- ============================================================================
-- Cypher · fase 4 (OAuth Desktop) · integridad de solo lectura
--
-- SOLO LECTURAS. Ninguna sentencia escribe, borra ni modifica nada.
--
-- Uso: ejecutar el bloque A ANTES del despliegue y el bloque B DESPUÉS, con la
-- cuenta real. Los resultados deben coincidir exactamente en:
--   · registros        (referencia actual: 208)
--   · revision_maxima  (= cursor máximo del pull)
--   · hash_registros   (md5 determinista sobre los registros ordenados)
--
-- Si cualquiera de los tres cambia, el despliegue no es inocuo y hay que parar.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- BLOQUE A · Antes del despliegue
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

-- ---------------------------------------------------------------------------
-- BLOQUE B · Después del despliegue (mismas consultas)
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

-- ---------------------------------------------------------------------------
-- BLOQUE C · Verificación de un registro individual (solo lectura)
-- Sustituye las dos primeras líneas por el registro que quieras comprobar.
-- ---------------------------------------------------------------------------
select kind, entity_id, updated_at, deleted, revision, md5(payload::text) as payload_md5
from public.sync_records
where user_id = '100305844804415678846'
order by kind, entity_id
limit 5;

-- Recuento por tipo, para comparar de un vistazo con el informe previo.
select kind, count(*) as registros, max(revision) as revision_maxima
from public.sync_records
where user_id = '100305844804415678846'
group by kind
order by kind;

-- ============================================================================
-- OBSOLETO: usar `phase3_verification_safe.sql`.
-- Este fichero mezcla un DELETE en un CTE con lecturas, lo que hace ambiguo el
-- orden de ejecución. Se conserva solo como referencia.
-- ============================================================================
-- Cypher · verificación de la fase 3 (pegar en Supabase → SQL Editor)
--
-- Solo LEE datos reales; los registros de prueba usan user_id sintéticos
-- ('test-fase3-a', 'test-fase3-b') y se borran al final. No toca nada más.
-- ============================================================================

-- 0. Limpieza de restos de ejecuciones anteriores
delete from public.sync_records where user_id like 'test-fase3-%';

-- 1. Qué hay realmente en la nube, por cuenta y por tipo
select user_id,
       count(*)          as registros,
       max(revision)     as ultima_revision,
       max(updated_at)   as ultimo_updated_at
from public.sync_records
group by user_id
order by registros desc;

select kind, count(*) as registros
from public.sync_records
where user_id = (
  select user_id from public.sync_records group by user_id order by count(*) desc limit 1
)
group by kind
order by kind;

-- 2. Push, paginación, LWW e aislamiento con datos sintéticos
with push_a as (
  select public.sync_push('test-fase3-a', '[
    {"kind":"PLAYLIST","id":"p1","updatedAt":1000,"deleted":false,"payload":{"name":"A"}},
    {"kind":"PLAYLIST","id":"p2","updatedAt":1001,"deleted":false,"payload":{"name":"B"}},
    {"kind":"FAVORITE","id":"t1","updatedAt":1002,"deleted":false,"payload":{"title":"C"}},
    {"kind":"STATS","id":"2026-11-11|t1","updatedAt":1003,"deleted":false,"payload":{"playCount":"1"}}
  ]'::jsonb) as res
),
page1 as (select public.sync_pull('test-fase3-a', 0, 2) as p from push_a),
page2 as (
  select public.sync_pull('test-fase3-a', ((select p->>'cursor' from page1))::bigint, 2) as p
  from page1
),
lww_old as (
  select public.sync_push('test-fase3-a',
    '[{"kind":"PLAYLIST","id":"p1","updatedAt":900,"deleted":false,"payload":{"name":"viejo"}}]'::jsonb) as res
  from page2
),
lww_new as (
  select public.sync_push('test-fase3-a',
    '[{"kind":"PLAYLIST","id":"p1","updatedAt":1100,"deleted":false,"payload":{"name":"nuevo"}}]'::jsonb) as res
  from lww_old
),
after_lww as (select public.sync_pull('test-fase3-a', 0, 50) as p from lww_new),
push_b as (
  select public.sync_push('test-fase3-b',
    '[{"kind":"PLAYLIST","id":"pb","updatedAt":1000,"deleted":false,"payload":{"name":"B"}}]'::jsonb) as res
  from after_lww
),
pull_b as (select public.sync_pull('test-fase3-b', 0, 10) as p from push_b),
cleanup as (delete from public.sync_records where user_id like 'test-fase3-%' returning 1)
select
  (select jsonb_array_length(p->'records') from page1)                             as pagina1_registros,
  (select p->>'hasMore' from page1)                                                as pagina1_has_more,
  (select p->>'cursor' from page1)                                                 as pagina1_cursor,
  (select jsonb_array_length(p->'records') from page2)                             as pagina2_registros,
  (select p->>'hasMore' from page2)                                                as pagina2_has_more,
  (select payload->>'name'
     from jsonb_array_elements((select p->'records' from after_lww)) e
    where e->>'id' = 'p1')                                                         as lww_ganador,
  (select count(*)
     from jsonb_array_elements((select p->'records' from pull_b)))                 as otra_cuenta_registros;

-- Resultado esperado:
--   pagina1_registros = 2, pagina1_has_more = true
--   pagina2_registros = 2, pagina2_has_more = false
--   lww_ganador = "nuevo"   (el updatedAt 1100 gana al 900)
--   otra_cuenta_registros = 1 (solo su propio registro: aislamiento correcto)

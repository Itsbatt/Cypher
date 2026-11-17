-- ============================================================================
-- Cypher · fase 3 · verificación SEGURA
--
-- Reglas de este fichero:
--   · Las lecturas de datos reales son SELECT puros.
--   · Los DELETE solo afectan a los ids sintéticos 'test-fase3-a' / 'test-fase3-b',
--     que no pueden coincidir con un sub de Google (los subs son numéricos).
--   · Ningún DELETE va mezclado con lecturas en la misma sentencia.
--   · Cada bloque es independiente y se ejecuta en orden.
--
-- Ejecuta los bloques de uno en uno. NO ejecutes el BLOQUE 8 hasta revisar todo.
-- Sustituye el sub real solo si cambia de cuenta.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- BLOQUE 0 · Inventario real (solo lectura)
-- ---------------------------------------------------------------------------
select count(*) as registros_reales
from public.sync_records
where user_id = '100305844804415678846';

select kind, count(*) as registros
from public.sync_records
where user_id = '100305844804415678846'
group by kind
order by kind;

select user_id, count(*) as registros, max(revision) as ultima_revision
from public.sync_records
group by user_id
order by registros desc;

-- ---------------------------------------------------------------------------
-- BLOQUE 1 · Limpieza de restos sintéticos (no toca datos reales)
-- ---------------------------------------------------------------------------
delete from public.sync_records
where user_id in ('test-fase3-a', 'test-fase3-b');

select count(*) as restos_sinteticos
from public.sync_records
where user_id in ('test-fase3-a', 'test-fase3-b');
-- Esperado: 0

-- ---------------------------------------------------------------------------
-- BLOQUE 2 · Push de 4 registros de prueba para el usuario sintético A
-- ---------------------------------------------------------------------------
select public.sync_push('test-fase3-a', $$[
  {"kind":"PLAYLIST","id":"p1","updatedAt":1000,"deleted":false,"payload":{"name":"A"}},
  {"kind":"PLAYLIST","id":"p2","updatedAt":1001,"deleted":false,"payload":{"name":"B"}},
  {"kind":"FAVORITE","id":"t1","updatedAt":1002,"deleted":false,"payload":{"title":"C"}},
  {"kind":"STATS","id":"2026-11-11|t1","updatedAt":1003,"deleted":false,"payload":{"playCount":"1"}}
]$$::jsonb) as push_a;
-- Esperado: accepted con PLAYLIST:p1, PLAYLIST:p2, FAVORITE:t1, STATS:2026-11-11|t1

-- ---------------------------------------------------------------------------
-- BLOQUE 3 · Paginación · página 1 (límite 2)
-- ---------------------------------------------------------------------------
select public.sync_pull('test-fase3-a', 0, 2) as pagina_1;
-- Esperado: 2 registros, "hasMore": true y un cursor (normalmente "3")

-- ---------------------------------------------------------------------------
-- BLOQUE 4 · Paginación · página 2, sin copiar el cursor a mano
-- ---------------------------------------------------------------------------
select public.sync_pull(
  'test-fase3-a',
  ((public.sync_pull('test-fase3-a', 0, 2))->>'cursor')::bigint,
  2
) as pagina_2;
-- Esperado: los 2 registros restantes, "hasMore": false

-- ---------------------------------------------------------------------------
-- BLOQUE 5 · Resumen de paginación en una fila (solo lecturas)
-- ---------------------------------------------------------------------------
with p1 as (select public.sync_pull('test-fase3-a', 0, 2) as p),
     p2 as (select public.sync_pull('test-fase3-a', ((select p->>'cursor' from p1))::bigint, 2) as p),
     p3 as (select public.sync_pull('test-fase3-a', ((select p->>'cursor' from p2))::bigint, 2) as p)
select
  (select jsonb_array_length(p->'records') from p1) as pagina_1_registros,
  (select p->>'hasMore' from p1)                    as pagina_1_has_more,
  (select p->>'cursor' from p1)                     as pagina_1_cursor,
  (select jsonb_array_length(p->'records') from p2) as pagina_2_registros,
  (select p->>'hasMore' from p2)                    as pagina_2_has_more,
  (select jsonb_array_length(p->'records') from p3) as pagina_3_registros,
  (select p->>'hasMore' from p3)                    as pagina_3_has_more;
-- Esperado: 2 / true / cursor ; 2 / false ; 0 / false

-- ---------------------------------------------------------------------------
-- BLOQUE 6 · Last writer wins
-- ---------------------------------------------------------------------------
-- 6a · Un push más antiguo (900 < 1000) no debe pisar el nombre "A"
select public.sync_push('test-fase3-a', $$[
  {"kind":"PLAYLIST","id":"p1","updatedAt":900,"deleted":false,"payload":{"name":"viejo"}}
]$$::jsonb) as push_antiguo;

select (public.sync_pull('test-fase3-a', 0, 100))->'records' as tras_push_antiguo;
-- Esperado: p1 con "name": "A"

-- 6b · Un push más nuevo (1100 > 1000) sí debe ganar
select public.sync_push('test-fase3-a', $$[
  {"kind":"PLAYLIST","id":"p1","updatedAt":1100,"deleted":false,"payload":{"name":"nuevo"}}
]$$::jsonb) as push_nuevo;

select (public.sync_pull('test-fase3-a', 0, 100))->'records' as tras_push_nuevo;
-- Esperado: p1 con "name": "nuevo"

-- 6c · Tombstone: borrar en la nube con updatedAt mayor
select public.sync_push('test-fase3-a', $$[
  {"kind":"PLAYLIST","id":"p2","updatedAt":1200,"deleted":true,"payload":{}}
]$$::jsonb) as push_borrado;

select (public.sync_pull('test-fase3-a', 0, 100))->'records' as tras_borrado;
-- Esperado: p2 con "deleted": true

-- ---------------------------------------------------------------------------
-- BLOQUE 7 · Aislamiento entre cuentas
-- ---------------------------------------------------------------------------
select public.sync_push('test-fase3-b', $$[
  {"kind":"PLAYLIST","id":"pb","updatedAt":1500,"deleted":false,"payload":{"name":"solo-B"}}
]$$::jsonb) as push_b;

select public.sync_pull('test-fase3-b', 0, 10) as pull_b;
-- Esperado: 1 registro, id "pb"

select count(*) as filas_a
from public.sync_records where user_id = 'test-fase3-a';
select count(*) as filas_b
from public.sync_records where user_id = 'test-fase3-b';
-- Esperado: filas_a = 4, filas_b = 1

select count(*) as registros_de_b_visibles_para_a
from jsonb_array_elements((public.sync_pull('test-fase3-a', 0, 100))->'records') as e
where e->>'id' = 'pb';
-- Esperado: 0

-- Los datos reales siguen intactos
select count(*) as registros_reales_siguen
from public.sync_records
where user_id = '100305844804415678846';
-- Esperado: el mismo número del BLOQUE 0

-- ---------------------------------------------------------------------------
-- BLOQUE 8 · Limpieza final (ejecutar SOLO después de revisar los resultados)
-- ---------------------------------------------------------------------------
delete from public.sync_records
where user_id in ('test-fase3-a', 'test-fase3-b');

select count(*) as restos_sinteticos_finales
from public.sync_records
where user_id in ('test-fase3-a', 'test-fase3-b');
-- Esperado: 0

select count(*) as registros_reales_finales
from public.sync_records
where user_id = '100305844804415678846';
-- Esperado: idéntico al BLOQUE 0

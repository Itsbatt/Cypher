-- ============================================================================
-- Cypher 1.5.0 · SQL listo para Supabase Dashboard → SQL Editor
--
-- Copia este fichero completo y pégalo en el SQL Editor del proyecto
-- mdwjcphgidagmasehtsg. Es idempotente: si lo ejecutas dos veces no rompe nada.
--
-- Es exactamente el mismo contenido que
-- `supabase/migrations/20261111120000_sync_records.sql`.
-- ============================================================================

create sequence if not exists public.sync_records_revision_seq;

create table if not exists public.sync_records (
  user_id    text        not null,
  kind       text        not null,
  entity_id  text        not null,
  updated_at bigint      not null,
  deleted    boolean     not null default false,
  payload    jsonb       not null default '{}'::jsonb,
  revision   bigint      not null default nextval('public.sync_records_revision_seq'),
  constraint sync_records_pkey primary key (user_id, kind, entity_id),
  constraint sync_records_kind_check
    check (kind in ('PROFILE', 'PLAYLIST', 'FAVORITE', 'PREFERENCE', 'STATS')),
  constraint sync_records_user_check check (char_length(user_id) between 1 and 128),
  constraint sync_records_id_check   check (char_length(entity_id) between 1 and 256)
);

comment on table public.sync_records is
  'Cypher: última versión de cada entidad sincronizada, aislada por cuenta de Google (claim sub).';

create index if not exists sync_records_pull_idx
  on public.sync_records (user_id, revision);

alter table public.sync_records enable row level security;
revoke all on public.sync_records from anon, authenticated;
revoke all on sequence public.sync_records_revision_seq from anon, authenticated;

create or replace function public.sync_pull(
  p_user   text,
  p_cursor bigint default 0,
  p_limit  int    default 100
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user    text    := trim(coalesce(p_user, ''));
  v_cursor  bigint  := greatest(coalesce(p_cursor, 0), 0);
  v_limit   int     := greatest(1, least(coalesce(p_limit, 100), 200));
  v_records jsonb   := '[]'::jsonb;
  v_cursor_out bigint := v_cursor;
  v_has_more boolean := false;
begin
  if char_length(v_user) = 0 then
    raise exception 'sync_pull: user required' using errcode = '22023';
  end if;

  with page as (
    select kind, entity_id, updated_at, deleted, payload, revision
    from public.sync_records
    where user_id = v_user
      and revision > v_cursor
    order by revision asc
    limit v_limit + 1
  ),
  trimmed as (
    select * from page order by revision asc limit v_limit
  )
  select
    coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'kind', kind,
          'id', entity_id,
          'updatedAt', updated_at,
          'deleted', deleted,
          'payload', payload
        ) order by revision
      )
      from trimmed
    ), '[]'::jsonb),
    (select count(*) from page) > v_limit,
    coalesce((select max(revision) from trimmed), v_cursor)
  into v_records, v_has_more, v_cursor_out;

  return jsonb_build_object(
    'records', v_records,
    'cursor', v_cursor_out::text,
    'hasMore', v_has_more
  );
end;
$$;

create or replace function public.sync_push(
  p_user    text,
  p_records jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user     text  := trim(coalesce(p_user, ''));
  v_accepted jsonb := '[]'::jsonb;
begin
  if char_length(v_user) = 0 then
    raise exception 'sync_push: user required' using errcode = '22023';
  end if;
  if p_records is null or jsonb_typeof(p_records) <> 'array' then
    raise exception 'sync_push: records must be an array' using errcode = '22023';
  end if;

  with incoming as (
    select
      (rec->>'kind')                                        as kind,
      (rec->>'id')                                          as entity_id,
      greatest(coalesce((rec->>'updatedAt')::bigint, 0), 0) as updated_at,
      coalesce((rec->>'deleted')::boolean, false)           as deleted,
      case
        when jsonb_typeof(coalesce(rec->'payload', '{}'::jsonb)) = 'object'
          then coalesce(rec->'payload', '{}'::jsonb)
        else '{}'::jsonb
      end                                                   as payload
    from jsonb_array_elements(p_records) as rec
    where coalesce(rec->>'id', '') <> ''
  ),
  deduped as (
    select distinct on (kind, entity_id)
      kind, entity_id, updated_at, deleted, payload
    from incoming
    order by kind, entity_id, updated_at desc
  ),
  upserted as (
    insert into public.sync_records as t
      (user_id, kind, entity_id, updated_at, deleted, payload, revision)
    select
      v_user, kind, entity_id, updated_at, deleted, payload,
      nextval('public.sync_records_revision_seq')
    from deduped
    on conflict (user_id, kind, entity_id) do update
      set updated_at = excluded.updated_at,
          deleted    = excluded.deleted,
          payload    = excluded.payload,
          revision   = nextval('public.sync_records_revision_seq')
      where excluded.updated_at > t.updated_at
         or (excluded.updated_at = t.updated_at and t.deleted and not excluded.deleted)
    returning 1
  )
  select coalesce(jsonb_agg(d.kind || ':' || d.entity_id), '[]'::jsonb)
  into v_accepted
  from deduped d
  left join lateral (select 1 from upserted limit 1) u on true;

  return jsonb_build_object('accepted', v_accepted);
end;
$$;

revoke all on function public.sync_pull(text, bigint, int) from public, anon, authenticated;
revoke all on function public.sync_push(text, jsonb)        from public, anon, authenticated;
grant execute on function public.sync_pull(text, bigint, int) to service_role;
grant execute on function public.sync_push(text, jsonb)        to service_role;

# `experimental/supabase` — backend archivado (congelado)

Este directorio **no forma parte de ningún build**. No lo compilan Gradle, CI, ni ninguna
pantalla de la app. Se conserva aquí para no perder el trabajo y para tener un punto de partida
si la sincronización en la nube vuelve algún día.

## Por qué está aquí

Cypher 1.5.0 tomó una decisión de producto explícita: **sin cuentas y sin nube, la biblioteca
viaja en un ZIP local**. Consecuencias:

- Se eliminaron las tablas de contabilidad de Android (`sync_outbox`, `sync_state`) mediante la
  migración Room 8→9.
- Se eliminaron el cliente Android del backend (`SyncEngine`, `SyncBackend`, `SyncQueue`,
  `SyncMerge`, `SessionStore`, `AccountRepository`…) y la sección de cuenta en Compose.
- El backend de Supabase dejó de tener consumidor. Ya no hay ninguna referencia en el código:
  ni `SYNC_BASE_URL`, ni llamadas HTTP al endpoint, ni dependencias en Gradle.

Antes de esta reubicación vivía en `supabase/`, en la raíz del repositorio, donde aparentaba ser
parte activa del producto. Eso confundía a cualquiera que leyera el árbol.

## Qué contiene

| Ruta | Contenido |
|---|---|
| `migrations/` | Migración SQL que crea las tablas de sincronización y sus RPC. |
| `functions/sync/` | Edge Function en Deno: validación, audiencias, RPC, handler y sus tests (`node --test`). |
| `dashboard/` | Consultas de verificación e integridad para el panel de Supabase. |
| `rollback/` | Copia del código desplegado que se usaba para volver atrás. Ignorada por git. |
| `config.toml` | Configuración de la CLI (`verify_jwt = false`: la función valida el ID token por su cuenta). |

## Cómo revivirlo

1. Decide el modelo de producto: cuentas opcionales o sincronización obligatoria. La app actual
   no tiene pantalla de cuenta, así que primero hay que recuperar el cliente Android (está en el
   historial de git, no en este directorio).
2. Crea un proyecto nuevo de Supabase y aplica `migrations/` y `dashboard/`.
3. Despliega `functions/sync/` con la CLI y fija los secretos del proyecto.
4. Sustituye cualquier referencia a un proyecto antiguo: este código no contiene URLs fijas,
   pero la documentación histórica sí.
5. Ejecuta los tests de la función (`node --test experimental/supabase/functions/sync/*.test.ts`)
   y añade una prueba de extremo a extremo antes de cablearlo a la app.

## Documentación relacionada

- [`docs/backend-sync-supabase.md`](../../docs/backend-sync-supabase.md) — contrato del servidor
  y pasos de despliegue. **Obsoleto** para la app actual.
- [`docs/runbooks/fase-b-despliegue-rollback.md`](../../docs/runbooks/fase-b-despliegue-rollback.md)
  — runbook de despliegue y rollback. **Obsoleto** para la app actual.
- [`docs/fase-a/PLAN-CYPHER-UNIFICADO.md`](../../docs/fase-a/PLAN-CYPHER-UNIFICADO.md) — decisión
  de producto que retiró la nube.

## Hoja de ruta (si vuelve)

Sin fechas comprometidas. Requisitos mínimos antes de reintroducir la nube:

- Que sea **opcional**: la app debe seguir siendo plenamente funcional sin cuenta.
- Sincronización por registros con resolución de conflictos documentada, no un volcado completo.
- Exportación e importación por ZIP como camino de salida garantizado (sin dependencia del servidor).
- Datos mínimos: biblioteca y playlists; nunca archivos de audio ni letras con derechos.
- Pruebas de aislamiento entre dos cuentas y de recuperación tras reconexión.

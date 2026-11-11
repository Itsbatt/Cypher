/**
 * Cypher sync backend · Supabase Edge Function entry point.
 *
 * Secrets (Dashboard → Edge Functions → Secrets):
 *   GOOGLE_WEB_CLIENT_ID       aud del cliente web (obligatorio)
 *   GOOGLE_DESKTOP_CLIENT_ID   aud del cliente Desktop (opcional)
 *   GOOGLE_ANDROID_CLIENT_ID   azp del cliente Android (opcional, recomendado)
 *   GOOGLE_ALLOWED_AUDIENCES   lista de audiencias separada por comas (opcional)
 *   SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY  los inyecta Supabase automáticamente
 *
 * Las audiencias forman una lista cerrada: un token con cualquier otra `aud` se
 * rechaza con 401 invalid_audience.
 *
 * No hay contraseña de base de datos: todo el SQL vive en funciones SECURITY
 * DEFINER invocadas por RPC con la service role key, que solo existe en el servidor.
 */

import { resolveAuth } from "./audiences.ts";
import { createHandler, type HandlerDeps, type PullPage } from "./handler.ts";
import { callRpc, type RpcEnv } from "./rpc.ts";
import type { IncomingRecord } from "./validation.ts";
import { createVerifier, type Verifier } from "./verify.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const rpcEnv: RpcEnv = { supabaseUrl, serviceRoleKey };

// Una lista mal escrita debe fallar de forma visible (500 server_misconfigured) en
// lugar de arrancar aceptando audiencias que nadie revisó.
let verifier: Verifier | null = null;
let configError: string | null = null;
try {
  const config = resolveAuth({
    GOOGLE_ALLOWED_AUDIENCES: Deno.env.get("GOOGLE_ALLOWED_AUDIENCES"),
    GOOGLE_WEB_CLIENT_ID: Deno.env.get("GOOGLE_WEB_CLIENT_ID"),
    GOOGLE_DESKTOP_CLIENT_ID: Deno.env.get("GOOGLE_DESKTOP_CLIENT_ID"),
    GOOGLE_ANDROID_CLIENT_ID: Deno.env.get("GOOGLE_ANDROID_CLIENT_ID"),
  });
  if (config.audiences.length === 0) {
    configError = "Falta GOOGLE_WEB_CLIENT_ID en los secretos.";
  } else {
    verifier = createVerifier({
      audiences: config.audiences,
      androidClientId: config.androidClientId ?? undefined,
    });
    // Solo números y booleanos: los client IDs son públicos, pero no hace falta
    // registrarlos en los logs.
    console.log(
      JSON.stringify({
        event: "audiences configured",
        count: config.audiences.length,
        androidAzp: config.androidClientId !== null,
      })
    );
  }
} catch (error) {
  configError = error instanceof Error ? error.message : "Configuración de audiencias inválida.";
  console.error(JSON.stringify({ event: "audiences invalid", reason: configError }));
}

const deps: HandlerDeps = {
  verifyToken: async (token: string) => {
    if (verifier === null) {
      throw Object.assign(new Error(configError ?? "Configuración de audiencias inválida."), {
        status: 500,
        code: "server_misconfigured",
      });
    }
    const user = await verifier.verify(token);
    return { sub: user.sub };
  },
  pull: async (userId: string, cursor: number, limit: number) => {
    const page = await callRpc<PullPage>(rpcEnv, "sync_pull", {
      p_user: userId,
      p_cursor: cursor,
      p_limit: limit,
    });
    return {
      records: page?.records ?? [],
      cursor: page?.cursor ?? String(cursor),
      hasMore: Boolean(page?.hasMore),
    };
  },
  push: async (userId: string, records: IncomingRecord[]) => {
    const result = await callRpc<{ accepted?: string[] }>(rpcEnv, "sync_push", {
      p_user: userId,
      p_records: records,
    });
    return result?.accepted ?? [];
  },
  log: (message, extra) => {
    // Never log tokens or payloads: only the event name and its coordinates.
    console.log(JSON.stringify({ event: message, ...(extra ?? {}) }));
  },
};

Deno.serve(createHandler(deps));

/**
 * Minimal PostgREST RPC client.
 *
 * The function never talks to Postgres directly: the migrations expose
 * `sync_pull` and `sync_push` as SECURITY DEFINER functions, and this client calls
 * them with the service role key that Supabase injects into Edge Functions. That
 * keeps the database password out of every deployment secret.
 */

export type RpcEnv = {
  supabaseUrl: string;
  serviceRoleKey: string;
};

export class RpcError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "RpcError";
    this.status = status;
  }
}

export function rpcUrl(env: RpcEnv, fn: string): string {
  return `${env.supabaseUrl.replace(/\/+$/, "")}/rest/v1/rpc/${fn}`;
}

export async function callRpc<T>(
  env: RpcEnv,
  fn: string,
  body: unknown,
  fetchImpl: typeof fetch = fetch
): Promise<T> {
  if (!env.supabaseUrl || !env.serviceRoleKey) {
    throw new RpcError(500, "El backend no tiene configurada la conexión con la base de datos.");
  }

  const response = await fetchImpl(rpcUrl(env, fn), {
    method: "POST",
    headers: {
      "content-type": "application/json",
      accept: "application/json",
      apikey: env.serviceRoleKey,
      authorization: `Bearer ${env.serviceRoleKey}`,
    },
    body: JSON.stringify(body),
  });

  const text = await response.text();
  if (!response.ok) {
    throw new RpcError(response.status, text.slice(0, 300) || `RPC ${fn} falló`);
  }
  if (text.trim() === "") return null as T;
  return JSON.parse(text) as T;
}

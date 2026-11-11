/**
 * HTTP layer of the Cypher sync backend.
 *
 * Routes match exactly what `HttpSyncBackend` calls once the base URL is
 * `https://<ref>.supabase.co/functions/v1/sync`:
 *
 *   POST …/v1/sync/pull
 *   POST …/v1/sync/push
 *
 * The verified token is the only source of the account id: `accountId` in the body
 * is checked for consistency but never trusted.
 */

import {
  normalizeLimit,
  normalizeRecords,
  parseCursor,
  ValidationError,
  type IncomingRecord,
} from "./validation.ts";

export type PullPage = {
  records: unknown[];
  cursor: string | null;
  hasMore: boolean;
};

export type HandlerDeps = {
  verifyToken: (token: string) => Promise<{ sub: string }>;
  pull: (userId: string, cursor: number, limit: number) => Promise<PullPage>;
  push: (userId: string, records: IncomingRecord[]) => Promise<string[]>;
  defaultLimit?: number;
  maxBatch?: number;
  log?: (message: string, extra?: Record<string, unknown>) => void;
};

export type Route = "pull" | "push" | "health";

const RESPONSE_HEADERS: Record<string, string> = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, content-type",
  "access-control-allow-methods": "POST, OPTIONS",
};

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: RESPONSE_HEADERS });
}

export function bearerToken(request: Request): string | null {
  const raw = request.headers.get("authorization") ?? "";
  const match = /^Bearer\s+(.+)$/i.exec(raw.trim());
  const token = match ? match[1].trim() : "";
  return token === "" ? null : token;
}

/** `endsWith` because the function is mounted under `/functions/v1/sync`. */
export function routeOf(pathname: string): Route | null {
  const path = pathname.replace(/\/+$/, "");
  if (path.endsWith("/v1/sync/pull")) return "pull";
  if (path.endsWith("/v1/sync/push")) return "push";
  if (path.endsWith("/health")) return "health";
  return null;
}

type ErrorLike = { status?: unknown; code?: unknown; message?: unknown };

function errorStatus(error: ErrorLike): number {
  const status = typeof error.status === "number" ? error.status : 500;
  return status >= 400 && status <= 599 ? status : 500;
}

function errorCode(error: ErrorLike, fallback: string): string {
  return typeof error.code === "string" && error.code !== "" ? error.code : fallback;
}

export function createHandler(deps: HandlerDeps) {
  const maxBatch = deps.maxBatch ?? 200;
  const defaultLimit = deps.defaultLimit ?? 100;
  const log = deps.log ?? (() => {});

  return async function handle(request: Request): Promise<Response> {
    const route = routeOf(new URL(request.url).pathname);
    if (route === null) return jsonResponse(404, { error: "not_found" });
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: RESPONSE_HEADERS });
    if (route === "health") return jsonResponse(200, { ok: true });
    if (request.method !== "POST") return jsonResponse(405, { error: "method_not_allowed" });

    const token = bearerToken(request);
    if (token === null) return jsonResponse(401, { error: "missing_token" });

    let user: { sub: string };
    try {
      user = await deps.verifyToken(token);
    } catch (error) {
      const failure = error as ErrorLike;
      log("auth rejected", { route, code: errorCode(failure, "invalid_token") });
      return jsonResponse(errorStatus(failure), { error: errorCode(failure, "invalid_token") });
    }
    if (!user || typeof user.sub !== "string" || user.sub.trim() === "") {
      return jsonResponse(401, { error: "invalid_token" });
    }

    let body: unknown;
    try {
      body = await request.json();
    } catch {
      return jsonResponse(400, { error: "invalid_json" });
    }
    if (typeof body !== "object" || body === null || Array.isArray(body)) {
      return jsonResponse(400, { error: "invalid_body" });
    }
    const payload = body as Record<string, unknown>;

    const claimedAccount = payload.accountId;
    if (typeof claimedAccount === "string" && claimedAccount !== "" && claimedAccount !== user.sub) {
      // A client that thinks it is someone else must never reach the database with
      // that identity; refusing is friendlier than silently syncing another account.
      log("account mismatch", { route });
      return jsonResponse(403, { error: "account_mismatch" });
    }

    try {
      if (route === "pull") {
        const cursor = parseCursor(payload.cursor);
        const limit = normalizeLimit(payload.limit, defaultLimit, maxBatch);
        const page = await deps.pull(user.sub, cursor, limit);
        return jsonResponse(200, {
          records: page.records ?? [],
          cursor: page.cursor ?? String(cursor),
          hasMore: Boolean(page.hasMore),
        });
      }

      const records = normalizeRecords(payload.records, { maxRecords: maxBatch });
      const accepted = records.length === 0 ? [] : await deps.push(user.sub, records);
      return jsonResponse(200, { accepted });
    } catch (error) {
      if (error instanceof ValidationError) {
        return jsonResponse(400, { error: error.code, message: error.message });
      }
      const failure = error as ErrorLike;
      log("sync error", { route, status: errorStatus(failure) });
      const status = errorStatus(failure);
      return jsonResponse(status >= 500 ? 502 : status, {
        error: status >= 500 ? "upstream_error" : errorCode(failure, "sync_failed"),
      });
    }
  };
}

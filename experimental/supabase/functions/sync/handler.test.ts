/**
 * Tests del enrutado y del contrato HTTP, sin red ni base de datos: las
 * dependencias (verificación de token, RPC de pull/push) se inyectan.
 *
 *   node --test supabase/functions/sync/handler.test.ts
 */

import assert from "node:assert/strict";
import { test } from "node:test";
import { createHandler, type HandlerDeps } from "./handler.ts";
import { TokenError } from "./verify.ts";
import type { IncomingRecord } from "./validation.ts";

const BASE = "https://mdwjcphgidagmasehtsg.supabase.co/functions/v1/sync";
const SUB = "100305844804415678846";

type Calls = {
  pull: Array<{ userId: string; cursor: number; limit: number }>;
  push: Array<{ userId: string; records: IncomingRecord[] }>;
};

function buildHandler(overrides: Partial<HandlerDeps> = {}) {
  const calls: Calls = { pull: [], push: [] };
  const deps: HandlerDeps = {
    verifyToken: async () => ({ sub: SUB }),
    pull: async (userId, cursor, limit) => {
      calls.pull.push({ userId, cursor, limit });
      return { records: [], cursor: String(cursor), hasMore: false };
    },
    push: async (userId, records) => {
      calls.push.push({ userId, records });
      return records.map((record) => `${record.kind}:${record.id}`);
    },
    ...overrides,
  };
  return { handler: createHandler(deps), calls };
}

function post(path: string, body: unknown, token: string | null = "token-valido") {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (token !== null) headers.authorization = `Bearer ${token}`;
  return new Request(`${BASE}${path}`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
}

test("404 en una ruta desconocida", async () => {
  const { handler } = buildHandler();
  const response = await handler(new Request(`${BASE}/v1/sync/otra`, { method: "POST" }));
  assert.equal(response.status, 404);
});

test("405 con GET en pull", async () => {
  const { handler } = buildHandler();
  const response = await handler(new Request(`${BASE}/v1/sync/pull`));
  assert.equal(response.status, 405);
});

test("401 sin cabecera Authorization", async () => {
  const { handler } = buildHandler();
  const response = await handler(post("/v1/sync/pull", {}, null));
  assert.equal(response.status, 401);
});

test("401 cuando el token no verifica y no toca la base de datos", async () => {
  const { handler, calls } = buildHandler({
    verifyToken: async () => {
      throw new TokenError("invalid_signature", "firma mala", 401);
    },
  });
  const response = await handler(post("/v1/sync/pull", {}));
  assert.equal(response.status, 401);
  assert.equal((await response.json()).error, "invalid_signature");
  assert.equal(calls.pull.length, 0);
});

test("pull usa el sub del token, no el accountId del cuerpo", async () => {
  const { handler, calls } = buildHandler();
  const response = await handler(post("/v1/sync/pull", { accountId: SUB, limit: 50 }));
  assert.equal(response.status, 200);
  assert.deepEqual(calls.pull, [{ userId: SUB, cursor: 0, limit: 50 }]);
  const body = await response.json();
  assert.deepEqual(body, { records: [], cursor: "0", hasMore: false });
});

test("pull pagina con el cursor opaco que envía el cliente", async () => {
  const { handler, calls } = buildHandler({
    pull: async (userId, cursor, limit) => {
      calls.pull.push({ userId, cursor, limit });
      return { records: [{ kind: "PLAYLIST", id: "p1", updatedAt: 1, deleted: false, payload: {} }], cursor: "77", hasMore: true };
    },
  });
  const response = await handler(post("/v1/sync/pull", { accountId: SUB, cursor: "42", limit: 999 }));
  const body = await response.json();

  assert.deepEqual(calls.pull, [{ userId: SUB, cursor: 42, limit: 200 }]);
  assert.equal(body.cursor, "77");
  assert.equal(body.hasMore, true);
  assert.equal(body.records.length, 1);
});

test("401 invalid_audience sin tocar la base de datos", async () => {
  const { handler, calls } = buildHandler({
    verifyToken: async () => {
      throw new TokenError("invalid_audience", "audiencia no permitida", 401);
    },
  });
  const response = await handler(
    post("/v1/sync/push", { records: [{ kind: "PLAYLIST", id: "p1", updatedAt: 1 }] })
  );
  assert.equal(response.status, 401);
  assert.equal((await response.json()).error, "invalid_audience");
  assert.equal(calls.push.length, 0);
});

test("403 si accountId no coincide con el token", async () => {
  const { handler, calls } = buildHandler();
  const response = await handler(post("/v1/sync/pull", { accountId: "otro-usuario" }));
  assert.equal(response.status, 403);
  assert.equal((await response.json()).error, "account_mismatch");
  assert.equal(calls.pull.length, 0);
});

test("ignora accountId ausente y usa igualmente el sub", async () => {
  const { handler, calls } = buildHandler();
  const response = await handler(post("/v1/sync/pull", {}));
  assert.equal(response.status, 200);
  assert.equal(calls.pull[0].userId, SUB);
});

test("push valida, normaliza y devuelve accepted", async () => {
  const { handler, calls } = buildHandler();
  const response = await handler(
    post("/v1/sync/push", {
      accountId: SUB,
      records: [
        { kind: "PLAYLIST", id: "p1", updatedAt: 10, deleted: false, payload: { name: "Viaje", trackCount: 3 } },
        { kind: "FAVORITE", id: "t1", updatedAt: 11, deleted: true, payload: {} },
      ],
    })
  );
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.deepEqual(body.accepted, ["PLAYLIST:p1", "FAVORITE:t1"]);
  assert.deepEqual(calls.push[0].records[0].payload, { name: "Viaje", trackCount: "3" });
  assert.equal(calls.push[0].records[1].deleted, true);
});

test("400 con un kind desconocido", async () => {
  const { handler, calls } = buildHandler();
  const response = await handler(
    post("/v1/sync/push", { records: [{ kind: "COOKIES", id: "x", updatedAt: 1 }] })
  );
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "invalid_kind");
  assert.equal(calls.push.length, 0);
});

test("400 si se superan los 200 registros por lote", async () => {
  const { handler } = buildHandler();
  const records = Array.from({ length: 201 }, (_, index) => ({
    kind: "STATS",
    id: `2026-11-${index}|track`,
    updatedAt: index,
  }));
  const response = await handler(post("/v1/sync/push", { records }));
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "too_many_records");
});

test("400 con un cursor no numérico", async () => {
  const { handler } = buildHandler();
  const response = await handler(post("/v1/sync/pull", { cursor: "abc" }));
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "invalid_cursor");
});

test("502 si la base de datos falla, sin filtrar el error interno", async () => {
  const { handler } = buildHandler({
    pull: async () => {
      throw new Error("connection refused a 10.0.0.1");
    },
  });
  const response = await handler(post("/v1/sync/pull", {}));
  assert.equal(response.status, 502);
  const body = await response.json();
  assert.equal(body.error, "upstream_error");
  assert.equal(JSON.stringify(body).includes("10.0.0.1"), false);
});

test("health responde sin token", async () => {
  const { handler } = buildHandler();
  const response = await handler(new Request(`${BASE}/health`));
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true });
});

test("responde a CORS preflight", async () => {
  const { handler } = buildHandler();
  const response = await handler(new Request(`${BASE}/v1/sync/pull`, { method: "OPTIONS" }));
  assert.equal(response.status, 204);
  assert.equal(response.headers.get("access-control-allow-origin"), "*");
});

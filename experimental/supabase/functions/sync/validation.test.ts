/**
 * Tests de validación del contrato: nada de lo que llega del cliente se acepta
 * sin comprobarlo.
 *
 *   node --test supabase/functions/sync/validation.test.ts
 */

import assert from "node:assert/strict";
import { test } from "node:test";
import {
  normalizeLimit,
  normalizeRecords,
  parseCursor,
  recordKey,
  ValidationError,
} from "./validation.ts";

function expectValidationError(fn: () => unknown, code: string) {
  try {
    fn();
  } catch (error) {
    assert.ok(error instanceof ValidationError);
    assert.equal(error.code, code);
    return;
  }
  assert.fail(`se esperaba ValidationError ${code}`);
}

test("normaliza un registro completo", () => {
  const [record] = normalizeRecords(
    [{ kind: "PLAYLIST", id: "pl-1", updatedAt: 1_760_000_000_000, payload: { name: "Viaje" } }],
    { maxRecords: 200 }
  );

  assert.equal(record.kind, "PLAYLIST");
  assert.equal(record.id, "pl-1");
  assert.equal(record.updatedAt, 1_760_000_000_000);
  assert.equal(record.deleted, false);
  assert.deepEqual(record.payload, { name: "Viaje" });
  assert.equal(recordKey(record), "PLAYLIST:pl-1");
});

test("convierte números y booleanos del payload a texto", () => {
  const [record] = normalizeRecords(
    [{ kind: "STATS", id: "d|t", updatedAt: 1, payload: { playCount: 7, deleted: false, album: null } }],
    { maxRecords: 200 }
  );

  assert.deepEqual(record.payload, { playCount: "7", deleted: "false", album: "" });
});

test("rechaza payloads anidados", () => {
  expectValidationError(
    () =>
      normalizeRecords(
        [{ kind: "PLAYLIST", id: "p", updatedAt: 1, payload: { trackIds: ["a", "b"] } }],
        { maxRecords: 200 }
      ),
    "invalid_payload"
  );
});

test("rechaza payloads demasiado grandes", () => {
  expectValidationError(
    () =>
      normalizeRecords(
        [{ kind: "PLAYLIST", id: "p", updatedAt: 1, payload: { name: "x".repeat(20_000) } }],
        { maxRecords: 200, maxPayloadBytes: 16 * 1024 }
      ),
    "invalid_payload"
  );
});

test("rechaza kinds fuera de la lista", () => {
  expectValidationError(
    () => normalizeRecords([{ kind: "AUDIO", id: "a", updatedAt: 1 }], { maxRecords: 200 }),
    "invalid_kind"
  );
});

test("rechaza ids vacíos o con caracteres de control", () => {
  expectValidationError(
    () => normalizeRecords([{ kind: "FAVORITE", id: "  ", updatedAt: 1 }], { maxRecords: 200 }),
    "invalid_record"
  );
  expectValidationError(
    () => normalizeRecords([{ kind: "FAVORITE", id: "a\u0001b", updatedAt: 1 }], { maxRecords: 200 }),
    "invalid_record"
  );
});

test("rechaza updatedAt negativo o no numérico", () => {
  expectValidationError(
    () => normalizeRecords([{ kind: "FAVORITE", id: "a", updatedAt: -5 }], { maxRecords: 200 }),
    "invalid_record"
  );
  expectValidationError(
    () => normalizeRecords([{ kind: "FAVORITE", id: "a", updatedAt: "ayer" }], { maxRecords: 200 }),
    "invalid_record"
  );
});

test("deduplica el mismo registro y se queda con el más nuevo", () => {
  const records = normalizeRecords(
    [
      { kind: "FAVORITE", id: "t1", updatedAt: 10, payload: { title: "viejo" } },
      { kind: "FAVORITE", id: "t1", updatedAt: 20, payload: { title: "nuevo" } },
      { kind: "FAVORITE", id: "t1", updatedAt: 5, payload: { title: "antiguo" } },
    ],
    { maxRecords: 200 }
  );

  assert.equal(records.length, 1);
  assert.equal(records[0].updatedAt, 20);
  assert.equal(records[0].payload.title, "nuevo");
});

test("parseCursor acepta vacío, número y dígitos", () => {
  assert.equal(parseCursor(undefined), 0);
  assert.equal(parseCursor(null), 0);
  assert.equal(parseCursor(""), 0);
  assert.equal(parseCursor(0), 0);
  assert.equal(parseCursor("42"), 42);
  assert.equal(parseCursor(42), 42);
});

test("parseCursor rechaza negativos y texto", () => {
  expectValidationError(() => parseCursor("-1"), "invalid_cursor");
  expectValidationError(() => parseCursor("abc"), "invalid_cursor");
  expectValidationError(() => parseCursor("1.5"), "invalid_cursor");
});

test("normalizeLimit acota al máximo del servidor", () => {
  assert.equal(normalizeLimit(undefined, 100, 200), 100);
  assert.equal(normalizeLimit(50, 100, 200), 50);
  assert.equal(normalizeLimit(9999, 100, 200), 200);
  assert.equal(normalizeLimit(-3, 100, 200), 100);
  assert.equal(normalizeLimit("25", 100, 200), 25);
});

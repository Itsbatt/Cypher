/**
 * Validation for the Cypher sync contract.
 *
 * Everything the Android client sends is untrusted: kinds are whitelisted, ids and
 * timestamps are bounded, and payloads are flattened to the `string -> string` map
 * the client expects when it decodes a pulled record.
 *
 * This module is runtime agnostic (no Deno, no Node APIs) so the tests can import it
 * directly.
 */

export const SYNC_KINDS = ["PROFILE", "PLAYLIST", "FAVORITE", "PREFERENCE", "STATS"] as const;

export type SyncKind = (typeof SYNC_KINDS)[number];

export type IncomingRecord = {
  kind: SyncKind;
  id: string;
  updatedAt: number;
  deleted: boolean;
  payload: Record<string, string>;
};

export class ValidationError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "ValidationError";
    this.code = code;
  }
}

const MAX_ID_LENGTH = 256;
const MAX_PAYLOAD_KEY_LENGTH = 128;
const DEFAULT_MAX_PAYLOAD_BYTES = 16 * 1024;
const MAX_CURSOR_DIGITS = 18;

export function recordKey(record: { kind: string; id: string }): string {
  return `${record.kind}:${record.id}`;
}

/** Cursor is opaque for the client but it is always a revision number for us. */
export function parseCursor(raw: unknown): number {
  if (raw === undefined || raw === null || raw === "") return 0;
  if (typeof raw === "number") {
    if (!Number.isSafeInteger(raw) || raw < 0) {
      throw new ValidationError("invalid_cursor", "El cursor no es un número de revisión válido.");
    }
    return raw;
  }
  if (typeof raw !== "string") {
    throw new ValidationError("invalid_cursor", "El cursor debe ser una cadena.");
  }
  const value = raw.trim();
  if (value === "") return 0;
  if (!new RegExp(`^[0-9]{1,${MAX_CURSOR_DIGITS}}$`).test(value)) {
    throw new ValidationError("invalid_cursor", "El cursor no es un número de revisión válido.");
  }
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed)) {
    throw new ValidationError("invalid_cursor", "El cursor está fuera de rango.");
  }
  return parsed;
}

export function normalizeLimit(raw: unknown, fallback: number, max: number): number {
  if (raw === undefined || raw === null) return fallback;
  const value = typeof raw === "number" ? raw : Number(raw);
  if (!Number.isFinite(value)) return fallback;
  const rounded = Math.trunc(value);
  if (rounded <= 0) return fallback;
  return Math.min(rounded, max);
}

function normalizeId(raw: unknown): string {
  if (typeof raw !== "string") {
    throw new ValidationError("invalid_record", "Cada registro necesita un id de texto.");
  }
  const id = raw.trim();
  if (id.length === 0 || id.length > MAX_ID_LENGTH) {
    throw new ValidationError("invalid_record", "El id de un registro está vacío o es demasiado largo.");
  }
  // Control characters never belong in an id and would break the outbox keys.
  if (/[\u0000-\u001f]/.test(id)) {
    throw new ValidationError("invalid_record", "El id de un registro contiene caracteres de control.");
  }
  return id;
}

function normalizeKind(raw: unknown): SyncKind {
  if (typeof raw !== "string" || !(SYNC_KINDS as readonly string[]).includes(raw)) {
    throw new ValidationError("invalid_kind", "Tipo de registro no admitido.");
  }
  return raw as SyncKind;
}

function normalizeUpdatedAt(raw: unknown): number {
  const value = typeof raw === "string" && raw.trim() !== "" ? Number(raw) : raw;
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new ValidationError("invalid_record", "El registro no tiene una marca de tiempo válida.");
  }
  const timestamp = Math.trunc(value);
  if (timestamp < 0 || !Number.isSafeInteger(timestamp)) {
    throw new ValidationError("invalid_record", "La marca de tiempo del registro está fuera de rango.");
  }
  return timestamp;
}

function normalizePayload(raw: unknown, maxBytes: number): Record<string, string> {
  if (raw === undefined || raw === null) return {};
  if (typeof raw !== "object" || Array.isArray(raw)) {
    throw new ValidationError("invalid_payload", "El payload debe ser un objeto de claves y valores.");
  }
  const payload: Record<string, string> = {};
  for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
    if (key.length === 0 || key.length > MAX_PAYLOAD_KEY_LENGTH) {
      throw new ValidationError("invalid_payload", "El payload contiene una clave no válida.");
    }
    if (value === null || value === undefined) {
      payload[key] = "";
      continue;
    }
    if (typeof value === "string") {
      payload[key] = value;
      continue;
    }
    if (typeof value === "number" || typeof value === "boolean") {
      // The client decodes every payload value with `optString`, so the wire format
      // is always text; converting here keeps old and future clients compatible.
      payload[key] = String(value);
      continue;
    }
    throw new ValidationError(
      "invalid_payload",
      "El payload solo admite texto, números, booleanos o nulos."
    );
  }
  const size = new TextEncoder().encode(JSON.stringify(payload)).length;
  if (size > maxBytes) {
    throw new ValidationError("invalid_payload", "El payload de un registro es demasiado grande.");
  }
  return payload;
}

export type NormalizeOptions = {
  maxRecords: number;
  maxPayloadBytes?: number;
};

/** Turns the untrusted `records` array into the exact shape the SQL layer expects. */
export function normalizeRecords(raw: unknown, options: NormalizeOptions): IncomingRecord[] {
  if (raw === undefined || raw === null) return [];
  if (!Array.isArray(raw)) {
    throw new ValidationError("invalid_records", "Los registros deben enviarse como una lista.");
  }
  if (raw.length > options.maxRecords) {
    throw new ValidationError(
      "too_many_records",
      `Un lote de sincronización no puede superar ${options.maxRecords} registros.`
    );
  }
  const maxPayloadBytes = options.maxPayloadBytes ?? DEFAULT_MAX_PAYLOAD_BYTES;
  const records: IncomingRecord[] = [];
  const seen = new Set<string>();

  for (const entry of raw) {
    if (typeof entry !== "object" || entry === null || Array.isArray(entry)) {
      throw new ValidationError("invalid_record", "Cada registro debe ser un objeto.");
    }
    const item = entry as Record<string, unknown>;
    const record: IncomingRecord = {
      kind: normalizeKind(item.kind),
      id: normalizeId(item.id),
      updatedAt: normalizeUpdatedAt(item.updatedAt),
      deleted: item.deleted === undefined ? false : item.deleted === true,
      payload: normalizePayload(item.payload, maxPayloadBytes),
    };
    if (item.deleted !== undefined && typeof item.deleted !== "boolean") {
      throw new ValidationError("invalid_record", "El campo deleted debe ser booleano.");
    }
    const key = recordKey(record);
    // Duplicates inside one batch would make the SQL upsert ambiguous; the newest
    // version of a key wins, which matches last-writer-wins.
    if (seen.has(key)) {
      const index = records.findIndex((existing) => recordKey(existing) === key);
      if (index >= 0 && records[index].updatedAt <= record.updatedAt) records[index] = record;
      continue;
    }
    seen.add(key);
    records.push(record);
  }
  return records;
}

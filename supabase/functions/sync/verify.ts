/**
 * Google ID token verification.
 *
 * The Android client authenticates with Credential Manager and sends the resulting
 * ID token as a bearer credential. This module validates that token without any
 * third-party dependency: RS256 against Google's JWKS, issuer, audience, lifetime
 * and subject. It only uses WebCrypto, `fetch` and a handful of Web APIs that exist
 * both in the Edge Function runtime and in Node 24, where the tests run.
 */

export type VerifiedUser = {
  sub: string;
  email: string | null;
  emailVerified: boolean;
};

export class TokenError extends Error {
  readonly code: string;
  readonly status: number;

  constructor(code: string, message: string, status = 401) {
    super(message);
    this.name = "TokenError";
    this.code = code;
    this.status = status;
  }
}

export type Verifier = {
  verify(token: string): Promise<VerifiedUser>;
  resetCache(): void;
};

export type VerifierOptions = {
  /**
   * Closed list of accepted `aud` values: the web/Android client ID and the
   * Desktop client ID. A token must name exactly one of them; anything else is
   * rejected with `invalid_audience`.
   */
  audiences: string[];
  /**
   * Client that Google may report as `azp` for a token requested from Android:
   * there `aud` is the web client (`serverClientId`) and `azp` is the Android app
   * client. Without this, those tokens would be rejected as incoherent.
   */
  androidClientId?: string;
  jwksUrl?: string;
  fetchImpl?: typeof fetch;
  now?: () => number;
  cacheTtlMs?: number;
};

const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const ALLOWED_ISSUERS = new Set(["https://accounts.google.com", "accounts.google.com"]);
const CLOCK_SKEW_SECONDS = 60;
const DEFAULT_CACHE_TTL_MS = 60 * 60 * 1000;

type Jwk = {
  kty?: string;
  kid?: string;
  n?: string;
  e?: string;
  alg?: string;
  use?: string;
};

function decodeBase64Url(segment: string): Uint8Array {
  const normalized = segment.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function decodeJson(segment: string): Record<string, unknown> {
  try {
    return JSON.parse(new TextDecoder().decode(decodeBase64Url(segment))) as Record<string, unknown>;
  } catch {
    throw new TokenError("invalid_token", "El token no contiene JSON válido.");
  }
}

function claimNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function normalizeAudiences(raw: unknown): string[] {
  if (!Array.isArray(raw)) return [];
  const seen = new Set<string>();
  const audiences: string[] = [];
  for (const entry of raw) {
    if (typeof entry !== "string") continue;
    const value = entry.trim();
    if (value === "" || seen.has(value)) continue;
    seen.add(value);
    audiences.push(value);
  }
  return audiences;
}

/**
 * Google ID tokens carry a single `aud`. A list is only tolerated with exactly one
 * entry; multiple audiences are rejected because Google never issues them for these
 * clients and accepting them would widen what a token can claim.
 */
function singleAudience(value: unknown): string | null {
  if (typeof value === "string") return value.trim() === "" ? null : value.trim();
  if (Array.isArray(value) && value.length === 1 && typeof value[0] === "string") {
    return value[0].trim() === "" ? null : value[0].trim();
  }
  return null;
}

export function createVerifier(options: VerifierOptions): Verifier {
  const allowedAudiences = normalizeAudiences(options.audiences);
  if (allowedAudiences.length === 0) {
    throw new Error("createVerifier necesita al menos una audiencia permitida");
  }
  const androidClientId = typeof options.androidClientId === "string"
    ? options.androidClientId.trim()
    : "";
  const jwksUrl = options.jwksUrl ?? GOOGLE_JWKS_URL;
  const doFetch = options.fetchImpl ?? fetch;
  const now = options.now ?? (() => Date.now());
  const cacheTtlMs = options.cacheTtlMs ?? DEFAULT_CACHE_TTL_MS;

  let keys = new Map<string, CryptoKey>();
  let fetchedAt = 0;

  async function loadKeys(force: boolean): Promise<void> {
    if (!force && keys.size > 0 && now() - fetchedAt < cacheTtlMs) return;

    let response: Response;
    try {
      response = await doFetch(jwksUrl, { headers: { accept: "application/json" } });
    } catch {
      throw new TokenError(
        "jwks_unavailable",
        "No se pudo contactar con Google para validar el token.",
        503
      );
    }
    if (!response.ok) {
      throw new TokenError(
        "jwks_unavailable",
        `Google respondió ${response.status} al entregar sus claves públicas.`,
        503
      );
    }

    let body: { keys?: Jwk[] };
    try {
      body = (await response.json()) as { keys?: Jwk[] };
    } catch {
      throw new TokenError("jwks_unavailable", "Las claves de Google no son JSON válido.", 503);
    }

    const next = new Map<string, CryptoKey>();
    for (const jwk of body.keys ?? []) {
      if (typeof jwk.kid !== "string" || jwk.kid === "") continue;
      if (jwk.alg && jwk.alg !== "RS256") continue;
      if (jwk.kty && jwk.kty !== "RSA") continue;
      if (!jwk.n || !jwk.e) continue;
      try {
        const key = await crypto.subtle.importKey(
          "jwk",
          { kty: "RSA", n: jwk.n, e: jwk.e, alg: "RS256", ext: true },
          { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
          false,
          ["verify"]
        );
        next.set(jwk.kid, key);
      } catch {
        // A single malformed key must not invalidate the whole set.
      }
    }
    if (next.size === 0) {
      throw new TokenError("jwks_empty", "Google no devolvió claves utilizables.", 503);
    }
    keys = next;
    fetchedAt = now();
  }

  async function verify(token: string): Promise<VerifiedUser> {
    const trimmed = (token ?? "").trim();
    const parts = trimmed.split(".");
    if (parts.length !== 3 || parts.some((part) => part === "")) {
      throw new TokenError("invalid_token", "El token no tiene el formato JWT esperado.");
    }

    const header = decodeJson(parts[0]);
    if (header.alg !== "RS256") {
      throw new TokenError("invalid_algorithm", "Solo se admite RS256.");
    }
    const kid = typeof header.kid === "string" ? header.kid : "";
    if (kid === "") {
      throw new TokenError("invalid_token", "El token no indica qué clave lo firmó.");
    }

    await loadKeys(false);
    let key = keys.get(kid);
    if (!key) {
      // Google rotates its keys: refresh once before rejecting the token.
      await loadKeys(true);
      key = keys.get(kid);
    }
    if (!key) {
      throw new TokenError("unknown_kid", "El token está firmado con una clave desconocida.");
    }

    const signed = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
    const signature = decodeBase64Url(parts[2]);
    let valid = false;
    try {
      valid = await crypto.subtle.verify({ name: "RSASSA-PKCS1-v1_5" }, key, signature, signed);
    } catch {
      valid = false;
    }
    if (!valid) {
      throw new TokenError("invalid_signature", "La firma del token no es válida.");
    }

    const claims = decodeJson(parts[1]);

    const issuer = typeof claims.iss === "string" ? claims.iss : "";
    if (!ALLOWED_ISSUERS.has(issuer)) {
      throw new TokenError("invalid_issuer", "El token no lo emitió Google.");
    }
    const audience = singleAudience(claims.aud);
    if (audience === null || !allowedAudiences.includes(audience)) {
      throw new TokenError("invalid_audience", "El token no está destinado a Cypher.");
    }
    // OIDC: `azp` names the party the token was issued to. Two values are coherent:
    //   · the audience itself (token requested directly by that client), or
    //   · the Android app client, which is what Credential Manager reports while
    //     `aud` stays the web client used as `serverClientId`.
    // Anything else is rejected: unknown parties and mismatched pairs are both
    // `invalid_azp`, never silently accepted.
    if (claims.azp !== undefined) {
      const azp = typeof claims.azp === "string" ? claims.azp.trim() : "";
      const coherent = azp !== "" && (azp === audience || (androidClientId !== "" && azp === androidClientId));
      if (!coherent) {
        throw new TokenError("invalid_azp", "El token declara una parte autorizada incoherente.");
      }
    }

    const secondsNow = Math.floor(now() / 1000);
    const expiry = claimNumber(claims.exp);
    if (expiry === null) {
      throw new TokenError("invalid_token", "El token no caduca nunca: se rechaza.");
    }
    if (secondsNow >= expiry + CLOCK_SKEW_SECONDS) {
      throw new TokenError("token_expired", "El token ha caducado.");
    }
    const issuedAt = claimNumber(claims.iat);
    if (issuedAt !== null && issuedAt > secondsNow + CLOCK_SKEW_SECONDS) {
      throw new TokenError("invalid_iat", "El token está emitido en el futuro.");
    }

    const sub = typeof claims.sub === "string" ? claims.sub.trim() : "";
    if (sub === "") {
      throw new TokenError("invalid_subject", "El token no identifica al usuario.");
    }

    return {
      sub,
      email: typeof claims.email === "string" ? claims.email : null,
      emailVerified: claims.email_verified === true,
    };
  }

  return {
    verify,
    resetCache() {
      keys = new Map();
      fetchedAt = 0;
    },
  };
}

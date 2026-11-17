/**
 * Tests de la verificación del Google ID token.
 *
 * Se ejecutan sin red y sin Deno: se genera un par RSA con WebCrypto, se firman
 * tokens equivalentes a los de Google y se sirve un JWKS falso a través de un
 * `fetch` inyectado.
 *
 *   node --test supabase/functions/sync/verify.test.ts
 */

import assert from "node:assert/strict";
import { test } from "node:test";
import { createVerifier, TokenError } from "./verify.ts";

const AUDIENCE = "764777457932-djot894fcm28vs15mhptks3p2cql56o6.apps.googleusercontent.com";
const DESKTOP_AUDIENCE = "764777457932-goan61rtqc8g3u6t7o7if2pdm6pkkc97.apps.googleusercontent.com";
const ANDROID_CLIENT = "764777457932-op2aasnvnqb9d6mrv9q3dtr2221ek36j.apps.googleusercontent.com";
const NOW_MS = 1_760_000_000_000;
const NOW_S = Math.floor(NOW_MS / 1000);

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function encodeJson(value: unknown): string {
  return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}

type Keypair = { privateKey: CryptoKey; publicJwk: JsonWebKey & { kid: string } };

async function createKeypair(kid = "test-kid-1"): Promise<Keypair> {
  const pair = await crypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["sign", "verify"]
  );
  const publicJwk = (await crypto.subtle.exportKey("jwk", pair.publicKey)) as JsonWebKey;
  return {
    privateKey: pair.privateKey,
    publicJwk: { ...publicJwk, kid, alg: "RS256", use: "sig", kty: "RSA" },
  };
}

type SignOptions = {
  kid?: string;
  alg?: string;
  claims?: Record<string, unknown>;
  key?: CryptoKey;
  /** When false the token is signed with a different key than the published one. */
  tamperSignature?: boolean;
};

async function signToken(keypair: Keypair, options: SignOptions = {}): Promise<string> {
  const header = { alg: options.alg ?? "RS256", typ: "JWT", kid: options.kid ?? keypair.publicJwk.kid };
  const claims = options.claims ?? {
    iss: "https://accounts.google.com",
    aud: AUDIENCE,
    sub: "100305844804415678846",
    email: "alguien@ejemplo.com",
    email_verified: true,
    iat: NOW_S - 30,
    exp: NOW_S + 3_300,
  };
  const signingInput = `${encodeJson(header)}.${encodeJson(claims)}`;
  const signature = await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    options.key ?? keypair.privateKey,
    new TextEncoder().encode(signingInput)
  );
  return `${signingInput}.${base64Url(new Uint8Array(signature))}`;
}

type Fetcher = typeof fetch;

function jwksFetcher(publicJwk: JsonWebKey, state?: { calls: number }): Fetcher {
  return (async () => {
    if (state) state.calls += 1;
    return new Response(JSON.stringify({ keys: [publicJwk] }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as Fetcher;
}

function buildVerifier(fetcher: Fetcher, nowMs = NOW_MS, androidClientId: string | null = ANDROID_CLIENT) {
  return createVerifier({
    audiences: [AUDIENCE, DESKTOP_AUDIENCE],
    // `null` and not `undefined`: passing undefined would re-apply the default.
    androidClientId: androidClientId ?? undefined,
    jwksUrl: "https://example.test/jwks",
    fetchImpl: fetcher,
    now: () => nowMs,
  });
}

async function expectTokenError(promise: Promise<unknown>, code: string): Promise<TokenError> {
  try {
    await promise;
  } catch (error) {
    assert.ok(error instanceof TokenError, `se esperaba TokenError, llegó ${String(error)}`);
    assert.equal(error.code, code);
    return error;
  }
  assert.fail(`se esperaba TokenError ${code}`);
}

test("acepta un token RS256 válido y devuelve el sub", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair);

  const user = await verifier.verify(token);
  assert.equal(user.sub, "100305844804415678846");
  assert.equal(user.email, "alguien@ejemplo.com");
  assert.equal(user.emailVerified, true);
});

test("reutiliza el JWKS en caché entre tokens", async () => {
  const keypair = await createKeypair();
  const state = { calls: 0 };
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk, state));

  await verifier.verify(await signToken(keypair));
  await verifier.verify(await signToken(keypair));
  assert.equal(state.calls, 1);
});

test("rechaza una firma manipulada", async () => {
  const keypair = await createKeypair();
  const other = await createKeypair("other-kid");
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, { key: other.privateKey });

  const error = await expectTokenError(verifier.verify(token), "invalid_signature");
  assert.equal(error.status, 401);
});

test("rechaza un payload alterado después de firmar", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair);
  const [header, , signature] = token.split(".");
  const forgedClaims = encodeJson({
    iss: "https://accounts.google.com",
    aud: AUDIENCE,
    sub: "otro-usuario",
    iat: NOW_S - 30,
    exp: NOW_S + 3_300,
  });
  const forged = `${header}.${forgedClaims}.${signature}`;

  await expectTokenError(verifier.verify(forged), "invalid_signature");
});

test("rechaza alg none", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, { alg: "none" });

  await expectTokenError(verifier.verify(token), "invalid_algorithm");
});

test("rechaza un audience distinto", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, {
    claims: {
      iss: "https://accounts.google.com",
      aud: "764777457932-otrocliente.apps.googleusercontent.com",
      sub: "100305844804415678846",
      iat: NOW_S - 30,
      exp: NOW_S + 3_300,
    },
  });

  await expectTokenError(verifier.verify(token), "invalid_audience");
});

test("rechaza un issuer distinto", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, {
    claims: {
      iss: "https://evil.example.com",
      aud: AUDIENCE,
      sub: "100305844804415678846",
      iat: NOW_S - 30,
      exp: NOW_S + 3_300,
    },
  });

  await expectTokenError(verifier.verify(token), "invalid_issuer");
});

test("rechaza un token caducado", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, {
    claims: {
      iss: "https://accounts.google.com",
      aud: AUDIENCE,
      sub: "100305844804415678846",
      iat: NOW_S - 7_200,
      exp: NOW_S - 600,
    },
  });

  await expectTokenError(verifier.verify(token), "token_expired");
});

test("rechaza un iat en el futuro", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, {
    claims: {
      iss: "https://accounts.google.com",
      aud: AUDIENCE,
      sub: "100305844804415678846",
      iat: NOW_S + 3_600,
      exp: NOW_S + 7_200,
    },
  });

  await expectTokenError(verifier.verify(token), "invalid_iat");
});

test("rechaza un token sin sub", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, {
    claims: {
      iss: "https://accounts.google.com",
      aud: AUDIENCE,
      sub: "",
      iat: NOW_S - 30,
      exp: NOW_S + 3_300,
    },
  });

  await expectTokenError(verifier.verify(token), "invalid_subject");
});

test("acepta el issuer sin esquema y el azp coherente", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, {
    claims: {
      iss: "accounts.google.com",
      aud: AUDIENCE,
      azp: AUDIENCE,
      sub: "100305844804415678846",
      iat: NOW_S - 30,
      exp: NOW_S + 3_300,
    },
  });

  const user = await verifier.verify(token);
  assert.equal(user.sub, "100305844804415678846");
});

test("acepta el token del cliente Desktop", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, {
    claims: {
      iss: "https://accounts.google.com",
      aud: DESKTOP_AUDIENCE,
      azp: DESKTOP_AUDIENCE,
      sub: "100305844804415678846",
      email: "alguien@ejemplo.com",
      email_verified: true,
      iat: NOW_S - 30,
      exp: NOW_S + 3_300,
    },
  });

  const user = await verifier.verify(token);
  assert.equal(user.sub, "100305844804415678846");
});

test("acepta un token Android: aud web y azp el cliente Android", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, {
    claims: {
      iss: "https://accounts.google.com",
      aud: AUDIENCE,
      azp: ANDROID_CLIENT,
      sub: "100305844804415678846",
      iat: NOW_S - 30,
      exp: NOW_S + 3_300,
    },
  });

  const user = await verifier.verify(token);
  assert.equal(user.sub, "100305844804415678846");
});

test("rechaza un azp desconocido aunque la audiencia sea válida", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, {
    claims: {
      iss: "https://accounts.google.com",
      aud: AUDIENCE,
      azp: "764777457932-desconocido00000000000000000.apps.googleusercontent.com",
      sub: "100305844804415678846",
      iat: NOW_S - 30,
      exp: NOW_S + 3_300,
    },
  });

  await expectTokenError(verifier.verify(token), "invalid_azp");
});

test("rechaza un azp permitido como audiencia pero incoherente con este token", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, {
    claims: {
      iss: "https://accounts.google.com",
      aud: AUDIENCE,
      azp: DESKTOP_AUDIENCE,
      sub: "100305844804415678846",
      iat: NOW_S - 30,
      exp: NOW_S + 3_300,
    },
  });

  await expectTokenError(verifier.verify(token), "invalid_azp");
});

test("rechaza azp Android si el backend no lo tiene configurado", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk), NOW_MS, null);
  const token = await signToken(keypair, {
    claims: {
      iss: "https://accounts.google.com",
      aud: AUDIENCE,
      azp: ANDROID_CLIENT,
      sub: "100305844804415678846",
      iat: NOW_S - 30,
      exp: NOW_S + 3_300,
    },
  });

  await expectTokenError(verifier.verify(token), "invalid_azp");
});

test("rechaza un azp que no coincide con la audiencia", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, {
    claims: {
      iss: "https://accounts.google.com",
      aud: DESKTOP_AUDIENCE,
      azp: AUDIENCE,
      sub: "100305844804415678846",
      iat: NOW_S - 30,
      exp: NOW_S + 3_300,
    },
  });

  await expectTokenError(verifier.verify(token), "invalid_azp");
});

test("rechaza aud con varias entradas aunque una esté permitida", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, {
    claims: {
      iss: "accounts.google.com",
      aud: ["otro-cliente", AUDIENCE],
      sub: "100305844804415678846",
      iat: NOW_S - 30,
      exp: NOW_S + 3_300,
    },
  });

  await expectTokenError(verifier.verify(token), "invalid_audience");
});

test("refresca el JWKS cuando el kid no está en caché", async () => {
  const first = await createKeypair("kid-1");
  const second = await createKeypair("kid-2");
  let served = first.publicJwk;
  let calls = 0;
  const fetcher = (async () => {
    calls += 1;
    return new Response(JSON.stringify({ keys: [served] }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as Fetcher;

  const verifier = buildVerifier(fetcher);
  await verifier.verify(await signToken(first));

  served = second.publicJwk;
  const user = await verifier.verify(await signToken(second, { kid: "kid-2" }));

  assert.equal(user.sub, "100305844804415678846");
  assert.equal(calls, 2);
});

test("rechaza un kid desconocido tras refrescar", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));
  const token = await signToken(keypair, { kid: "kid-inexistente" });

  await expectTokenError(verifier.verify(token), "unknown_kid");
});

test("devuelve 503 si el JWKS no responde", async () => {
  const keypair = await createKeypair();
  const failing = (async () => {
    throw new Error("sin red");
  }) as Fetcher;
  const verifier = buildVerifier(failing);

  const error = await expectTokenError(verifier.verify(await signToken(keypair)), "jwks_unavailable");
  assert.equal(error.status, 503);
});

test("rechaza tokens con formato inválido", async () => {
  const keypair = await createKeypair();
  const verifier = buildVerifier(jwksFetcher(keypair.publicJwk));

  await expectTokenError(verifier.verify("no-es-un-jwt"), "invalid_token");
  await expectTokenError(verifier.verify("a.b"), "invalid_token");
});

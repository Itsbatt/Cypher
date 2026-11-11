#!/usr/bin/env node
/**
 * Cypher · auditoría HTTP con un Google ID token real (cliente Desktop + PKCE).
 *
 * Garantías de seguridad de este script:
 *   · El authorization code, el code_verifier y el ID token viven SOLO en memoria.
 *   · Nunca se escriben en disco, ni en variables de entorno, ni en la línea de comandos.
 *   · Nunca se imprimen, ni completos ni en fragmentos.
 *   · El servidor loopback escucha únicamente en 127.0.0.1, atiende un callback y se cierra.
 *   · Se valida `state` (tiempo constante), `nonce`, `aud`, `azp`, `iss` y `exp` antes de usar el token.
 *
 * Uso:
 *   node tools/oauth-audit/audit.mjs --self-test
 *   node tools/oauth-audit/audit.mjs --help
 *   node tools/oauth-audit/audit.mjs
 *
 * Requiere (nunca por chat):
 *   tools/oauth-audit/.client_id      -> client ID del cliente Desktop (público)
 *   tools/oauth-audit/.client_secret   -> opcional, solo si Google lo exige en el canje
 */

import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { createServer } from "node:http";
import { execFile } from "node:child_process";
import { existsSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
const TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
const SCOPES = "openid email profile";
const CALLBACK_PATH = "/callback";

/**
 * Con `--read-only` ninguna ruta puede escribir: `callApi` rechaza cualquier
 * petición cuya ruta contenga "/push", aunque en el futuro se añada una llamada
 * nueva o un camino de error intente escribir.
 */
let auditReadOnly = false;

/** `sub` que ya usa la app Android para esta cuenta (verificado en la fase 3). */
const DEFAULT_EXPECTED_SUB = "100305844804415678846";

const argv = process.argv.slice(2);

function argValue(name) {
  const index = argv.indexOf(name);
  return index >= 0 && argv[index + 1] ? argv[index + 1] : "";
}

function readOptional(file) {
  const full = path.join(HERE, file);
  if (!existsSync(full)) return "";
  return readFileSync(full, "utf8").trim();
}

function readSyncBaseUrl() {
  const propsPath = path.resolve(HERE, "..", "..", "local.properties");
  if (!existsSync(propsPath)) return "";
  const line = readFileSync(propsPath, "utf8")
    .split(/\r?\n/)
    .find((entry) => entry.trim().startsWith("cypher.syncBaseUrl="));
  return line ? line.slice(line.indexOf("=") + 1).trim() : "";
}

function base64Url(buffer) {
  return Buffer.from(buffer)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function sha256(buffer) {
  return createHash("sha256").update(buffer).digest();
}

function safeEqual(left, right) {
  // Comparar digests evita filtrar la longitud y mantiene el tiempo constante.
  return timingSafeEqual(sha256(String(left)), sha256(String(right)));
}

function decodeJwtPayload(token) {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("El ID token no tiene tres segmentos.");
  return JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"));
}

function stablePayload(payload) {
  const entries = Object.entries(payload || {})
    .filter(([key]) => key !== "")
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([key, value]) => `${key}=${String(value)}`);
  return entries.join("\u001f");
}

function recordLine(record) {
  return [
    record.kind,
    record.id,
    record.updatedAt,
    record.deleted === true ? "1" : "0",
    stablePayload(record.payload),
  ].join("|");
}

function digestRecords(records) {
  const lines = records.map(recordLine).sort();
  return sha256(lines.join("\n")).toString("hex");
}

/* ------------------------------------------------------------------ */
/* Servidor de callback                                               */
/* ------------------------------------------------------------------ */

/**
 * Levanta el loopback antes de construir la URL y expone `ready` (puerto) y `code`.
 * Solo escucha en 127.0.0.1, atiende un callback y se cierra.
 */
function startCallbackServer(expectedState, timeoutMs) {
  let settled = false;
  let timer = null;
  let resolveCode;
  let rejectCode;

  const codePromise = new Promise((resolve, reject) => {
    resolveCode = resolve;
    rejectCode = reject;
  });
  // Un consumidor puede suscribirse unos milisegundos tarde (el callback llega por
  // red). Este catch evita que Node lo trate como rechazo no gestionado.
  codePromise.catch(() => {});

  const server = createServer((request, response) => {
    const url = new URL(request.url ?? "/", "http://127.0.0.1");
    const reply = (status, message) => {
      response
        .writeHead(status, { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" })
        .end(`<!doctype html><meta charset="utf-8"><p>${message}</p>`);
    };

    if (url.pathname !== CALLBACK_PATH) {
      reply(404, "Ruta desconocida.");
      return;
    }

    const error = url.searchParams.get("error");
    if (error) {
      reply(400, "Google devolvió un error. Puedes cerrar esta ventana.");
      finish(new Error(`autorización rechazada (${error})`));
      return;
    }

    const returnedState = url.searchParams.get("state") ?? "";
    if (!safeEqual(returnedState, expectedState)) {
      reply(400, "state inválido: se descarta esta respuesta.");
      finish(new Error("state inválido en el callback"));
      return;
    }

    const code = url.searchParams.get("code") ?? "";
    if (code === "") {
      reply(400, "Respuesta sin código.");
      finish(new Error("callback sin code"));
      return;
    }

    reply(200, "Autorización recibida. Puedes cerrar esta ventana y volver a la terminal.");
    finish(null, code);
  });

  function finish(error, code) {
    if (settled) return;
    settled = true;
    if (timer) clearTimeout(timer);
    server.close();
    if (error) rejectCode(error);
    else resolveCode(code);
  }

  const ready = new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      resolve(typeof address === "object" && address ? address.port : 0);
    });
  });

  timer = setTimeout(() => finish(new Error("tiempo de espera agotado esperando el callback")), timeoutMs);

  return {
    ready,
    code: codePromise,
    close() {
      if (settled) return;
      settled = true;
      if (timer) clearTimeout(timer);
      server.close();
    },
  };
}

/* ------------------------------------------------------------------ */
/* Self test: criptografía, digest y callback, sin tocar la red        */
/* ------------------------------------------------------------------ */

async function runSelfTest() {
  const results = [];
  const check = (name, condition) => results.push({ name, ok: Boolean(condition) });

  const verifier = base64Url(randomBytes(64));
  const challenge = base64Url(sha256(verifier));
  check("PKCE: challenge = SHA256(verifier), 43 chars", challenge.length === 43 && challenge !== verifier);
  check("PKCE: base64url sin '=' ni '+' ni '/'", !/[=+/]/.test(challenge));
  check("state: 32 bytes aleatorios", Buffer.from(base64Url(randomBytes(32)), "base64url").length === 32);
  check("state: igualdad correcta", safeEqual("abc", "abc") && !safeEqual("abc", "abd"));
  check("state: longitudes distintas no lanzan", !safeEqual("a", "aaaaaaaaaaaaaaaaaaaa"));

  const nonce = base64Url(randomBytes(32));
  check("nonce: acepta el mismo", safeEqual(nonce, nonce));
  check("nonce: rechaza otro", !safeEqual(nonce, nonce + "x"));

  const records = [
    { kind: "PLAYLIST", id: "p1", updatedAt: 10, deleted: false, payload: { name: "A", b: "2" } },
    { kind: "FAVORITE", id: "t1", updatedAt: 11, deleted: false, payload: { title: "B" } },
  ];
  check("digest: estable ante el orden", digestRecords(records) === digestRecords([...records].reverse()));
  check(
    "digest: cambia con updatedAt",
    digestRecords(records) !== digestRecords([{ ...records[0], updatedAt: 12 }, records[1]])
  );

  // Callback con state incorrecto: debe rechazar y responder 400.
  const bad = startCallbackServer("state-esperado", 5_000);
  const badPort = await bad.ready;
  const badResponse = await fetch(`http://127.0.0.1:${badPort}/callback?code=abc&state=otro`);
  let rejected = false;
  await bad.code.then(
    () => {},
    () => {
      rejected = true;
    }
  );
  check("callback: state incorrecto responde 400", badResponse.status === 400);
  check("callback: state incorrecto rechaza el flujo", rejected);
  bad.close();

  // Callback correcto: devuelve el código una sola vez.
  const good = startCallbackServer("state-bueno", 5_000);
  const goodPort = await good.ready;
  const goodResponse = await fetch(`http://127.0.0.1:${goodPort}/callback?code=codigo&state=state-bueno`);
  const received = await good.code;
  check("callback: state correcto responde 200", goodResponse.status === 200);
  check("callback: state correcto entrega el code", received === "codigo");

  const sample = new URL(
    "https://accounts.google.com/o/oauth2/v2/auth?client_id=x&response_type=code&redirect_uri=y&state=s"
  );
  const page = launchPage(sample);
  const pageHtml = readFileSync(page, "utf8");
  check("pagina: conserva response_type", pageHtml.includes("response_type=code"));
  check("pagina: conserva redirect_uri y state", pageHtml.includes("redirect_uri=y") && pageHtml.includes("state=s"));
  check("pagina: ampersands escapados", !/&(?!amp;)/.test(pageHtml));
  discardPage(page);

  for (const result of results) console.log(`${result.ok ? "PASS" : "FAIL"}  ${result.name}`);
  const failed = results.filter((result) => !result.ok).length;
  console.log(`\n${results.length - failed}/${results.length} comprobaciones correctas`);
  process.exit(failed === 0 ? 0 : 1);
}

/* ------------------------------------------------------------------ */
/* Flujo OAuth                                                        */
/* ------------------------------------------------------------------ */

function usage() {
  console.log(`Cypher · auditoría con token Google real

  node tools/oauth-audit/audit.mjs --self-test   comprobaciones locales sin red
  node tools/oauth-audit/audit.mjs --help        esta ayuda
  node tools/oauth-audit/audit.mjs               flujo OAuth + auditoría HTTP

Opciones:
  --client-id <id>      client ID del cliente Desktop (o .client_id)
  --base-url <url>      base del backend (por defecto cypher.syncBaseUrl)
  --expected-sub <sub>  sub esperado (por defecto ${DEFAULT_EXPECTED_SUB})
  --no-open             no intentar abrir el navegador
  --read-only           no envía ningún push (primera verificación tras desplegar)
  --timeout <segundos>  espera máxima del callback (por defecto 300)`);
}

/**
 * Página local que redirige a la URL de autorización.
 *
 * Abrir la URL directamente es frágil: en Windows los lanzadores pueden cortar la
 * query en el primer `&` y Google responde «Required parameter is missing:
 * response_type». Un fichero .html no lleva query string en la línea de comandos,
 * así que la URL llega intacta.
 */
function launchPage(authUrl) {
  const file = path.join(tmpdir(), `cypher-oauth-${process.pid}.html`);
  const target = authUrl.toString().replace(/&/g, "&amp;").replace(/"/g, "&quot;");
  writeFileSync(
    file,
    `<!doctype html><meta charset="utf-8"><title>Cypher · Google</title>
<meta http-equiv="refresh" content="0;url=${target}">
<p style="font-family:system-ui;padding:2rem">Abriendo Google… si no ocurre, <a href="${target}">continúa aquí</a>.</p>
`
  );
  return file;
}

function discardPage(file) {
  try {
    unlinkSync(file);
  } catch {
    /* ya no existe */
  }
}

function openBrowser(fileOrUrl) {
  try {
    if (process.platform === "win32") {
      execFile("rundll32.exe", ["url.dll,FileProtocolHandler", fileOrUrl], { windowsHide: true }, () => {});
    } else if (process.platform === "darwin") {
      execFile("open", [fileOrUrl], () => {});
    } else {
      execFile("xdg-open", [fileOrUrl], () => {});
    }
  } catch {
    // Si no se puede abrir, la URL ya está impresa.
  }
}

async function runOAuth(clientId, clientSecret, expectedSub, timeoutMs, noOpen) {
  const state = base64Url(randomBytes(32));
  const nonce = base64Url(randomBytes(32));
  const verifier = base64Url(randomBytes(64));
  const challenge = base64Url(sha256(verifier));

  const callback = startCallbackServer(state, timeoutMs);
  const port = await callback.ready;
  if (!port) throw new Error("no se pudo abrir el servidor loopback");
  const redirectUri = `http://127.0.0.1:${port}${CALLBACK_PATH}`;

  const authUrl = new URL(AUTH_ENDPOINT);
  authUrl.searchParams.set("client_id", clientId);
  authUrl.searchParams.set("redirect_uri", redirectUri);
  authUrl.searchParams.set("response_type", "code");
  authUrl.searchParams.set("scope", SCOPES);
  authUrl.searchParams.set("state", state);
  authUrl.searchParams.set("nonce", nonce);
  authUrl.searchParams.set("code_challenge", challenge);
  authUrl.searchParams.set("code_challenge_method", "S256");
  authUrl.searchParams.set("prompt", "select_account");

  console.log("Abre esta URL y elige la MISMA cuenta de Google que usas en Cypher:\n");
  console.log(`  ${authUrl.toString()}\n`);
  const page = launchPage(authUrl);
  if (!noOpen) openBrowser(page);
  console.log("Se abre una pestaña local que redirige a Google. No cierres la terminal.\n");

  let code;
  try {
    code = await callback.code;
  } catch (error) {
    callback.close();
    discardPage(page);
    throw error;
  }
  callback.close();
  discardPage(page);

  const form = new URLSearchParams({
    client_id: clientId,
    code,
    code_verifier: verifier,
    grant_type: "authorization_code",
    redirect_uri: redirectUri,
  });
  if (clientSecret) form.set("client_secret", clientSecret);

  const response = await fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: form.toString(),
  });
  const text = await response.text();
  if (!response.ok) {
    let error = "token_exchange_failed";
    try {
      error = JSON.parse(text).error ?? error;
    } catch {
      /* sin cuerpo JSON */
    }
    if (error === "invalid_request" || /client_secret/i.test(text)) {
      throw new Error(
        "Google exige client_secret para este cliente. Crea tools/oauth-audit/.client_secret " +
          "con el secreto (nunca lo pegues en el chat) y vuelve a ejecutar."
      );
    }
    throw new Error(`el canje del código falló (${error})`);
  }

  let idToken;
  try {
    idToken = JSON.parse(text).id_token;
  } catch {
    throw new Error("respuesta del canje sin JSON");
  }
  if (typeof idToken !== "string" || idToken === "") {
    throw new Error("Google no devolvió id_token");
  }

  const claims = decodeJwtPayload(idToken);
  const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
  const checks = {
    nonce: typeof claims.nonce === "string" && safeEqual(claims.nonce, nonce),
    audience_single: audiences.length === 1,
    audience_match: audiences.length === 1 && audiences[0] === clientId,
    azp:
      claims.azp === undefined ||
      (typeof claims.azp === "string" && audiences.length === 1 && claims.azp === audiences[0]),
    issuer: claims.iss === "https://accounts.google.com" || claims.iss === "accounts.google.com",
    not_expired: typeof claims.exp === "number" && claims.exp * 1000 > Date.now(),
    subject_present: typeof claims.sub === "string" && claims.sub !== "",
  };
  const subMatch = typeof claims.sub === "string" && claims.sub === expectedSub;

  return { token: idToken, checks, subMatch };
}

/* ------------------------------------------------------------------ */
/* Auditoría HTTP                                                     */
/* ------------------------------------------------------------------ */

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

async function callApi(baseUrl, pathName, payload, token) {
  if (auditReadOnly && pathName.includes("/push")) {
    throw new Error("modo --read-only: se ha bloqueado una petición de push");
  }
  const response = await fetch(`${baseUrl.replace(/\/+$/, "")}${pathName}`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
    body: JSON.stringify(payload),
  });
  const text = await response.text();
  return { status: response.status, body: safeJson(text) };
}

async function pullAll(baseUrl, token, accountId) {
  let cursor = null;
  let lastCursor = null;
  let pages = 0;
  const records = [];

  while (pages < 200) {
    const result = await callApi(baseUrl, "/v1/sync/pull", { accountId, cursor, limit: 100 }, token);
    if (result.status !== 200) {
      return { status: result.status, error: result.body?.error ?? "unknown", records, pages };
    }
    const page = Array.isArray(result.body?.records) ? result.body.records : [];
    records.push(...page);
    lastCursor = result.body?.cursor ?? cursor;
    cursor = lastCursor;
    pages += 1;
    if (!result.body?.hasMore) break;
  }

  return { status: 200, records, pages, cursor: lastCursor, hash: digestRecords(records) };
}

function printTable(rows) {
  const header = ["Prueba", "Esperado", "Real", "Estado"];
  const widths = header.map((title, index) =>
    Math.max(title.length, ...rows.map((row) => String(row[index]).length))
  );
  const line = (cells) => `| ${cells.map((cell, index) => String(cell).padEnd(widths[index])).join(" | ")} |`;
  console.log(line(header));
  console.log(`| ${widths.map((width) => "-".repeat(width)).join(" | ")} |`);
  rows.forEach((row) => console.log(line(row)));
}

async function runAudit(baseUrl, token, sub, options = {}) {
  const readOnly = options.readOnly === true;
  const rows = [];
  const add = (name, expected, actual, ok) => rows.push([name, expected, actual, ok ? "PASS" : "FAIL"]);

  const healthResponse = await fetch(`${baseUrl.replace(/\/+$/, "")}/health`);
  const healthBody = safeJson(await healthResponse.text());
  add(
    "GET /health",
    "200 ok",
    `${healthResponse.status} ${healthBody?.ok === true ? "ok" : "?"}`,
    healthResponse.status === 200 && healthBody?.ok === true
  );

  const before = await pullAll(baseUrl, token, sub);
  if (before.status !== 200) {
    add("pull con token", "200", `${before.status} ${before.error}`, false);
    printTable(rows);
    if (before.status === 401 && before.error === "invalid_audience") {
      console.log(
        "\nEl token es válido pero el backend todavía no acepta la audiencia del cliente Desktop.\n" +
          "Eso es la Fase B (GOOGLE_ALLOWED_AUDIENCES). No se ha escrito nada."
      );
    }
    return { ok: false, rows, before: null, after: null };
  }
  add("pull con token", "200", `200 (${before.records.length} registros, ${before.pages} página/s)`, true);

  if (readOnly) {
    add("push idempotente (updatedAt=0)", "omitido", "--read-only: sin escrituras", true);
  } else {
  const candidate = [...before.records].sort((a, b) => b.updatedAt - a.updatedAt)[0];
  if (candidate) {
    // Push idempotente: mismo contenido con updatedAt 0, que LWW descarta por antiguo.
    const noop = await callApi(
      baseUrl,
      "/v1/sync/push",
      {
        accountId: sub,
        records: [
          {
            kind: candidate.kind,
            id: candidate.id,
            updatedAt: 0,
            deleted: candidate.deleted === true,
            payload: candidate.payload ?? {},
          },
        ],
      },
      token
    );
    const accepted = Array.isArray(noop.body?.accepted) ? noop.body.accepted : [];
    add(
      "push idempotente (updatedAt=0)",
      "200 y accepted",
      `${noop.status} accepted=${accepted.length}`,
      noop.status === 200 && accepted.length === 1
    );
  } else {
    add("push idempotente (updatedAt=0)", "200 y accepted", "cuenta sin registros: omitido", true);
  }
  }

  const mismatchPull = await callApi(
    baseUrl,
    "/v1/sync/pull",
    { accountId: `${sub}-audit-mismatch`, limit: 10 },
    token
  );
  add(
    "pull con accountId ajeno",
    "403 account_mismatch",
    `${mismatchPull.status} ${mismatchPull.body?.error ?? ""}`,
    mismatchPull.status === 403 && mismatchPull.body?.error === "account_mismatch"
  );

  let pushLeaked = false;
  if (readOnly) {
    add("push con accountId ajeno (kind inválido)", "omitido", "--read-only: sin escrituras", true);
  } else {
    const mismatchPush = await callApi(
      baseUrl,
      "/v1/sync/push",
      { accountId: `${sub}-audit-mismatch`, records: [{ kind: "AUDIT", id: "no-write", updatedAt: 1 }] },
      token
    );
    pushLeaked = mismatchPush.body?.accepted !== undefined;
    add(
      "push con accountId ajeno (kind inválido)",
      "403 y sin accepted",
      `${mismatchPush.status} ${mismatchPush.body?.error ?? ""}`,
      mismatchPush.status === 403 &&
        mismatchPush.body?.error === "account_mismatch" &&
        mismatchPush.body?.accepted === undefined
    );
  }

  add(
    "403 sin datos de sincronización",
    "sin records/accepted",
    `${mismatchPull.body?.records === undefined ? "sin records" : "CON records"} / ${
      readOnly ? "push omitido" : pushLeaked ? "CON accepted" : "sin accepted"
    }`,
    mismatchPull.body?.records === undefined && !pushLeaked
  );

  const after = await pullAll(baseUrl, token, sub);
  add("pull posterior", "200", `${after.status}`, after.status === 200);

  if (after.status === 200) {
    add(
      "integridad · recuento",
      String(before.records.length),
      String(after.records.length),
      before.records.length === after.records.length
    );
    add(
      "integridad · cursor",
      String(before.cursor),
      String(after.cursor),
      String(before.cursor) === String(after.cursor)
    );
    add(
      "integridad · revisión máxima",
      String(before.cursor),
      String(after.cursor),
      String(before.cursor) === String(after.cursor)
    );
    add(
      "integridad · hash de registros",
      "idéntico",
      before.hash === after.hash ? "idéntico" : "DISTINTO",
      before.hash === after.hash
    );
  }

  printTable(rows);
  console.log(`\nDigest antes:  ${before.hash.slice(0, 16)}…`);
  if (after.status === 200) console.log(`Digest después: ${after.hash.slice(0, 16)}…`);

  return { ok: rows.every((row) => row[3] === "PASS"), rows, before, after };
}

/* ------------------------------------------------------------------ */

async function main() {
  if (argv.includes("--help") || argv.includes("-h")) {
    usage();
    return;
  }
  if (argv.includes("--self-test")) {
    await runSelfTest();
    return;
  }

  const clientId = argValue("--client-id") || process.env.CYPHER_AUDIT_CLIENT_ID || readOptional(".client_id");
  if (!clientId) {
    console.error("Falta el client ID del cliente Desktop.");
    console.error("Guárdalo en tools/oauth-audit/.client_id (ignorado por git) o pásalo con --client-id.");
    process.exit(2);
  }

  if (clientId.startsWith("GOCSPX-")) {
    console.error(".client_id contiene un client SECRET (GOCSPX-…), no un client ID.");
    console.error("Mueve ese valor a tools/oauth-audit/.client_secret y guarda el client ID en .client_id.");
    process.exit(2);
  }
  if (!clientId.endsWith(".apps.googleusercontent.com")) {
    console.error(`El client ID no tiene el formato esperado (…apps.googleusercontent.com), longitud ${clientId.length}.`);
    console.error("Copia el campo «ID de cliente» del cliente Desktop; el secreto va en .client_secret.");
    process.exit(2);
  }

  const clientSecret = readOptional(".client_secret");
  const expectedSub = argValue("--expected-sub") || DEFAULT_EXPECTED_SUB;
  const baseUrl = argValue("--base-url") || readSyncBaseUrl();
  const timeoutMs = Number(argValue("--timeout") || "300") * 1000;
  const noOpen = argv.includes("--no-open");
  const readOnly = argv.includes("--read-only");
  auditReadOnly = readOnly;

  if (!baseUrl) {
    console.error("No hay base URL: define cypher.syncBaseUrl en local.properties o usa --base-url.");
    process.exit(2);
  }

  console.log("Cypher · auditoría con token Google real");
  console.log(`Backend: ${baseUrl}`);
  console.log(`Cliente: ${clientId.slice(0, 12)}… (Desktop)\n`);

  const { token, checks, subMatch } = await runOAuth(
    clientId,
    clientSecret,
    expectedSub,
    timeoutMs,
    noOpen
  );

  const preflight = [
    ["state validado en el callback", true],
    ["nonce del ID token", checks.nonce],
    ["aud única", checks.audience_single],
    ["aud === client_id", checks.audience_match],
    ["azp coherente", checks.azp],
    ["iss de Google", checks.issuer],
    ["exp vigente", checks.not_expired],
    ["sub presente", checks.subject_present],
    ["sub === sub de Android", subMatch],
  ];
  console.log("Validaciones del token:");
  preflight.forEach(([name, ok]) => console.log(`  ${ok ? "PASS" : "FAIL"}  ${name}`));

  if (!preflight.every(([, ok]) => ok)) {
    console.error("\nEl token no pasa las validaciones locales. No se llama al backend.");
    process.exit(1);
  }

  console.log("");
  const result = await runAudit(baseUrl, token, expectedSub, { readOnly });
  console.log("");
  if (result.ok) {
    console.log(
      readOnly
        ? "Auditoría completa en modo lectura: sin escrituras, todas las comprobaciones en PASS."
        : "Auditoría completa: todas las comprobaciones en PASS."
    );
    console.log("El token no se ha escrito en disco ni en logs; el proceso termina aquí.");
    process.exit(0);
  }
  console.error("Auditoría incompleta: revisa las filas FAIL.");
  process.exit(1);
}

await main();

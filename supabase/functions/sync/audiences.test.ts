/**
 * Tests de la lista cerrada de audiencias.
 *
 *   node --test supabase/functions/sync/audiences.test.ts
 */

import assert from "node:assert/strict";
import { test } from "node:test";
import { AudienceConfigError, resolveAudiences, resolveAuth } from "./audiences.ts";

const WEB = "764777457932-djot894fcm28vs15mhptks3p2cql56o6.apps.googleusercontent.com";
const DESKTOP = "764777457932-goan61rtqc8g3u6t7o7if2pdm6pkkc97.apps.googleusercontent.com";
const ANDROID = "764777457932-op2aasnvnqb9d6mrv9q3dtr2221ek36j.apps.googleusercontent.com";

test("compatibilidad: el secreto web existente basta por sí solo", () => {
  assert.deepEqual(resolveAudiences({ GOOGLE_WEB_CLIENT_ID: WEB }), [WEB]);
});

test("admite web y desktop a la vez", () => {
  const audiences = resolveAudiences({
    GOOGLE_WEB_CLIENT_ID: WEB,
    GOOGLE_DESKTOP_CLIENT_ID: DESKTOP,
  });
  assert.deepEqual(audiences, [WEB, DESKTOP]);
});

test("acepta una lista explícita separada por comas, con espacios y duplicados", () => {
  const audiences = resolveAudiences({
    GOOGLE_ALLOWED_AUDIENCES: ` ${WEB} , ${DESKTOP} ,${WEB} `,
  });
  assert.deepEqual(audiences, [WEB, DESKTOP]);
});

test("la lista explícita se combina con las variables individuales sin repetir", () => {
  const audiences = resolveAudiences({
    GOOGLE_ALLOWED_AUDIENCES: DESKTOP,
    GOOGLE_WEB_CLIENT_ID: WEB,
    GOOGLE_DESKTOP_CLIENT_ID: DESKTOP,
  });
  assert.deepEqual(audiences, [DESKTOP, WEB]);
});

test("sin configuración devuelve lista vacía", () => {
  assert.deepEqual(resolveAudiences({}), []);
  assert.deepEqual(
    resolveAudiences({ GOOGLE_WEB_CLIENT_ID: "   ", GOOGLE_ALLOWED_AUDIENCES: " , " }),
    []
  );
});

test("rechaza una entrada que no es un client ID de Google", () => {
  assert.throws(
    () => resolveAudiences({ GOOGLE_ALLOWED_AUDIENCES: `${WEB},https://ejemplo.com` }),
    AudienceConfigError
  );
  assert.throws(() => resolveAudiences({ GOOGLE_DESKTOP_CLIENT_ID: "123-abc" }), AudienceConfigError);
});

test("el cliente Android se acepta como azp pero no como audiencia", () => {
  const config = resolveAuth({ GOOGLE_WEB_CLIENT_ID: WEB, GOOGLE_ANDROID_CLIENT_ID: ANDROID });
  assert.deepEqual(config.audiences, [WEB]);
  assert.equal(config.androidClientId, ANDROID);
  assert.equal(config.audiences.includes(ANDROID), false);
});

test("sin cliente Android configurado no hay azp alternativo", () => {
  assert.equal(resolveAuth({ GOOGLE_WEB_CLIENT_ID: WEB }).androidClientId, null);
  assert.equal(resolveAuth({ GOOGLE_ANDROID_CLIENT_ID: "  " }).androidClientId, null);
});

test("solo con el cliente Android no hay audiencias", () => {
  const config = resolveAuth({ GOOGLE_ANDROID_CLIENT_ID: ANDROID });
  assert.deepEqual(config.audiences, []);
  assert.equal(config.androidClientId, ANDROID);
});

test("una entrada inválida en el cliente Android falla al arrancar", () => {
  assert.throws(() => resolveAuth({ GOOGLE_ANDROID_CLIENT_ID: "no-es-un-cliente" }), AudienceConfigError);
});

test("rechaza más de 8 audiencias", () => {
  const many = Array.from({ length: 9 }, (_, index) => `764777457932-cliente${index}.apps.googleusercontent.com`);
  assert.throws(() => resolveAudiences({ GOOGLE_ALLOWED_AUDIENCES: many.join(",") }), AudienceConfigError);
});

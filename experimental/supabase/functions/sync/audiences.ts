/**
 * Resolución de los clientes OAuth de Google que este backend reconoce.
 *
 * Hay tres clientes legítimos para el mismo proyecto:
 *
 *   · Web / Android  -> `GOOGLE_WEB_CLIENT_ID`     (`aud` del ID token)
 *   · Desktop        -> `GOOGLE_DESKTOP_CLIENT_ID` (`aud` del cliente Desktop)
 *   · Android app    -> `GOOGLE_ANDROID_CLIENT_ID` (solo puede aparecer como `azp`)
 *
 * La distinción importa: el token que emite Credential Manager en Android lleva
 * `aud` = cliente web (el `serverClientId`) y `azp` = cliente Android, que es quien
 * pidió el token. Por eso el Android no es una audiencia aceptable, sino una parte
 * autorizada (`azp`) aceptable.
 *
 * `GOOGLE_ALLOWED_AUDIENCES` permite declarar la lista de audiencias a mano (valores
 * separados por comas). Nunca se acepta una audiencia fuera de la lista: no hay
 * comodines ni comparaciones parciales.
 *
 * Este módulo no depende de Deno para poder testearse con `node --test`.
 */

const GOOGLE_CLIENT_SUFFIX = ".apps.googleusercontent.com";
const MAX_AUDIENCES = 8;
const MAX_AUDIENCE_LENGTH = 200;

export class AudienceConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AudienceConfigError";
  }
}

export type AudienceEnv = {
  GOOGLE_WEB_CLIENT_ID?: string | undefined;
  GOOGLE_DESKTOP_CLIENT_ID?: string | undefined;
  GOOGLE_ANDROID_CLIENT_ID?: string | undefined;
  GOOGLE_ALLOWED_AUDIENCES?: string | undefined;
};

export type AuthConfig = {
  /** Valores aceptados como `aud`, en orden de declaración y sin duplicados. */
  audiences: string[];
  /** Cliente que puede aparecer como `azp` además de la propia audiencia. */
  androidClientId: string | null;
};

function splitList(raw: string | undefined): string[] {
  if (typeof raw !== "string") return [];
  return raw
    .split(",")
    .map((entry) => entry.trim())
    .filter((entry) => entry !== "");
}

/** Valida el formato de un client ID de Google; lanza si no lo parece. */
function normalizeClientId(raw: string | undefined, label: string): string | null {
  if (typeof raw !== "string") return null;
  const value = raw.trim();
  if (value === "") return null;
  if (value.length > MAX_AUDIENCE_LENGTH) {
    throw new AudienceConfigError(`El ${label} configurado es demasiado largo.`);
  }
  if (!value.endsWith(GOOGLE_CLIENT_SUFFIX)) {
    throw new AudienceConfigError(
      `El ${label} configurado no es un client ID de Google (debe terminar en ${GOOGLE_CLIENT_SUFFIX}).`
    );
  }
  return value;
}

/**
 * Devuelve la configuración completa: audiencias permitidas y parte autorizada
 * alternativa. Lanza `AudienceConfigError` si alguna entrada no parece un client ID
 * de Google: es preferible fallar al arrancar (500 `server_misconfigured`) que
 * aceptar una lista con una errata que dejaría fuera a un cliente legítimo.
 */
export function resolveAuth(env: AudienceEnv): AuthConfig {
  const declared = [
    ...splitList(env.GOOGLE_ALLOWED_AUDIENCES),
    ...splitList(env.GOOGLE_WEB_CLIENT_ID),
    ...splitList(env.GOOGLE_DESKTOP_CLIENT_ID),
  ];

  const seen = new Set<string>();
  const audiences: string[] = [];
  for (const value of declared) {
    const normalized = normalizeClientId(value, "una audiencia");
    if (normalized === null || seen.has(normalized)) continue;
    seen.add(normalized);
    audiences.push(normalized);
  }

  if (audiences.length > MAX_AUDIENCES) {
    throw new AudienceConfigError(`No se admiten más de ${MAX_AUDIENCES} audiencias.`);
  }

  return {
    audiences,
    androidClientId: normalizeClientId(env.GOOGLE_ANDROID_CLIENT_ID, "cliente Android"),
  };
}

/** Atajo para quien solo necesita las audiencias. */
export function resolveAudiences(env: AudienceEnv): string[] {
  return resolveAuth(env).audiences;
}

// Renders the AGSL_LIQUID_GLASS shader from LiquidGlass.kt on the CPU.
//
// There is no Android device here, so this is not a screenshot: it is the same math
// (same uniforms, same constants, same order of operations) evaluated per pixel and
// written to PNG. The three fallbacks (legacy radial pools and the reduced-motion
// variant) are rendered too, from the same values their Compose code uses.
//
// node tools/liquid-glass/render.mjs
import { deflateSync } from "node:zlib";
import { writeFileSync, mkdirSync } from "node:fs";

const W = 450;
const H = 780;

// ---------------------------------------------------------------- PNG writer
const crcTable = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
})();

function crc32(buffer) {
  let c = 0xffffffff;
  for (const byte of buffer) c = crcTable[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, crc]);
}

function writePng(path, width, height, pixels) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // RGBA
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0; // filter: none
    pixels.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  }
  writeFileSync(
    path,
    Buffer.concat([
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      chunk("IHDR", ihdr),
      chunk("IDAT", deflateSync(raw, { level: 9 })),
      chunk("IEND", Buffer.alloc(0)),
    ])
  );
}

// ------------------------------------------------------------------- helpers
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
const mix = (a, b, t) => a + (b - a) * t;
const fract = (v) => v - Math.floor(v);
/** Same behaviour as GLSL/AGSL, including edge0 > edge1 (reversed ramp). */
function smoothstep(edge0, edge1, x) {
  const t = clamp((x - edge0) / (edge1 - edge0), 0, 1);
  return t * t * (3 - 2 * t);
}

function colorOf(argb) {
  return {
    r: ((argb >> 16) & 0xff) / 255,
    g: ((argb >> 8) & 0xff) / 255,
    b: (argb & 0xff) / 255,
  };
}

// Colores del tema (ui/theme/Color.kt).
const IosDarkBackground = colorOf(0x0d0b11);

// ------------------------------------------------------------- AGSL shader
/** Direct port of AGSL_LIQUID_GLASS from ui/components/LiquidGlass.kt. */
function shade(x, y, { c1, c2, c3, time, speed, distortion, opacity }) {
  const uvx = x / W;
  const uvy = y / H;
  const aspect = W / H;
  const px = uvx * aspect;
  const py = uvy;

  const t = time * speed * 0.32;

  const warpFactor = clamp(distortion, 0, 2) * 0.32;
  const flowX =
    (Math.sin(py * 2.2 + t * 0.65) * 0.6 + Math.cos(px * 1.5 - t * 0.45) * 0.4) * warpFactor;
  const flowY =
    (Math.cos(px * 2.2 + t * 0.55) * 0.6 + Math.sin(py * 1.5 + t * 0.35) * 0.4) * warpFactor;
  const warpedX = px + flowX;
  const warpedY = py + flowY;

  const core1X = 0.35 * aspect + Math.sin(t * 0.52) * 0.22;
  const core1Y = 0.32 + Math.cos(t * 0.42) * 0.22;
  const w1 = smoothstep(0.92, 0.05, Math.hypot(warpedX - core1X, warpedY - core1Y));

  const core2X = 0.68 * aspect + Math.cos(t * 0.44) * 0.24;
  const core2Y = 0.68 + Math.sin(t * 0.54) * 0.24;
  const w2 = smoothstep(0.88, 0.05, Math.hypot(warpedX - core2X, warpedY - core2Y));

  const core3X = 0.42 * aspect + Math.sin(t * 0.62 + 1.2) * 0.2;
  const core3Y = 0.8 + Math.cos(t * 0.48 + 0.9) * 0.2;
  const w3 = smoothstep(0.72, 0.05, Math.hypot(warpedX - core3X, warpedY - core3Y));

  let r = 0.05;
  let g = 0.05;
  let b = 0.07;

  const a1 = clamp(w1 * 1.25, 0, 1);
  r = mix(r, c1.r * 0.92, a1);
  g = mix(g, c1.g * 0.92, a1);
  b = mix(b, c1.b * 0.92, a1);

  const a2 = clamp(w2 * 1.15, 0, 1);
  r = mix(r, c2.r * 0.88, a2);
  g = mix(g, c2.g * 0.88, a2);
  b = mix(b, c2.b * 0.88, a2);

  r += c3.r * (w3 * 0.7);
  g += c3.g * (w3 * 0.7);
  b += c3.b * (w3 * 0.7);

  const dispersion = smoothstep(0.32, 0, Math.abs(w1 - w2)) * 0.18 * clamp(distortion, 0.1, 2);
  r += dispersion * c1.r;
  b += dispersion * c3.b;
  g += dispersion * 0.5 * c2.g;

  const vignette = smoothstep(1.35, 0.35, Math.hypot((uvx - 0.5) * 1.1, (uvy - 0.5) * 1.25));
  const v = mix(0.82, 1.05, vignette);
  r *= v;
  g *= v;
  b *= v;

  const grain = (fract(Math.sin(x * 12.9898 + y * 78.233) * 43758.5453) - 0.5) * 0.022;
  r += grain;
  g += grain;
  b += grain;

  // The shader outputs half4(col, iOpacity) and is drawn over IosDarkBackground.
  const out = { r: clamp(r, 0, 1), g: clamp(g, 0, 1), b: clamp(b, 0, 1) };
  return {
    r: mix(IosDarkBackground.r, out.r, opacity),
    g: mix(IosDarkBackground.g, out.g, opacity),
    b: mix(IosDarkBackground.b, out.b, opacity),
  };
}

// ---------------------------------------------- legacy / reduced-motion pools
/** StaticLiquidGlassRenderer & LegacyLiquidGlassRenderer, evaluated per pixel. */
function pools(x, y, { c1, c2, c3, opacity, drift }) {
  const radius = Math.max(W, H) * 0.95;
  const layers = [
    { color: c1, alpha: opacity * 0.55, cx: W * 0.18, cy: H * 0.1 },
    { color: c2, alpha: opacity * 0.45, cx: W * 0.95, cy: H * 0.55 },
    { color: c3, alpha: opacity * 0.35, cx: W * 0.32, cy: H * 0.96 },
  ];
  if (drift !== null) {
    // Drifting variant (API 29-32): the two first pools move with sin(time / 100).
    layers[0].cx = W * (0.1 + 0.12 * drift);
    layers[0].cy = H * 0.08;
    layers[1].cx = W * 0.95;
    layers[1].cy = H * (0.5 + 0.18 * (1 - drift));
    layers[2].cx = W * (0.3 + 0.1 * (1 - drift));
  }
  let r = IosDarkBackground.r;
  let g = IosDarkBackground.g;
  let b = IosDarkBackground.b;
  for (const layer of layers) {
    const distance = Math.hypot(x - layer.cx, y - layer.cy);
    if (distance >= radius) continue;
    const alpha = layer.alpha * (1 - distance / radius);
    r = mix(r, layer.color.r, alpha);
    g = mix(g, layer.color.g, alpha);
    b = mix(b, layer.color.b, alpha);
  }
  return { r, g, b };
}

// ----------------------------------------------------------------- rendering
function render(path, fn) {
  const pixels = Buffer.alloc(W * H * 4);
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const c = fn(x + 0.5, y + 0.5);
      const i = (y * W + x) * 4;
      pixels[i] = Math.round(clamp(c.r, 0, 1) * 255);
      pixels[i + 1] = Math.round(clamp(c.g, 0, 1) * 255);
      pixels[i + 2] = Math.round(clamp(c.b, 0, 1) * 255);
      pixels[i + 3] = 255;
    }
  }
  writePng(path, W, H, pixels);
  console.log("render: " + path);
}

const out = "docs/liquid-glass/capturas";
mkdirSync(out, { recursive: true });

// ThemePreferences default custom palette (lavanda Cypher).
const lavender = { c1: colorOf(0x9d7bb0), c2: colorOf(0x7a588e), c3: colorOf(0x352244) };
// LiquidGlassConfig defaults: rojo/violeta/cian de Apple Music.
const apple = { c1: colorOf(0xfa243c), c2: colorOf(0x651fff), c3: colorOf(0x00e5ff) };

render(`${out}/01-shader-lavanda-t12.png`, (x, y) =>
  shade(x, y, { ...lavender, time: 12, speed: 0.6, distortion: 0.7, opacity: 0.65 })
);
render(`${out}/02-shader-paleta-apple-t40.png`, (x, y) =>
  shade(x, y, { ...apple, time: 40, speed: 0.6, distortion: 0.7, opacity: 0.65 })
);
render(`${out}/03-shader-opacidad-maxima.png`, (x, y) =>
  shade(x, y, { ...lavender, time: 12, speed: 0.6, distortion: 0.7, opacity: 1 })
);
render(`${out}/04-fallback-api29-32.png`, (x, y) =>
  pools(x, y, { ...lavender, opacity: 0.65, drift: Math.sin(0.5) * 0.5 + 0.5 })
);
render(`${out}/05-reduced-motion.png`, (x, y) =>
  pools(x, y, { ...lavender, opacity: 0.65, drift: null })
);

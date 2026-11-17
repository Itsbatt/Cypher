# Parámetros visuales compartidos: Android ↔ Desktop

Una sola lista de valores para que las dos plataformas se vean como el mismo producto. Android
usa AGSL; Desktop usa SkSL. Los nombres de los parámetros son los mismos a propósito.

## 1. Parámetros del cristal

| Parámetro | Qué controla | Rango útil | Android | Desktop |
| --- | --- | --- | --- | --- |
| `opacity` | Cuerpo del vidrio: cuánto tapa el fondo | 0.50 – 0.95 | `glassAlpha` | `cypherGlass(opacity = …)` |
| `density` | Cuánto se acerca a placa opaca (1 = menú, 0.3 = lente) | 0 – 1 | `glassDensity` | `cypherGlass(density = …)` |
| `radius` | Radio de la SDF, en dp | 14 – 28 | `cornerRadius` | `cornerRadiusDp` |
| `rim` | Intensidad del borde Fresnel | 0.4 – 1.2 | `rimStrength` | `GlassSpec.RIM` × `strength` |
| `rimWidth` | Grosor del borde, en px de shader | 1.5 – 3.0 | `rimWidth` | `GlassSpec.RIM_WIDTH` |
| `refraction` | Desplazamiento de la muestra del fondo | 0.004 – 0.012 | `refraction` | `GlassSpec.REFRACTION` |
| `dispersion` | Separación de canales (borde arcoíris) | 0.2 – 0.6 | `dispersion` | `GlassSpec.DISPERSION` |
| `specular` | Brillo fijo arriba-izquierda | 0.02 – 0.08 | `specular` | `GlassSpec.SPECULAR` |
| `tint` | Tinte del acento sobre el vidrio | 0.03 – 0.10 | `glassTint` | `GlassSpec.TINT` |
| `grain` | Grano contra bandas | 0.008 – 0.02 | `grain` | `GlassSpec.GRAIN` |
| `sheenSpeed` | Velocidad del brillo que recorre la superficie | 0.010 – 0.020 | `sheenSpeed` | dentro de `GLASS_SKSL` |

## 2. Valores por superficie (los dos lados)

| Superficie | radius | opacity | density |
| --- | --- | --- | --- |
| Rail / barra de navegación | 18 | 0.58 | 0.30 |
| Panel de playlists / menú desplegable | 20 | 0.92 | 1.00 |
| Panel derecho (letras, cola) | 20 | 0.70 | 0.45 |
| Dock / reproductor flotante | 22 | 0.80 | 0.50 |
| Diálogo modal | 26 | 0.92 | 0.70 |
| Tarjetas de contenido | — | sin cristal | — |

Regla: el cristal aparece donde hay una **superficie que flota** sobre contenido. El contenido en
sí (tarjetas, listas, cabecera) es plano. Si todo es cristal, nada destaca.

## 3. Fondo

| Parámetro | Android | Desktop |
| --- | --- | --- |
| Modos | ORIGINAL / GLASS / IMAGE | ORIGINAL / GLASS / IMAGE |
| Desenfoque | 0–40 px | 0–40 dp |
| Oscurecido | 0–0.8 | 0–0.8 |
| Opacidad de la imagen | 0.2–1 | 0.2–1 |
| Ajuste | CROP / FIT / FILL | CROP / FIT / FILL |

## 4. Movimiento

| Parámetro | Valor | Nota |
| --- | --- | --- |
| Duración de apertura de panel | 180 ms | `fadeIn + slideInHorizontally(-18)` |
| Curva | `FastOutSlowIn` | igual en las dos plataformas |
| Reloj del shader | `withFrameNanos` | sin recomposición |
| `sheen` | 1 ciclo ≈ 80 s | no distrae |

## 5. Degradación

| Situación | Android | Desktop |
| --- | --- | --- |
| Sin soporte de shader | cristal con degradados + borde | `drawDegradedGlass`, mismo borde |
| Coste alto | — | media > 12 ms en 90 muestras → degradado |
| Motivo visible | Ajustes | Ajustes, tarjeta "Liquid Glass" |

En ningún caso se apaga en silencio.

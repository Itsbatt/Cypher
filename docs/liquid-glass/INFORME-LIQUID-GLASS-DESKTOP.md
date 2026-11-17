# Liquid Glass ASG en Cypher Desktop

Fecha: 16 de noviembre de 2026 · Estado: implementado y medido, con una decisión pendiente (§8).

Este documento cubre el efecto de cristal. El rediseño de la interfaz está en
[`../fase-c/REDISENO-VISUAL-DESKTOP.md`](../fase-c/REDISENO-VISUAL-DESKTOP.md) y las portadas en
[`../fase-c/AUDITORIA-PORTADAS.md`](../fase-c/AUDITORIA-PORTADAS.md).

---

## 1. De dónde viene el efecto

En Android el cristal es un AGSL (`RuntimeShader`) que corre en la GPU. Compose Desktop **no**
tiene `RuntimeShader`, pero sí tiene Skia, y Skia acepta **SkSL**, que es el mismo lenguaje con
otro nombre: `half4 main(float2 fragCoord)` en lugar de `half4 main(vec2 fragCoord)`, y
`uniform` explícito en vez de `uniform shader`.

Por eso el efecto no es una "aproximación con transparencia": se traduce el shader y se ejecuta
de verdad. Lo que **no** se puede replicar tal cual es el *backdrop* del sistema:

| Android | Escritorio | Consecuencia |
| --- | --- | --- |
| `RenderEffect.createRuntimeShaderEffect` sobre la vista de fondo: ve la pantalla real, incluida la ventana de atrás | Compose no entrega un snapshot del escritorio; la ventana es opaca por defecto | El cristal refracta una **textura de fondo de la app** (320×180), no el escritorio. Ver §8 |

## 2. Arquitectura

`desktop/src/main/kotlin/com/cypher/desktop/ui/glass/GlassSurface.kt`

| Pieza | Función |
| --- | --- |
| `GLASS_SKSL` | El shader: SDF de rectángulo redondeado, normal por gradiente del SDF, refracción, dispersión por canal, Fresnel teñido con el acento, especular, *sheen* animado, viñeta y grano |
| `glassEffect` | Compila el `RuntimeEffect` **una sola vez por proceso** (antes se compilaba por panel y por fotograma) |
| `GlassBackdrop` | Textura 320×180: procedural para ORIGINAL/GLASS, miniatura de la imagen para IMAGE |
| `LocalGlassBackdrop` | Una textura por ventana; todos los paneles muestrean la misma |
| `rememberGlassTime()` | Reloj con `withFrameNanos`: anima sin recomponer |
| `Modifier.cypherGlass(accent, cornerRadiusDp, opacity, density, strength)` | Superficie de cristal con su propio origen y tamaño |
| `GlassSupport` | Estado del soporte: `degradedReason`, motivo legible y contador de coste |
| `GlassBackdropProvider(mode, image, accent) { … }` | Envuelve el shell en `App.kt` |

`density` no es un adorno: los menús necesitan más cuerpo que el reproductor para que el texto se
lea sobre contenido ocupado. Ver la guía de parámetros en
[`PARAMETROS-COMPARTIDOS.md`](PARAMETROS-COMPARTIDOS.md).

## 3. Dónde se aplica

| Superficie | Radio | opacity | density | Motivo |
| --- | --- | --- | --- | --- |
| Rail lateral | 18 dp | 0.58 | 0.30 | Casi lente: es navegación, no contenido |
| Panel de playlists | 20 dp | 0.92 | 1.00 | Menú sobre el shell: primero legibilidad |
| Panel derecho (LETRAS/COLA) | 20 dp | 0.70 | 0.45 | Contenido con texto largo |
| Dock flotante | 22 dp | 0.80 | 0.50 | Tiene que leerse sobre portadas grandes |
| Diálogo de perfil | 26 dp | 0.92 | 0.70 | Es modal y pide una decisión |

No hay cristal en el contenido: tarjetas, listas y cabecera son superficies planas. El efecto
marca jerarquía, no decora todo.

## 4. Rendimiento medido

Benchmark de ventana real (`--glass-bench`, GPU, contando `withFrameNanos`):

```
frames=179 size=1440x900  avg=5.6ms p50=5.6ms p95=7.1ms max=17.3ms fps=179.5 shader=on
frames=179 size=1920x1080 avg=5.7ms p50=5.6ms p95=6.9ms max=13.5ms fps=175.6 shader=on
frames=179 size=1120x720  avg=5.6ms p50=5.6ms p95=6.6ms max=14.4ms fps=177.9 shader=on
frames=179 size=1440x900  avg=5.8ms p50=5.6ms p95=6.8ms max=30.0ms fps=173.2 shader=on  (fondo IMAGE)
```

Lectura: 5.6–5.8 ms por fotograma con shader activo, p95 por debajo de 7.1 ms y sin degradación
en ningún tamaño ni con imagen de fondo. Los picos de 13–30 ms son el primer fotograma de cada
captura (compilación y subida de textura), no un patrón sostenido.

**No** se usa `ImageComposeScene` como métrica: rasteriza por CPU y daba 651–1349 ms por fotograma,
un número sin relación con la GPU.

## 5. Degradación

Si la GPU no puede compilar el shader, o si el coste medio por panel supera 12 ms en una ventana
de 90 muestras, `GlassSupport` cae a `drawDegradedGlass`: degradados en capas que conservan el
borde luminoso, el brillo especular y el radio. Se guarda un motivo legible y Ajustes lo muestra
en la tarjeta "Liquid Glass".

Nunca se apaga en silencio: `reason` vacío = shader activo; cualquier otro valor se ve en la
interfaz.

## 6. Ajustes

Tarjeta **Liquid Glass** en APARIENCIA: estado (`Activo · shader SkSL en la GPU` o el motivo de la
degradación) y una frase que explica qué superficies lo usan.

## 7. Comparación con Android

| Rasgo | Android (AGSL) | Desktop (SkSL) |
| --- | --- | --- |
| Refracción | Sí, del fondo real | Sí, de la textura de fondo |
| Dispersión por canal | Sí | Sí |
| Borde luminoso (Fresnel) | Sí | Sí, teñido con el acento |
| Especular fijo + sheen | Sí | Sí |
| Grano antialias de bandas | Sí | Sí |
| Desenfoque del fondo | `RenderEffect` | Muestreo bilineal de la textura 320×180 |
| Radio y densidad por superficie | Sí | Sí |
| Animación sin recomposición | Sí | `withFrameNanos` |

## 8. Límite real: transparencia de ventana

El dock ya no tiene barra detrás: ocupa solo su superficie y el contenido pasa por debajo
(capturas `07-reproductor-flotante.png` y `11-ventana-estrecha.png`). Lo que **no** está hecho es
que la ventana sea transparente al escritorio. Opciones, con su coste:

1. **Ventana sin decorar y transparente** (`undecorated = true, transparent = true`).
   Se ve el escritorio detrás del dock. Se pierden la barra de título nativa, el arrastre del
   sistema, el redimensionado por bordes y la sombra de Windows: habría que reimplementarlos a
   mano (hit-test, snap, maximizar, DPI). Es un cambio de arquitectura de ventana, reversible,
   pero grande.
2. **Ventana normal con fondo propio** (lo actual): sin barra negra detrás del dock, pero el
   fondo de la ventana sigue siendo el de Cypher.
3. **Ventana por capas de Win32** (`WS_EX_LAYERED` + `UpdateLayeredWindow`), manteniendo la
   decoración. Requiere JNI o una librería nativa; no está en el stack actual.

No se ha elegido ninguna: la 1 cambia la arquitectura de ventanas y la 3 añade una dependencia
nativa. Queda a la espera de decisión.

## 9. Archivos

- `desktop/src/main/kotlin/com/cypher/desktop/ui/glass/GlassSurface.kt` (shader, backdrop, modifier)
- `desktop/src/main/kotlin/com/cypher/desktop/ui/App.kt` (provider y los cuatro usos)
- `desktop/src/main/kotlin/com/cypher/desktop/GlassBench.kt` (`--glass-bench [frames] [ancho] [alto] [modo] [imagen]`)
- `desktop/src/test/kotlin/com/cypher/desktop/GlassSurfaceTest.kt`
- `docs/liquid-glass/PARAMETROS-COMPARTIDOS.md`

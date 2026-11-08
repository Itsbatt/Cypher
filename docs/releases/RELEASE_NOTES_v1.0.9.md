# Cypher 1.0.9 (versionCode 12) — notas locales

> **No publicada.** Esta versión está compilada, instalada y verificada en el
> dispositivo, y guardada localmente. No se ha creado ninguna release, no se ha
> subido ningún APK y no se ha anunciado nada. La publicación queda pendiente de
> autorización expresa.

## Compartir fragmentos de letras

- Nuevo botón **Compartir** en la hoja de letras. Activa la selección y permite
  elegir un **fragmento de hasta 6 líneas consecutivas** (nunca líneas sueltas):
  el primer toque marca el inicio y el segundo el final, y el bloque se ajusta
  solo para no pasar de 6. Funciona con letras sincronizadas y planas.
- **Tarjeta CYPHER** generada con el canvas nativo a 1080 px reales, no una
  captura de pantalla reescalada:
  - **Minimal**: fondo oscuro, filete de acento, carátula y tipografía serif.
  - **Portada**: carátula a sangre, desenfoque suave intencionado y scrim para
    que el texto siempre se lea.
  - **Tipografía**: composición editorial centrada, comilla de gran formato.
- Formatos **Historia** (1080×1920) y **Cuadrado** (1080×1080).
- Previsualización WYSIWYG: es el mismo renderizador a media resolución, así que
  lo que se ve es exactamente lo que se comparte.
- Exportación a `cache/shared` y envío con el **menú nativo de Android**
  (`ACTION_SEND` + FileProvider). Probado con Quick Share, Instagram, Mensajes,
  Bluetooth y Discord.
- La tarjeta firma «CYPHER · FRAGMENTO DE LETRA» y limita el fragmento: no se
  reproduce la letra completa ni se eluden las condiciones de LRCLIB.

## Cypher Wrapped anual

- Estadísticas ampliadas, todas calculadas con datos reales de `playback_daily`:
  minutos totales, top 5 canciones, top 5 artistas con **porcentaje de escucha**,
  top 5 álbumes, géneros favoritos, canción más repetida y día pico.
- Los álbumes y géneros se guardan ahora en el rollup diario (migración 6→7,
  sin borrar datos). Los géneros proceden de las etiquetas reales de MediaStore
  en pistas locales; si no existen, la app lo dice en lugar de inventarlos.
- Rediseño de las seis diapositivas: listas con hairlines en vez de una tarjeta
  por fila, jerarquía tipográfica clara, barras finas de distribución y el mismo
  lenguaje visual sobrio de la referencia.
- El resumen anual se puede abrir siempre desde Ajustes; el aviso automático y
  la notificación del 31 de diciembre siguen limitados a la ventana de temporada.
- Preparada la estructura visual compartida (`SharePalette`) para incorporar
  «compartir Wrapped» en diciembre sin rehacer el sistema de tarjetas.

## Portadas más nítidas

- Las miniaturas de YouTube pasan de `hqdefault` (480×360, con barras) a
  `maxresdefault` (1280×720). Un interceptor de Coil reintenta con `hqdefault`
  cuando un vídeo no tiene frame maxres, así que nunca queda una portada vacía.
- Un único `ImageLoader` compartido para toda la app: caché de memoria al 20 %
  y caché de disco persistente de 192 MB, con crossfade corto.
- La extracción de paleta reutiliza ese loader y su caché pasa a estar acotada
  (32 entradas). Antes se creaba un `ImageLoader` nuevo por cada pista y el mapa
  de paletas crecía sin límite.

## Recomendaciones

- Motor existente afinado, sin servicios nuevos: excluye lo que acabas de
  escuchar, evita repetir una pista en dos secciones y usa los **géneros reales**
  de la biblioteca en lugar de una lista fija («Pop», «Urbano»).
- Nueva sección de biblioteca por género y estado vacío honesto cuando todavía
  no hay historial suficiente.
- «Top Picks for You» pasa a «Elegido para ti», coherente con el resto de la UI.

## Reparaciones de la auditoría

- **Reproductor completo:** «Reproducir a continuación» y «Añadir a la cola»
  del menú contextual ejecutaban lambdas vacías; ahora llaman al reproductor.
- **Accesibilidad de la cola:** el botón se anunciaba como «A continuación» y
  la auditoría anterior no lo encontraba como «Cola». Ahora es
  «Cola de reproducción».
- **Wrapped fuera de temporada:** `loadWrappedStats` usaba un año fijo (2026);
  ahora el año sale de la fecha real, igual que el deep link y Ajustes.
- **Memoria de imágenes:** eliminado el `ImageLoader` por pista de la paleta.

## Verificación

- `:app:testDebugUnitTest` y `:app:assembleRelease` correctos.
- Instalada sobre 1.0.8 sin desinstalar: migración Room 6→7 correcta, datos
  intactos (22 playlists, 154 canciones, favoritos y descargas).
- Recorrido en dispositivo real (CPH2579 / OnePlus Nord 3 5G): inicio,
  biblioteca, búsqueda, playlist, reproducción, mini reproductor, reproductor
  completo, letras, selección, tarjeta en los tres diseños y dos formatos,
  menú nativo de compartir, cola, Ajustes, Wrapped (6 diapositivas),
  persistencia tras `force-stop`, segundo plano.
- `logcat -b crash` vacío. PSS ≈ 118 MB en reposo tras reabrir y ≈ 134 MB con
  reproducción activa y la cola abierta.

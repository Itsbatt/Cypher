# Cypher 1.5.0 — tu biblioteca viaja al PC, letras incluidas

`versionName` **1.5.0** · `versionCode` **21** · `applicationId` sin cambios (`com.cypher.music`).

## Qué trae

- **Respaldo en ZIP con las letras dentro.** El botón de exportar guarda, en
  `Descargas/Cypher`, un ZIP con la biblioteca (playlists, favoritos, ajustes de tema,
  letras, pantalla de inicio y movimiento) **más las letras ya descargadas**. Ese archivo
  se importa tal cual en **Cypher PC 1.0**, así que la biblioteca del móvil se lleva al
  ordenador sin nube y sin cuenta.
- **Las letras que descargas se quedan.** Al pedirle letras a LRCLIB, el documento se
  guarda en el dispositivo (`LyricsStore`): sobrevive a un reinicio, entra en el ZIP y no
  hay que volver a descargarlo.
- **Base de datos v9.** Migración declarada `7 → 9` para quien venía de 1.4.0 y borrado de
  las dos tablas de contabilidad que solo existían para empujar una copia a la nube. Tus
  playlists, favoritos y estadísticas no cambian.
- **Playlists que no pierden canciones.** El orden se lee de la tabla de cruce, así que una
  pista cuya fila todavía no está en la biblioteca ya no desaparece de la lista.
- **Temporizador de sueño con fundido**: los últimos 30 segundos bajan el volumen en vez de
  cortar en seco.
- **Atajos en el icono de la app**: favoritas y última sesión, además de los accesos que ya
  había.
- **Portadas propias de playlist desde la galería**, y en Ajustes el espacio ocupado por las
  descargas con la opción de borrarlas todas.

## Lo que no cambia

- **Sin cuenta y sin nube.** Cypher sigue funcionando entero sin conexión y nada de tu
  biblioteca sale del dispositivo; el ZIP lo eliges y lo mueves tú.
- **Nada de audio en la nube**: descargas, cachés y cookies de YouTube se quedan en el
  teléfono.

## Requisitos

- Android 13 o superior (API 33), 64 bits. `minSdk` y `applicationId` sin cambios, así que
  se instala encima de 1.4.0 sin perder la biblioteca.
- Para el respaldo con letras: permiso de almacenamiento para escribir en
  `Descargas/Cypher` (la app lo pide la primera vez).

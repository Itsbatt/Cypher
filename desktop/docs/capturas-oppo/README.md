# Capturas de referencia — Cypher Android 1.5.0 en OPPO

Dispositivo: **OPPO CPH2579** (Android 15), identificado por `ro.product.brand`,
`ro.product.manufacturer` y `ro.product.model` — no por el orden de ADB.
Instalación: `adb install -r` sobre la 1.5.0 existente (`versionCode 21`, `versionName 1.5.0`),
conservando datos (`firstInstallTime` sigue siendo del 6 de noviembre).

| Archivo | Pantalla |
| --- | --- |
| `01-inicio.png` | Inicio con saludo, resumen de escucha, carrusel «Elegido para ti» y mini reproductor |
| `02-biblioteca.png` | Biblioteca: filas con icono, contador y chevron; carrusel «Tus playlists» |
| `03-buscar.png` | Buscador con sugerencias recientes en píldoras y rejilla de categorías |
| `04-playlists.png` | Lista de playlists con filtro y portadas Compose |
| `05-playlist-detalle.png` | Playlist con cabecera, botón Reproducir y filas de pista |
| `05-playlist-vacia.png` | Estado vacío de playlist (mismo patrón de icono + texto guía) |
| `06-reproductor.png` | Reproductor: portada, título, «Spatial Audio», progreso y tiempos |
| `07-perfil.png` | Ajustes y personalización: perfil local, cuenta y nube, estadísticas |

Las capturas incluyen nombres de playlist y del perfil local del dispositivo. Son material de
referencia interna del proyecto: no se publican ni se comparten fuera del repositorio.

## Lo que se midió sobre la app real

- Fondo casi negro (`#0D0B11`) sin degradados; el color entra por portadas y acentos.
- Títulos de pantalla a 34 sp bold, con saludo de 15–17 sp en gris.
- Filas de biblioteca de ~56 dp con separador de 1 px al 8 % y chevron tenue.
- Píldoras de búsqueda recientes: radio completo, borde al 10 %, icono de reloj.
- Tarjetas de playlist: esquinas de 16–20 dp, portada cuadrada y texto a 13 sp debajo.
- Mini reproductor flotante con radio 18 dp, portada de 48 dp y botones de 24 dp.
- Barra de navegación inferior tipo píldora con icono relleno y etiqueta de 10 sp.
- Acento lavanda `#9D7BB0` en iconos activos, botones principales y enlaces.
- Botón principal: relleno lavanda, texto oscuro, radio 14 dp, altura 44–48 dp.

Todo esto está reflejado en `../IDENTIDAD-VISUAL.md` y en `ui/Theme.kt`.

# Cypher PC 1.0.0 — primera versión de escritorio

`version` **1.0.0** · Windows 10/11 (x64) · Compose for Desktop · biblioteca local-first.

## Qué es

La app de Android, llevada a Windows: la misma biblioteca por JSON, el mismo lenguaje
visual y las mismas reglas (nada de cuenta obligatoria, nada en la nube salvo lo que el
usuario pida). Reproduce archivos locales y, si hay `yt-dlp`, también YouTube Music.

## Qué trae esta primera versión

- **Biblioteca local** con importación de carpetas, ZIP y JSON exportados desde el móvil
  (se fusiona por id: nada se duplica), favoritos, playlists, estadísticas y Wrapped.
- **Cola y reproductor**: dock flotante, vista ampliada, aleatorio, repetición, volumen y
  atajos (`Ctrl+K` busca, `Esc` cierra).
- **YouTube Music**: buscar, importar listas, descargar pistas y referencias sin audio.
- **Letras sincronizadas** con shader SkSL en la GPU (Aurora, degradado o halo), karaoke,
  tamaño, interlineado, alineación y opacidad; el panel derecho envuelve las líneas largas
  en dos filas en vez de encogerlas.
- **Paleta editable al completo**: acento, fondo, panel, tarjeta, píldora, borde,
  separador y los tres niveles de texto, cada uno con su selector HSV y su código hex.
  «Restablecer paleta» vuelve a la referencia.
- **Fondo propio** (imagen con desenfoque y velo), estilo de tarjetas, densidad,
  radio de esquinas, cristal del dock y reducción de animaciones.
- **Todo se guarda en este equipo**: `%APPDATA%\Cypher Desktop\library.json`, con
  escritura atómica y copia previa a cada escaneo.

## Instalación

- `Cypher-1.0.0.msi` — instalador por usuario, sin permisos de administrador; el
  desinstalador no toca `%APPDATA%\Cypher Desktop`.
- `Cypher-1.0.0-windows-portable.zip` — misma app sin instalar ni Java.

## Requisitos

- Windows 10 u 11 de 64 bits.
- Para descargar de YouTube Music, `yt-dlp.exe` (se elige una vez en Ajustes).

## Notas

- La sincronización con la nube de Cypher 1.5.0 (Android) todavía no está en la versión de
  escritorio; la biblioteca se mueve por ZIP/JSON.
- El cristal del dock usa un shader SkSL; si la GPU no lo compila, el dock cae a una placa
  opaca y el resto de la app no cambia.

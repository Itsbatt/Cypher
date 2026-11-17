# Cypher Desktop

> **Propietario / Autor:** itsbatt ([@Itsbatt](https://github.com/Itsbatt))

Versión de escritorio de Cypher: misma identidad visual, misma cuenta y misma biblioteca
que la app de Android, con funcionamiento local-first.

Estado: **0.2**, paridad funcional con la biblioteca local de Android y sincronización
implementada contra el contrato real (pendiente de la primera prueba con tu cuenta).

## Requisitos

- JDK 17 (el mismo que usa la app Android).
- Gradle no hace falta instalarlo: hay wrapper 8.10.2.

## Ejecutar

```bash
cd desktop
export JAVA_HOME="C:/Users/bat/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"
./gradlew run
```

PowerShell:

```powershell
cd desktop
$env:JAVA_HOME="C:\Users\bat\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"
.\gradlew.bat run
```

## Verificar que el audio funciona en este equipo

```bash
./gradlew run --args="--self-test"
```

Escribe un WAV real, lo escanea y lo reproduce, e imprime la posición alcanzada. Es la
comprobación honesta del motor de audio, porque las pruebas unitarias no abren dispositivo.

## Revisar la interfaz sin abrirla

```bash
./gradlew run --args="--screenshots capturas"
```

Renderiza cada pantalla a PNG en la carpeta indicada (útil para revisar el diseño o
compararlo con Android).

## Pruebas

```bash
./gradlew test        # 51 pruebas
```

## Compilación de Windows

```bash
./gradlew createDistributable   # imagen con JRE propio
./gradlew packageMsi            # instalador MSI (requiere WiX Toolset)
```

Resultado: `build/compose/binaries/main/app/Cypher/Cypher.exe`.

## Qué incluye

- Inicio con resumen real de escucha, recientes y playlists.
- Biblioteca con álbumes, artistas y canciones, ordenación y búsqueda.
- Reproductor con anterior/siguiente, cola, aleatorio, repetición y volumen persistido.
- Playlists con descripción, edición, reordenación y portada compuesta.
- Favoritos, incluidos los que llegan del móvil sin archivo local.
- Estadísticas derivadas de reproducciones y minutos reales.
- Wrapped por año con horas, artista del año y su porcentaje, top de canciones, la más repetida y el día récord.
- Descargas: añade a la biblioteca una carpeta de archivos descargados sin moverlos, y reproduce en streaming los favoritos que el móvil comparte con URL.
- YouTube Music: busca, escucha en streaming con cola, carga playlists o álbumes por enlace, descarga audio a la biblioteca y guarda una lista entera como playlist local con su portada. Usa [yt-dlp](https://github.com/yt-dlp/yt-dlp) (se instala desde la pantalla o se indica su ruta con `CYPHER_YTDLP`); sin él la pantalla lo explica y no hace ninguna petición.
- Ajustes de acento, sección inicial, animaciones y volumen.
- Cuenta y nube con estados claros de sesión y sincronización.

## Datos

- Biblioteca: `%APPDATA%\Cypher Desktop\library.json` (escritura atómica y copia previa a
  cada escaneo).
- Carátulas: `%APPDATA%\Cypher Desktop\artwork\`.
- Cerrar sesión no borra nada. El token de Google vive solo en memoria.

## Variables de entorno opcionales

| Variable | Uso |
| --- | --- |
| `CYPHER_SYNC_BASE_URL` | Cambiar el endpoint de sync. |
| `CYPHER_DESKTOP_CLIENT_SECRET` | Solo si Google exige el secreto en el intercambio. |
| `CYPHER_GOOGLE_TOKEN_ENDPOINT` | Endpoint de token alternativo (pruebas). |

## Documentación

- [docs/PARIDAD-ANDROID.md](docs/PARIDAD-ANDROID.md) — matriz de funciones Android/Desktop.
- [docs/SINCRONIZACION.md](docs/SINCRONIZACION.md) — protocolo, seguridad y prueba real.
- [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) — tecnología, estructura y decisiones.
- [docs/IDENTIDAD-VISUAL.md](docs/IDENTIDAD-VISUAL.md) — identidad tomada de Android.
- [docs/INFORME-FASE2.md](docs/INFORME-FASE2.md) — resultados y pendientes de esta fase.
- [docs/capturas-oppo/](docs/capturas-oppo/) — capturas de referencia del OPPO.

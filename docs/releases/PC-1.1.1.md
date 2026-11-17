# Cypher PC 1.1.1 — Chromium/CEF integrado y arranque 100% offline

Cypher PC 1.1.1 resuelve la principal fricción en la reproducción de YouTube en escritorio: la descarga en frío de los binarios de Chromium.

## Novedades

### Chromium / CEF empaquetado en el instalador y portable
- **Arranque offline inmediato:** Los binarios completos de CEF (`me.friwi:jcef-natives-windows-amd64`) ahora se incluyen pre-extraídos dentro de `app/resources/cef`.
- **Cero descargas en frío:** Ya no se descargan ~150 MB desde GitHub al reproducir la primera canción en `%APPDATA%\Cypher Desktop\cef`.
- **Adiós a los bloqueos de red:** Se eliminan los problemas en entornos corporativos con proxies, conexiones lentas o antivirus que bloqueaban la descarga en segundo plano.

### Tarjeta de diagnóstico y recuperación de YouTube
- Si Chromium encuentra problemas de inicialización (como permisos de carpeta o certificados interceptados), la vista muestra una tarjeta persistente y accionable (`YouTubeIssueCard`) que explica la causa concreta y ofrece un botón de reintento.

### Autodiagnóstico CLI (`--cef-check`)
- Soporte para ejecutar `Cypher.exe --cef-check` desde la consola para auditar al instante si los binarios de Chromium están bien resueltos y operan con normalidad.

## Instalación

- **`Cypher-1.1.1.msi`**: Instalador por usuario para Windows (no requiere permisos de administrador). Actualiza instalaciones previas sin tocar tu biblioteca.
- **`Cypher-1.1.1-windows-portable.zip`**: Versión portable (~234 MB). Incluye el runtime de Java y Chromium preconfigurados; solo descomprimir y disfrutar.

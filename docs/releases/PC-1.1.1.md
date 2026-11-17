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

### Reproducción en segundo plano (sin ventana emergente)
- El reproductor de Chromium ahora se aloja en un marco de utilidad en segundo plano completamente transparente para el usuario. Ya no se abre una ventana emergente de vídeo al reproducir cada canción: la música se disfruta exclusivamente dentro de la interfaz nativa de Cypher (con su fondo líquido, letras sincronizadas y controles en dock).

### Carga robusta de carátulas
- Motor de carátulas modernizado con `java.net.http.HttpClient` (siguiendo redirecciones entre protocolos y cabeceras de navegador estándar).
- Fallback automático e instantáneo a la imagen oficial de alta resolución de YouTube (`hqdefault.jpg`) para canciones en línea, búsquedas y reproducciones remotas.
- Tintado cromático en tiempo real del fondo de cristal líquido y panel ampliado garantizado para todas las canciones.

### Modo Portable 100% Autónomo
- En la versión portable (`Cypher-1.1.1-windows-portable.zip`), la totalidad de los datos de usuario (biblioteca `library.json`, carátulas descargadas, letras en caché y archivos temporales) se almacenan estrictamente dentro de la subcarpeta `data/` del ejecutable.
- No se escribe nada en `%APPDATA%\Cypher Desktop`: puedes llevar la carpeta en una memoria USB o moverla de equipo manteniendo toda tu configuración.

### Actualizaciones automáticas para Windows (MSI)
- Detección automática de nuevas versiones publicadas en GitHub Releases.
- Si estás usando la versión instalada por MSI, Cypher te avisa de nuevas versiones en Ajustes y te permite descargar e instalar la actualización en un solo clic.
- En la versión portable, la actualización automática mediante MSI queda desactivada por diseño para garantizar que tu instalación portátil nunca sea reemplazada ni dispersada en el sistema.

## Instalación

- **`Cypher-1.1.1.msi`**: Instalador por usuario para Windows (no requiere permisos de administrador). Actualiza instalaciones previas sin tocar tu biblioteca.
- **`Cypher-1.1.1-windows-portable.zip`**: Versión portable (~234 MB). Incluye el runtime de Java y Chromium preconfigurados; solo descomprimir y disfrutar.

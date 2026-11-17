# Cypher Desktop 0.1.0 — informe de entrega

Fecha: 12 de noviembre de 2026.

## 1. Fase 0 — monitor de caducidad

- Monitor detenido; no queda ningún proceso esperando a las 02:05.
- No se forzó la caducidad ni se cambió el reloj.
- Resultados conservados en `.backups/expiry-monitor.log` (validaciones 01:26/01:30/01:34/01:38).
- Pendiente anotado en `.backups/PENDIENTE-reconexion-caducidad.txt`: **la prueba de
  reconexión tras caducidad queda para otro día**. No se ha validado la renovación
  silenciosa; Google devuelve `NEED_REMOTE_CONSENT` y el usuario debe reconectar.

## 2. Instalación en el OPPO

- `adb devices -l` mostró el OPPO como `LNH6FY8T6PDEZHXC`, `product:CPH2579EEA`,
  `model:CPH2579`, `device:OP5759L1`.
- Propiedades: `ro.product.brand=OPPO`, `ro.product.manufacturer=OPPO`, Android 15 (SDK 35).
  Identificado por propiedades, no por orden de conexión.
- APK instalada: `app/build/outputs/apk/release/app-arm64-v8a-release.apk`
  (61.476.431 bytes, SHA-256 `00e64117…f04ce5`).
- `adb install -r` → `Success`.
- Verificación: `versionCode=21`, `versionName=1.5.0`, `firstInstallTime=2026-11-06` (datos
  conservados), `lastUpdateTime=2026-11-12 01:53`.
- La app abre con su biblioteca, playlists y mini reproductor intactos.
- **No se tocó** el Samsung S22 (`RZCT106THDK`) ni la tablet SM-T580. No se desinstaló nada,
  no se borraron datos ni caché.

## 3. Capturas y análisis estético

Ocho capturas en `docs/capturas-oppo/` (inicio, biblioteca, buscar, playlists, detalle,
estado vacío, reproductor, ajustes/perfil) y el análisis en
`docs/capturas-oppo/README.md` y `docs/IDENTIDAD-VISUAL.md`.

Resumen: fondo `#0D0B11`, superficies `#16141D`, tarjetas `#1E1B27`, acento lavanda
`#9D7BB0`, títulos 34 sp bold, filas de 56 dp con separadores al 8 %, píldoras y botones de
radio 12–18 dp con texto oscuro sobre el acento.

Se eliminó una captura que había abierto, sin querer, la hoja de cuenta de Google con nombre
y correo: no forma parte del repositorio.

## 4. Tecnología elegida

**Kotlin + Compose Multiplatform (Compose Desktop 1.7.3, Kotlin 2.0.21, JVM 17)** frente a
Tauri, Flutter y Electron. Motivos en `docs/ARQUITECTURA.md`: reutiliza el lenguaje, los
modelos y la identidad visual de Android, y produce un `.exe`/`.msi` con su propio runtime.

## 5. Funcionalidades creadas

- Ventana de escritorio 1320×860 con tema Cypher y acento configurable.
- Barra lateral: Inicio, Biblioteca, Favoritos, Playlists, Ajustes, Cuenta y nube.
- Biblioteca local: elección de carpeta, escaneo con etiquetas (mp3/m4a/aac/flac/ogg/opus/wav),
  duración, carátula embebida copiada a la carpeta de datos, ids estables por ruta.
- Búsqueda por título, artista y álbum.
- Favoritos con corazón por fila.
- Playlists: crear, eliminar, abrir, añadir pistas desde el menú de cada fila, quitar pistas.
- Reproductor: play/pausa, progreso con búsqueda, volumen persistido (JavaFX Media).
- Barra de reproducción con portada, título, artista, tiempos y aviso explícito si el
  reproductor no está disponible.
- Persistencia local JSON con escritura atómica y copia previa a cada escaneo.
- Ajustes: acento, carpeta, reescaneo, ruta de datos.
- Cuenta: OAuth PKCE con loopback, cierre de sesión sin borrar datos, sincronización
  pull→merge→push respetando PROFILE/PLAYLIST/FAVORITE/PREFERENCE/STATS, LWW y tombstones.

## 6. Pruebas

```bash
cd desktop && ./gradlew test
```

- `SyncCoreTest` (7): gana el más reciente, lo local más nuevo no se pisa, tombstones,
  unión ordenada de pistas, acumulación de estadísticas, reconciliado de huérfanos, ids estables.
- `StorageTest` (3): ida y vuelta, archivo ausente, archivo corrupto.
- Resultado: **10 pruebas, 0 fallos** (`BUILD SUCCESSFUL`).
- Arranque verificado dos veces: `./gradlew run` y el `.exe` empaquetado; en ambos la ventana
  «Cypher» aparece y no hay excepciones en el log.

## 7. Compilación generada

`desktop/build/compose/binaries/main/app/Cypher/Cypher.exe` (imagen de app con JRE propio,
generada con `./gradlew createDistributable`). El instalador MSI no se generó: requiere WiX
Toolset instalado en el equipo.

## 8. Problemas encontrados

- El OPPO no aparecía en ADB al empezar; apareció más tarde y entonces se completó la fase.
- `TextStyle` posicional en el tema y la conversión de imágenes de portada fallaron en la
  primera compilación; corregidos con argumentos nombrados e import explícito.
- JavaFX avisa «Unsupported JavaFX configuration: classes were loaded from unnamed module»:
  es una advertencia conocida al usar JavaFX desde el classpath, no un fallo.
- **La sincronización no se ha ejecutado contra el backend**: requiere un inicio de sesión
  Google real. El motor y las reglas están implementados y probados en aislamiento, pero no
  hay evidencia de un intercambio real de registros con Android.

## 9. Próximos pasos

1. Probar el inicio de sesión real (posible necesidad de `CYPHER_DESKTOP_CLIENT_SECRET`) y una
   sincronización de ida y vuelta con el móvil, verificando que Android no pierde datos.
2. Añadir ajustes de cuenta persistentes (hoy el token vive en memoria de la sesión).
3. Cola de reproducción real (anterior/siguiente), repetición y aleatorio.
4. Empaquetado MSI firmado para distribuir.
5. Estudiar compatibilidad macOS/Linux cambiando clasificadores de JavaFX.

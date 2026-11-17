# Contribuir a Cypher

Gracias por querer mejorar Cypher. Este documento reúne lo necesario para compilar, probar y
enviar cambios sin fricción.

## Requisitos

- **JDK 17** (el proyecto usa `jvmToolchain(17)`).
- **Android SDK** con `compileSdk = 36` y `minSdk = 29` para el módulo `app`.
- **Windows 10/11** para el módulo `desktop` (JavaFX, JCEF y las llamadas DWM de Windows).
- Git.

## Estructura

| Ruta | Contenido |
|---|---|
| `app/` | App Android (Kotlin, Compose, Media3, Room). |
| `desktop/` | Cypher PC, con su **propio** Gradle wrapper (`desktop/gradlew`). |
| `experimental/` | Código congelado o en exploración; no forma parte de los builds. |
| `docs/` | Documentación técnica, auditorías y notas de release. |

## Compilar y probar

Android:

```bash
./gradlew :app:testDebugUnitTest   # pruebas JVM (incluye migraciones Room con Robolectric)
./gradlew :app:lintDebug           # lint de Android: debe pasar sin errores
./gradlew :app:assembleDebug       # APK de depuración, sin firma de producción
```

Escritorio:

```bash
cd desktop
./gradlew test
./gradlew run
```

Ambos son obligatorios antes de abrir un Pull Request: si `lintDebug` o los tests fallan, CI
también fallará.

## Flujo de ramas

- `main` es el código estable publicado.
- Los cambios se hacen en ramas cortas: `feature/...`, `fix/...`, `docs/...`.
- Se integran mediante Pull Request; el PR explica el *por qué*, no solo el *qué*.
- Las ramas se borran al integrarse. Un release se etiqueta desde `main` (`v1.5.0`,
  `pc-v1.1.0`), nunca desde una rama de funcionalidad.

## Estilo

- **Kotlin idiomático** en ambos módulos. No se añaden clases Java nuevas: Room se genera con
  **KSP** y todo el código de datos es Kotlin.
- Compose: estado inmutable hacia arriba, efectos en `LaunchedEffect`/`remember`, sin trabajo
  bloqueante en composición.
- Los comentarios explican el *porqué* (una decisión, una rareza de un dispositivo, un
  contrato de API). No se comentan obviedades.
- Respeta el idioma del archivo que edites: hay comentarios en español e inglés.

## Reglas de persistencia (importante)

- El esquema Room está en `app/schemas/`. **No se edita a mano** ni se regenera sin motivo:
  cambiarlo cambia el `identityHash` y deja fuera a las bases de datos ya instaladas.
- Cualquier cambio de columnas exige: subir `version`, añadir una `Migration` nueva a
  `CypherDatabase.MIGRATIONS` y una prueba en `CypherDatabaseMigrationTest` que verifique que
  no se pierden datos.
- La biblioteca de PC viaja en un ZIP compatible con Android: si tocas el formato, mantén la
  compatibilidad en ambos sentidos.

## Qué no se versiona

`keystore.properties`, `local.properties`, `keystore/`, `*.jks`, `*.keystore` y cualquier token
o credencial. Si un cambio necesita configuración local, documenta el nombre de la propiedad en
el README y usa un valor por defecto seguro.

## Reportar problemas

- Fallos y sugerencias: plantillas de issue del repositorio.
- Vulnerabilidades: sigue [`SECURITY.md`](SECURITY.md); nunca en un issue público.

## Qué cambia

<!-- Una o dos frases. El "por qué" importa más que el listado de archivos. -->

## Por qué

<!-- Fallo que arregla, necesidad que cubre, decisión de producto que aplica. -->

## Cómo se ha comprobado

<!-- Comandos ejecutados y resultado real. Si algo no se ha podido probar, dilo. -->

## Checklist

- [ ] `./gradlew :app:testDebugUnitTest` pasa (si toca `app/`).
- [ ] `./gradlew :app:lintDebug` pasa sin errores (si toca `app/`).
- [ ] `cd desktop && ./gradlew test` pasa (si toca `desktop/`).
- [ ] Si cambia el esquema Room: `version` subida, migración nueva en `CypherDatabase.MIGRATIONS` y prueba de migración que preserve datos.
- [ ] La biblioteca ZIP sigue siendo compatible entre Android y PC (si toca el formato).
- [ ] Sin secretos, keystores ni `local.properties` en el diff.
- [ ] Probado en un dispositivo o equipo real cuando el cambio afecta a reproducción, ventanas o audio.

## Notas para la revisión

<!-- Riesgos, seguimientos, capturas o pasos manuales pendientes. -->

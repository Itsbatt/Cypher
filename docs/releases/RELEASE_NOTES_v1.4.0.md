# Cypher 1.4.0

## Reproducción
- Al cambiar a una canción que no está descargada, la reproducción ya no se queda en pausa: el motor de YouTube avisa de una pausa falsa mientras carga el vídeo nuevo y eso se confundía con una pausa real. Ahora solo cuenta la pausa de la canción pedida o la que pides tú.
- Si esa pausa falsa llega igualmente, el reproductor vuelve a arrancar el vídeo (como máximo dos veces por canción) en lugar de dejar la cola parada.

## Proyecto
- Workflow de GitHub Actions en `ci/android.yml`: tests JVM, compilación de los APK y publicación firmada al empujar un tag `v*`. Queda activado en cuanto un push con scope `workflow` lo mueva a `.github/workflows/`.

## Notas
- `versionCode` 19, `versionName` 1.4.0.
- Pendiente de probar un día completo en el móvil antes de publicar la release.

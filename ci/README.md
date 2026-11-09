# Android CI

El workflow vive ahora en `.github/workflows/android.yml`, que es donde GitHub
Actions lo ejecuta. `android.yml` estaba en esta carpeta porque el token con el
que se hizo el push no tenía el scope `workflow`; para activarlo basta con
empujar el archivo a `.github/workflows/` con un token que sí lo tenga.

Qué hace:

- En cada push a `main` y en cada pull request: ejecuta los tests JVM
  (`:app:testDebugUnitTest`) y compila los APK de release.
- Sube los APK como artefacto `cypher-release-apks`.
- Al empujar un tag `v*`: además calcula `SHA256SUMS.txt` y sube los APK y el
  fichero de sumas a la release de ese tag (`gh release upload --clobber`).

Firma: el workflow lee el keystore de los secretos `CYPHER_KEYSTORE_BASE64`,
`CYPHER_KEYSTORE_PASSWORD`, `CYPHER_KEY_ALIAS` y `CYPHER_KEY_PASSWORD`. Sin
ellos los tests y la compilación siguen corriendo, pero los APK quedan sin firmar
y no se publican en la release.

Para publicar una versión desde cero, con los secretos ya configurados:

```bash
git tag v1.4.0 && git push origin v1.4.0
```

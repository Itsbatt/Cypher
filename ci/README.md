# Android CI

`android.yml` es el workflow completo de GitHub Actions: tests JVM, compilación
de los APK y publicación firmada en los tags.

Vive en esta carpeta porque el token con el que se sube este repositorio (una
OAuth App) no tiene el scope `workflow`, y GitHub rechaza cualquier push que cree
`.github/workflows/*` sin ese permiso:

```
! [remote rejected] main -> main (refusing to allow an OAuth App to create or
update workflow `.github/workflows/android.yml` without `workflow` scope)
```

## Activarlo

En esta máquina ya está copiado en `.github/workflows/android.yml` (sin commitear).
Con un token o una sesión que tenga el scope `workflow`:

```bash
git add .github/workflows/android.yml
git rm ci/android.yml
git commit -m "ci: activate the android workflow"
git push
```

## Qué ejecuta

- En cada push a `main`, en cada pull request y a mano: `:app:testDebugUnitTest`
  y `:app:assembleRelease`. Los APK quedan como artefacto `cypher-release-apks`.
- Al empujar un tag `v*`: además calcula `SHA256SUMS.txt` y sube los APK y las
  sumas a la release de ese tag (`gh release upload --clobber`).

## Firma

El workflow toma el keystore de los secretos `CYPHER_KEYSTORE_BASE64`,
`CYPHER_KEYSTORE_PASSWORD`, `CYPHER_KEY_ALIAS` y `CYPHER_KEY_PASSWORD`. Sin
ellos los tests y la compilación siguen corriendo, pero los APK quedan sin firmar
y no se publican en la release.

Con los secretos configurados, publicar una versión es:

```bash
git tag v1.4.0 && git push origin v1.4.0
```

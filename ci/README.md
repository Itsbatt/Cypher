# Android CI

`android.yml` es el workflow de GitHub Actions: ejecuta los tests JVM, compila
los APK y, en tags `v*`, calcula y adjunta `SHA256SUMS.txt` a la release.

Está aquí y no en `.github/workflows/` porque el token con el que se hizo el
push no tenía el scope `workflow`. Para activarlo, cuando haya un token o una
sesión con ese permiso:

```bash
mkdir -p .github/workflows
git mv ci/android.yml .github/workflows/android.yml
git commit -m "ci: activate the android workflow"
git push
```

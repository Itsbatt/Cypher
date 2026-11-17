# Cypher Desktop · Informe de la quinta fase (reintento de YouTube)

Fecha: 13 de noviembre de 2026.
Perfil aislado: `%LOCALAPPDATA%\Temp\cypher-smtc-audit`. Biblioteca real sin abrir.

---

## 1. Consentimiento

**Concedido.** Se reinició la app (sin token en memoria) y la pantalla de Importar mostraba
«Conecta tu cuenta de Google», no una sesión reutilizada. Tras pulsar «Iniciar sesión con
Google», el consentimiento se completó en el navegador y la app mostró:

> Cuenta de Google conectada · alejopp333pro@gmail.com

## 2. Scope concedido

El token se pidió con `openid email profile https://www.googleapis.com/auth/youtube.readonly`.
La llamada a la API llegó autorizada: Google no devolvió `insufficient scopes`, sino un error
de API deshabilitada (ver abajo). Eso confirma que el scope de YouTube está en el token.
El token no se inspeccionó ni se imprimió: vive solo en memoria.

## 3. Playlists

**No se pudo obtener ninguna.** Al pulsar «Cargar mis playlists», la app mostró el error de
Google:

> La API de YouTube Data no está habilitada en el proyecto OAuth. Actívala en Google Cloud…
> YouTube Data API v3 has not been used in project 764777457932 before or it is disabled…
> Enable it by visiting https://console.developers.google.com/apis/api/youtube.googleapis.com/overview?project=764777457932

- Código: **403 `SERVICE_DISABLED`** (no es un problema de permisos del usuario ni del
  scope; el consentimiento funcionó).
- Playlists encontradas: **0**, porque la consulta no llegó a ejecutarse. No es un resultado
  de la API.

## 4. Paginación

**No probada.** Sin la API habilitada no hay primera página que recorrer. No se inventan
recuentos ni cursores.

## 5. Errores de API

- `SERVICE_DISABLED` / `accessNotConfigured`: YouTube Data API v3 deshabilitada en el
  proyecto `764777457932`. Es el único error; no hubo `access_denied` esta vez.

## 6. Datos

- Ninguna playlist importada, ninguna canción añadida, ningún audio descargado.
- Sin pull ni push; sin sincronización.
- Biblioteca real: **13 archivos, 90.421.208 bytes**, sin cambios.
- El secreto se escribió solo en el perfil aislado y **se borró al terminar**; el perfil real
  nunca lo tuvo. Sigue siendo necesario restablecerlo porque el valor usado estuvo expuesto.
- Sin commits, tags, push, releases, despliegues ni cambios en Supabase o Google Cloud.

## Para continuar

1. Google Cloud → **APIs y servicios → Biblioteca → YouTube Data API v3 → Habilitar** en el
   proyecto `764777457932`.
2. Esperar uno o dos minutos a la propagación.
3. Repetir «Cargar mis playlists» en el perfil aislado (el consentimiento ya está dado; no
   hace falta volver a autorizar salvo que caduque el token).
4. Solo entonces se podrán comprobar títulos, identificadores, metadatos y paginación.

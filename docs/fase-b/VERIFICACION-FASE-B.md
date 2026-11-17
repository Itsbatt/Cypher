# Verificación adicional de la FASE B

Fecha: 16 de noviembre de 2026
Alcance: migraciones reales (Room 7→9 y 8→9, library.json v2→v3) y separación de
autenticación. Sin commits, sin tags, sin push, sin publicaciones y sin tocar
Supabase ni ningún dato real.

---

## 1. Android — migraciones Room

### Cómo se ha probado

Test nuevo: `app/src/test/java/com/cypher/music/data/room/CypherDatabaseMigrationTest.kt`
(Robolectric 4.14.1, SDK 34, `androidx.test:core` añadido solo a `testImplementation`,
más `testOptions.unitTests.isIncludeAndroidResources`).

Cada base de prueba se construye **desde el esquema exportado real**
(`app/schemas/com.cypher.music.data.room.CypherDatabase/{7,8}.json`), ejecutando el
`createSql` de cada entidad y sus índices sobre un fichero SQLite auténtico, y fijando
`PRAGMA user_version`. Después se abre con el mismo `Room.databaseBuilder` de la app y
los **mismos objetos `Migration`** que registra `CypherDatabase.java`, obtenidos por
reflexión. Por tanto Room ejecuta las migraciones de verdad y además valida el esquema
resultante: si faltara un paso, el test fallaría con
`A migration from X to Y was required but not found`.

Datos sembrados antes de migrar (idénticos en 7 y 8): 1 playlist con descripción y
carátula, 3 temas con ruta y carátula, 2 de ellos favoritos (`isLiked = 1`), orden de
playlist en `playlist_tracks_crossref`, 1 reproducción reciente, 2 filas de
`playback_analytics` (9000 ms, 5 reproducciones), 2 días de `playback_daily` con álbum
y género, y 2 búsquedas.

### Resultado — `tests=5 saltados=0 fallos=0` (14,9 s)

| Test | Qué demuestra |
| --- | --- |
| `the shipped migration list covers every version up to 9` | Existen rutas 3→4, 4→5, 5→6, 6→7, 7→9 y 8→9; ningún salto queda sin migración declarada |
| `schema 7 reaches 9 without losing playlists tracks favourites stats or history` | Tras migrar se conservan playlist (nombre, descripción, `coverUri`), los 3 temas con `path` y `artworkUrl`, el orden de la playlist, los 2 favoritos, las estadísticas y el rollup diario con álbum/género, la reproducción reciente y las búsquedas |
| `schema 8 reaches 9 and only the two sync tables disappear` | `sync_outbox` y `sync_state` desaparecen y **todos** los datos de usuario quedan idénticos |
| `a stale sync table left behind never blocks the upgrade` | Un dispositivo que ya hubiera borrado `sync_outbox` a mano sigue migrando (`DROP TABLE IF EXISTS`) |
| `opening a v9 database twice keeps the identity and the rows` | Reabrir una base ya migrada no vuelve a migrar ni pierde filas |

Comprobado además que `playlists`, `tracks`, `playlist_tracks_crossref`,
`recently_played`, `playback_analytics`, `playback_daily` y `search_queries` son
**idénticas byte a byte entre el esquema 7 y el 9**, lo que confirma que
`MIGRATION_7_9` puede ser un no-op y que la ruta desde 1.4.0 es segura.

Sobre **preferencias**: no viven en Room. `ThemePreferences`, `UserViewPreferences`,
`LyricsDisplaySettings` y `MotionPreferences` usan DataStore/SharedPreferences, que la
migración de base de datos no toca. La migración solo ejecuta
`DROP TABLE IF EXISTS sync_outbox` y `DROP TABLE IF EXISTS sync_state`, así que no
puede afectar a ninguna preferencia.

Nota honesta: son bases SQLite reales ejecutando migraciones reales, pero en JVM con
Robolectric, **no en un dispositivo**. La ruta de código es la misma; el dispositivo
sigue sin probarse.

## 2. Desktop — migración de `library.json`

Test nuevo: `desktop/src/test/kotlin/com/cypher/desktop/StorageMigrationTest.kt`, sobre
un documento v2 escrito a mano con lo que la 1.4.0/1.5.0 real guardaba: 2 temas con
ruta y carátula, 1 playlist con descripción, carátula y orden, 2 favoritos (uno local y
uno de cuenta), 1 día de estadísticas, historial, perfil, carpeta, `cloudTracks` con
una fila de YouTube y otra sin identidad de servicio, más `sync`, `outbox` y
`remoteThemeJson`. Todo en un directorio temporal; nunca el `%APPDATA%` del usuario.

### Resultado — `tests=6 saltados=0 fallos=0`

| Test | Qué demuestra |
| --- | --- |
| `migrating keeps every piece of local data` | Temas, rutas, carátulas de tema y de playlist, orden, descripción, favoritos, estadísticas, historial, perfil y carpeta sobreviven |
| `cloudTracks become service references and never disappear silently` | La fila `yt_dQw4w9WgXcQ` se convierte en `RemoteTrack` con `videoId`, título, artista y portada; la fila sin identidad de servicio se descarta (no tiene audio posible) |
| `the document on disk loses the account keys and keeps version 3` | El archivo escrito ya no contiene `cloudTracks`, `sync`, `outbox` ni `remoteThemeJson`, y queda en `version: 3` |
| `the bak copy restores the original document byte for byte` | Existe exactamente una copia `library-v2-*.json.bak`, su contenido es **idéntico** al original, y devolverla a su sitio recupera el archivo tal cual |
| `loading twice is idempotent and leaves a single backup` | Cargar dos veces no crea copias nuevas ni cambia los datos |
| `a v3 document is never rewritten` | Un documento ya migrado no se toca |

## 3. Separación de autenticación

### 3.1 Google Login como cuenta de Cypher: **eliminado**

`grep` sobre `app/src/main` y `desktop/src` de `GoogleSignIn`, `GoogleIdToken`,
`CredentialManager`, `getGoogleIdOption`, `Supabase`, `AccountRepository` y
`signInWithGoogle`: **cero resultados** en ambos proyectos. `Screen.ACCOUNT` y
`AccountView` ya no existen, y el rail de Desktop no tiene entrada de cuenta.

### 3.2 YouTube sigue siendo independiente: **sí**

- Desktop: el consentimiento vive en `importers/YouTubeOAuth.kt`, un paquete aparte;
  solo lo usan las acciones de IMPORT. No comparte sesión, almacenamiento ni estado con
  nada más.
- Android: `network/YouTubeAccountManager.kt` y `ui/components/YouTubeLoginDialog.kt`
  son el importador/reproductor; ninguna pantalla los presenta como «tu cuenta de
  Cypher».
- No hay ninguna ruta en la que iniciar sesión en YouTube habilite funciones de Cypher,
  ni al revés.

### 3.3 Tokens de YouTube: **Desktop sí, Android no**

| Plataforma | Qué se guarda | Veredicto |
| --- | --- | --- |
| Desktop | `YouTubeOAuth.Tokens` en una `var` privada de `AppState`; se pone a `null` al desconectar; no se serializa, no se escribe en `library.json`, no se registra en logs. El único fichero es `desktop-client-secret.txt`, que es la credencial de la **aplicación** (no del usuario) y solo se guarda si el usuario lo pide | Cumple |
| Android | `YouTubeAccountManager` guarda la cookie completa y el `SAPISID` en `EncryptedSharedPreferences` (`yt_secure_account_<id>`), y además la sesión queda en el cookie jar del WebView. Es persistente por diseño: sin ella no hay reproducción ni importación tras reiniciar | **No cumple** |

Este es un hallazgo real, no un fallo introducido en la FASE B: es el método que ya
existía en 1.4.0 y es exactamente lo que las decisiones **D11/D12** (pendientes de tu
aprobación) debían sustituir por `AuthorizationClient` con OAuth y scope
`youtube.readonly`. No lo he cambiado por mi cuenta porque:

1. vaciar la cookie al cerrar la app obligaría a iniciar sesión en YouTube en cada
   arranque y dejaría la reproducción sin sesión;
2. la sustitución correcta necesita un cliente OAuth y la verificación del scope
   sensible, que son precisamente las decisiones que siguen abiertas.

Opciones para cuando lo decidas: (a) OAuth con token de refresco guardado en
`EncryptedSharedPreferences` y cookie efímera solo en memoria; (b) cookie cifrada como
ahora pero con caducidad explícita y borrado al cerrar sesión desde Ajustes; (c) no
persistir nada y pedir sesión en cada uso del importador.

## 4. Datos reales y Supabase

- Todas las bases de prueba son ficheros temporales creados y borrados por el test
  (`context.deleteDatabase` en `@After`, `Files.createTempDirectory` en Desktop).
- No se ha abierto la base de la app ni `%APPDATA%\Cypher Desktop\library.json`.
- No se ha ejecutado nada contra Supabase: ni SQL, ni migraciones, ni funciones, ni
  secretos. El proyecto remoto queda intacto.

## 5. Estado de las suites tras estos cambios

| Suite | Resultado |
| --- | --- |
| Android `:app:testDebugUnitTest` (completa) | BUILD SUCCESSFUL — **123 tests, 0 fallos, 1 saltado** |
| Desktop `./gradlew test` (completa) | **84 tests, 0 fallos** (78 previos + 6 de migración) |

Dependencias añadidas, solo para pruebas: `org.robolectric:robolectric:4.14.1` y
`androidx.test:core` en `testImplementation`, más `testOptions.unitTests.isIncludeAndroidResources`.
No cambian el APK ni el runtime.

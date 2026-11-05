# Cypher — Documentació Tècnica Integral i Historial Complet del Projecte

> **Nom Oficial de l'Aplicació:** Cypher  
> **Identificador del Paquet (Package Name):** `com.cypher.music`  
> **Versió:** 1.0 (Build 1) — Codi Obert (GPL-3.0)  
> **Ruta Local del Projecte:** `C:\Users\bat\.gemini\antigravity\scratch\cypher`  
> **Target SDK:** Android 16 (API 36) | **Min SDK:** Android 13 (API 33)  
> **Llenguatges i Runtimes:** Kotlin 2.2, Java 21, Jetpack Compose (Material 3), AndroidX Media3, AGSL Shaders, Room v3  
> **Dispositius de Validació en Temps Real:** Samsung Galaxy S22 5G (`SM-S901E`, Android 16 / SDK 36, ADB: `RZCT106THDK`), OnePlus Nord 3 5G  
> **Data de Consolidació:** 04 de Novembre de 2026  

---

## 1. Visió General i Filosofia de Disseny

**Cypher** és un reproductor de música d'alta fidelitat i client de streaming híbrid per a Android 13+ (API 33 a 36).
El projecte neix amb la premissa d'unificar en una única experiència d'usuari fluida, privada i estèticament moderna tant la música desada localment al dispositiu com el catàleg global de **YouTube Music / InnerTube**, aprofitant al 100% l'acceleració per maquinari de la GPU mitjançant **AGSL (Android Graphics Shading Language)** i el motor d'àudio professional **AndroidX Media3**.

### Pilars Arquitectònics
1. **Streaming Híbrid Resilient:** Resolució directa d'àudio en streaming a través de yt-dlp i endpoints natius d'InnerTube TVHTML5, combinat amb telemetria passiva per detectar bloquejos HTTP 403 / protocols SABR de Google.
2. **Base de Dades Room v3 Unificada:** Esquema relacional monousuari amb relacions `@Relation` / `@Junction` (`PlaylistWithTracks`), `TypeConverters` i gestió eficient d'analítiques d'escolta i cerques recents.
3. **Bóveda Offline Segura (Scoped Storage):** Emmagatzematge aïllat de fitxers pesats (FLAC, WAV, ALAC, Opus) prioritzant targetes MicroSD externes muntades, amb generació automàtica del fitxer `.nomedia`.
4. **Interfície Gràfica Líquida (AGSL Liquid Glass):** Shaders orgànics directament executats a la GPU amb aberració cromàtica i dispersió de color basada en la caràtula de l'àlbum (AndroidX Palette).
5. **Lletres en Viu Sincronitzades:** Integració amb la xarxa de LRCLIB per a seguiment dinàmic del text línia a línia (`CypherLyricsSheet`).
6. **Integració Automobilística:** Servei en primer pla amb suport natiu per a Android Auto mitjançant `MediaLibraryService`.

---

## 2. Historial Evolutiu i Fases del Projecte

### Fase 1: Gènesi i Rèplica Visual (Apple Music iOS 18)
- El projecte va començar com una recreació d'alta fidelitat de la interfície d'Apple Music per a Android (`com.example.applemusic` / `apple-music`), implementant tabs d'Inici, Explorar, Ràdio, Biblioteca i Cerca.
- Utilitzava una base de dades SQLite nativa manual (`apple_music_playlists.db`) amb un `PlaylistDatabaseHelper.kt` i suport per a múltiples usuaris locals (`UserManager.kt`).
- Compatibilitat retroactiva des d'Android 9 (API 28) utilitzant degradats radials estàndard com a alternativa per a dispositius sense suport de shaders AGSL.

### Fase 2: Motor de Streaming i Integració de Serveis Externs
- Incorporació de binaris natius de `yt-dlp` i `ffmpeg` a través de les llibreries `youtubedl-android` i `ffmpeg-android` per a descàrrega i extracció d'àudio.
- Implementació de suport per a Last.fm (`ScrobbleManager`), resolució de lletres sincronitzades amb `LrcLibService`, generació i lectura de codis QR per compartir llistes de reproducció (`QrCodeGenerator`, Google Code Scanner).
- Creació del servei d'àudio en segon pla `PlaybackService` (`MediaLibraryService`) per garantir la reproducció amb la pantalla apagada.

### Fase 3: Refactorització Arquitectònica Profunda (Cypher Rebranding)
- **Eliminació del Suport Multiusuari:** L'aplicació es reconverteix en una eina personal per a un sol usuari; s'elimina la pantalla de selecció de perfils (`AuthScreen.kt`) i les entitats d'usuari.
- **Migració a Room v3:** Eliminació definitiva de l'arxiu SQLite heretat `apple_music_playlists.db`. Creació de `CypherDatabase` en Java (garantint compatibilitat amb AGP 9 i Kotlin 2.2) amb 6 taules relacionals:
  - `playlists`
  - `tracks`
  - `playlist_tracks_crossref`
  - `recently_played`
  - `playback_analytics`
  - `search_queries`
- **Mapejadors Idiomàtics Kotlin:** Implementació de `RoomMappers.kt` per convertir de forma transparent entre les entitats de Room i els models de domini de l'aplicació.

### Fase 4: Migració de la Bóveda a Scoped Storage (MicroSD)
- Modificació de `MediaCacheManager.kt` per abandonar l'emmagatzematge intern de l'aplicació (`context.filesDir`) i utilitzar `ContextCompat.getExternalFilesDirs(context, null)`.
- Detecció automàtica de targetes MicroSD externes extraïbles (`Environment.isExternalStorageRemovable`) per desar els formats pesats (FLAC/WAV).
- Generació garantida de l'arxiu de privadesa `.nomedia` i migració automàtica de fitxers heretats.

### Fase 5: Resiliència del Motor de Streaming i Telemetria
- Creació de `StreamingTelemetry.kt`: supervisor reactiu de la salut de la connexió que detecta anomalies com ara errors HTTP 403 o talls del protocol SABR.
- Estratègia de fallback en 4 nivells a `YouTubeRepository.kt` i `MusicPlayerManager.kt`:
  - **Tier 1:** Clients mòbils yt-dlp (`android`, `ios`, `tv`).
  - **Tier 2:** Clients alternatius yt-dlp (`tv_embedded`, `mweb`).
  - **Tier 3:** Endpoint JSON directe d'InnerTube TVHTML5.
  - **Tier 4:** Conmutació al reproductor WebView IFrame oficial en cas de bloqueig persistent.

### Fase 6: Modernització de l'SDK (API 33+) i Shaders Natius
- S'eleva `minSdkVersion` de 28 a **33 (Android 13)**, permetent eliminar tot el codi de suport per a versions antigues.
- Eliminació de `FallbackGradientRenderer`; el shader `LiquidGlass.kt` s'executa exclusivament a través de `RuntimeShader` de la GPU amb aberració cromàtica nativa.
- Introducció d'un limitador temporal de 45 ms (`fireThrottledHaptic`) al `CypherScrubber` per evitar la saturació del fil principal i dels motors de vibració hàptica.

### Fase 7: Nova Identitat Visual Cypher (Lavanda Fosc)
- Abandonament del disseny vermell d'Apple Music.
- Establiment del color d'accent oficial **Lavanda Cypher (`#9D7BB0`)**, amb fons ultra-fosc optimitzat per a pantalles OLED (`#0D0B11`) i targetes en `#16141D`.
- Creació de `CypherTheme` i actualització de recursos natius del sistema.

### Fase 8: Resolució de Bugs de Progrés, Lletres i Cançons Clàssiques
- **Correcció del Salt de la Barra de Progrés:** Es va detectar que, en reproduir vídeos de YouTube, el seguiment local `startLocalProgressTracker()` continuava llegint la posició de l'arxiu silent d'ExoPlayer (`silence.wav`, 0 a 5 s) mentre que el reproductor de YouTube emetia la posició real (ex. 40 s), provocant salts continus cada 300 ms i moviments erràtics a les lletres. S'ha encapsulat el seguiment perquè només s'executi en pistes locals, resolent el problema al 100%.
- **Eliminació de Cançons Clàssiques:** S'ha retirat el mock de Beethoven, Satie i Chopin a `LocalMusicRepository.kt` i s'han afegit consultes de purga a Room perquè les instal·lacions existents quedin netes.

### Fase 9: Trasllat del Projecte i Preparació Codi Obert
- El directori local es va traslladar de `apple-music` a **`cypher`**.
- Actualització de `settings.gradle.kts` amb `rootProject.name = "Cypher"`.
- Validació de compilació i desplegament en dispositiu físic real (**Samsung Galaxy S22**, Android 16 / SDK 36).
- Preparació del repositori obert a GitHub sota la llicència GPL-3.0.

---

## 3. Arquitectura del Sistema i Tecnologies

```
+--------------------------------------------------------------------------+
|                             CAPA DE PRESENTACIÓ                          |
|  Jetpack Compose + Material 3 + Shaders AGSL (GPU) + StateFlow Reactiu   |
|  MainScreen | ListenNow | Browse | Library | Search | CypherNowPlayingModal|
+--------------------------------------------------------------------------+
                                     |
                                     v
+--------------------------------------------------------------------------+
|                             CAPA DE VIEWMODELS                           |
|  MainViewModel | PlayerViewModel | LibraryViewModel | YouTubeViewModel   |
+--------------------------------------------------------------------------+
                                     |
                                     v
+--------------------------------------------------------------------------+
|                       REPRODUCTOR I MOTOR MULTIMÈDIA                     |
|  MusicPlayerManager (Singleton)                                          |
|  - AndroidX Media3 (ExoPlayer) -> Àudio Local, FLAC/WAV i Offline Vault  |
|  - YouTube Player Engine (IFrame + Fallback URL Directe)                 |
|  - PlaybackService (MediaLibraryService / Android Auto)                  |
|  - MediaCacheManager (Scoped Storage / MicroSD amb .nomedia)             |
+--------------------------------------------------------------------------+
                                     |
                                     v
+--------------------------------------------------------------------------+
|                             CAPA DE DADES                                |
|  - Room v3 (CypherDatabase): Playlists, Tracks, Recents, Analytics, Search |
|  - MediaStore Resolver: Exploració de pistes d'àudio locals              |
|  - YouTubeRepository: InnerTube TVHTML5, yt-dlp, DoH Cloudflare          |
|  - LrcLibService: Lletres sincronitzades (.lrc)                          |
+--------------------------------------------------------------------------+
```

---

## 4. Requisits de Compilació i Instruccions d'Entorn

- **Java Development Kit:** JDK 21 estricte.
- **Android SDK:** Compile SDK 36, Target SDK 36, Min SDK 33.
- **Gradle:** Gradle 9.1 amb Android Gradle Plugin (AGP) 9.x.
- **Comanda de Compilació (Windows):**
  ```powershell
  .\gradlew.bat assembleDebug
  ```
- **Instal·lació per ADB:**
  ```powershell
  adb install -r -d app\build\outputs\apk\debug\app-debug.apk
  ```

---

## 5. Llicència de Codi Obert

Aquest projecte es distribueix sota els termes de la llicència **GNU General Public License v3.0 (GPL-3.0)**.
Qualsevol pot inspeccionar, modificar i distribuir el codi sempre que mantingui la mateixa llibertat i llicència per als usuaris finals.

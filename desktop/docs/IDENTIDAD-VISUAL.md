# Identidad visual de Cypher Desktop

Referencia: tema Compose de Android (`ui/theme/Color.kt`, `Type.kt`) y las capturas del
OPPO guardadas en `build-output/1.4.1/` (ajustes, acentos, tarjetas, píldoras).

## Paleta (idéntica a Android)

| Token | Valor | Uso |
| --- | --- | --- |
| `CypherDarkBg` | `#0D0B11` | Fondo de la ventana |
| `CypherDarkSurface` | `#16141D` | Barra lateral y barra de reproducción |
| `CypherDarkCard` | `#1E1B27` | Tarjetas y filas en hover |
| `CypherDarkPill` | `#282334` | Píldoras, portadas vacías |
| `CypherDarkBorder` | `#2C2738` | Bordes de 1 dp |
| `CypherDarkSeparator` | `#221E2C` | Separadores |
| `CypherLavender` | `#9D7BB0` | Acento por defecto |
| `CypherLavenderLight` | `#B89BC9` | Acento secundario |
| `CypherLavenderDark` | `#7A588E` | Degradados |
| Texto primario | `#F7F5FA` | Títulos y filas |
| Texto secundario | `#AFA9BB` | Subtítulos |
| Texto terciario | `#7E778D` | Metadatos y ayudas |

El acento es intercambiable (lavanda, verde, azul, ámbar, rojo), igual que el selector de
acento que ya existe en Android. El tema oscuro es el único: Android también arranca en
oscuro por defecto.

## Tipografía

Misma escala que `AppleTypography` en Android, con la fuente del sistema:

- Título de pantalla 26 sp bold (Android usa 34 sp en móvil; en escritorio se reduce para
  no competir con el contenido).
- Título de sección 18–20 sp semibold.
- Cuerpo 13–15 sp.
- Etiquetas de sección 10 sp, mayúsculas, `letter-spacing 1.4 sp` — el mismo recurso que
  Android usa en «DATOS Y ALMACENAMIENTO» o «DIAGNÓSTICO Y LEGAL».

## Formas y superficies

- Tarjetas: radio 18 dp, borde blanco al 6 % (Android usa 1 dp con `outline`).
- Filas de pista: radio 10 dp, alto 52 dp, hover con `Card` al 80 %.
- Portadas: radio 12 dp; botón de reproducción circular de 38 dp con el acento y texto
  oscuro, exactamente como los botones de acento de Android.
- Píldoras de navegación: radio 11 dp, fondo del acento al 16 % cuando están activas.

## Qué se adaptó al escritorio

| Android | Desktop |
| --- | --- |
| Barra inferior de pestañas | Barra lateral fija de 232 dp con las mismas secciones + sistema |
| Título grande centrado | Título a la izquierda y búsqueda global siempre visible |
| Reproductor a pantalla completa | Barra inferior de 88 dp con portada, controles, progreso y volumen |
| Listas de una columna | Filas densas con acciones en hover y menú contextual |
| Ajustes en scroll vertical | Tarjetas agrupadas por secciones con el mismo lenguaje |
| Selector de acento en ajustes | Mismos cinco acentos, aplicados en vivo |

## Lo que no se copió

- La atenuación de versos y el shader líquido: son efectos móviles que no aportan en una
  ventana y costarían batería/GPU sin beneficio.
- Los iconos Material redondeados se mantienen, porque Android ya los usa.

## Estados vacíos

Todos los estados vacíos usan el mismo patrón: círculo con el acento al 14 %, icono
redondeado, título bold y una frase que explica el siguiente paso. Nunca una pantalla en
blanco.

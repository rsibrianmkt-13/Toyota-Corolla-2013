# Corolla Dash — v1.2.0

Dashboard/launcher para la pantalla VECHTEL (`auto_rk_t20`, Android 15, 1280×720) del Toyota Corolla 2013.
Kotlin + Jetpack Compose. Funciona sin Internet. Solo muestra datos reales.

## Qué muestra (todo real)

| Elemento | Fuente |
|---|---|
| Hora y fecha | Sistema |
| Bluetooth / Wi-Fi (barra superior) | Estado real del sistema; sin batería ni señal ficticias |
| Velocidad | GPS de la radio. Sin señal: `— km/h` |
| Música Bluetooth | MediaSession de la app **Música BT** configurada (prioridad) |
| Android Auto, Radio FM, Teléfono BT, Ecualizador | Apps de la radio elegidas en Ajustes |

Se eliminaron OBD-II, CANBUS, datos simulados y la pantalla Vehículo.

## Navegación

Barra lateral: **Inicio · Música · Android Auto · Aplicaciones · Ajustes**.
El centro de control se abre tocando los iconos de Bluetooth/Wi-Fi (arriba a la derecha) o desde Ajustes.

## Configuración inicial en la radio

1. Al abrir por primera vez se piden **Ubicación** (velocidad) y **Dispositivos cercanos** (estado Bluetooth).
2. Tarjeta de música → **Permitir** (acceso multimedia).
3. **Ajustes → Aplicaciones del sistema**: para Android Auto, Radio FM, Música BT, Teléfono BT y Ecualizador,
   toca **Probar** para comprobar y **Usar** para guardar. Las sugeridas aparecen primero.
   (En la VECHTEL: Radio = `com.imotor.fmam`, Música BT = `com.imotor.btmusic`.)
4. **Ajustes → Aplicaciones favoritas** para la pantalla Aplicaciones.
5. **Ajustes → Aplicación de inicio** para usarla como launcher (reversible por la misma ruta).

Si Android Auto o Radio no están configurados, tocar su tarjeta abre directamente su selector.

## Seguridad al conducir

- Velocidad GPS real ≥ 8 km/h: se bloquean favoritas, selección de apps y etiqueta de radio.
- Sin GPS: antes de esos cambios se pide confirmar que el vehículo está detenido (válido 5 min).
- Nunca se bloquean música, Android Auto, Radio ni Inicio.

## Carátula (sin Internet)

1. Bitmap en MediaMetadata (`ALBUM_ART`, `ART`, `DISPLAY_ICON`)
2. URI en MediaMetadata (`ALBUM_ART_URI`, `ART_URI`, `DISPLAY_ICON_URI`) leída con ContentResolver (solo `content://`, `file://`, `android.resource://`)
3. Imagen de la notificación de **la misma app Música BT** (`largeIcon`, `EXTRA_LARGE_ICON(_BIG)`, `EXTRA_PICTURE`; se ignoran iconos < 96 px)
4. Placeholder gris carbón

Se reduce a 512 px máx. y se guarda la última carátula válida de la canción actual (evita parpadeo; se borra al cambiar de canción).

## Diagnóstico (APK debug)

**Ajustes → Información → Diagnóstico**: velocidad y permisos, Bluetooth/Wi-Fi, acceso multimedia,
todas las sesiones activas (package, estado, título, artista, duración, si hay ALBUM_ART o URI, acciones),
apps configuradas y últimos intentos fallidos de abrir apps. **Apps instaladas (técnico)** muestra package y Activity.
Logcat: etiquetas `CorollaMedia` y `CorollaLauncher`.

## Compilar

Android Studio → abrir carpeta → Gradle Sync → **Build → Generate App Bundles or APKs → Generate APKs**
→ `app/build/outputs/apk/debug/app-debug.apk`. Por terminal: `./gradlew assembleDebug` / `./gradlew assembleRelease`.

Release: crea `keystore.properties` en la raíz (storeFile, storePassword, keyAlias, keyPassword). Sin él, el release se firma con la clave debug.

## Actualizar sin perder ajustes

Instala el APK nuevo **encima** del anterior (mismo `applicationId` `com.bryan.corolladash` y misma firma = mismo equipo
y mismo tipo debug/release). Las preferencias se migran solas: la antigua opción "Bluetooth / Teléfono" pasa a
**Música BT** (si el package contiene "music") o a **Teléfono BT**; se borran las claves de OBD/CANBUS.
Si cambias de firma (otro equipo, o de debug a release) Android pide desinstalar primero y se pierden los ajustes.

## Limitaciones conocidas (Android 15, sin root)

- Bluetooth, Wi-Fi y hotspot no se pueden encender/apagar desde una app: se abre su panel.
- Shuffle/repeat no existen en la API pública de MediaSession (solo en la biblioteca compat), por eso no se muestran.
- La etiqueta de Radio FM es fija (la elige el usuario); CorollaDash no sintoniza la radio.
- En algunas radios el MCU controla el brillo e ignora el valor del sistema.

## Novedades v0.3.0

- **Teléfono** en la barra lateral: abre la app Teléfono BT configurada.
- **Aviso de llamada**: se lee de la notificación de llamada; usa sus botones reales (contestar/rechazar/colgar). Si la app no los publica, solo "Abrir". Diagnóstico → "Llamadas" muestra qué notificaciones llegan.
- **Deslizar** sobre la tarjeta de música: izquierda = siguiente, derecha = anterior.
- **Reposo** (reloj tenue + canción) y **Apagar pantalla** (negro, brillo mínimo): Centro de control, Ajustes → Pantalla y reposo, o mantener presionada la hora. Reposo automático opcional (2/5/10 min, solo detenido). Cualquier toque despierta.
- **GitHub Actions** (`.github/workflows/build-apk.yml`): cada push a `main` compila el APK debug; queda como artifact y en la rama `apk`.
- **Firma estable**: si existe `keystore/debug.keystore` (copia de `~/.android/debug.keystore`), Android Studio y GitHub firman igual y el APK se instala encima.

## Novedades v0.4.0

- **Música**: aleatorio y repetir (solo si la app Música BT los declara), álbum, botón **Letra**.
- **Letras** (`lyrics/`): LRCLIB (lrclib.net) con título, artista, álbum y duración; parser LRC propio; caché en memoria (40) y disco (300 archivos JSON en `filesDir/lyrics`); una consulta por canción; estados Buscando / Sin conexión / No encontrada / Instrumental / Error. Vista completa con línea actual centrada (blanca, semibold) y hoja desplegable de 3 líneas (deslizar hacia arriba en la parte inferior del reproductor). Con ≥ 8 km/h solo 3 líneas y sin desplazamiento. Ajustes → Música y sonido → «Mostrar función de letras». Diagnóstico → «Lyrics».
- **Aplicaciones**: fila fija «Sistema» + todas las apps instaladas con búsqueda (solo detenido).
- **Ajustes**: una lista por categorías (Conectividad, Aplicaciones del sistema, Pantalla y reposo, Música y sonido, Conducción, Sistema).
- **Home**: Corolla 2013 azul en vista 3/4 (imagen de referencia, bordes difuminados) sobre fondo casi negro.

## Novedades v0.5.0

- **Inicio en tres columnas**: Velocidad + Corolla (352 px) · Música Bluetooth (400 px) · Mapa (337 px). Abajo sin cambios.
- **Mapa**: MapLibre 11.7.0 + estilo oscuro de OpenFreeMap (gratis, sin clave; © OpenStreetMap). Sigue la posición GPS y gira con el rumbo; brújula "N"; calle actual si el sistema tiene geocodificador. Sin gestos; tocarlo abre la app de **Navegación** (nueva en Ajustes → Aplicaciones del sistema). Requiere internet; sin conexión usa las zonas en caché.
- **Botón de apagar pantalla** en la barra superior (un toque).

## Optimización (v0.6.0)

- **Usar la variante release** para el día a día: Build Variants → `release` → Build APK. Queda en `app/build/outputs/apk/release/app-release.apk`, firmada con la misma clave de debug (se instala encima). Es bastante más fluida que la de debug (R8, sin modo depuración). El Diagnóstico solo aparece en debug.
- Perfiles de arranque de Compose (`profileinstaller`), logs de depuración eliminados en release.
- Quitada la librería `material-icons-extended` (no se usaba): APK y compilación mucho más ligeros.
- Lista de apps en caché: se carga 4 s después del arranque y solo se recarga si se instala/desinstala una app.
- Fondo con rutas precalculadas; progreso a 1 actualización/s; letra recompone solo al cambiar de línea.
- Mapa a 30 fps máx. y sin mover la cámara por el ruido del GPS detenido.
- En reposo/pantalla apagada no se dibuja la interfaz (mapa y animaciones detenidos).
- Imagen del Corolla en WebP (40 KB en lugar de 210 KB).

## Novedades v0.8.0

- **Combustible** (Aplicaciones → Sistema → Combustible, o Ajustes → Conducción): registra recargas (galones, precio opcional, tanque lleno) y calcula km/galón, promedio, costo por km y km desde la última recarga. El kilometraje lo suma un servicio GPS en primer plano (`fuel/OdometerService`), también con otras apps en pantalla.
- **Clima** en la barra superior: Open-Meteo (gratis, sin registro), temperatura, icono y aviso «Lluvia en ~N min» / «Lloviendo». Se actualiza cada 15 min según la posición GPS.

## Novedades v0.9.0

- **Inicio con 3 vistas** (deslizar sobre el carro/mapa o tocar los puntos; se recuerda la última):
  completa (Velocidad · Música · Mapa, columnas iguales) · limpia (carro integrado al fondo + música sin tarjeta) · letra (solo el menú lateral).
- **Foto del Corolla** a tarjeta completa; velocidad al 70 % de opacidad.
- **Música**: tarjeta 30 % más oscura; origen "Spotify · Bluetooth · iPhone de…" cuando la radio informa el reproductor (si no, "Música Bluetooth" + nombre del teléfono).
- **Mapa**: destino con ruta, distancia y hora de llegada (Photon + OSRM, sin clave); "Ir" abre Waze/Maps. Pantalla de mapa completa: buscar, mantener presionado para marcar, Casa / Trabajo / Guardados.
- **Accesos inferiores** 45 % más bajos, sin subtítulos; Radio FM | Música BT en una tarjeta dividida.

## Optimización (v0.9.2)

- Modelos de la UI marcados `@Immutable`: Compose salta recomposiciones cuando el contenido no cambia.
- La velocidad solo se redibuja cuando cambian los km/h (no con cada posición GPS).
- Fotos del Inicio decodificadas una sola vez (`ImageCache`).
- Listas y acciones del Inicio/Mapa/Aplicaciones creadas una vez (`remember`), no en cada recomposición.
- Mapa de la tarjeta a 20 fps (30 en el mapa completo); transiciones más cortas.

## Novedades v1.0.0

- **Actualización desde la app**: cada push a `main` compila en GitHub Actions y publica `CorollaDash-latest.apk` y `version.json` en la rama `apk`. La app avisa al arrancar y se instala desde **Ajustes → Sistema → Actualizaciones**.
- **Firma fija** (`keystore/debug.keystore`): Android Studio y GitHub firman igual, así se instala encima sin perder ajustes.
- **Volumen en pasos de 10 %** y botones **+ / −** en la letra.
- **Letra en grande**: deslizar a la derecha en Música.
- **Tocar el carro** (Inicio) abre Combustible.

Descarga directa del último APK: https://github.com/rsibrianmkt-13/Toyota-Corolla-2013/raw/apk/CorollaDash-latest.apk

## Novedades v1.1.0

- **Inicio**: tarjetas más altas; velocidad más delgada con barra de nivel; mapa cuadrado decorativo + **Este viaje** (km, tiempo manejando y parte del tanque usada); música con **A continuación** (si el teléfono comparte la cola) o **Escuchadas antes**; botones de abajo de solo línea; esquinas iguales (24).
- **Vista limpia**: carro algo más pequeño, música más cerca, mismos botones de línea.
- **Música**: pantalla sin tarjeta; con carátula, fondo con sus colores muy difuminado; sin carátula, solo texto. Deslizar hacia arriba abre la letra en grande (abajo o ✕ la cierra). Pausa sin círculo.
- **Menú lateral** de alto completo; sin fondo ondulado arriba; fecha corta ("28 oct").
- **Ícono** con el logo de Toyota.
- **Música BT**: al conectar el teléfono o al tocar play sin reproductor, CorollaDash despierta la app Música BT de la radio y vuelve sola.

## Novedades v1.2.0

- **Android Auto**: si la app de proyección (csrplay…) tiene una pantalla propia de Android Auto, se abre directo ahí (Diagnóstico lista sus actividades).
- **Música**: botones de **buscar** (teclado, solo detenido) y **micrófono**. Se envía la búsqueda al reproductor por Bluetooth; si la radio no lo permite, el micrófono abre el asistente ("Ok Google").
- **Origen**: ya no aparece "Cola de reproducción" como nombre del reproductor.
- **Aplicaciones**: 6 columnas, iconos de 72 dp y nombres de 17 sp; Sistema en dos filas de accesos grandes; todo desplaza junto.
- **Letra**: línea actual 46 sp, vecinas 27/25 sp, máximo 5 líneas; el volumen lateral se oculta a los 2 s y vuelve al tocar; icono para **recargar** la letra.
- **Avisos de mensajes** (WhatsApp, Telegram, SMS) de apps instaladas en la radio; en movimiento solo el remitente.

## v1.2.1

- Volumen en la letra: barra vertical delgada (tocar o arrastrar), se oculta a los 2 s.
- Micrófono: abre directamente el asistente (Ok Google / Gemini). La lupa avisa si el teléfono no respondió a la búsqueda.

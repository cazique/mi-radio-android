# Radio Dari — app Android de radio, podcasts y noticias

App Android nativa (Kotlin + Jetpack Compose + Media3/ExoPlayer + Google Cast + Room + DataStore)
para escuchar emisoras de radio online, podcasts y noticias, con alarmas, tiempo y widgets, y
enviar la reproducción a altavoces y dispositivos Google (Chromecast, Nest, Android TV).

> Nota sobre el nombre: el paquete (`com.miradio.app`) y el repositorio (`mi-radio-android`) se
> llamaron así al principio del proyecto; de cara al usuario la app se presenta como **Radio Dari**
> (icono, notificaciones, diálogos) — es el nombre que importa, los otros dos son solo
> identificadores técnicos que no vale la pena tocar ahora (renombrar el paquete rompe la firma de
> actualizaciones in-app y el historial de releases).

## Estado del proyecto

El CI (`.github/workflows/build-apk.yml`) compila y publica un APK de depuración en cada push a
`main`, y así lleva decenas de builds consecutivos en verde. Si vas a compilarlo tú mismo con
Android Studio, la sección de más abajo explica cómo; no hay ninguna duda pendiente sobre si
compila, ya está verificado en CI de forma continua.

## Funcionalidades

- **Radio en directo**: catálogo de cientos de emisoras españolas (`remote-example/stations-espana-completo.json`),
  búsqueda, favoritos, alta/edición/borrado manual, reproducción en segundo plano con notificación y
  controles del sistema, retardo configurable del directo, temporizador para dormir.
- **Google Cast**: enviar la reproducción a Chromecast, altavoces/pantallas Nest o Android TV.
- **Catálogo remoto**: el listado de emisoras se puede ampliar con un JSON alojado donde quieras
  (GitHub raw, tu propio servidor), sin publicar una nueva versión de la app.
- **Alarmas**: alarma despertador con emisora y volumen creciente, pantalla a todo tamaño incluso
  con el móvil bloqueado, reintento a un tono del sistema si la emisora no arranca, se
  reprograma sola tras reiniciar el móvil.
- **Podcasts**: buscar y ver los más populares (API pública de iTunes, sin clave), seguir tus
  podcasts favoritos, reproducción con barra de progreso arrastrable y avance/retroceso de 15 s,
  recordando por dónde te quedaste en cada episodio.
- **Noticias**: secciones de COPE más cualquier fuente RSS propia (El Mundo, ABC, La Razón, el
  periódico de tu provincia...), pestaña "Para ti" que mezcla tus fuentes activas dando más peso a
  las que de verdad lees, último boletín informativo en audio, lectura de noticias en voz alta,
  avisos de última hora (comprobación periódica en segundo plano, desactivado por defecto), y
  portadas del día de la prensa nacional y deportiva (se abren en un navegador embebido dentro de
  la propia app).
- **Tiempo**: temperatura y previsión en tu ubicación (Open-Meteo, sin clave) con AEMET como fuente
  opcional si añades tu propia clave gratuita.
- **Widgets**: uno grande con nombre/estado/controles y esquinas redondeadas, y otro compacto de
  solo logo + reproducir/pausar, para quien quiera únicamente un icono en la pantalla de inicio.
- **Actualizaciones in-app**: la app comprueba sola si hay una versión nueva publicada en
  Releases de este repo y la instala sin pasar por ninguna tienda de apps.
- **Diagnóstico**: registro propio de la app y de sus errores, con opción de compartirlo o subirlo
  automáticamente a una URL propia (webhook, servidor personal) tras un cierre inesperado.
- **Personalización**: tema claro/oscuro/según el sistema, colores dinámicos (Material You)
  opcionales, modo simple (interfaz reducida), escala de letra propia independiente de la del
  sistema.
- **Voz y accesos directos**: "Ok Google, reproduce \<emisora\> en Radio Dari" (App Actions) y
  enlace directo `miradio://play` para retomar la última emisora sin abrir la interfaz.

## Arquitectura

```
app/src/main/java/com/miradio/app/
├── alarm/                 AlarmScheduler, AlarmReceiver, BootReceiver, pantalla de alarma sonando
├── data/
│   ├── database/          StationEntity, StationDao, AppDatabase (Room)
│   ├── remote/             StationDto + RemoteStationsService (catálogo JSON remoto)
│   └── repository/         Station/Preferences/News/Podcast/Weather Repository (DataStore + red)
├── domain/
│   ├── model/               RadioStation, PlayerUiState, NewsArticle, Podcast, NewspaperCover...
│   └── usecase/              Add/Update/Delete/ToggleFavorite/RefreshRemote/ValidateStreamUrl
├── playback/
│   ├── RadioPlayer.kt        Fachada sobre ExoPlayer + CastPlayer (Media3): radio, podcasts y boletín
│   ├── PlaybackService.kt    MediaSessionService en primer plano (notificación, Cast, widget)
│   ├── PlaybackController    Puente ViewModel -> servicio
│   ├── CastManager.kt        Recuerda el último dispositivo Cast
│   └── VoiceCommandParser     Interpreta "reproduce X" de App Actions/Asistente
├── ui/
│   ├── home/                 Pantalla principal (reloj, tiempo, tarjeta "ahora suena", buscador)
│   ├── player/                 Reproductor a pantalla completa (radio, podcast y boletín)
│   ├── news/                   Noticias, "Para ti", boletín, portadas del día
│   ├── podcast/                 Buscar, más populares, episodios
│   ├── alarm/                    Lista y edición de alarmas
│   ├── stations/                  Alta/edición/borrado de emisoras
│   ├── settings/                   Tema, catálogo remoto, diagnóstico, noticias, clima
│   ├── components/                  Piezas reutilizables (logo, onda, botón Cast…)
│   └── navigation/                   NavHost + barra inferior + transiciones
├── util/                    RssDates, DiagnosticsLog, AppUpdater, Changelog, VoiceCommandParser...
└── widget/                  RadioWidgetProvider (grande) y RadioCompactWidgetProvider (compacto)
```

No se usa Hilt/Dagger: con este tamaño de app, un contenedor manual (`AppContainer`, en
`RadioApp.kt`) es más que suficiente y mucho más fácil de leer.

## URLs de streaming utilizadas

No se han inventado URLs. Se han verificado contra un catálogo público y mantenido de streams de
radio españolas (proyecto `TDTChannels`, que a su vez enlaza con `cope.es/directos/...`):

| Emisora | URL del stream | Notas |
|---|---|---|
| **COPE Madrid** | `https://flucast09-h-cloud.flumotion.com/cope/madrid.mp3` | Coincide con el reproductor de `cope.es/directos/madrid`. MP3/Icecast servido por la CDN de Flumotion que usa Cadena COPE. |
| **COPE La Bañeza** | `https://wecast-bl01.flumotion.com/copesedes/leon.mp3` | **COPE La Bañeza no tiene un stream propio independiente** en ningún directorio de streaming público (es una emisora local muy pequeña, de la sede de León, sin presencia propia en `cope.es/directos/...`). Se usa el stream regional de **COPE León** — la sede de referencia para toda la provincia — como mejor aproximación real. Si consigues la URL exacta de la sede de La Bañeza, sustitúyela en `app/src/main/assets/stations_seed.json` (o mejor aún, en tu catálogo remoto, ver abajo). |

Ambas usan MP3 sobre HTTP/HTTPS (compatibles con Icecast/Shoutcast clásico), así que ExoPlayer las
reproduce sin necesidad de HLS. El proyecto también soporta HLS (`media3-exoplayer-hls`) por si
añades emisoras que usen `.m3u8` (varias de Onda Cero, por ejemplo).

## Catálogo remoto de emisoras

Las emisoras no están incrustadas en la interfaz. Vienen de tres sitios, en este orden de prioridad
visual (favoritos primero, luego alfabético):

1. **`assets/stations_seed.json`** — el catálogo de fábrica (COPE Madrid + COPE La Bañeza), se carga
   una sola vez en Room la primera vez que se abre la app.
2. **Añadidas a mano** desde la app (pantalla `+ Añadir emisora`).
3. **Catálogo remoto** — un JSON alojado donde quieras (GitHub raw, Gist, tu propio servidor) que se
   descarga al pulsar **Ajustes → Catálogo remoto de emisoras → Actualizar ahora**.

Para añadir COPE León, SER Madrid, Onda Cero, RNE, etc. **sin publicar una nueva versión de la APK**:

1. Edita (o copia) `remote-example/stations-remote-example.json` — ya incluye ejemplos reales de esas
   emisoras con sus streams verificados. `remote-example/stations-espana-completo.json` trae ya un
   catálogo de cientos de emisoras de toda España.
2. Súbelo a donde quieras servirlo en crudo (por ejemplo, si haces push a este repo, la URL cruda
   sería `https://raw.githubusercontent.com/<tu-usuario>/<tu-repo>/<rama>/ruta/al.json`).
3. En la app, ve a **Ajustes**, pega esa URL en "URL del JSON remoto" y pulsa **Actualizar ahora**.
4. La próxima vez que alguien abra la app y sincronice, verá las emisoras nuevas — sin tocar código.

Formato del JSON:

```json
{
  "stations": [
    {
      "id": "identificador-unico",
      "name": "Nombre de la emisora",
      "city": "Ciudad",
      "streamUrl": "https://.../stream.mp3",
      "logoUrl": "https://.../logo.png",
      "description": "Texto opcional",
      "category": "Generalista",
      "isAvailable": true
    }
  ]
}
```

Quitar una emisora del JSON remoto y volver a sincronizar también la quita de la app (las que el
usuario haya marcado como favorita mantienen esa marca si vuelve a aparecer con el mismo `id`).

## Cómo compilar el APK desde el móvil (sin PC ni Android Studio)

El repo incluye un flujo de **GitHub Actions** (`.github/workflows/build-apk.yml`) que compila el APK
en la nube cada vez que hay cambios en `main`. No necesitas Termux, SDK ni nada instalado en el
teléfono:

1. Cada `push` a `main` lanza la compilación automáticamente. Si quieres forzar una sin cambiar
   código, entra en la app o web de GitHub → este repo → pestaña **Actions** → workflow
   "Compilar APK" → **Run workflow**.
2. Espera a que termine (unos 3-5 minutos; verás un ✅ verde cuando acabe).
3. Ve a la pestaña **Releases** del repositorio (o a `github.com/cazique/mi-radio-android/releases`)
   y abre **"Última compilación (debug)"**.
4. Descarga `app-debug.apk` directamente desde ahí con el navegador del móvil — es un `.apk` suelto,
   no un `.zip`, así que no hace falta descomprimir nada.
5. Al abrir el archivo descargado, Android te pedirá permitir "instalar apps de origen desconocido"
   para Chrome (o la app que hayas usado para descargar) la primera vez. Actívalo solo para esa app
   y confirma la instalación.

Es un APK de depuración (firmado con la clave de debug automática de Android, no para subir a Play
Store), pero perfectamente instalable y funcional para probar la app en tu propio dispositivo. Una
vez instalada, la propia app comprueba sola si hay una versión más reciente publicada aquí y se
ofrece a actualizarse, sin tener que repetir estos pasos cada vez.

## Cómo compilar el APK con Android Studio (en un PC)

1. Instala **Android Studio** (Ladybug o posterior) con el SDK de Android 35 y JDK 17.
2. Clona este repositorio y ábrelo como proyecto (`File → Open`).
3. Deja que Android Studio sincronice Gradle (con conexión normal a Internet descargará el Android
   Gradle Plugin 8.7.2, Jetpack Compose (BOM 2024.10.01), Media3 1.5.1 y el SDK de Cast desde los
   repositorios de Google/Maven Central).
4. Conecta un dispositivo/emulador y pulsa **Run ▶**, o genera el APK con
   `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
5. Por línea de comandos, una vez el SDK esté instalado:
   ```bash
   ./gradlew assembleDebug
   # APK en app/build/outputs/apk/debug/app-debug.apk
   ```

## Cómo probar cada función

- **Reproducción**: abre la app, pulsa play en la tarjeta de "COPE Madrid" o "COPE La Bañeza".
- **Segundo plano**: reproduce, bloquea la pantalla o cambia de app — debe seguir sonando y aparecer
  en la notificación / controles del sistema (esto lo gestiona `PlaybackService`, un
  `MediaSessionService` de Media3).
- **Cast**: con el móvil y un Chromecast/Nest en la misma red Wi-Fi, pulsa el icono de Cast (arriba a
  la derecha en Inicio y en Reproducción) y elige el dispositivo.
- **Añadir emisora**: botón `+` en Inicio → rellena nombre y URL del stream → "Comprobar stream" hace
  una petición real para validar que responde → Guardar.
- **Favoritos**: icono de corazón en cada emisora; la pestaña "Favoritos" de la barra inferior las
  filtra.
- **Catálogo remoto**: Ajustes → pega la URL de `remote-example/stations-remote-example.json` (o la
  tuya) → Actualizar ahora.
- **Alarmas**: pestaña Noticias/Ajustes → Alarmas → añade una con la emisora que quieras; suena a
  pantalla completa aunque el móvil esté bloqueado.
- **Podcasts**: pestaña Podcasts → busca uno o mira "Más populares" → ábrelo y reproduce un episodio.
- **Noticias**: pestaña Noticias → cambia entre "Para ti" y las secciones de cada medio; toca una
  portada para verla en el navegador embebido.
- **Widget**: mantén pulsado en el launcher → Widgets → Radio Dari, para verlo en la pantalla de
  inicio (hay dos: uno con controles y uno compacto de solo logo).

## Permisos usados (y por qué)

| Permiso | Motivo |
|---|---|
| `INTERNET` | Reproducir streams y descargar el catálogo remoto, noticias, podcasts, tiempo y logos. |
| `ACCESS_NETWORK_STATE` | Detectar pérdida de conexión para mostrar errores claros. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Mantener la radio sonando en segundo plano (Android 14 exige el subtipo `mediaPlayback`). |
| `WAKE_LOCK` | Que ExoPlayer no se corte si la CPU entra en reposo mientras suena. |
| `POST_NOTIFICATIONS` | Mostrar la notificación de reproducción en Android 13+. |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_MULTICAST_STATE` | El SDK de Google Cast los necesita para descubrir dispositivos por mDNS en la red local. |
| `ACCESS_COARSE_LOCATION` | Tiempo en la ubicación actual (Inicio); solo aproximada, se pide únicamente al pulsar "Activar" en la tarjeta del tiempo, nunca al abrir la app. |
| `REQUEST_INSTALL_PACKAGES` | Instalar la actualización descargada dentro de la propia app (fuera de Play Store, Android sigue pidiendo confirmación al usuario). |
| `SCHEDULE_EXACT_ALARM` / `RECEIVE_BOOT_COMPLETED` / `USE_FULL_SCREEN_INTENT` | Alarmas despertador a la hora exacta, reprogramadas tras reiniciar el móvil, con pantalla a todo tamaño aunque esté bloqueado. |

No se piden permisos de contactos, almacenamiento ni cámara: no hacen falta.

## Limitaciones conocidas / siguientes pasos razonables

- COPE La Bañeza usa el stream de COPE León como mejor aproximación disponible (ver tabla de URLs).
- Las portadas de prensa se muestran abriendo una búsqueda de imágenes en un navegador embebido
  dentro de la app, en vez de una miniatura descargada directamente: se intentó enlazar la imagen
  de un agregador externo (kiosko.net) y no se consiguió cargar de forma fiable.
- AEMET (fuente opcional de tiempo) se ha implementado de forma defensiva porque no se ha podido
  verificar contra una clave real en el entorno de desarrollo; si el formato de su respuesta
  cambia, como mucho no aparece ese dato en la comparación, sin afectar a Open-Meteo.
- El widget usa `RemoteViews` clásico (máxima compatibilidad); si prefieres Glance (Compose) para el
  widget, la lógica de `RadioWidgetProvider`/`RadioCompactWidgetProvider` se traslada casi tal cual.
- No hay firma de release ni ficha en ninguna tienda de apps: la app se distribuye como APK de
  depuración desde las Releases de este repositorio, con actualización in-app propia.

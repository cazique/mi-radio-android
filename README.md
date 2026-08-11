# Mi Radio — app Android de radio por Internet

App Android nativa (Kotlin + Jetpack Compose + Media3/ExoPlayer + Google Cast + Room) para escuchar
emisoras de radio online y enviarlas a altavoces y dispositivos Google (Chromecast, Nest, Android TV).
Incluye de fábrica **COPE La Bañeza** y **COPE Madrid**, y un catálogo de emisoras ampliable por JSON
remoto sin publicar una nueva versión de la app.

## Estado de este entregable

Este proyecto se ha escrito completo (Gradle, manifest, código Kotlin, recursos, layouts) en un
entorno **sin Android SDK y sin acceso a `dl.google.com`** (el repositorio Maven de Google, de donde
salen el Android Gradle Plugin, Jetpack y Compose, está bloqueado por la red de este entorno). Eso
significa que **no ha sido posible ejecutar `./gradlew assembleDebug` aquí** para verificar la
compilación real. El código se ha escrito y revisado con mucho cuidado (APIs de Media3 1.4.x, Cast
Framework 21.x, Compose BOM 2024.10, Room 2.6, arquitectura MVVM estándar), pero **el primer build en
tu máquina, con Android Studio y conexión normal a Internet, es el que debe validarlo de verdad**. Si
algo no compila a la primera, casi seguro es un detalle menor de versión de alguna dependencia — la
sección de compilación de abajo explica cómo abrirlo y qué mirar.

## Arquitectura

```
app/src/main/java/com/miradio/app/
├── data/
│   ├── model/            (vacío: el modelo de dominio vive en domain/model)
│   ├── database/         StationEntity, StationDao, AppDatabase (Room)
│   ├── remote/           StationDto + RemoteStationsService (catálogo JSON remoto)
│   └── repository/       StationRepository, PreferencesRepository (DataStore)
├── domain/
│   ├── model/             RadioStation, PlayerUiState, ThemeMode
│   └── usecase/           Add/Update/Delete/ToggleFavorite/RefreshRemote/ValidateStreamUrl
├── playback/
│   ├── RadioPlayer.kt      Fachada sobre ExoPlayer + CastPlayer (Media3)
│   ├── PlaybackService.kt  MediaSessionService en primer plano
│   ├── PlaybackController  Puente ViewModel -> servicio
│   ├── CastManager.kt      Recuerda el último dispositivo Cast
│   └── CastOptionsProvider Configuración del SDK de Google Cast
├── ui/
│   ├── home/               Pantalla principal (tarjeta "ahora suena", buscador, lista)
│   ├── player/              Pantalla de reproducción a pantalla completa
│   ├── stations/            Alta/edición/borrado de emisoras
│   ├── settings/             Tema, catálogo remoto
│   ├── components/           Piezas reutilizables (logo, onda, botón Cast…)
│   └── navigation/            NavHost + barra inferior
└── MainActivity.kt
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

## Catálogo remoto de emisoras (la mejora pedida)

Las emisoras no están incrustadas en la interfaz. Vienen de tres sitios, en este orden de prioridad
visual (favoritos primero, luego alfabético):

1. **`assets/stations_seed.json`** — el catálogo de fábrica (COPE Madrid + COPE La Bañeza), se carga
   una sola vez en Room la primera vez que se abre la app.
2. **Añadidas a mano** desde la app (pantalla `+ Añadir emisora`).
3. **Catálogo remoto** — un JSON alojado donde quieras (GitHub raw, Gist, tu propio servidor) que se
   descarga al pulsar **Ajustes → Catálogo remoto de emisoras → Actualizar ahora**.

Para añadir COPE León, SER Madrid, Onda Cero, RNE, etc. **sin publicar una nueva versión de la APK**:

1. Edita (o copia) `remote-example/stations-remote-example.json` — ya incluye ejemplos reales de esas
   emisoras con sus streams verificados.
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
Store), pero perfectamente instalable y funcional para probar la app en tu propio dispositivo.

## Cómo compilar el APK con Android Studio (en un PC)

1. Instala **Android Studio** (Ladybug o posterior) con el SDK de Android 35 y JDK 17.
2. Clona este repositorio y ábrelo como proyecto (`File → Open`).
3. Deja que Android Studio sincronice Gradle (con conexión normal a Internet descargará el Android
   Gradle Plugin, Jetpack Compose, Media3 y el SDK de Cast desde los repositorios de Google/Maven
   Central — en este entorno de desarrollo esos dominios estaban bloqueados, así que esta sincronización
   **no se ha podido probar aquí**).
4. Genera el icono de lanzador para todas las densidades con **Image Asset Studio**
   (clic derecho en `res` → `New → Image Asset`) si quieres reemplazar el icono vectorial incluido;
   ya hay un icono adaptativo (`mipmap-anydpi-v26`) válido para Android 8+, pero faltan los PNG de
   fallback para Android 7 (API 24-25).
5. Conecta un dispositivo/emulador y pulsa **Run ▶**, o genera el APK con
   `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
6. Por línea de comandos, una vez el SDK esté instalado:
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
- **Widget**: mantén pulsado en el launcher → Widgets → Mi Radio, para verlo en la pantalla de inicio
  con play/pausa y "siguiente emisora".

## Permisos usados (y por qué)

| Permiso | Motivo |
|---|---|
| `INTERNET` | Reproducir streams y descargar el catálogo remoto/logos. |
| `ACCESS_NETWORK_STATE` | Detectar pérdida de conexión para mostrar errores claros. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Mantener la radio sonando en segundo plano (Android 14 exige el subtipo `mediaPlayback`). |
| `WAKE_LOCK` | Que ExoPlayer no se corte si la CPU entra en reposo mientras suena. |
| `POST_NOTIFICATIONS` | Mostrar la notificación de reproducción en Android 13+. |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_MULTICAST_STATE` | El SDK de Google Cast los necesita para descubrir dispositivos por mDNS en la red local. |

No se piden permisos de ubicación, contactos, almacenamiento ni cámara: no hacen falta.

## Limitaciones conocidas / siguientes pasos razonables

- No se ha podido compilar en este entorno (ver "Estado de este entregable" arriba): revísalo en
  Android Studio antes de darlo por definitivo.
- Faltan los iconos de lanzador PNG para Android 7 (API 24-25); el icono adaptativo vectorial cubre
  Android 8+ (la inmensa mayoría de dispositivos activos hoy).
- COPE La Bañeza usa el stream de COPE León como mejor aproximación disponible (ver tabla de URLs).
- El widget usa `RemoteViews` clásico (máxima compatibilidad); si prefieres Glance (Compose) para el
  widget, la lógica de `RadioWidgetProvider` se traslada casi tal cual.

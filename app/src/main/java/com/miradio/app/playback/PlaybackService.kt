package com.miradio.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.android.gms.cast.framework.CastContext
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.miradio.app.R
import com.miradio.app.data.database.AppDatabase
import com.miradio.app.data.repository.PreferencesRepository
import com.miradio.app.data.repository.StationRepository
import com.miradio.app.domain.model.OutputDevice
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.util.DiagnosticsLog
import com.miradio.app.widget.RadioWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "PlaybackService"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "miradio_playback"
private const val CMD_PREVIOUS_STATION = "com.miradio.app.command.PREVIOUS_STATION"
private const val CMD_NEXT_STATION = "com.miradio.app.command.NEXT_STATION"

/**
 * Servicio en primer plano que mantiene viva la reproducción cuando la app
 * pasa a segundo plano, la pantalla se bloquea o se usa otra aplicación.
 * Al ser un [MediaSessionService], Media3 gestiona automáticamente la
 * notificación multimedia y los controles del sistema (pantalla de bloqueo,
 * auriculares Bluetooth, Android Auto) a partir de la [MediaSession].
 */
class PlaybackService : MediaSessionService() {

    private lateinit var radioPlayer: RadioPlayer
    private lateinit var mediaSession: MediaSession

    // Repositorio propio y ligero, independiente del AppContainer de la UI,
    // que solo se usa para resolver "siguiente emisora" desde el widget.
    private val stationRepository by lazy { StationRepository(this, AppDatabase.getInstance(this).stationDao()) }
    private val preferencesRepository by lazy { PreferencesRepository(this) }
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // La app no reproduce una lista/cola de Media3 (cada emisora sustituye a
    // la anterior), así que "anterior"/"siguiente" no son los comandos
    // estándar de la sesión (que exigen una cola real): se publican como
    // botones personalizados en la notificación multimedia, y el reproductor
    // del sistema (pantalla de bloqueo, coche, reloj) los ofrece igual que si
    // lo fueran.
    // by lazy: no se evalúan hasta el primer uso real (dentro de onCreate/
    // onPostConnect). Como propiedades normales, llamar aquí a getString()
    // fallaría: el sistema construye el Service antes de adjuntarle su
    // Context (attachBaseContext), así que un inicializador de propiedad
    // corre demasiado pronto para poder usarlo.
    private val previousStationButton by lazy {
        CommandButton.Builder()
            .setDisplayName(getString(R.string.player_previous_station))
            .setSessionCommand(SessionCommand(CMD_PREVIOUS_STATION, Bundle.EMPTY))
            .setIconResId(android.R.drawable.ic_media_previous)
            .build()
    }

    private val nextStationButton by lazy {
        CommandButton.Builder()
            .setDisplayName(getString(R.string.player_next_station))
            .setSessionCommand(SessionCommand(CMD_NEXT_STATION, Bundle.EMPTY))
            .setIconResId(android.R.drawable.ic_media_next)
            .build()
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            // Se parte SIEMPRE de lo que el propio Media3 concede por defecto
            // (super.onConnect) y solo se añaden los dos comandos nuevos para
            // la notificación; sustituir los comandos de reproducción por un
            // conjunto propio (como se hacía antes) podía dejar sin
            // play/pausa u otros controles a la notificación, la pantalla de
            // bloqueo o Android Auto.
            val defaultResult = super.onConnect(session, controller)
            if (!session.isMediaNotificationController(controller)) return defaultResult
            val sessionCommands = defaultResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(CMD_PREVIOUS_STATION, Bundle.EMPTY))
                .add(SessionCommand(CMD_NEXT_STATION, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(sessionCommands, defaultResult.availablePlayerCommands)
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            if (session.isMediaNotificationController(controller)) {
                session.setCustomLayout(controller, listOf(previousStationButton, nextStationButton))
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_PREVIOUS_STATION -> handlePreviousStation()
                CMD_NEXT_STATION -> handleNextStation()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /**
         * Se dispara con "Ok Google, reproduce <frase> en Radio Dari": es el
         * mismo mecanismo con el que Spotify o TuneIn responden a "pon
         * <canción> en Spotify". Android entrega la frase completa tal cual
         * (sin analizar) en requestMetadata.searchQuery; aquí se interpreta
         * con VoiceCommandParser para reconocer emisora y/o retardo, y se
         * reproduce mediante el propio RadioPlayer para que el resto de la
         * app (favoritos, widget, registro, Cast) se entere del cambio.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val query = mediaItems.firstOrNull()?.requestMetadata?.searchQuery
            if (query.isNullOrBlank() || !::radioPlayer.isInitialized) {
                return super.onAddMediaItems(mediaSession, controller, mediaItems)
            }
            DiagnosticsLog.log(this@PlaybackService, "PlaybackService", "Comando de voz: \"$query\"")
            handleVoiceCommand(query)
            val resolved = radioPlayer.activePlayer.currentMediaItem?.let { mutableListOf(it) } ?: mediaItems
            return Futures.immediateFuture(resolved)
        }
    }

    private fun handleVoiceCommand(query: String) {
        when (val command = VoiceCommandParser.parse(query)) {
            is VoiceCommand.SetDelay -> {
                radioPlayer.setPlaybackDelaySeconds(command.delaySeconds)
                serviceScope.launch { preferencesRepository.setPlaybackDelaySeconds(command.delaySeconds) }
            }
            VoiceCommand.ClearDelay -> {
                radioPlayer.setPlaybackDelaySeconds(0)
                serviceScope.launch { preferencesRepository.setPlaybackDelaySeconds(0) }
            }
            is VoiceCommand.PlayStation -> {
                serviceScope.launch {
                    val stations = stationRepository.stations.first()
                    val station = VoiceCommandParser.findBestMatch(stations, command.stationQuery)
                    if (station != null) {
                        radioPlayer.playStation(station)
                        preferencesRepository.setLastStation(station.id)
                        command.delaySeconds?.let {
                            radioPlayer.setPlaybackDelaySeconds(it)
                            preferencesRepository.setPlaybackDelaySeconds(it)
                        }
                    } else {
                        DiagnosticsLog.log(this@PlaybackService, "PlaybackService", "Comando de voz: emisora no reconocida (\"${command.stationQuery}\")")
                    }
                }
            }
            VoiceCommand.Unrecognized -> Unit
        }
    }

    override fun onCreate() {
        super.onCreate()
        DiagnosticsLog.log(this, "PlaybackService", "onCreate")

        // Android exige llamar a startForeground() casi inmediatamente
        // después de startForegroundService() (unos segundos); si el resto
        // del arranque (CastContext, ExoPlayer, MediaSession) tarda más que
        // eso, el sistema mata la app con ForegroundServiceDidNotStartInTimeException.
        // Por eso publicamos aquí una notificación provisional ya mismo, y
        // luego dejamos que Media3 la sustituya por la real con los datos
        // de la emisora en reproducción.
        //
        // Reportado con FATAL en el registro (Android 12+): el sistema
        // puede negar el paso a primer plano con
        // ForegroundServiceStartNotAllowedException si decide que el
        // proceso no tiene ya suficiente "recencia" con el usuario en ese
        // instante (una carrera conocida de Android, no algo que la app
        // pueda evitar del todo). Antes esto tumbaba toda la app; ahora se
        // para el servicio con orden y se sale de onCreate() sin montar
        // nada más, en vez de dejar que la excepción se propague.
        if (!startForegroundImmediately()) {
            stopSelf()
            return
        }

        // Si algo de lo siguiente falla, la notificación provisional de
        // arriba se quedaría pegada para siempre (sin emisora, sin
        // controles: el fallo reportado por usuarios). Mejor dejar
        // constancia del motivo exacto en el registro y parar el servicio
        // con orden que quedarse a medio montar de forma silenciosa.
        try {
            val castContext = try {
                CastContext.getSharedInstance(this)
            } catch (e: Exception) {
                // Dispositivos sin Google Play Services (algunos emuladores, Android TV
                // sin GMS, etc.) no deben impedir que la radio suene localmente.
                Log.w(TAG, "No se pudo inicializar CastContext: ${e.message}")
                null
            }

            radioPlayer = RadioPlayer(this, castContext, preferencesRepository)
            radioPlayer.onActivePlayerChanged = { newPlayer -> mediaSession.player = newPlayer }

            mediaSession = MediaSession.Builder(this, radioPlayer.activePlayer)
                .setId("MiRadioSession")
                .setCallback(sessionCallback)
                .build()

            // Imprescindible: sin registrar la sesión aquí, el propio
            // MediaSessionService nunca se entera de que existe para
            // gestionarle la notificación (aunque onGetSession() la
            // devuelva igualmente a los controladores que se conectan). Sin
            // esta llamada, createNotification() de abajo no se invocaba
            // nunca -algo confirmado en varios días de registros reales
            // donde su log de diagnóstico jamás aparecía-, así que la
            // notificación con controles (portada, pausa, siguiente...)
            // no llegaba a mostrarse: solo quedaba la provisional genérica
            // publicada en startForegroundImmediately().
            addSession(mediaSession)
            DiagnosticsLog.log(this, "PlaybackService", "addSession() registrada")

            // A partir de aquí, Media3 gestiona la notificación real (portada,
            // controles) reutilizando el mismo canal e id que la provisional.
            // Envuelta en OngoingWhilePlayingNotificationProvider para que no
            // se pueda deslizar y quitar mientras la radio sigue sonando (ver
            // su documentación más abajo).
            setMediaNotificationProvider(
                OngoingWhilePlayingNotificationProvider(
                    context = this,
                    delegate = DefaultMediaNotificationProvider.Builder(this)
                        .setChannelId(CHANNEL_ID)
                        .setChannelName(R.string.playback_channel_name)
                        .setNotificationId(NOTIFICATION_ID)
                        .build(),
                ),
            )

            PlaybackServiceConnector.attach(radioPlayer)

            serviceScope.launch {
                // distinctUntilChanged: uiState cambia por muchos motivos que no
                // afectan al widget (canción "ahora suena" vía ICY, temporizador
                // de apagado contando segundo a segundo, retardo del directo...).
                // Sin esto, se repintaba el widget en cada uno de esos cambios,
                // muchas veces por minuto sin necesidad.
                radioPlayer.uiState
                    .map { Triple(it.station?.name, it.status, it.station?.logoUrl) }
                    .distinctUntilChanged()
                    .collect { (stationName, status, logoUrl) ->
                        runCatching { RadioWidgetProvider.updateAll(this@PlaybackService, stationName, status, logoUrl) }
                            .onFailure { DiagnosticsLog.logThrowable(this@PlaybackService, "PlaybackService", "Fallo actualizando el widget", it) }
                    }
            }

            serviceScope.launch {
                val storedDelay = runCatching { preferencesRepository.playbackDelaySeconds.first() }
                    .onFailure { DiagnosticsLog.logThrowable(this@PlaybackService, "PlaybackService", "Fallo leyendo el retardo guardado", it) }
                    .getOrDefault(0)
                if (storedDelay > 0) radioPlayer.setPlaybackDelaySeconds(storedDelay)
            }
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(this, "PlaybackService", "Fallo inicializando el servicio de reproducción", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY_PAUSE -> handleTogglePlayPause()
            ACTION_NEXT_STATION -> handleNextStation()
            ACTION_PREVIOUS_STATION -> handlePreviousStation()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleTogglePlayPause() {
        if (!::radioPlayer.isInitialized) return
        val status = radioPlayer.uiState.value.status
        DiagnosticsLog.log(this, "PlaybackService", "handleTogglePlayPause() estado=$status")
        if (status == PlaybackStatus.PLAYING || status == PlaybackStatus.BUFFERING) {
            radioPlayer.pause()
        } else {
            radioPlayer.play()
        }
    }

    private fun handlePreviousStation() {
        if (!::radioPlayer.isInitialized) return
        DiagnosticsLog.log(this, "PlaybackService", "handlePreviousStation()")
        serviceScope.launch {
            val stations = stationRepository.stations.first()
            if (stations.isEmpty()) return@launch
            val currentId = radioPlayer.uiState.value.station?.id
            val currentIndex = stations.indexOfFirst { it.id == currentId }
            val previous = stations[(currentIndex - 1 + stations.size) % stations.size]
            radioPlayer.playStation(previous)
        }
    }

    private fun handleNextStation() {
        if (!::radioPlayer.isInitialized) return
        DiagnosticsLog.log(this, "PlaybackService", "handleNextStation()")
        serviceScope.launch {
            val stations = stationRepository.stations.first()
            if (stations.isEmpty()) return@launch
            val currentId = radioPlayer.uiState.value.station?.id
            val currentIndex = stations.indexOfFirst { it.id == currentId }
            val next = stations[(currentIndex + 1 + stations.size) % stations.size]
            radioPlayer.playStation(next)
        }
    }

    /** @return false si Android ha rechazado el paso a primer plano (en vez
     *  de dejar que la excepción tumbe toda la app), para que [onCreate]
     *  pueda parar el servicio con orden en ese caso. */
    private fun startForegroundImmediately(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService<NotificationManager>()?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .build()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
            }
            true
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(this, "PlaybackService", "Rechazado el paso a primer plano", e)
            false
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        DiagnosticsLog.log(this, "PlaybackService", "onGetSession(${controllerInfo.packageName})")
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        DiagnosticsLog.log(this, "PlaybackService", "onTaskRemoved")
        // Si se está emitiendo por Cast, cerrar la app desde "recientes" NUNCA
        // debe cortar la reproducción: todo el sentido de Cast es que el
        // altavoz siga sonando aunque el teléfono se apague o cierres la app.
        // Sin esta comprobación, el servicio podía autodestruirse (y con él
        // la sesión de Cast) justo al cerrar la app tras activar el altavoz.
        val isCasting = ::radioPlayer.isInitialized && radioPlayer.uiState.value.outputDevice == OutputDevice.CAST
        if (!isCasting &&
            (!::radioPlayer.isInitialized ||
                !radioPlayer.activePlayer.playWhenReady ||
                radioPlayer.activePlayer.mediaItemCount == 0)
        ) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        DiagnosticsLog.log(this, "PlaybackService", "onDestroy")
        PlaybackServiceConnector.detach()
        serviceScope.cancel()
        if (::mediaSession.isInitialized) {
            removeSession(mediaSession)
            mediaSession.release()
        }
        if (::radioPlayer.isInitialized) radioPlayer.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.miradio.app.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT_STATION = "com.miradio.app.action.NEXT_STATION"
        const val ACTION_PREVIOUS_STATION = "com.miradio.app.action.PREVIOUS_STATION"
    }
}

/**
 * A partir de Android 14, una notificación de servicio en primer plano ya no
 * es "fija" por defecto: el usuario puede deslizarla y quitarla, y la radio
 * se queda sonando de fondo sin ninguna notificación con la que volver a
 * ella o pausarla (justo lo que se reportaba: "al borrarla, desaparece
 * aunque siga sonando"). Se envuelve el proveedor por defecto de Media3 para
 * fijar esa notificación (FLAG_ONGOING_EVENT) mientras haya reproducción
 * activa, y dejarla deslizable en cuanto se pausa o se para, como en
 * cualquier reproductor de música.
 */
@androidx.media3.common.util.UnstableApi
private class OngoingWhilePlayingNotificationProvider(
    private val context: android.content.Context,
    private val delegate: DefaultMediaNotificationProvider,
) : MediaNotification.Provider by delegate {
    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val result = delegate.createNotification(mediaSession, customLayout, actionFactory, onNotificationChangedCallback)
        // OJO: Player.isPlaying exige STATE_READY; en un stream de radio en
        // directo, un simple bache de red pone al reproductor en
        // STATE_BUFFERING con isPlaying=false aunque el usuario lo siga
        // "teniendo puesto" (playWhenReady sigue en true). Con isPlaying a
        // secas, la notificación se volvía deslizable en cada rebuffering
        // y se podía cerrar aunque la radio siguiera con intención de sonar
        // (justo lo reportado: "se puede borrar mientras reproduce").
        val player = mediaSession.player
        val isActive = player.playWhenReady && player.playbackState != Player.STATE_IDLE
        result.notification.flags = if (isActive) {
            result.notification.flags or Notification.FLAG_ONGOING_EVENT
        } else {
            result.notification.flags and Notification.FLAG_ONGOING_EVENT.inv()
        }
        // Diagnóstico temporal: para poder confirmar en el próximo registro
        // si esto es lo único que fallaba o si, además, Media3 tarda en
        // sustituir la notificación provisional por esta de verdad.
        DiagnosticsLog.log(
            context,
            "PlaybackService",
            "Notificación actualizada: título=\"${player.mediaMetadata.title}\" activa=$isActive estado=${player.playbackState}",
        )
        return result
    }
}

package com.miradio.app.playback

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
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.android.gms.cast.framework.CastContext
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.miradio.app.R
import com.miradio.app.data.database.AppDatabase
import com.miradio.app.data.repository.PreferencesRepository
import com.miradio.app.data.repository.StationRepository
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.util.DiagnosticsLog
import com.miradio.app.widget.RadioWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_PREVIOUS_STATION, Bundle.EMPTY))
                .add(SessionCommand(CMD_NEXT_STATION, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(sessionCommands, MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
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
        startForegroundImmediately()

        val castContext = try {
            CastContext.getSharedInstance(this)
        } catch (e: Exception) {
            // Dispositivos sin Google Play Services (algunos emuladores, Android TV
            // sin GMS, etc.) no deben impedir que la radio suene localmente.
            Log.w(TAG, "No se pudo inicializar CastContext: ${e.message}")
            null
        }

        radioPlayer = RadioPlayer(this, castContext)
        radioPlayer.onActivePlayerChanged = { newPlayer -> mediaSession.player = newPlayer }

        mediaSession = MediaSession.Builder(this, radioPlayer.activePlayer)
            .setId("MiRadioSession")
            .setCallback(sessionCallback)
            .build()

        // A partir de aquí, Media3 gestiona la notificación real (portada,
        // controles) reutilizando el mismo canal e id que la provisional.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .setNotificationId(NOTIFICATION_ID)
                .build(),
        )

        PlaybackServiceConnector.attach(radioPlayer)

        serviceScope.launch {
            radioPlayer.uiState.collect { state ->
                RadioWidgetProvider.updateAll(this@PlaybackService, state.station?.name, state.status)
            }
        }

        serviceScope.launch {
            val storedDelay = preferencesRepository.playbackDelaySeconds.first()
            if (storedDelay > 0) radioPlayer.setPlaybackDelaySeconds(storedDelay)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY_PAUSE -> handleTogglePlayPause()
            ACTION_NEXT_STATION -> handleNextStation()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleTogglePlayPause() {
        if (!::radioPlayer.isInitialized) return
        val status = radioPlayer.uiState.value.status
        if (status == PlaybackStatus.PLAYING || status == PlaybackStatus.BUFFERING) {
            radioPlayer.pause()
        } else {
            radioPlayer.play()
        }
    }

    private fun handlePreviousStation() {
        if (!::radioPlayer.isInitialized) return
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
        serviceScope.launch {
            val stations = stationRepository.stations.first()
            if (stations.isEmpty()) return@launch
            val currentId = radioPlayer.uiState.value.station?.id
            val currentIndex = stations.indexOfFirst { it.id == currentId }
            val next = stations[(currentIndex + 1 + stations.size) % stations.size]
            radioPlayer.playStation(next)
        }
    }

    private fun startForegroundImmediately() {
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        DiagnosticsLog.log(this, "PlaybackService", "onTaskRemoved")
        // Si no está sonando nada cuando se cierra la app desde "recientes",
        // no tiene sentido mantener el servicio ni la notificación con vida.
        if (!::radioPlayer.isInitialized ||
            !radioPlayer.activePlayer.playWhenReady ||
            radioPlayer.activePlayer.mediaItemCount == 0
        ) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        DiagnosticsLog.log(this, "PlaybackService", "onDestroy")
        PlaybackServiceConnector.detach()
        serviceScope.cancel()
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::radioPlayer.isInitialized) radioPlayer.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.miradio.app.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT_STATION = "com.miradio.app.action.NEXT_STATION"
    }
}

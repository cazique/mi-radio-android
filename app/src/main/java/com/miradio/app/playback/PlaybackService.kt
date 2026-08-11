package com.miradio.app.playback

import android.content.Intent
import android.util.Log
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.android.gms.cast.framework.CastContext
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

    override fun onCreate() {
        super.onCreate()
        DiagnosticsLog.log(this, "PlaybackService", "onCreate")

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
            .build()

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

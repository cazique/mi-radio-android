package com.miradio.app.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.framework.CastContext
import com.miradio.app.data.repository.PreferencesRepository
import com.miradio.app.domain.model.OutputDevice
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.domain.model.PlayerUiState
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.util.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "RadioPlayer"
private val RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 10_000L)

/**
 * Envuelve un [ExoPlayer] local y un [CastPlayer] y expone siempre un único
 * [Player] activo ([activePlayer]) más un [StateFlow] con el estado que la UI
 * necesita. Cuando el usuario conecta con un dispositivo Cast, la
 * reproducción transfiere la emisora del móvil al altavoz (hay una breve
 * transición entre reproductores, no es instantáneo).
 */
class RadioPlayer(
    private val context: Context,
    private val castContext: CastContext?,
    private val preferencesRepository: PreferencesRepository,
) {
    var localPlayer: ExoPlayer = buildLocalPlayer(0)
        private set

    private val castPlayer: CastPlayer? = castContext?.let { CastPlayer(it) }

    var activePlayer: Player = localPlayer
        private set

    private var currentStation: RadioStation? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sleepTimerJob: Job? = null
    private var retryJob: Job? = null
    private var retryAttempt = 0

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    /** Notifica al servicio (MediaSession) cada vez que cambia el reproductor activo. */
    var onActivePlayerChanged: ((Player) -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateStatusFromPlayer()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateStatusFromPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback error: ${error.errorCodeName}", error)
            DiagnosticsLog.logThrowable(context, "RadioPlayer", "onPlayerError (${error.errorCodeName})", error)
            val playbackError = mapExoError(error)
            _uiState.update { it.copy(status = PlaybackStatus.ERROR, errorMessage = playbackError.messageKey) }
            scheduleRetry()
        }
    }

    /**
     * Reintenta la emisora actual con backoff (2 s, 5 s, 10 s) antes de
     * rendirse y dejar el error visible. Sin esto, un simple corte de red
     * (Wi-Fi ↔ datos, un segundo de mala cobertura) obliga al usuario a
     * pulsar play a mano cada vez.
     */
    private fun scheduleRetry() {
        val station = currentStation ?: return
        if (retryAttempt >= RETRY_DELAYS_MS.size) return
        val delayMs = RETRY_DELAYS_MS[retryAttempt]
        retryAttempt += 1
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMs)
            if (currentStation?.id != station.id) return@launch // el usuario cambió de emisora mientras tanto
            DiagnosticsLog.log(context, "RadioPlayer", "Reintentando ${station.id} (intento $retryAttempt)")
            activePlayer.setMediaItem(buildMediaItem(station))
            activePlayer.prepare()
            activePlayer.playWhenReady = true
        }
    }

    init {
        localPlayer.addListener(playerListener)
        castPlayer?.addListener(playerListener)
        castPlayer?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                switchTo(castPlayer, OutputDevice.CAST, castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName)
            }

            override fun onCastSessionUnavailable() {
                switchTo(localPlayer, OutputDevice.PHONE, null)
            }
        })
    }

    private fun switchTo(newPlayer: Player, device: OutputDevice, castDeviceName: String?) {
        if (newPlayer === activePlayer) return
        DiagnosticsLog.log(context, "RadioPlayer", "switchTo($device, $castDeviceName)")
        val previousPlayer = activePlayer
        val station = currentStation
        val wasPlaying = previousPlayer.isPlaying
        val position = if (device == OutputDevice.CAST) 0L else previousPlayer.currentPosition

        // ExoPlayer (móvil) y CastPlayer son objetos independientes: cambiar
        // cuál es "activePlayer" no detiene al anterior por sí solo. Sin esto,
        // al pasar a Cast el teléfono seguía sonando de fondo a la vez que el
        // altavoz.
        previousPlayer.playWhenReady = false
        previousPlayer.stop()

        activePlayer = newPlayer
        _uiState.update { it.copy(outputDevice = device, castDeviceName = castDeviceName) }
        onActivePlayerChanged?.invoke(newPlayer)

        if (station != null) {
            newPlayer.setMediaItem(buildMediaItem(station), position)
            newPlayer.prepare()
            newPlayer.playWhenReady = wasPlaying
        }
    }

    /**
     * ExoPlayer no permite cambiar el [androidx.media3.exoplayer.LoadControl]
     * de una instancia ya creada, así que "retrasar" el directo se consigue
     * reconstruyendo el reproductor local con un búfer de arranque igual al
     * retardo deseado: al reproducir a velocidad normal mientras se sigue
     * ingiriendo el stream (que llega a ritmo real, no más rápido), ese
     * desfase se mantiene constante mientras dure la escucha.
     */
    private fun buildLocalPlayer(delaySeconds: Int): ExoPlayer {
        val delayMs = delaySeconds.coerceIn(0, 300) * 1_000
        val bufferForPlaybackMs = (2_500).coerceAtLeast(delayMs)
        val minBufferMs = (50_000).coerceAtLeast(bufferForPlaybackMs + 10_000)
        val maxBufferMs = minBufferMs + 20_000
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackMs)
            .build()
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }

    /**
     * Cambia el retardo del directo en el móvil. No afecta a Cast, que usa su
     * propio buffer en el dispositivo receptor. Reconstruye el reproductor
     * local (breve recorte de audio, como al cambiar de emisora) y, si estaba
     * activo, retoma la emisora que sonaba.
     */
    fun setPlaybackDelaySeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 300)
        val wasActive = activePlayer === localPlayer
        val station = currentStation
        val wasPlaying = localPlayer.playWhenReady

        localPlayer.removeListener(playerListener)
        localPlayer.release()
        localPlayer = buildLocalPlayer(clamped).apply { addListener(playerListener) }

        if (wasActive) {
            activePlayer = localPlayer
            onActivePlayerChanged?.invoke(localPlayer)
            if (station != null) {
                localPlayer.setMediaItem(buildMediaItem(station))
                localPlayer.prepare()
                localPlayer.playWhenReady = wasPlaying
            }
        }
        _uiState.update { it.copy(playbackDelaySeconds = clamped) }
    }

    private fun buildMediaItem(station: RadioStation): MediaItem =
        MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(Uri.parse(station.streamUrl))
            .setMimeType(station.mimeType?.takeIf { it.isNotBlank() } ?: guessStreamMimeType(station.streamUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(station.city)
                    .apply { station.logoUrl?.let { setArtworkUri(Uri.parse(it)) } }
                    .build(),
            )
            .build()

    /**
     * ExoPlayer detecta el formato del stream él solo con el mimeType en
     * blanco, pero CastPlayer (androidx.media3.cast) lo exige explícito para
     * construir el MediaQueueItem que envía al Chromecast: sin esto, al
     * activar Cast la app entera se cerraba con
     * "IllegalArgumentException: The item must specify its mimeType".
     */
    private fun guessStreamMimeType(url: String): String {
        val path = Uri.parse(url).lastPathSegment?.lowercase().orEmpty()
        return when {
            path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".aac") -> MimeTypes.AUDIO_AAC
            path.endsWith(".ogg") || path.endsWith(".opus") -> MimeTypes.AUDIO_OGG
            // La mayoría de streams de radio (Icecast/Shoutcast) son MP3,
            // incluso cuando la URL no lleva extensión.
            else -> MimeTypes.AUDIO_MPEG
        }
    }

    fun playStation(station: RadioStation) {
        DiagnosticsLog.log(context, "RadioPlayer", "playStation(${station.id}, ${station.streamUrl})")
        retryJob?.cancel()
        retryAttempt = 0
        currentStation = station
        _uiState.update { it.copy(station = station, status = PlaybackStatus.BUFFERING, errorMessage = null) }
        activePlayer.setMediaItem(buildMediaItem(station))
        activePlayer.prepare()
        activePlayer.playWhenReady = true
        // Centralizado aquí (y no en cada pantalla/comando que puede arrancar
        // una emisora) para que "últimas escuchadas" funcione sin importar
        // si viene de un toque en la lista, de un comando de voz o del
        // enlace directo de Ok Google.
        scope.launch {
            preferencesRepository.setLastStation(station.id)
            preferencesRepository.addRecentStation(station.id)
        }
    }

    fun play() {
        if (activePlayer.playbackState == Player.STATE_IDLE) {
            currentStation?.let { playStation(it) }
        } else {
            activePlayer.playWhenReady = true
        }
    }

    fun pause() {
        activePlayer.playWhenReady = false
    }

    fun stop() {
        retryJob?.cancel()
        retryAttempt = 0
        activePlayer.stop()
        _uiState.update { it.copy(status = PlaybackStatus.STOPPED) }
        cancelSleepTimer()
    }

    /**
     * Vive aquí (en el reproductor, dentro del servicio) y no en un ViewModel
     * para que siga contando aunque el usuario navegue a otra pantalla o
     * Android recree la Activity.
     */
    fun startSleepTimer(totalSeconds: Int) {
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(sleepTimerSecondsLeft = totalSeconds) }
        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
                _uiState.update { it.copy(sleepTimerSecondsLeft = remaining) }
            }
            pause()
            _uiState.update { it.copy(sleepTimerSecondsLeft = null) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.update { it.copy(sleepTimerSecondsLeft = null) }
    }

    private fun updateStatusFromPlayer() {
        val status = when {
            activePlayer.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            activePlayer.playbackState == Player.STATE_IDLE -> PlaybackStatus.STOPPED
            activePlayer.playWhenReady && activePlayer.isPlaying -> PlaybackStatus.PLAYING
            activePlayer.playbackState == Player.STATE_READY && !activePlayer.playWhenReady -> PlaybackStatus.PAUSED
            else -> _uiState.value.status
        }
        if (status == PlaybackStatus.PLAYING && retryAttempt > 0) {
            // Se recuperó solo tras un error: se reinicia el contador de reintentos.
            retryJob?.cancel()
            retryAttempt = 0
        }
        _uiState.update { it.copy(status = status, errorMessage = null) }
    }

    private fun mapExoError(error: PlaybackException): PlaybackError = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        -> PlaybackError.StationOffline
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> PlaybackError.StreamUnavailable
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_TIMEOUT,
        -> PlaybackError.Timeout
        else -> PlaybackError.Generic
    }

    fun release() {
        DiagnosticsLog.log(context, "RadioPlayer", "release()")
        sleepTimerJob?.cancel()
        retryJob?.cancel()
        localPlayer.removeListener(playerListener)
        castPlayer?.removeListener(playerListener)
        castPlayer?.setSessionAvailabilityListener(null)
        localPlayer.release()
        castPlayer?.release()
    }
}

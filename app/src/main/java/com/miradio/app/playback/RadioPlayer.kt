package com.miradio.app.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.util.Log
import androidx.core.content.getSystemService
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.icy.IcyInfo
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
import kotlinx.coroutines.cancel
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

    private val connectivityManager = context.getSystemService<ConnectivityManager>()

    /**
     * Tras agotar los reintentos con backoff, en vez de rendirse hasta que el
     * usuario toque play a mano, se espera a que vuelva la conectividad. Sin
     * esto, un corte de red algo más largo que ~17 s (2+5+10) dejaba la radio
     * muerta para siempre aunque el Wi-Fi volviera solo segundos después.
     * onAvailable() se dispara con cualquier cambio de red, no solo cuando
     * "vuelve" tras haberse caído, así que solo actúa si de verdad hay una
     * emisora esperando en estado de error.
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (_uiState.value.status != PlaybackStatus.ERROR) return
            val station = currentStation ?: return
            DiagnosticsLog.log(context, "RadioPlayer", "Conectividad recuperada, reintentando ${station.id}")
            // registerDefaultNetworkCallback() sin Handler explícito entrega
            // los callbacks en un hilo de Android que NO es el principal;
            // ExoPlayer exige que se le acceda siempre desde el mismo hilo
            // en el que se creó (aquí, el principal). Llamar a retryNow()
            // (que toca directamente al reproductor) aquí mismo crasheaba
            // con "Player is accessed on the wrong thread" justo al volver
            // la conectividad.
            scope.launch { retryNow(station) }
        }
    }

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

        /** El volumen puede cambiar sin pasar por setVolume() de esta clase
         *  (p. ej. los botones físicos de un altavoz Cast), así que se
         *  escucha en vez de fiarse solo de lo que se manda aquí. */
        override fun onVolumeChanged(volume: Float) {
            _uiState.update { it.copy(volume = volume) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback error: ${error.errorCodeName}", error)
            DiagnosticsLog.logThrowable(context, "RadioPlayer", "onPlayerError (${error.errorCodeName})", error)
            val playbackError = mapExoError(error)
            _uiState.update { it.copy(status = PlaybackStatus.ERROR, errorMessage = playbackError.messageKey) }
            scheduleRetry()
        }

        /**
         * Icecast/Shoutcast intercalan metadatos (título "Artista - Canción")
         * dentro del propio stream de audio; Media3 los extrae solo y los
         * entrega aquí como IcyInfo. No todas las emisoras los mandan: cuando
         * no llegan, nowPlayingTitle simplemente se queda en null.
         */
        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is IcyInfo) {
                    val title = entry.title?.trim()?.takeIf { it.isNotBlank() }
                    if (title != null) {
                        DiagnosticsLog.log(context, "RadioPlayer", "ICY: \"$title\"")
                        _uiState.update { it.copy(nowPlayingTitle = title) }
                    }
                }
            }
        }
    }

    /**
     * Reintenta la emisora actual con backoff (2 s, 5 s, 10 s). Si se agotan
     * los tres intentos, no se rinde para siempre: se queda en estado de
     * error hasta que vuelva la conectividad (ver [networkCallback]) o el
     * usuario pulse play a mano.
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
            retryNow(station)
        }
    }

    private fun retryNow(station: RadioStation) {
        activePlayer.setMediaItem(buildMediaItem(station))
        activePlayer.prepare()
        activePlayer.playWhenReady = true
    }

    private var pendingSwitchJob: Job? = null

    init {
        localPlayer.addListener(playerListener)
        castPlayer?.addListener(playerListener)
        castPlayer?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                scheduleSwitch(castPlayer, OutputDevice.CAST, castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName)
            }

            override fun onCastSessionUnavailable() {
                scheduleSwitch(localPlayer, OutputDevice.PHONE, null)
            }
        })
        runCatching { connectivityManager?.registerDefaultNetworkCallback(networkCallback) }
            .onFailure { Log.w(TAG, "No se pudo registrar el callback de conectividad: ${it.message}") }
        scope.launch {
            while (true) {
                delay(500)
                tickPosition()
            }
        }
    }

    /** Se reinicia a -1 en cada [playStation] para que el primer guardado de
     *  progreso de un episodio nuevo no espere a que la posición avance 5 s
     *  desde la del episodio anterior. */
    private var lastPersistedPositionMs = -1L

    /**
     * Sondea posición/duración cada 500 ms (Media3 no las expone como Flow):
     * alimenta la barra de progreso de contenido bajo demanda (episodios de
     * podcast, boletín) y, cada ~5 s mientras se reproduce, guarda el avance
     * para poder retomar el episodio donde se dejó.
     */
    private fun tickPosition() {
        val duration = activePlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val position = activePlayer.currentPosition.coerceAtLeast(0L)
        if (_uiState.value.durationMs != duration || _uiState.value.positionMs != position) {
            _uiState.update { it.copy(positionMs = position, durationMs = duration) }
        }
        val station = currentStation
        if (duration > 0 && station != null && activePlayer.isPlaying && position - lastPersistedPositionMs >= 5_000) {
            lastPersistedPositionMs = position
            scope.launch { preferencesRepository.savePodcastProgress(station.id, position) }
        }
    }

    /** Mueve la reproducción a [positionMs]. Sin efecto en un directo de
     *  radio (Media3 simplemente lo ignora si el contenido no es buscable). */
    fun seekTo(positionMs: Long) {
        activePlayer.seekTo(positionMs.coerceAtLeast(0L))
    }

    /** Avanza/retrocede [deltaSeconds] segundos (negativo para retroceder),
     *  sin pasarse de los límites del episodio. */
    fun skipSeconds(deltaSeconds: Int) {
        val duration = activePlayer.duration.takeIf { it != C.TIME_UNSET }
        val target = activePlayer.currentPosition + deltaSeconds * 1_000L
        activePlayer.seekTo(target.coerceIn(0L, duration ?: Long.MAX_VALUE))
    }

    /**
     * Con un altavoz Cast en un grupo/red inestable, la sesión puede
     * conectarse y desconectarse varias veces seguidas en segundos (visto en
     * campo: hasta 6 veces en 2 minutos). Sin este pequeño retardo, cada
     * bandazo reiniciaba la reproducción entera (con su rebuffering) tanto en
     * el móvil como en el altavoz. Al esperar un momento y cancelar si llega
     * otro cambio antes, solo se aplica el estado en el que la sesión se
     * queda quieta de verdad.
     */
    private fun scheduleSwitch(newPlayer: Player, device: OutputDevice, castDeviceName: String?) {
        pendingSwitchJob?.cancel()
        pendingSwitchJob = scope.launch {
            delay(1_200)
            switchTo(newPlayer, device, castDeviceName)
        }
    }

    private fun switchTo(newPlayer: Player, device: OutputDevice, castDeviceName: String?) {
        if (newPlayer === activePlayer) return
        DiagnosticsLog.log(context, "RadioPlayer", "switchTo($device, $castDeviceName)")
        val previousPlayer = activePlayer
        val station = currentStation
        // OJO: no usar previousPlayer.isPlaying aquí. isPlaying solo es true
        // en STATE_READY; si el Cast se conecta justo durante un pequeño
        // rebuffering del stream (algo normal en radio en directo),
        // isPlaying da false aunque el usuario tuviera la radio sonando, y
        // el altavoz se queda con la emisora cargada pero sin arrancar
        // hasta que el usuario pausa y vuelve a dar a play a mano.
        // playWhenReady refleja la intención del usuario (play/pausa) sin
        // verse afectado por esos baches de buffering, así que es el valor
        // correcto para decidir si el nuevo reproductor debe arrancar solo.
        val wasPlaying = previousPlayer.playWhenReady
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
        // Antes el mínimo era 50 s fijos, muchísimo más de lo que necesita un
        // directo (no hay "hacia adelante" al que adelantar, así que un
        // búfer enorme no aporta nada salvo mantener el móvil trabajando más
        // de la cuenta para mantenerlo siempre lleno). 15 s de colchón es de
        // sobra para aguantar cortes breves de red sin cortarse.
        val minBufferMs = (15_000).coerceAtLeast(bufferForPlaybackMs + 10_000)
        val maxBufferMs = minBufferMs + 15_000
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackMs)
            .build()
        // Sin esto, ExoPlayer no pide el foco de audio al sistema y la radio
        // sigue sonando por encima de cualquier otra cosa: una nota de voz
        // de WhatsApp, un audio de otra app, una llamada entrante... Con
        // handleAudioFocus=true, Android avisa automáticamente y ExoPlayer
        // se pausa solo mientras dura el otro sonido (y se reanuda solo al
        // terminar, si el corte fue temporal).
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .build()
            .apply {
                // WAKE_LOCK está declarado en el manifest pero no servía de
                // nada sin esto: en varios móviles la radio se cortaba al
                // apagar la pantalla porque la CPU entraba en reposo
                // profundo a media descarga del stream.
                setWakeMode(C.WAKE_MODE_NETWORK)
                // Al desenchufar los auriculares (o cortarse el Bluetooth),
                // ExoPlayer seguía sonando a todo volumen por el altavoz en
                // vez de pausarse solo, como hace cualquier reproductor de
                // música normal.
                setHandleAudioBecomingNoisy(true)
            }
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

    /** [startPositionMs] es para retomar un episodio de podcast donde se
     *  dejó; en una emisora de radio normal se deja en 0 (no tendría sentido
     *  "reanudar" un directo). */
    fun playStation(station: RadioStation, startPositionMs: Long = 0L) {
        DiagnosticsLog.log(context, "RadioPlayer", "playStation(${station.id}, ${station.streamUrl})")
        retryJob?.cancel()
        retryAttempt = 0
        currentStation = station
        lastPersistedPositionMs = -1L
        // Se limpia aquí: si no, al cambiar de emisora se seguía viendo la
        // canción de la anterior (o de ninguna) hasta que llegara el primer
        // metadato ICY de la nueva, que puede tardar según el servidor.
        _uiState.update {
            it.copy(
                station = station,
                status = PlaybackStatus.BUFFERING,
                errorMessage = null,
                nowPlayingTitle = null,
                positionMs = startPositionMs,
                durationMs = 0L,
            )
        }
        activePlayer.setMediaItem(buildMediaItem(station), startPositionMs)
        activePlayer.prepare()
        activePlayer.playWhenReady = true
        // Centralizado aquí (y no en cada pantalla/comando que puede arrancar
        // una emisora) para que "últimas escuchadas" funcione sin importar
        // si viene de un toque en la lista, de un comando de voz o del
        // enlace directo de Ok Google. Se excluye el contenido bajo demanda
        // "de un solo uso" (episodio de podcast, boletín): no son emisoras
        // reales del catálogo, así que guardar su id aquí solo ensuciaría
        // "última escuchada"/"recientes" con algo que Inicio nunca podría
        // volver a resolver.
        if (station.category != "Podcast" && station.category != "Boletín") {
            scope.launch {
                preferencesRepository.setLastStation(station.id)
                preferencesRepository.addRecentStation(station.id)
            }
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

    /** Funciona igual reproduciendo en el móvil que en un altavoz Cast: la
     *  interfaz [Player] de Media3 expone el volumen de la misma forma en
     *  ambos casos (CastPlayer lo traduce al volumen del propio altavoz). */
    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        activePlayer.volume = clamped
        _uiState.update { it.copy(volume = clamped) }
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
            // Importante: no usar pause() aquí. pause() solo pone
            // playWhenReady a false y deja el búfer de ExoPlayer tal cual
            // (STATE_READY); al volver a dar a play() más tarde, retomaba
            // desde esa posición ya vieja en vez de saltar al directo actual
            // (reportado: "cuando la das a play, continúa desde donde se
            // quedó"). Con stop() el reproductor pasa a STATE_IDLE, así que
            // play() (ver más abajo) detecta ese estado y llama a
            // playStation() de nuevo, recargando el stream desde el directo.
            DiagnosticsLog.log(context, "RadioPlayer", "Temporizador de apagado agotado, deteniendo")
            retryJob?.cancel()
            retryAttempt = 0
            activePlayer.stop()
            _uiState.update { it.copy(status = PlaybackStatus.STOPPED, sleepTimerSecondsLeft = null) }
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
        // scope.cancel() antes que nada: cancelar sleepTimerJob/retryJob/
        // pendingSwitchJob por separado no paraba el bucle de tickPosition()
        // (while(true) { delay(500); tickPosition() } lanzado en init con
        // scope.launch, sin guardar su Job) — seguía sondeando posición
        // contra un ExoPlayer ya liberado unas líneas más abajo.
        scope.cancel()
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        localPlayer.removeListener(playerListener)
        castPlayer?.removeListener(playerListener)
        castPlayer?.setSessionAvailabilityListener(null)
        localPlayer.release()
        castPlayer?.release()
    }
}

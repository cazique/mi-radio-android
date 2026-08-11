package com.miradio.app.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.framework.CastContext
import com.miradio.app.domain.model.OutputDevice
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.domain.model.PlayerUiState
import com.miradio.app.domain.model.RadioStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "RadioPlayer"

/**
 * Envuelve un [ExoPlayer] local y un [CastPlayer] y expone siempre un único
 * [Player] activo ([activePlayer]) más un [StateFlow] con el estado que la UI
 * necesita. Cuando el usuario conecta con un dispositivo Cast, la
 * reproducción salta automáticamente del móvil al altavoz sin cortar el
 * audio (se reanuda la misma emisora en el punto en que estaba).
 */
class RadioPlayer(
    private val context: Context,
    private val castContext: CastContext?,
) {
    val localPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val castPlayer: CastPlayer? = castContext?.let { CastPlayer(it) }

    var activePlayer: Player = localPlayer
        private set

    private var currentStation: RadioStation? = null

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
            val playbackError = mapExoError(error)
            _uiState.update { it.copy(status = PlaybackStatus.ERROR, errorMessage = playbackError.messageKey) }
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
        val previousPlayer = activePlayer
        val station = currentStation
        val wasPlaying = previousPlayer.isPlaying

        activePlayer = newPlayer
        _uiState.update { it.copy(outputDevice = device, castDeviceName = castDeviceName) }
        onActivePlayerChanged?.invoke(newPlayer)

        if (station != null) {
            val position = if (device == OutputDevice.CAST) 0L else previousPlayer.currentPosition
            newPlayer.setMediaItem(buildMediaItem(station), position)
            newPlayer.prepare()
            newPlayer.playWhenReady = wasPlaying
        }
    }

    private fun buildMediaItem(station: RadioStation): MediaItem =
        MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(Uri.parse(station.streamUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(station.city)
                    .apply { station.logoUrl?.let { setArtworkUri(Uri.parse(it)) } }
                    .build(),
            )
            .build()

    fun playStation(station: RadioStation) {
        currentStation = station
        _uiState.update { it.copy(station = station, status = PlaybackStatus.BUFFERING, errorMessage = null) }
        activePlayer.setMediaItem(buildMediaItem(station))
        activePlayer.prepare()
        activePlayer.playWhenReady = true
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
        activePlayer.stop()
        _uiState.update { it.copy(status = PlaybackStatus.STOPPED) }
    }

    private fun updateStatusFromPlayer() {
        val status = when {
            activePlayer.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            activePlayer.playbackState == Player.STATE_IDLE -> PlaybackStatus.STOPPED
            activePlayer.playWhenReady && activePlayer.isPlaying -> PlaybackStatus.PLAYING
            activePlayer.playbackState == Player.STATE_READY && !activePlayer.playWhenReady -> PlaybackStatus.PAUSED
            else -> _uiState.value.status
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
        localPlayer.removeListener(playerListener)
        castPlayer?.removeListener(playerListener)
        castPlayer?.setSessionAvailabilityListener(null)
        localPlayer.release()
        castPlayer?.release()
    }
}

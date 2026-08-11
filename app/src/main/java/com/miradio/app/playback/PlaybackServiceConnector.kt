package com.miradio.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Puente muy ligero entre [PlaybackService] (que vive mientras dura la
 * reproducción, en primer plano) y la capa de UI. En lugar de montar un
 * [androidx.media3.session.MediaController] asíncrono solo para la propia
 * app -algo pensado sobre todo para exponer control a otros procesos-,
 * la UI observa directamente la instancia de [RadioPlayer] que crea el
 * servicio. El control remoto (notificación, Bluetooth, Android Auto...)
 * sigue funcionando igualmente porque ambos comparten la misma MediaSession.
 */
object PlaybackServiceConnector {
    private val _player = MutableStateFlow<RadioPlayer?>(null)
    val player: StateFlow<RadioPlayer?> = _player

    fun attach(radioPlayer: RadioPlayer) {
        _player.value = radioPlayer
    }

    fun detach() {
        _player.value = null
    }
}

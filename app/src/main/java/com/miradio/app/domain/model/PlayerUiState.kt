package com.miradio.app.domain.model

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    STOPPED,
    ERROR,
}

enum class OutputDevice {
    PHONE,
    CAST,
}

data class PlayerUiState(
    val station: RadioStation? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val outputDevice: OutputDevice = OutputDevice.PHONE,
    val castDeviceName: String? = null,
    val errorMessage: String? = null,
    /** Vive en el reproductor (servicio), no en la pantalla, para que no se
     *  pierda al navegar o si Android recrea el ViewModel. */
    val sleepTimerSecondsLeft: Int? = null,
    /** Retardo actual (segundos) del directo en el móvil. No se aplica al
     *  reproducir por Cast: el dispositivo Cast usa su propio buffer. */
    val playbackDelaySeconds: Int = 0,
    /** Título "artista - canción" que manda el propio stream (metadatos ICY
     *  de Icecast/Shoutcast), si el servidor los envía. Null mientras no ha
     *  llegado ninguno (muchas emisoras no los mandan) o justo tras cambiar
     *  de emisora, hasta que llegue el primero de la nueva. */
    val nowPlayingTitle: String? = null,
)

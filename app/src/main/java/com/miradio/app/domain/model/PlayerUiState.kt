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
)

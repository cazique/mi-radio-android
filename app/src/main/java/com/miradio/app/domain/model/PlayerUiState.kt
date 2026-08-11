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
)

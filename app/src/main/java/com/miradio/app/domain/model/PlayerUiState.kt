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
    /** Volumen del reproductor activo (0f-1f). Funciona igual en el móvil
     *  que en Cast: la propia interfaz Player de Media3 lo traduce al
     *  volumen del altavoz cuando se está reproduciendo por Cast. */
    val volume: Float = 1f,
    /** Posición actual, en milisegundos. Solo tiene sentido junto a
     *  [durationMs]: en un directo de radio no hay nada que arrastrar. */
    val positionMs: Long = 0L,
    /** Duración total, en milisegundos. 0 = contenido en directo (sin
     *  duración fija, como una emisora de radio): la pantalla usa esto para
     *  decidir si mostrar o no la barra de progreso y los botones de
     *  avance/retroceso. Solo es mayor que 0 en contenido bajo demanda
     *  (episodios de podcast, el boletín de noticias). */
    val durationMs: Long = 0L,
)

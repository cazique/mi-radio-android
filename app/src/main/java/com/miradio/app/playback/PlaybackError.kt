package com.miradio.app.playback

/** Errores de reproducción traducidos a algo que la UI puede mostrar sin tecnicismos. */
sealed class PlaybackError(val messageKey: String) {
    data object StreamUnavailable : PlaybackError("error_stream_unavailable")
    data object InvalidUrl : PlaybackError("error_invalid_url")
    data object Timeout : PlaybackError("error_timeout")
    data object StationOffline : PlaybackError("error_station_offline")
    data object CastUnavailable : PlaybackError("error_cast_unavailable")
    data object Generic : PlaybackError("error_generic_playback")
}

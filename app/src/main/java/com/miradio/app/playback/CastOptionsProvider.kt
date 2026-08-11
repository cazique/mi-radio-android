package com.miradio.app.playback

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Configuración de Google Cast. Usamos el receptor multimedia por defecto de
 * Google (admite streams de audio HTTP/HLS estándar como los de esta app),
 * así que no hace falta desplegar una app receptora propia para poder
 * enviar la radio a altavoces Nest, Chromecast o Android TV.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        // OJO: no configurar CastMediaOptions.NotificationOptions aquí. Eso
        // hace que el propio SDK de Cast publique SU notificación propia
        // (independiente de PlaybackService), y como nuestro servicio de
        // Media3 ya mantiene su propia notificación mientras se hace Cast,
        // el resultado eran dos notificaciones duplicadas para lo mismo.
        // Con Media3 basta: DefaultMediaNotificationProvider sigue
        // mostrando (y controlando) la notificación aunque el reproductor
        // activo pase a ser el CastPlayer.
        return CastOptions.Builder()
            .setReceiverApplicationId(com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setResumeSavedSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
